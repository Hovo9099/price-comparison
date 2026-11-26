package am.pricecompare.alert;

import am.pricecompare.crawler.CrawlFinishedEvent;
import am.pricecompare.crawler.CrawlerProperties;
import am.pricecompare.crawler.CrawlerService;
import am.pricecompare.crawler.SupermarketProvider;
import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.domain.Store;
import am.pricecompare.repository.CrawlRunRepository;
import am.pricecompare.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns crawl outcomes into alerts: failed runs, full-catalog crawls that found far fewer products than the previous
 * one (usually a changed store website), runs with many errors, and stores without a successful crawl for too long.
 */
@Component
public class CrawlAlerts {

    static final double SHRINK_RATIO = 0.5;
    static final int SHRINK_MIN_PREVIOUS = 100;

    private final AlertService alerts;
    private final CrawlRunRepository runRepo;
    private final StoreRepository storeRepo;
    private final CrawlerService crawlerService;
    private final CrawlerProperties props;
    private final Duration staleAfter;
    private final Map<Long, Instant> staleAlertedAt = new ConcurrentHashMap<>();

    public CrawlAlerts(AlertService alerts, CrawlRunRepository runRepo, StoreRepository storeRepo, CrawlerService crawlerService,
                       CrawlerProperties props, @Value("${app.alerts.stale-hours:36}") int staleHours) {
        this.alerts = alerts;
        this.runRepo = runRepo;
        this.storeRepo = storeRepo;
        this.crawlerService = crawlerService;
        this.props = props;
        this.staleAfter = Duration.ofHours(staleHours);
    }

    @EventListener
    public void onCrawlFinished(CrawlFinishedEvent e) {
        String store = e.storeName() != null ? e.storeName() : "store " + e.storeId();
        if (e.status() == CrawlStatus.FAILED) {
            alerts.alert("❌ Crawl " + e.runId() + " for " + store + " FAILED"
                    + (e.message() != null ? ": " + abbreviate(e.message()) : "")
                    + " (discovered " + e.discovered() + ", errors " + e.errors() + ")");
            return;
        }
        if (e.discovered() > 0 && e.errors() > Math.max(50, e.discovered() / 20)) {
            alerts.alert("⚠️ Crawl " + e.runId() + " for " + store + ": " + e.errors() + " errors for " + e.discovered()
                    + " products – check the crawl errors in the admin");
        }
        if (e.maxProducts() == 0 && e.message() == null) {
            List<CrawlRun> complete = runRepo.findCompleteCrawls(e.storeId(), PageRequest.of(0, 3));
            complete.stream().filter(r -> r.getId() != e.runId()).findFirst().ifPresent(prev -> {
                if (prev.getProductsDiscovered() >= SHRINK_MIN_PREVIOUS
                        && e.discovered() < prev.getProductsDiscovered() * SHRINK_RATIO) {
                    alerts.alert("⚠️ " + store + ": full crawl " + e.runId() + " found " + e.discovered() + " products, previous full crawl "
                            + prev.getId() + " found " + prev.getProductsDiscovered() + " – the store website may have changed");
                }
            });
        }
    }

    /** Hourly: stores whose last successful crawl is older than {@code app.alerts.stale-hours} (only with scheduled crawls). */
    @Scheduled(cron = "${app.alerts.stale-check-cron:0 17 * * * *}", zone = "Asia/Yerevan")
    public void checkStale() {
        if (props.getScheduleCron() == null || props.getScheduleCron().isBlank()) {
            return; // crawls are started by hand: no expectation of freshness
        }
        Instant now = Instant.now();
        for (SupermarketProvider p : crawlerService.providers()) {
            if (!p.isAvailable() || !props.provider(p.getStoreCode()).isEnabled()) {
                continue;
            }
            Store store = storeRepo.findBySlug(p.getStoreCode()).orElse(null);
            if (store == null) {
                continue;
            }
            Instant last = runRepo.findFirstByStoreIdAndStatusInOrderByStartedAtDesc(store.getId(),
                            List.of(CrawlStatus.SUCCESS, CrawlStatus.PARTIAL))
                    .map(CrawlRun::getFinishedAt).orElse(null);
            boolean stale = last == null || Duration.between(last, now).compareTo(staleAfter) > 0;
            if (!stale) {
                staleAlertedAt.remove(store.getId());
                continue;
            }
            Instant alerted = staleAlertedAt.get(store.getId());
            if (alerted == null || Duration.between(alerted, now).toHours() >= 24) {
                staleAlertedAt.put(store.getId(), now);
                alerts.alert("⏰ " + store.getName() + ": no successful crawl "
                        + (last == null ? "yet" : "since " + last + " (" + Duration.between(last, now).toHours() + " h)")
                        + " – prices are getting stale");
            }
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
