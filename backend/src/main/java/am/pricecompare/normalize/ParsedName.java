package am.pricecompare.normalize;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured view of a product name.
 *
 * @param normalized full normalized text (lower case, units canonicalised, e.g. "coca cola 1500ml")
 * @param core       normalized text without size / percent tokens
 * @param tokens     significant word tokens of {@code core}
 * @param keys       phonetic latin keys of {@code tokens} (script independent)
 * @param sizeValue  package size in base units (ml, g or pcs), null when unknown
 * @param sizeUnit   "ml", "g" or "pcs", null when unknown
 * @param packCount  multipack count (e.g. 6 for "6x0.5l"), null when not a multipack
 * @param percent    a percentage found in the name (fat / alcohol content), null when absent
 * @param quoted     text found between quotes/guillemets, typically brand + line, may be null
 */
public record ParsedName(
        String normalized,
        String core,
        List<String> tokens,
        List<String> keys,
        BigDecimal sizeValue,
        String sizeUnit,
        Integer packCount,
        BigDecimal percent,
        String quoted
) {
    public boolean hasSize() {
        return sizeValue != null && sizeUnit != null;
    }
}
