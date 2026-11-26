package am.pricecompare.web.dto;

import am.pricecompare.domain.Store;

import java.math.BigDecimal;
import java.time.Instant;

/** Delivery terms as published by the store; any amount may be null (unknown, never assumed). */
public record DeliveryTermsDto(BigDecimal fee, BigDecimal freeFrom, BigDecimal minOrder, BigDecimal smallOrderBelow,
                               BigDecimal smallOrderFee, String source, Instant checkedAt) {

    /** null when nothing is known about the store's delivery terms. */
    public static DeliveryTermsDto of(Store s) {
        if (s.getDeliveryFee() == null && s.getFreeDeliveryFrom() == null && s.getMinOrderAmount() == null
                && s.getSmallOrderBelow() == null) {
            return null;
        }
        return new DeliveryTermsDto(s.getDeliveryFee(), s.getFreeDeliveryFrom(), s.getMinOrderAmount(), s.getSmallOrderBelow(),
                s.getSmallOrderFee(), s.getDeliveryTermsSource(), s.getDeliveryTermsCheckedAt());
    }
}
