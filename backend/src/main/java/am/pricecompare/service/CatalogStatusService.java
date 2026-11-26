package am.pricecompare.service;

import am.pricecompare.crawler.SupermarketProvider;
import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.domain.Store;
import am.pricecompare.repository.CrawlRunRepository;
import am.pricecompare.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What we actually know about each store's catalog. The app and the admin dashboard use it to tell "the store does
 * not sell this" (only after a complete crawl) apart from "we could not read the store" or "not imported yet".
 */
@Service
@Transactional(readOnly = true)
public class CatalogStatusService {

    public enum CatalogState {
        /** An unlimited crawl walked the whole public catalog. */
        COMPLETE,
        /** Listings exist (limited/dev crawl, cancelled crawl or CSV) but no crawl has covered the whole catalog. */
        PARTIAL,
        /** A provider exists but nothing has been imported yet. */
        NOT_IMPORTED,
        /** The provider exists but every attempt failed and no listings are stored. */
        UNAVAILABLE,
        /** No catalog parser exists for this store (e.g. the site serves no public catalog). */
        NOT_IMPLEMENTED
    }

    /**
     * @param listings          store-specific listings ever imported
     * @param pricedListings    active listings with a current, available price
     * @param lastCompleteCrawl end of the last crawl that covered the whole catalog
     * @param lastPriceUpdate   newest price observation of this store
     */
    public record StoreCatalog(Store store, boolean implemented, CatalogState state, String reason, long listings,
                               long activeListings, long pricedListings, Instant lastCompleteCrawl, Instant lastPriceUpdate,
                               CrawlRun lastRun, CrawlRun lastCompleteRun) {

        /** True when a missing product can be reported as "not sold here". */
        public boolean catalogChecked() {
            return state == CatalogState.COMPLETE;
        }
    }

    private static final long CACHE_MILLIS = 30_000;

    private final StoreRepository storeRepo;
    private final CrawlRunRepository runRepo;
    private final EntityManager em;
    private final Map<String, SupermarketProvider> providers;
    private volatile List<StoreCatalog> cached;
    private volatile long cachedAt;

    public CatalogStatusService(StoreRepository storeRepo, CrawlRunRepository runRepo, EntityManager em,
                                List<SupermarketProvider> providers) {
        this.storeRepo = storeRepo;
        this.runRepo = runRepo;
        this.em = em;
        this.providers = providers.stream().collect(Collectors.toMap(SupermarketProvider::getStoreCode, Function.identity()));
    }

    /** Every store, in id order. Cached briefly because search calls it on every request. */
    public List<StoreCatalog> all() {
        List<StoreCatalog> c = cached;
        if (c != null && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) {
            return c;
        }
        c = compute();
        cached = c;
        cachedAt = System.currentTimeMillis();
        return c;
    }

    public Map<Long, StoreCatalog> byStoreId() {
        return all().stream().collect(Collectors.toMap(s -> s.store().getId(), Function.identity(), (a, b) -> a,
                java.util.LinkedHashMap::new));
    }

    public void invalidate() {
        cached = null;
    }

    private record Counts(long listings, long active, long priced, Instant lastPrice) {
    }

    @SuppressWarnings("unchecked")
    private List<StoreCatalog> compute() {
        Map<Long, Counts> counts = new HashMap<>();
        List<Object[]> rows = em.createNativeQuery("select sp.store_id, count(*), count(*) filter (where sp.active), "
                + "count(*) filter (where sp.active and p.available), max(p.captured_at) "
                + "from store_products sp left join prices p on p.store_product_id = sp.id and p.valid_to is null "
                + "group by sp.store_id").getResultList();
        for (Object[] r : rows) {
            counts.put(((Number) r[0]).longValue(), new Counts(((Number) r[1]).longValue(), ((Number) r[2]).longValue(),
                    ((Number) r[3]).longValue(), toInstant(r[4])));
        }
        List<StoreCatalog> out = new ArrayList<>();
        for (Store s : storeRepo.findAllByOrderByIdAsc()) {
            SupermarketProvider p = providers.get(s.getSlug());
            boolean implemented = p != null && p.isImplemented();
            Counts c = counts.getOrDefault(s.getId(), new Counts(0, 0, 0, null));
            CrawlRun last = runRepo.findFirstByStoreIdOrderByStartedAtDesc(s.getId()).orElse(null);
            CrawlRun complete = runRepo.findCompleteCrawls(s.getId(), PageRequest.of(0, 1)).stream().findFirst().orElse(null);
            CatalogState state;
            String reason;
            if (complete != null) {
                state = CatalogState.COMPLETE;
                reason = last != null && !last.getId().equals(complete.getId()) && (last.getStatus() == CrawlStatus.FAILED || last.getStatus() == CrawlStatus.UNAVAILABLE)
                        ? "Վերջին ստուգումը ձախողվել է (" + last.getErrorMessage() + ")․ տվյալները վերջին ամբողջական ստուգումից են"
                        : null;
            } else if (c.listings() > 0) {
                state = CatalogState.PARTIAL;
                reason = "Կատալոգի միայն մի մասն է ներմուծված․ բացակայող ապրանքը կարող է վաճառվել այստեղ";
            } else if (!implemented) {
                state = CatalogState.NOT_IMPLEMENTED;
                reason = p == null ? "Այս խանութի համար քրոլեր չկա"
                        : p.unavailableReason() != null ? p.unavailableReason() : "Այս խանութի կատալոգը միացված չէ";
            } else if (last != null && (last.getStatus() == CrawlStatus.FAILED || last.getStatus() == CrawlStatus.UNAVAILABLE)) {
                state = CatalogState.UNAVAILABLE;
                reason = last.getErrorMessage() != null ? last.getErrorMessage() : "Վերջին ստուգումը ձախողվել է";
            } else {
                state = CatalogState.NOT_IMPORTED;
                reason = last != null && last.getStatus() == CrawlStatus.RUNNING ? "Առաջին ներմուծումն ընթացքում է" : "Կատալոգը դեռ ներմուծված չէ";
            }
            out.add(new StoreCatalog(s, implemented, state, reason, c.listings(), c.active(), c.priced(),
                    complete == null ? null : complete.getFinishedAt(), c.lastPrice(), last, complete));
        }
        return out;
    }

    private static Instant toInstant(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof Timestamp t) {
            return t.toInstant();
        }
        if (o instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return Instant.parse(o.toString());
    }
}
