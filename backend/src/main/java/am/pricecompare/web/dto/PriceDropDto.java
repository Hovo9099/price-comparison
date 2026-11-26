package am.pricecompare.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceDropDto(ProductSummaryDto product, Long storeId, String storeName, BigDecimal previousPrice,
                           BigDecimal currentPrice, int dropPercent, Instant changedAt) {
}
