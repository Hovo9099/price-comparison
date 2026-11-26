package am.pricecompare.ingest;

import am.pricecompare.crawler.DiscoveredProduct;
import am.pricecompare.domain.Price;
import am.pricecompare.domain.RawProductSnapshot;
import am.pricecompare.domain.Store;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.matching.MatchingService;
import am.pricecompare.normalize.ParsedName;
import am.pricecompare.normalize.ProductNormalizer;
import am.pricecompare.repository.PriceRepository;
import am.pricecompare.repository.RawProductSnapshotRepository;
import am.pricecompare.repository.StoreProductRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalizes one discovered product and stores it: store product upsert, price period / history,
 * raw snapshot, and canonical matching. Each product runs in its own transaction so one bad product
 * never aborts a crawl.
 */
@Service
public class ProductIngestionService {

    private final StoreProductRepository storeProductRepo;
    private final RawProductSnapshotRepository snapshotRepo;
    private final PriceRepository priceRepo;
    private final PriceRecorder priceRecorder;
    private final MatchingService matchingService;
    private final EntityManager em;

    public ProductIngestionService(StoreProductRepository storeProductRepo, RawProductSnapshotRepository snapshotRepo,
                                   PriceRepository priceRepo, PriceRecorder priceRecorder,
                                   MatchingService matchingService, EntityManager em) {
        this.storeProductRepo = storeProductRepo;
        this.snapshotRepo = snapshotRepo;
        this.priceRepo = priceRepo;
        this.priceRecorder = priceRecorder;
        this.matchingService = matchingService;
        this.em = em;
    }

    public record Result(boolean newProduct, boolean priceChanged, MatchingService.Outcome match) {
    }

    /**
     * Callers hold this lock around {@link #ingest} so ingestion transactions of concurrent crawls are serialized:
     * matching must see every committed canonical product and never another crawl's uncommitted one.
     */
    public static final Object LOCK = new Object();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result ingest(long storeId, Long runId, DiscoveredProduct p, boolean storeRawSnapshot) {
        validate(p);
        Store store = em.getReference(Store.class, storeId);
        Instant now = Instant.now();
        Instant captured = p.capturedAt() != null ? p.capturedAt() : now;

        StoreProduct sp = storeProductRepo.findByStoreIdAndExternalProductId(storeId, p.externalId()).orElse(null);
        boolean isNew = sp == null;
        if (isNew) {
            sp = new StoreProduct();
            sp.setStore(store);
            sp.setExternalProductId(p.externalId());
        }
        String oldNameEn = sp.getExternalNameEn();
        String oldName = sp.getExternalName();
        apply(sp, p, captured, now);
        sp = storeProductRepo.save(sp);

        if (storeRawSnapshot) {
            RawProductSnapshot snap = new RawProductSnapshot();
            snap.setStoreId(storeId);
            snap.setCrawlRunId(runId);
            snap.setExternalProductId(p.externalId());
            snap.setSourceUrl(truncate(p.url(), 1000));
            snap.setRawName(truncate(p.name(), 500));
            snap.setRawPrice(p.price() == null ? null : p.price().toPlainString());
            snap.setRawOriginalPrice(p.originalPrice() == null ? null : p.originalPrice().toPlainString());
            snap.setRawData(p.rawData());
            snap.setCapturedAt(captured);
            snapshotRepo.save(snap);
        }

        boolean changed;
        if (p.price() != null) {
            changed = priceRecorder.record(sp, new PriceRecorder.Observation(
                    money(p.price()), money(p.originalPrice()), money(p.unitPrice()), p.unitPriceUnit(),
                    p.currency(), p.available(), captured), runId);
        } else {
            // Listed as unavailable without a price: close the available period using the last known price.
            changed = recordUnavailable(sp, captured, runId);
        }

        MatchingService.Outcome outcome = null;
        if (sp.getCanonicalProduct() == null) {
            outcome = matchingService.matchAndLink(sp);
        } else if (!Objects.equals(oldNameEn, sp.getExternalNameEn()) || !Objects.equals(oldName, sp.getExternalName())) {
            matchingService.onStoreProductNamesChanged(sp);
        }
        return new Result(isNew, changed, outcome);
    }

    private static void validate(DiscoveredProduct p) {
        if (p.externalId() == null || p.externalId().isBlank()) {
            throw new IllegalArgumentException("Missing external product id");
        }
        if (p.name() == null || p.name().isBlank()) {
            throw new IllegalArgumentException("Missing product name for " + p.externalId());
        }
        if (p.price() == null ? p.available() : p.price().signum() <= 0) {
            throw new IllegalArgumentException("Missing or non-positive price for " + p.externalId());
        }
    }

    /** Copies observed values; never replaces a known value with an unknown (null) one. */
    private static void apply(StoreProduct sp, DiscoveredProduct p, Instant captured, Instant now) {
        sp.setExternalName(truncate(p.name().trim(), 500));
        if (p.nameEn() != null && !p.nameEn().isBlank()) {
            sp.setExternalNameEn(truncate(p.nameEn().trim(), 500));
        }
        if (p.sku() != null) {
            sp.setExternalSku(truncate(p.sku(), 100));
        }
        if (p.barcode() != null && !p.barcode().isBlank()) {
            sp.setBarcode(truncate(p.barcode().trim(), 50));
        }
        if (p.brand() != null && !p.brand().isBlank()) {
            sp.setBrand(truncate(p.brand().trim(), 200));
        }
        if (p.url() != null) {
            sp.setExternalUrl(truncate(p.url(), 1000));
        }
        if (p.imageUrl() != null) {
            sp.setImageUrl(truncate(p.imageUrl(), 1000));
        }
        if (p.categoryPath() != null) {
            sp.setExternalCategory(truncate(p.categoryPath(), 500));
        }
        sp.setSoldByWeight(p.soldByWeight());
        if (p.packageSize() != null) {
            sp.setPackageSize(p.packageSize());
            sp.setPackageUnit(p.packageUnit());
        } else if (!p.soldByWeight()) {
            ParsedName en = p.nameEn() == null ? null : ProductNormalizer.parse(p.nameEn());
            ParsedName hy = ProductNormalizer.parse(p.name());
            ParsedName src = en != null && en.hasSize() ? en : hy;
            if (src.hasSize()) {
                sp.setPackageSize(src.sizeValue());
                sp.setPackageUnit(src.sizeUnit());
            }
        } else {
            sp.setPackageSize(null);
            sp.setPackageUnit(p.packageUnit());
        }
        if (p.detailsFetched()) {
            sp.setDetailsFetchedAt(now);
        }
        sp.setLastSeenAt(captured);
        sp.setActive(true);
        sp.setUpdatedAt(now);
    }

    private boolean recordUnavailable(StoreProduct sp, Instant captured, Long runId) {
        Price current = priceRepo.findCurrent(sp.getId()).orElse(null);
        if (current == null) {
            return false; // never seen with a price: nothing to compare yet
        }
        return priceRecorder.record(sp, new PriceRecorder.Observation(current.getPrice(), current.getOriginalPrice(),
                current.getUnitPrice(), current.getUnitPriceUnit(), current.getCurrency(), false, captured), runId);
    }

    /** Marks a product that disappeared from the store's catalog as unavailable (history is kept). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markUnavailable(long storeProductId, Long runId) {
        StoreProduct sp = storeProductRepo.findById(storeProductId).orElse(null);
        if (sp == null) {
            return false;
        }
        sp.setActive(false);
        sp.setUpdatedAt(Instant.now());
        Price current = priceRepo.findCurrent(sp.getId()).orElse(null);
        if (current == null || !current.isAvailable()) {
            return false;
        }
        return priceRecorder.record(sp, new PriceRecorder.Observation(current.getPrice(), current.getOriginalPrice(),
                current.getUnitPrice(), current.getUnitPriceUnit(), current.getCurrency(), false, Instant.now()), runId);
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
