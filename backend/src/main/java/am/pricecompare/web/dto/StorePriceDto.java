package am.pricecompare.web.dto;

import am.pricecompare.service.Freshness;

import java.math.BigDecimal;
import java.time.Instant;

/** The current price of one canonical product at one store. */
public record StorePriceDto(
        Long storeId,
        String storeName,
        String storeSlug,
        Long storeProductId,
        String externalName,
        String externalUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        Integer discountPercent,
        BigDecimal unitPrice,
        String unitPriceUnit,
        String currency,
        boolean available,
        boolean soldByWeight,
        Instant capturedAt,
        Instant validFrom,
        long ageHours,
        Freshness freshness,
        boolean cheapest
) {
}
