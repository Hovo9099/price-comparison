package am.pricecompare.crawler;

/** A category as published by the store. */
public record DiscoveredCategory(
        String externalId,
        String name,
        String url,
        String parentExternalId,
        int level,
        boolean leaf
) {
}
