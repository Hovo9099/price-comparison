package am.pricecompare.ingest;

import am.pricecompare.domain.Price;
import am.pricecompare.domain.PriceHistory;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.repository.PriceHistoryRepository;
import am.pricecompare.repository.PriceRepository;
import am.pricecompare.service.PriceAnomalyService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Records price observations without ever losing history.
 *
 * <ul>
 *   <li>{@code price_history}: one immutable row per observation (per product per run).</li>
 *   <li>{@code prices}: price periods. When price, original price or availability changes, the current period
 *       is closed ({@code valid_to}) and a new one is opened. An unchanged observation only refreshes
 *       {@code captured_at}, which drives freshness.</li>
 * </ul>
 */
@Component
public class PriceRecorder {

    private final PriceRepository priceRepo;
    private final PriceHistoryRepository historyRepo;
    private final PriceAnomalyService anomalies;

    public PriceRecorder(PriceRepository priceRepo, PriceHistoryRepository historyRepo, PriceAnomalyService anomalies) {
        this.priceRepo = priceRepo;
        this.historyRepo = historyRepo;
        this.anomalies = anomalies;
    }

    public record Observation(BigDecimal price, BigDecimal originalPrice, BigDecimal unitPrice, String unitPriceUnit,
                              String currency, boolean available, Instant capturedAt) {
    }

    /** @return true when a new price period was opened (price or availability changed, or first price) */
    @Transactional
    public boolean record(StoreProduct sp, Observation o, Long runId) {
        BigDecimal original = o.originalPrice() != null && o.originalPrice().compareTo(o.price()) > 0 ? o.originalPrice() : null;
        boolean changed = false;
        Optional<Price> currentOpt = priceRepo.findCurrent(sp.getId());
        if (currentOpt.isEmpty()) {
            priceRepo.save(newPeriod(sp, o, original));
            changed = true;
        } else {
            Price current = currentOpt.get();
            if (o.capturedAt().isBefore(current.getValidFrom())) {
                // Late (older) observation, e.g. a historical CSV row: keep it in history only.
                saveHistory(sp, o, original, runId);
                return false;
            }
            boolean differs = current.getPrice().compareTo(o.price()) != 0
                    || !sameAmount(current.getOriginalPrice(), original)
                    || current.isAvailable() != o.available();
            if (differs) {
                BigDecimal lastTrusted = priceRepo.findLastTrusted(sp.getId()).map(Price::getPrice).orElse(null);
                current.setValidTo(o.capturedAt());
                priceRepo.saveAndFlush(current);
                Price opened = priceRepo.saveAndFlush(newPeriod(sp, o, original));
                anomalies.onNewPeriod(sp, opened, lastTrusted);
                changed = true;
            } else {
                if (o.capturedAt().isAfter(current.getCapturedAt())) {
                    current.setCapturedAt(o.capturedAt());
                }
                if (o.unitPrice() != null) {
                    current.setUnitPrice(o.unitPrice());
                    current.setUnitPriceUnit(o.unitPriceUnit());
                }
            }
        }
        saveHistory(sp, o, original, runId);
        return changed;
    }

    private void saveHistory(StoreProduct sp, Observation o, BigDecimal original, Long runId) {
        if (runId != null && historyRepo.existsSameObservationInRun(sp.getId(), runId, o.price(), o.available())) {
            return; // identical observation already recorded in this run (e.g. listing + detail page)
        }
        PriceHistory h = new PriceHistory();
        h.setStoreProduct(sp);
        h.setPrice(o.price());
        h.setOriginalPrice(original);
        h.setAvailable(o.available());
        h.setCapturedAt(o.capturedAt());
        h.setSourceRunId(runId);
        historyRepo.save(h);
    }

    private static Price newPeriod(StoreProduct sp, Observation o, BigDecimal original) {
        Price p = new Price();
        p.setStoreProduct(sp);
        p.setPrice(o.price());
        p.setOriginalPrice(original);
        p.setUnitPrice(o.unitPrice());
        p.setUnitPriceUnit(o.unitPriceUnit());
        p.setCurrency(o.currency() == null ? "AMD" : o.currency());
        p.setAvailable(o.available());
        p.setCapturedAt(o.capturedAt());
        p.setValidFrom(o.capturedAt());
        return p;
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return a.compareTo(b) == 0;
    }
}
