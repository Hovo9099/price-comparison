package am.pricecompare.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceComparisonDto(
        ProductDetailDto product,
        CheapestDto cheapest,
        BigDecimal mostExpensive,
        BigDecimal potentialSaving,
        List<StorePriceDto> prices
) {
}
