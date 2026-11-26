package am.pricecompare.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A store listing currently shown with a crossed-out "old" price.
 *
 * @param verdict        GENUINE: the store charged the old price (or more) within the last 90 days;
 *                       INFLATED: it never did, so the "discount" is measured from a price nobody paid;
 *                       UNVERIFIED: less than 14 days of price history for this listing.
 * @param maxPaid90Days  highest price actually observed at this store in the last 90 days
 * @param cheapestAnywhere true when no other store sells the same product for less right now
 */
public record DealDto(ProductSummaryDto product, Long storeId, String storeName, String storeSlug, BigDecimal price,
                      BigDecimal originalPrice, int discountPercent, Verdict verdict, BigDecimal maxPaid90Days,
                      int historyDays, Instant since, boolean cheapestAnywhere) {

    public enum Verdict { GENUINE, INFLATED, UNVERIFIED }
}
