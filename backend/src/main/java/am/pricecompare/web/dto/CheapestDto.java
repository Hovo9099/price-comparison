package am.pricecompare.web.dto;

import am.pricecompare.service.Freshness;

import java.math.BigDecimal;
import java.time.Instant;

public record CheapestDto(BigDecimal price, String currency, Long storeId, String storeName, String storeSlug,
                          Instant capturedAt, Freshness freshness) {
}
