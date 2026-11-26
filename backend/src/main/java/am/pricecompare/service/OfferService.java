package am.pricecompare.service;

import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.domain.Price;
import am.pricecompare.domain.Store;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.normalize.ProductNormalizer;
import am.pricecompare.web.dto.CheapestDto;
import am.pricecompare.web.dto.OfferDto;
import am.pricecompare.web.dto.ProductSummaryDto;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads current store prices ("offers") for canonical products and builds product summaries. */
@Service
@Transactional(readOnly = true)
public class OfferService {

    private final EntityManager em;

    public OfferService(EntityManager em) {
        this.em = em;
    }

    public record Offer(StoreProduct storeProduct, Store store, Price price, Freshness freshness) {
        /** Available, listed, not outdated and not flagged as a suspicious price. */
        public boolean usable() {
            return price.isAvailable() && !price.isSuspect() && storeProduct.isActive() && freshness.usableForComparison();
        }
    }

    /** canonicalId -> current offers, one per store product, sorted by price. */
    public Map<Long, List<Offer>> currentOffers(Collection<Long> canonicalIds) {
        Map<Long, List<Offer>> out = new HashMap<>();
        if (canonicalIds.isEmpty()) {
            return out;
        }
        Instant now = Instant.now();
        List<Object[]> rows = em.createQuery("select sp, p from StoreProduct sp join fetch sp.store, Price p "
                        + "where p.storeProduct.id = sp.id and p.validTo is null and sp.canonicalProduct.id in :ids", Object[].class)
                .setParameter("ids", canonicalIds)
                .getResultList();
        for (Object[] r : rows) {
            StoreProduct sp = (StoreProduct) r[0];
            Price p = (Price) r[1];
            out.computeIfAbsent(sp.getCanonicalProduct().getId(), k -> new ArrayList<>())
                    .add(new Offer(sp, sp.getStore(), p, Freshness.of(p.getCapturedAt(), now)));
        }
        out.values().forEach(l -> l.sort(Comparator.comparing((Offer o) -> !o.usable()).thenComparing(o -> o.price().getPrice())));
        return out;
    }

    /** Cheapest usable offer per store. */
    public static Map<Long, Offer> bestPerStore(List<Offer> offers) {
        Map<Long, Offer> best = new LinkedHashMap<>();
        for (Offer o : offers) {
            if (!o.usable()) {
                continue;
            }
            best.merge(o.store().getId(), o, (a, b) -> a.price().getPrice().compareTo(b.price().getPrice()) <= 0 ? a : b);
        }
        return best;
    }

    public static Optional<Offer> cheapest(List<Offer> offers) {
        return offers.stream().filter(Offer::usable).min(Comparator.comparing(o -> o.price().getPrice()));
    }

    public static CheapestDto toCheapest(Offer o) {
        return o == null ? null : new CheapestDto(o.price().getPrice(), o.price().getCurrency(), o.store().getId(),
                o.store().getName(), o.store().getSlug(), o.price().getCapturedAt(), o.freshness());
    }

    public List<ProductSummaryDto> summaries(List<CanonicalProduct> products) {
        Map<Long, List<Offer>> offers = currentOffers(products.stream().map(CanonicalProduct::getId).toList());
        List<ProductSummaryDto> out = new ArrayList<>();
        for (CanonicalProduct c : products) {
            out.add(summary(c, offers.getOrDefault(c.getId(), List.of())));
        }
        return out;
    }

    public static ProductSummaryDto summary(CanonicalProduct c, List<Offer> offers) {
        Map<Long, Offer> perStore = bestPerStore(offers);
        Offer cheapest = perStore.values().stream().min(Comparator.comparing(o -> o.price().getPrice())).orElse(null);
        BigDecimal max = perStore.values().stream().map(o -> o.price().getPrice()).max(Comparator.naturalOrder()).orElse(null);
        return new ProductSummaryDto(c.getId(), c.getName(), c.getNameEn(), c.getBrand(),
                displaySize(c), c.isSoldByWeight(), c.getImageUrl(),
                c.getCategory() == null ? null : c.getCategory().getId(), toCheapest(cheapest), max, perStore.size(),
                offerPerStore(offers).stream().map(OfferService::toOfferDto).toList());
    }

    /**
     * One offer per store: the cheapest usable listing, or (when the store has none usable) its cheapest listing so
     * the app can show "out of stock" / "outdated" for that store. Usable offers first, then by price.
     */
    public static List<Offer> offerPerStore(List<Offer> offers) {
        Map<Long, Offer> best = new LinkedHashMap<>();
        for (Offer o : offers) {
            best.merge(o.store().getId(), o, (a, b) -> {
                if (a.usable() != b.usable()) {
                    return a.usable() ? a : b;
                }
                return a.price().getPrice().compareTo(b.price().getPrice()) <= 0 ? a : b;
            });
        }
        List<Offer> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparing((Offer o) -> !o.usable()).thenComparing(o -> o.price().getPrice()));
        return out;
    }

    public static OfferDto toOfferDto(Offer o) {
        StoreProduct sp = o.storeProduct();
        Price p = o.price();
        Integer discount = p.getOriginalPrice() != null && p.getOriginalPrice().compareTo(p.getPrice()) > 0
                ? p.getOriginalPrice().subtract(p.getPrice()).multiply(BigDecimal.valueOf(100))
                .divide(p.getOriginalPrice(), 0, java.math.RoundingMode.HALF_UP).intValue() : null;
        String size = sp.isSoldByWeight() ? "1 կգ" : ProductNormalizer.formatSize(sp.getPackageSize(), sp.getPackageUnit());
        return new OfferDto(o.store().getId(), o.store().getName(), o.store().getSlug(), sp.getId(), sp.getExternalProductId(),
                sp.getExternalName(), sp.getBrand(), size, sp.getImageUrl(), sp.getExternalUrl(), p.getPrice(), p.getOriginalPrice(),
                discount, p.getUnitPrice(), p.getUnitPriceUnit(), p.getCurrency(), p.isAvailable() && sp.isActive(), o.usable(),
                p.getCapturedAt(), o.freshness());
    }

    public static String displaySize(CanonicalProduct c) {
        if (c.isSoldByWeight()) {
            return "1 կգ";
        }
        String size = ProductNormalizer.formatSize(c.getSizeValue(), c.getSizeUnit());
        if (size != null && c.getVariant() != null && c.getVariant().endsWith("x")) {
            size = c.getVariant().replaceAll(".*?(\\d+x)$", "$1") + size;
        }
        return size;
    }
}
