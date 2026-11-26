package am.pricecompare.crawler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * A product exactly as observed on a store's public catalog. Fields the store does not publish are null;
 * nothing is invented.
 *
 * @param price         current selling price (after discount) for one sale unit; for products sold by weight
 *                      this is the price per kilogram
 * @param originalPrice price before discount, null when not discounted
 * @param unitPrice     price per base unit published by the store (e.g. per 1 kg / 1 l), may be null
 */
public record DiscoveredProduct(
        String externalId,
        String sku,
        String barcode,
        String name,
        String nameEn,
        String brand,
        String categoryPath,
        String url,
        String imageUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        BigDecimal unitPrice,
        String unitPriceUnit,
        String currency,
        boolean available,
        boolean soldByWeight,
        BigDecimal packageSize,
        String packageUnit,
        boolean detailsFetched,
        Instant capturedAt,
        Map<String, Object> rawData
) {
}
