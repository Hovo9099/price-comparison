package am.pricecompare.web.dto;

import java.math.BigDecimal;

public record StoreLocationDto(Long id, String name, String address, BigDecimal latitude, BigDecimal longitude) {
}
