package am.pricecompare.web.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class PriceWatchDtos {

    private PriceWatchDtos() {
    }

    /** {@code pushToken} null/empty = the user did not allow notifications. */
    public record DeviceRequest(@Size(max = 255) String pushToken, @Size(max = 5) String language) {
    }

    /**
     * {@code dropped}: the cheapest price is now at/below the target, or (without a target) below the price when the
     * watch was created.
     */
    public record PriceWatchDto(Long id, Long productId, String name, String nameEn, String size, String imageUrl,
                                BigDecimal targetPrice, BigDecimal startPrice, CheapestDto cheapest, boolean dropped,
                                Instant lastNotifiedAt, Instant createdAt) {
    }
}
