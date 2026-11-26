package am.pricecompare.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PriceHistoryDto(Long productId, int days, List<Series> series) {

    public record Series(Long storeId, String storeName, String storeSlug, List<Point> points) {
    }

    public record Point(Instant capturedAt, BigDecimal price, BigDecimal originalPrice, boolean available) {
    }
}
