package am.pricecompare.web.dto;

import am.pricecompare.service.Freshness;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One store-specific listing with its own current price. Listings of different stores are never merged: a canonical
 * product carries one OfferDto per store listing.
 *
 * @param available false when the store lists the product as out of stock or it disappeared from the catalog
 * @param usable    available, listed and the price is not outdated (only usable offers are compared)
 */
public record OfferDto(
        Long storeId,
        String storeName,
        String storeSlug,
        Long storeProductId,
        String externalProductId,
        String externalName,
        String brand,
        String size,
        String imageUrl,
        String externalUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        Integer discountPercent,
        BigDecimal unitPrice,
        String unitPriceUnit,
        String currency,
        boolean available,
        boolean usable,
        Instant capturedAt,
        Freshness freshness
) {
}
