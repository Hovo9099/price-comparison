package am.pricecompare.support;

import am.pricecompare.crawler.DiscoveredProduct;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Builders for FICTIONAL, test-only products. Names are prefixed or clearly synthetic; these values must never be
 * loaded into a production database.
 */
public final class TestProducts {

    private TestProducts() {
    }

    public static DiscoveredProduct product(String externalId, String name, String nameEn, String brand, String barcode,
                                            String price, Instant capturedAt) {
        return new DiscoveredProduct(externalId, null, barcode, name, nameEn, brand, null, "https://test.invalid/p/" + externalId,
                null, new BigDecimal(price), null, null, null, "AMD", true, false, null, null, false, capturedAt,
                Map.of("test", true));
    }

    public static DiscoveredProduct product(String externalId, String name, String price, Instant capturedAt) {
        return product(externalId, name, null, null, null, price, capturedAt);
    }
}
