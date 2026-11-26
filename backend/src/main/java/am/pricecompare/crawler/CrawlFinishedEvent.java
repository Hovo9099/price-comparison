package am.pricecompare.crawler;

import am.pricecompare.domain.CrawlStatus;

/**
 * Published after a crawl run has recorded its final status (also for FAILED runs).
 *
 * @param maxProducts 0 = unlimited (a full-catalog crawl)
 * @param message     failure / cancellation reason, null when the run finished normally
 */
public record CrawlFinishedEvent(long storeId, String storeName, long runId, CrawlStatus status, int discovered,
                                 int pricesUpdated, int errors, int maxProducts, String message) {

    public CrawlFinishedEvent(long storeId, long runId, CrawlStatus status, int pricesUpdated) {
        this(storeId, null, runId, status, 0, pricesUpdated, 0, 0, null);
    }
}
