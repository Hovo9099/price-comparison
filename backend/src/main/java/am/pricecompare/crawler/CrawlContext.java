package am.pricecompare.crawler;

import java.util.Set;

/** Callbacks from a provider to the crawl engine. */
public interface CrawlContext {

    /** Hand over one discovered product for normalization, storage and matching. */
    void emit(DiscoveredProduct product);

    /** Record a non-fatal error; the crawl continues. */
    void error(String url, String externalId, String message);

    void pageVisited();

    /** True when the configured product limit is reached or the run was cancelled: stop discovering new products. */
    boolean shouldStop();

    /** True only when the run was cancelled; enrichment of already discovered products may continue otherwise. */
    boolean isCancelled();

    /** 0 = unlimited. */
    int maxProducts();

    /** External ids of products whose detail page was already fetched in an earlier run. */
    Set<String> productsWithDetails();

    /** Provider-specific settings from {@code crawler.providers.<code>.*}. */
    CrawlerProperties.ProviderSettings settings();
}
