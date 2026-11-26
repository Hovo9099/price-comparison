package am.pricecompare.crawler;

import am.pricecompare.domain.CrawlStatus;

/** Published after a crawl run has recorded its final status. */
public record CrawlFinishedEvent(long storeId, long runId, CrawlStatus status, int pricesUpdated) {
}
