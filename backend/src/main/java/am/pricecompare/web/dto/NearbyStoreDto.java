package am.pricecompare.web.dto;

import java.math.BigDecimal;

/** The nearest known branch of a store. */
public record NearbyStoreDto(Long storeId, String storeName, String storeSlug, Long locationId, String name, String address,
                             BigDecimal latitude, BigDecimal longitude, double distanceKm) {
}
