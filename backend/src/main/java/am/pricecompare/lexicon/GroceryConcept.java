package am.pricecompare.lexicon;

import java.math.BigDecimal;
import java.util.List;

/**
 * A kind of grocery a user can ask for ("milk", "eggs", "chicken breast"). Concepts map shopping-list words in any
 * script to real products through include/exclude word rules; see {@code lexicon/grocery-concepts.json}.
 */
public record GroceryConcept(
        String code,
        String nameHy,
        String nameEn,
        String nameRu,
        String category,
        List<String> aliases,
        List<List<String>> include,
        List<String> exclude,
        BigDecimal defaultSizeValue,
        String defaultSizeUnit,
        List<BigDecimal> sizes,
        boolean soldByWeight,
        boolean requiresChoice,
        List<String> children,
        List<String> alternatives
) {
    public boolean hasDefaultSize() {
        return defaultSizeValue != null && defaultSizeUnit != null;
    }
}
