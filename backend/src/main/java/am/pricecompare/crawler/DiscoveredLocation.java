package am.pricecompare.crawler;

import java.math.BigDecimal;

/** A physical branch as published by the store. */
public record DiscoveredLocation(String name, String address, BigDecimal latitude, BigDecimal longitude) {
}
