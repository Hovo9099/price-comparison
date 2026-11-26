package am.pricecompare.lexicon;

import am.pricecompare.normalize.ParsedName;
import am.pricecompare.normalize.ProductNormalizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns free text such as {@code "kat 1l, 2 hac, dzu 10 hat; karag x2"} into item requests: product phrase, quantity
 * and package size. Pure and deterministic (no network, no AI).
 */
public final class ShoppingListParser {

    private static final Pattern SEPARATORS = Pattern.compile("[,;\\n]+|\\s+(?:and|և|եվ|ու|и|\\+)\\s+");
    private static final Pattern MULTIPLIER = Pattern.compile("(?<![\\p{L}\\d.])(?:[x×х*]\\s*(\\d{1,3})|(\\d{1,3})\\s*[x×х*])(?![\\p{L}\\d.])");
    private static final Pattern LEADING_COUNT = Pattern.compile("^(\\d{1,3})\\s+(?=\\p{L})");

    private ShoppingListParser() {
    }

    /**
     * @param phrase   product words without quantity/size (e.g. "kat marianna")
     * @param quantity number of packages, or kilograms for products sold by weight
     */
    public record Request(String raw, String phrase, BigDecimal quantity, BigDecimal sizeValue, String sizeUnit,
                          BigDecimal percent, BigDecimal trailingNumber) {
    }

    private static final Pattern TRAILING_NUMBER = Pattern.compile("\\s(\\d{1,3})$");

    public static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : SEPARATORS.split(text)) {
            String p = part.trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    public static Request parseItem(String raw) {
        String s = raw.trim();
        BigDecimal quantity = null;
        Matcher m = MULTIPLIER.matcher(s);
        if (m.find()) {
            quantity = new BigDecimal(m.group(1) != null ? m.group(1) : m.group(2));
            s = (s.substring(0, m.start()) + " " + s.substring(m.end())).trim();
        }
        // Sizes first ("2 kg kartofil" is a weight), then a leading bare count ("2 kat 1l" = two packs)
        ParsedName parsed = ProductNormalizer.parse(s);
        BigDecimal size = parsed.sizeValue();
        String unit = parsed.sizeUnit();
        String phrase = parsed.core();
        Matcher lead = LEADING_COUNT.matcher(phrase);
        if (quantity == null && lead.find()) {
            quantity = new BigDecimal(lead.group(1));
            phrase = phrase.substring(lead.end()).trim();
        }
        BigDecimal trailing = null;
        Matcher tail = TRAILING_NUMBER.matcher(phrase);
        if (tail.find()) {
            trailing = new BigDecimal(tail.group(1));
            phrase = phrase.substring(0, tail.start()).trim();
        }
        if (parsed.packCount() != null && quantity == null) {
            quantity = BigDecimal.valueOf(parsed.packCount());
        }
        return new Request(raw.trim(), phrase.isBlank() ? s : phrase, quantity, size, unit, parsed.percent(), trailing);
    }

    /**
     * Adapts quantity and size to the concept: weights given for products sold by weight are a quantity in kg,
     * and "2 hat" for bread means two loaves, not a pack size.
     */
    public static Request adaptToConcept(Request r, GroceryConcept concept) {
        BigDecimal qty = r.quantity();
        BigDecimal size = r.sizeValue();
        String unit = r.sizeUnit();
        if (r.trailingNumber() != null) {
            // "dzu 10" = a pack of 10 eggs; "hac 2" = two loaves
            if (concept != null && size == null && concept.hasDefaultSize() && "pcs".equals(concept.defaultSizeUnit())) {
                size = r.trailingNumber();
                unit = "pcs";
            } else if (qty == null) {
                qty = r.trailingNumber();
            }
        }
        if (concept != null && concept.soldByWeight()) {
            if (size != null && "g".equals(unit)) {
                BigDecimal kg = size.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
                qty = qty == null ? kg : qty.multiply(kg);
            } else if (size != null && "pcs".equals(unit)) {
                qty = qty == null ? size : qty.multiply(size);
            }
            size = null;
            unit = null;
        } else if (concept != null && size != null && "pcs".equals(unit)
                && !(concept.hasDefaultSize() && "pcs".equals(concept.defaultSizeUnit()))) {
            qty = qty == null ? size : qty.multiply(size);
            size = null;
            unit = null;
        }
        if (concept != null && size == null && concept.hasDefaultSize() && !concept.soldByWeight()) {
            size = concept.defaultSizeValue();
            unit = concept.defaultSizeUnit();
        }
        return new Request(r.raw(), r.phrase(), qty == null ? BigDecimal.ONE : qty.stripTrailingZeros(), size, unit, r.percent(), null);
    }
}
