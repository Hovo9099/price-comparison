package am.pricecompare.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailDto(
        Long id,
        String name,
        String nameEn,
        String brand,
        String barcode,
        BigDecimal sizeValue,
        String sizeUnit,
        String size,
        String variant,
        boolean soldByWeight,
        String imageUrl,
        CategoryDto category,
        List<String> aliases
) {
}
