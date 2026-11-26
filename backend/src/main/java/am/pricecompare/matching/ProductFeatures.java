package am.pricecompare.matching;

import am.pricecompare.normalize.ParsedName;
import am.pricecompare.normalize.ProductNormalizer;

import java.math.BigDecimal;

/**
 * Everything the matcher needs to know about a product, independent of where it came from
 * (a store product or a canonical product).
 */
public record ProductFeatures(
        String barcode,
        String brandKey,
        ParsedName name,
        ParsedName nameEn,
        BigDecimal sizeValue,
        String sizeUnit,
        boolean soldByWeight
) {
    public static ProductFeatures of(String name, String nameEn, String brand, String barcode,
                                     BigDecimal sizeValue, String sizeUnit, boolean soldByWeight) {
        ParsedName hy = ProductNormalizer.parse(name);
        ParsedName en = nameEn == null || nameEn.isBlank() ? null : ProductNormalizer.parse(nameEn);
        BigDecimal size = sizeValue;
        String unit = sizeUnit;
        if (size == null || unit == null) {
            ParsedName source = en != null && en.hasSize() ? en : hy;
            size = source.sizeValue();
            unit = source.sizeUnit();
        }
        String brandKey = ProductNormalizer.brandKey(brand);
        String cleanBarcode = barcode == null || barcode.isBlank() ? null : barcode.trim();
        return new ProductFeatures(cleanBarcode, brandKey, hy, en, size, unit, soldByWeight);
    }

    public Integer packCount() {
        if (nameEn != null && nameEn.packCount() != null) {
            return nameEn.packCount();
        }
        return name.packCount();
    }

    public BigDecimal percent() {
        if (nameEn != null && nameEn.percent() != null) {
            return nameEn.percent();
        }
        return name.percent();
    }
}
