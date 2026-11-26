package am.pricecompare.crawler;

import java.util.List;

/**
 * A source of real product/price data for one supermarket. Implementations contain all
 * store-specific discovery and parsing logic; generic services never parse store markup.
 */
public interface SupermarketProvider {

    /** Matches {@code stores.slug}. */
    String getStoreCode();

    String getStoreName();

    /** Whether the store currently exposes a publicly accessible catalog that this provider can read. */
    boolean isAvailable();

    /**
     * Whether a catalog parser exists for this store. A provider that only probes an unreachable site (and has never
     * been able to read a catalog) reports false, so dashboards and the app never present it as integrated.
     */
    default boolean isImplemented() {
        return true;
    }

    /** Human readable reason when {@link #isAvailable()} is false. */
    default String unavailableReason() {
        return null;
    }

    List<DiscoveredCategory> discoverCategories();

    /** Physical branches, when the store publishes them. */
    default List<DiscoveredLocation> discoverLocations() {
        return List.of();
    }

    /**
     * Streams every product of the public catalog to {@code ctx.emit(...)}. Implementations must honour
     * {@link CrawlContext#shouldStop()} (product limit / cancellation) and report per-item failures through
     * {@link CrawlContext#error} instead of throwing.
     */
    void discoverProducts(CrawlContext ctx);
}
