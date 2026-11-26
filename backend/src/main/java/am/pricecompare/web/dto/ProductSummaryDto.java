package am.pricecompare.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A canonical product in search results / lists. {@code cheapest} is null when no usable current price exists.
 * {@code offers} holds each store's own listing (best one per store, cheapest first), never a merged price.
 */
public record ProductSummaryDto(
        Long id,
        String name,
        String nameEn,
        String brand,
        String size,
        boolean soldByWeight,
        String imageUrl,
        Long categoryId,
        CheapestDto cheapest,
        BigDecimal maxPrice,
        int storeCount,
        List<OfferDto> offers
) {
}
