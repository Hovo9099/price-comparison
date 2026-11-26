package am.pricecompare.service;

import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.domain.Category;
import am.pricecompare.domain.Price;
import am.pricecompare.domain.PriceHistory;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.repository.CanonicalProductRepository;
import am.pricecompare.repository.CategoryRepository;
import am.pricecompare.repository.PriceHistoryRepository;
import am.pricecompare.repository.PriceRepository;
import am.pricecompare.repository.ProductAliasRepository;
import am.pricecompare.repository.StoreProductRepository;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.web.NotFoundException;
import am.pricecompare.web.dto.CategoryDto;
import am.pricecompare.web.dto.CheapestDto;
import am.pricecompare.web.dto.PageDto;
import am.pricecompare.web.dto.PriceComparisonDto;
import am.pricecompare.web.dto.PriceDropDto;
import am.pricecompare.web.dto.PriceHistoryDto;
import am.pricecompare.web.dto.ProductDetailDto;
import am.pricecompare.web.dto.ProductOffersDto;
import am.pricecompare.web.dto.StoreListingDto;
import am.pricecompare.web.dto.ProductSummaryDto;
import am.pricecompare.web.dto.StoreDto;
import am.pricecompare.web.dto.StorePriceDto;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read side of the catalog: product details, cross-store price comparison, price history, browsing. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CanonicalProductRepository canonicalRepo;
    private final CategoryRepository categoryRepo;
    private final StoreRepository storeRepo;
    private final StoreProductRepository storeProductRepo;
    private final PriceRepository priceRepo;
    private final PriceHistoryRepository historyRepo;
    private final ProductAliasRepository aliasRepo;
    private final OfferService offerService;
    private final CatalogStatusService catalogStatus;
    private final EntityManager em;

    public CatalogService(CanonicalProductRepository canonicalRepo, CategoryRepository categoryRepo, StoreRepository storeRepo,
                          StoreProductRepository storeProductRepo, PriceRepository priceRepo, PriceHistoryRepository historyRepo,
                          ProductAliasRepository aliasRepo, OfferService offerService, CatalogStatusService catalogStatus,
                          EntityManager em) {
        this.canonicalRepo = canonicalRepo;
        this.categoryRepo = categoryRepo;
        this.storeRepo = storeRepo;
        this.storeProductRepo = storeProductRepo;
        this.priceRepo = priceRepo;
        this.historyRepo = historyRepo;
        this.aliasRepo = aliasRepo;
        this.offerService = offerService;
        this.catalogStatus = catalogStatus;
        this.em = em;
    }

    public CanonicalProduct getProduct(long id) {
        return canonicalRepo.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    public ProductDetailDto detail(long id) {
        return toDetail(getProduct(id));
    }

    /**
     * Product by a scanned barcode: canonical barcode first, then any matched store listing with that barcode.
     * UPC-A (12 digits) and its EAN-13 form (leading 0) are treated as the same code.
     */
    public ProductDetailDto byBarcode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        if (!code.matches("\\d{6,14}")) {
            throw new IllegalArgumentException("Barcode must be 6-14 digits");
        }
        for (String candidate : barcodeVariants(code)) {
            CanonicalProduct c = canonicalRepo.findByBarcodeAndActiveTrue(candidate).stream().findFirst()
                    .or(() -> storeProductRepo.findByBarcodeAndCanonicalProductIsNotNull(candidate).stream()
                            .map(StoreProduct::getCanonicalProduct)
                            .filter(CanonicalProduct::isActive)
                            .findFirst())
                    .orElse(null);
            if (c != null) {
                return toDetail(c);
            }
        }
        throw new NotFoundException("No product with barcode " + code);
    }

    static List<String> barcodeVariants(String code) {
        if (code.length() == 12) {
            return List.of(code, "0" + code);
        }
        if (code.length() == 13 && code.startsWith("0")) {
            return List.of(code, code.substring(1));
        }
        return List.of(code);
    }

    ProductDetailDto toDetail(CanonicalProduct c) {
        Category cat = c.getCategory();
        List<String> aliases = aliasRepo.findByCanonicalProductId(c.getId()).stream().map(a -> a.getAlias()).distinct().toList();
        return new ProductDetailDto(c.getId(), c.getName(), c.getNameEn(), c.getBrand(), c.getBarcode(), c.getSizeValue(),
                c.getSizeUnit(), OfferService.displaySize(c), c.getVariant(), c.isSoldByWeight(), c.getImageUrl(),
                cat == null ? null : new CategoryDto(cat.getId(), cat.getName(), cat.getNameEn(), cat.getSlug(), cat.getIcon(), 0),
                aliases);
    }

    /** Current price at every store that carries the product, cheapest first; includes freshness and age. */
    public PriceComparisonDto compare(long id) {
        CanonicalProduct c = getProduct(id);
        Instant now = Instant.now();
        List<OfferService.Offer> offers = offerService.currentOffers(List.of(id)).getOrDefault(id, List.of());
        OfferService.Offer cheapest = OfferService.cheapest(offers).orElse(null);
        List<StorePriceDto> prices = new ArrayList<>();
        for (OfferService.Offer o : offers) {
            Price p = o.price();
            StoreProduct sp = o.storeProduct();
            Integer discount = p.getOriginalPrice() != null && p.getOriginalPrice().signum() > 0
                    ? p.getOriginalPrice().subtract(p.getPrice()).multiply(BigDecimal.valueOf(100))
                    .divide(p.getOriginalPrice(), 0, RoundingMode.HALF_UP).intValue() : null;
            prices.add(new StorePriceDto(o.store().getId(), o.store().getName(), o.store().getSlug(), sp.getId(),
                    sp.getExternalName(), sp.getExternalUrl(), p.getPrice(), p.getOriginalPrice(), discount, p.getUnitPrice(),
                    p.getUnitPriceUnit(), p.getCurrency(), p.isAvailable() && sp.isActive(), sp.isSoldByWeight(),
                    p.getCapturedAt(), p.getValidFrom(), Duration.between(p.getCapturedAt(), now).toHours(), o.freshness(),
                    cheapest != null && o == cheapest));
        }
        BigDecimal max = offers.stream().filter(OfferService.Offer::usable).map(o -> o.price().getPrice())
                .max(Comparator.naturalOrder()).orElse(null);
        BigDecimal saving = cheapest != null && max != null ? max.subtract(cheapest.price().getPrice()) : null;
        return new PriceComparisonDto(toDetail(c), OfferService.toCheapest(cheapest), max, saving, prices);
    }

    /** Every recorded observation per store within the last {@code days} days. */
    public PriceHistoryDto history(long id, int days) {
        getProduct(id);
        List<StoreProduct> sps = storeProductRepo.findByCanonicalProductIdWithStore(id);
        Map<Long, StoreProduct> byId = sps.stream().collect(Collectors.toMap(StoreProduct::getId, Function.identity()));
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        Map<Long, List<PriceHistoryDto.Point>> points = new LinkedHashMap<>();
        if (!byId.isEmpty()) {
            for (PriceHistory h : historyRepo.findForProducts(byId.keySet(), since)) {
                points.computeIfAbsent(h.getStoreProduct().getId(), k -> new ArrayList<>())
                        .add(new PriceHistoryDto.Point(h.getCapturedAt(), h.getPrice(), h.getOriginalPrice(), h.isAvailable()));
            }
        }
        List<PriceHistoryDto.Series> series = new ArrayList<>();
        for (StoreProduct sp : sps) {
            series.add(new PriceHistoryDto.Series(sp.getStore().getId(), sp.getStore().getName(), sp.getStore().getSlug(),
                    points.getOrDefault(sp.getId(), List.of())));
        }
        return new PriceHistoryDto(id, days, series);
    }

    public List<StoreDto> stores() {
        return catalogStatus.all().stream().map(CatalogService::toStoreDto).toList();
    }

    static StoreDto toStoreDto(CatalogStatusService.StoreCatalog c) {
        var s = c.store();
        return new StoreDto(s.getId(), s.getName(), s.getSlug(), s.getWebsiteUrl(), s.getLogoUrl(), s.isActive(), c.implemented(),
                c.state(), c.reason(), c.listings(), c.pricedListings(), c.lastCompleteCrawl(), c.lastPriceUpdate());
    }

    /**
     * One row per supermarket for this product: the store's own listing(s) and price, or why there is none. "Not found"
     * is only reported for stores whose whole catalog was crawled.
     */
    public ProductOffersDto offers(long id) {
        CanonicalProduct c = getProduct(id);
        List<OfferService.Offer> offers = offerService.currentOffers(List.of(id)).getOrDefault(id, List.of());
        Map<Long, List<OfferService.Offer>> byStore = new LinkedHashMap<>();
        for (OfferService.Offer o : offers) {
            byStore.computeIfAbsent(o.store().getId(), k -> new ArrayList<>()).add(o);
        }
        List<ProductOffersDto.StoreRow> rows = new ArrayList<>();
        int withOffer = 0;
        for (CatalogStatusService.StoreCatalog sc : catalogStatus.all()) {
            var store = sc.store();
            List<OfferService.Offer> own = byStore.getOrDefault(store.getId(), List.of());
            ProductOffersDto.RowStatus status;
            String message = null;
            if (!own.isEmpty()) {
                List<OfferService.Offer> sorted = own.stream()
                        .sorted(java.util.Comparator.comparing((OfferService.Offer o) -> !o.usable()).thenComparing(o -> o.price().getPrice()))
                        .toList();
                OfferService.Offer best = sorted.get(0);
                if (best.usable()) {
                    status = ProductOffersDto.RowStatus.AVAILABLE;
                    withOffer++;
                } else if (!best.price().isAvailable() || !best.storeProduct().isActive()) {
                    status = ProductOffersDto.RowStatus.OUT_OF_STOCK;
                    message = best.storeProduct().isActive() ? "The store marks it out of stock" : "No longer listed in the store's catalog";
                } else {
                    status = ProductOffersDto.RowStatus.OUTDATED;
                    message = "Price is too old to compare";
                }
                rows.add(new ProductOffersDto.StoreRow(store.getId(), store.getName(), store.getSlug(), status, message,
                        sorted.stream().map(OfferService::toOfferDto).toList()));
                continue;
            }
            switch (sc.state()) {
                case COMPLETE -> {
                    status = ProductOffersDto.RowStatus.NOT_FOUND;
                    message = "Not found in the store's catalog (last full crawl " + sc.lastCompleteCrawl() + ")";
                }
                case PARTIAL, NOT_IMPORTED -> {
                    status = ProductOffersDto.RowStatus.NOT_YET_IMPORTED;
                    message = sc.reason();
                }
                case UNAVAILABLE -> {
                    status = ProductOffersDto.RowStatus.CATALOG_UNAVAILABLE;
                    message = sc.reason();
                }
                default -> {
                    status = ProductOffersDto.RowStatus.NOT_INTEGRATED;
                    message = sc.reason();
                }
            }
            rows.add(new ProductOffersDto.StoreRow(store.getId(), store.getName(), store.getSlug(), status, message, List.of()));
        }
        // stores with a price first (cheapest first), then the rest in store order
        List<ProductOffersDto.StoreRow> ordered = new ArrayList<>(rows);
        ordered.sort(java.util.Comparator.comparingInt((ProductOffersDto.StoreRow r) -> r.status().ordinal())
                .thenComparing(r -> r.offers().isEmpty() ? BigDecimal.ZERO : r.offers().get(0).price()));
        return new ProductOffersDto(toDetail(c), OfferService.toCheapest(OfferService.cheapest(offers).orElse(null)), withOffer, ordered);
    }

    /** A store's own listings with current prices (paged, optionally filtered by name). */
    public PageDto<StoreListingDto> storeListings(long storeId, String q, boolean availableOnly, int page, int size) {
        storeRepo.findById(storeId).orElseThrow(() -> new NotFoundException("Store " + storeId + " not found"));
        String filter = " from StoreProduct sp join sp.store st, Price p where p.storeProduct.id = sp.id and p.validTo is null "
                + "and st.id = :storeId" + (availableOnly ? " and sp.active = true and p.available = true" : "")
                + (q != null && !q.isBlank() ? " and (lower(sp.externalName) like :q or lower(sp.externalNameEn) like :q or sp.barcode = :raw "
                + "or sp.externalProductId = :raw)" : "");
        var count = em.createQuery("select count(sp)" + filter, Long.class).setParameter("storeId", storeId);
        var rows = em.createQuery("select sp, p" + filter + " order by sp.externalName, sp.id", Object[].class).setParameter("storeId", storeId);
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            count.setParameter("q", like).setParameter("raw", q.trim());
            rows.setParameter("q", like).setParameter("raw", q.trim());
        }
        Instant now = Instant.now();
        List<StoreListingDto> items = rows.setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(r -> {
            StoreProduct sp = (StoreProduct) r[0];
            Price p = (Price) r[1];
            OfferService.Offer o = new OfferService.Offer(sp, sp.getStore(), p, Freshness.of(p.getCapturedAt(), now));
            return new StoreListingDto(sp.getCanonicalProduct() == null ? null : sp.getCanonicalProduct().getId(), OfferService.toOfferDto(o));
        }).toList();
        return new PageDto<>(items, page, size, count.getSingleResult());
    }

    public List<am.pricecompare.web.dto.StoreLocationDto> locations(long storeId) {
        storeRepo.findById(storeId).orElseThrow(() -> new NotFoundException("Store " + storeId + " not found"));
        return em.createQuery("select l from StoreLocation l where l.store.id = :id and l.active = true order by l.name",
                        am.pricecompare.domain.StoreLocation.class)
                .setParameter("id", storeId).getResultList().stream()
                .map(l -> new am.pricecompare.web.dto.StoreLocationDto(l.getId(), l.getName(), l.getAddress(), l.getLatitude(), l.getLongitude()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<CategoryDto> categories() {
        Map<Long, Long> counts = new HashMap<>();
        List<Object[]> rows = em.createNativeQuery("select cp.category_id, count(*) from canonical_products cp "
                + "where cp.active and exists (select 1 from store_products sp join prices p on p.store_product_id = sp.id "
                + "and p.valid_to is null and p.available where sp.canonical_product_id = cp.id) group by cp.category_id").getResultList();
        for (Object[] r : rows) {
            if (r[0] != null) {
                counts.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
            }
        }
        return categoryRepo.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(c -> new CategoryDto(c.getId(), c.getName(), c.getNameEn(), c.getSlug(), c.getIcon(), counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    /** Products of a category, those sold by the most stores first. */
    @SuppressWarnings("unchecked")
    public PageDto<ProductSummaryDto> browse(Long categoryId, int page, int size) {
        String where = " where cp.active" + (categoryId != null ? " and cp.category_id = :cat" : "")
                + " and exists (select 1 from store_products sp join prices p on p.store_product_id = sp.id and p.valid_to is null "
                + "and p.available where sp.canonical_product_id = cp.id)";
        var countQuery = em.createNativeQuery("select count(*) from canonical_products cp" + where);
        var idQuery = em.createNativeQuery("select cp.id from canonical_products cp" + where
                + " order by (select count(distinct sp2.store_id) from store_products sp2 where sp2.canonical_product_id = cp.id"
                + " and sp2.active) desc, cp.id limit :lim offset :off");
        if (categoryId != null) {
            countQuery.setParameter("cat", categoryId);
            idQuery.setParameter("cat", categoryId);
        }
        idQuery.setParameter("lim", size).setParameter("off", page * size);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        List<Long> ids = ((List<Number>) idQuery.getResultList()).stream().map(Number::longValue).toList();
        Map<Long, CanonicalProduct> byId = canonicalRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(CanonicalProduct::getId, Function.identity()));
        List<CanonicalProduct> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return new PageDto<>(offerService.summaries(ordered), page, size, total);
    }

    /** Biggest current price drops (vs the previous price period) that started within {@code days} days. */
    public List<PriceDropDto> priceDrops(int days, int limit) {
        List<Object[]> rows = priceRepo.findRecentDrops(Instant.now().minus(days, ChronoUnit.DAYS), limit);
        List<PriceDropDto> out = new ArrayList<>();
        Map<Long, BigDecimal> previousById = new LinkedHashMap<>();
        for (Object[] r : rows) {
            previousById.put(((Number) r[0]).longValue(), (BigDecimal) r[1]);
        }
        if (previousById.isEmpty()) {
            return out;
        }
        List<Price> prices = priceRepo.findAllById(previousById.keySet());
        Map<Long, Price> priceById = prices.stream().collect(Collectors.toMap(Price::getId, Function.identity()));
        List<Long> canonicalIds = prices.stream().map(p -> p.getStoreProduct().getCanonicalProduct().getId()).distinct().toList();
        Map<Long, List<OfferService.Offer>> offers = offerService.currentOffers(canonicalIds);
        Instant now = Instant.now();
        for (Map.Entry<Long, BigDecimal> e : previousById.entrySet()) {
            Price p = priceById.get(e.getKey());
            if (p == null || !Freshness.of(p.getCapturedAt(), now).usableForComparison()) {
                continue;
            }
            StoreProduct sp = p.getStoreProduct();
            CanonicalProduct c = sp.getCanonicalProduct();
            ProductSummaryDto summary = OfferService.summary(c, offers.getOrDefault(c.getId(), List.of()));
            int pct = e.getValue().subtract(p.getPrice()).multiply(BigDecimal.valueOf(100))
                    .divide(e.getValue(), 0, RoundingMode.HALF_UP).intValue();
            out.add(new PriceDropDto(summary, sp.getStore().getId(), sp.getStore().getName(), e.getValue(), p.getPrice(), pct, p.getValidFrom()));
        }
        return out;
    }

    public CheapestDto cheapestFor(long canonicalId) {
        return OfferService.toCheapest(OfferService.cheapest(
                offerService.currentOffers(List.of(canonicalId)).getOrDefault(canonicalId, List.of())).orElse(null));
    }
}
