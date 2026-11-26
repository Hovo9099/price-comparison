package am.pricecompare.crawler.sas;

import am.pricecompare.crawler.DiscoveredProduct;

import java.time.Instant;

/** Exposes package-private mapping functions to tests in other packages. */
public final class SasProviderTestAccess {
    private SasProviderTestAccess() {
    }

    public static DiscoveredProduct fromListing(SasParser.ListingItem item, String nameEn, String path, Instant at) {
        return SasProvider.fromListing(item, nameEn, path, at);
    }

    public static DiscoveredProduct enrich(DiscoveredProduct p, SasParser.Detail d) {
        return SasProvider.enrich(p, d);
    }
}
