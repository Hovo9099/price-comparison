package am.pricecompare.service;

import am.pricecompare.domain.Price;
import am.pricecompare.domain.PriceAnomaly;
import am.pricecompare.domain.PriceAnomaly.Kind;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.repository.PriceAnomalyRepository;
import am.pricecompare.repository.PriceRepository;
import am.pricecompare.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;

/**
 * Flags implausible prices so that a crawler or store-side error never becomes "the cheapest price".
 * A flagged price is kept as observed (history is never rewritten) but marked {@code suspect} until reviewed.
 */
@Service
public class PriceAnomalyService {

    private static final Logger log = LoggerFactory.getLogger(PriceAnomalyService.class);
    static final BigDecimal DROP_RATIO = new BigDecimal("0.40");
    static final BigDecimal RISE_RATIO = new BigDecimal("2.5");
    static final BigDecimal BELOW_MARKET_RATIO = new BigDecimal("0.25");
    static final BigDecimal ABOVE_MARKET_RATIO = new BigDecimal("4");
    static final int MIN_OTHER_STORES = 2;

    private final PriceAnomalyRepository anomalyRepo;
    private final PriceRepository priceRepo;
    private final OfferService offerService;

    public PriceAnomalyService(PriceAnomalyRepository anomalyRepo, PriceRepository priceRepo, OfferService offerService) {
        this.anomalyRepo = anomalyRepo;
        this.priceRepo = priceRepo;
        this.offerService = offerService;
    }

    /** Change vs the listing's own last trusted price: ≤ 40 % of it or ≥ 2.5 × it. */
    static Kind classifyChange(BigDecimal reference, BigDecimal price) {
        if (reference == null || price == null || reference.signum() <= 0) {
            return null;
        }
        BigDecimal ratio = price.divide(reference, MathContext.DECIMAL64);
        if (ratio.compareTo(DROP_RATIO) <= 0) {
            return Kind.SUDDEN_DROP;
        }
        return ratio.compareTo(RISE_RATIO) >= 0 ? Kind.SUDDEN_RISE : null;
    }

    /** Price vs the median of other stores' current prices for the same product: ≤ 25 % or ≥ 4 ×. */
    static Kind classifyMarket(BigDecimal median, BigDecimal price) {
        if (median == null || price == null || median.signum() <= 0) {
            return null;
        }
        BigDecimal ratio = price.divide(median, MathContext.DECIMAL64);
        if (ratio.compareTo(BELOW_MARKET_RATIO) <= 0) {
            return Kind.BELOW_MARKET;
        }
        return ratio.compareTo(ABOVE_MARKET_RATIO) >= 0 ? Kind.ABOVE_MARKET : null;
    }

    static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
    }

    /** Called by the price recorder right after a new price period was opened. */
    @Transactional
    public void onNewPeriod(StoreProduct sp, Price period, BigDecimal lastTrusted) {
        anomalyRepo.supersede(sp.getId(), Instant.now());
        if (period.isAvailable()) {
            Kind kind = classifyChange(lastTrusted, period.getPrice());
            if (kind != null) {
                flag(sp, period, kind, lastTrusted);
            }
        }
    }

    /** Called after matching, when the listing's price changed: compares it with the other stores. */
    @Transactional
    public void checkAgainstMarket(StoreProduct sp) {
        if (sp.getCanonicalProduct() == null) {
            return;
        }
        Price current = priceRepo.findCurrent(sp.getId()).orElse(null);
        if (current == null || !current.isAvailable() || current.isSuspect() || anomalyRepo.existsByPriceId(current.getId())) {
            return;
        }
        long canonicalId = sp.getCanonicalProduct().getId();
        List<BigDecimal> others = offerService.currentOffers(List.of(canonicalId)).getOrDefault(canonicalId, List.of()).stream()
                .filter(OfferService.Offer::usable)
                .filter(o -> !o.storeProduct().getId().equals(sp.getId()))
                .filter(o -> o.storeProduct().isSoldByWeight() == sp.isSoldByWeight())
                .map(o -> o.price().getPrice())
                .toList();
        if (others.size() < MIN_OTHER_STORES) {
            return;
        }
        BigDecimal median = median(others);
        Kind kind = classifyMarket(median, current.getPrice());
        if (kind != null) {
            flag(sp, current, kind, median);
        }
    }

    private void flag(StoreProduct sp, Price price, Kind kind, BigDecimal reference) {
        price.setSuspect(true);
        priceRepo.save(price);
        PriceAnomaly a = new PriceAnomaly();
        a.setPrice(price);
        a.setStoreProduct(sp);
        a.setKind(kind);
        a.setNewPrice(price.getPrice());
        a.setReferencePrice(reference.setScale(2, java.math.RoundingMode.HALF_UP));
        anomalyRepo.save(a);
        log.info("Suspicious price for store product {}: {} {} (reference {})", sp.getId(), kind, price.getPrice(), reference);
    }

    /** The price is real: it becomes usable again. */
    @Transactional
    public PriceAnomaly confirm(long id) {
        PriceAnomaly a = get(id);
        a.setStatus(PriceAnomaly.Status.CONFIRMED);
        a.setReviewedAt(Instant.now());
        a.getPrice().setSuspect(false);
        return a;
    }

    /** The price is wrong: it stays excluded. */
    @Transactional
    public PriceAnomaly reject(long id) {
        PriceAnomaly a = get(id);
        a.setStatus(PriceAnomaly.Status.REJECTED);
        a.setReviewedAt(Instant.now());
        return a;
    }

    private PriceAnomaly get(long id) {
        PriceAnomaly a = anomalyRepo.findById(id).orElseThrow(() -> new NotFoundException("Anomaly " + id + " not found"));
        if (a.getStatus() != PriceAnomaly.Status.PENDING) {
            throw new IllegalStateException("Anomaly " + id + " is already " + a.getStatus());
        }
        return a;
    }
}
