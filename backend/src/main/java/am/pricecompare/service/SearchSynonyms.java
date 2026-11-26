package am.pricecompare.service;

import am.pricecompare.normalize.ProductNormalizer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bilingual (Armenian / English / Russian) synonyms for common grocery words, used to expand search queries.
 * Many store names exist in one language only (e.g. Carrefour lists Armenian names), and Armenian has several words
 * for some staples (eggs: "ձու" / "հավկիթ").
 */
final class SearchSynonyms {

    private static final List<List<String>> GROUPS = List.of(
            List.of("milk", "կաթ", "молоко"),
            List.of("egg", "eggs", "ձու", "հավկիթ", "яйца", "яйцо"),
            List.of("rice", "բրինձ", "рис"),
            List.of("bread", "հաց", "хлеб"),
            List.of("cheese", "պանիր", "сыр"),
            List.of("butter", "կարագ", "масло"),
            List.of("water", "ջուր", "вода"),
            List.of("juice", "հյութ", "сок"),
            List.of("sugar", "շաքար", "շաքարավազ", "сахар"),
            List.of("salt", "աղ", "соль"),
            List.of("flour", "ալյուր", "мука"),
            List.of("pasta", "macaroni", "մակարոն", "макароны"),
            List.of("buckwheat", "հնդկաձավար", "гречка"),
            List.of("oil", "ձեթ", "масло"),
            List.of("tea", "թեյ", "чай"),
            List.of("coffee", "սուրճ", "кофе"),
            List.of("yogurt", "yoghurt", "յոգուրտ", "йогурт"),
            List.of("matsun", "մածուն", "мацун"),
            List.of("sour cream", "թթվասեր", "сметана"),
            List.of("cottage cheese", "կաթնաշոռ", "творог"),
            List.of("chicken", "հավ", "курица"),
            List.of("beef", "տավարի", "говядина"),
            List.of("pork", "խոզի", "свинина"),
            List.of("fish", "ձուկ", "рыба"),
            List.of("sausage", "երշիկ", "колбаса"),
            List.of("potato", "potatoes", "կարտոֆիլ", "картофель"),
            List.of("tomato", "tomatoes", "լոլիկ", "помидор"),
            List.of("cucumber", "վարունգ", "огурец"),
            List.of("onion", "սոխ", "лук"),
            List.of("apple", "apples", "խնձոր", "яблоко"),
            List.of("banana", "bananas", "բանան", "банан"),
            List.of("lemon", "կիտրոն", "лимон"),
            List.of("honey", "մեղր", "мёд"),
            List.of("chocolate", "շոկոլադ", "шоколад"),
            List.of("beer", "գարեջուր", "пиво"),
            List.of("wine", "գինի", "вино"),
            List.of("vodka", "օղի", "водка"),
            List.of("cognac", "brandy", "կոնյակ", "коньяк"),
            List.of("ice cream", "պաղպաղակ", "мороженое"),
            List.of("lavash", "լավաշ", "лаваш"),
            List.of("cola", "կոլա", "кола"),
            List.of("oil", "cooking oil", "vegetable oil", "ձեթ", "растительное масло"));

    private static final Map<String, Set<String>> INDEX = new HashMap<>();
    /** Multi-word phrases ("sour cream", "cooking oil") -> a single word of the same group. Longest phrases first. */
    private static final Map<String, String> PHRASES = new java.util.LinkedHashMap<>();

    static {
        for (List<String> group : GROUPS) {
            List<String> normalized = group.stream().map(ProductNormalizer::normalize).toList();
            for (String word : normalized) {
                String single = normalized.stream().filter(w -> !w.contains(" ")).filter(w -> !w.matches("[a-z]+")).findFirst()
                        .orElse(normalized.stream().filter(w -> !w.contains(" ")).findFirst().orElse(null));
                if (word.contains(" ") && single != null) {
                    PHRASES.putIfAbsent(word, single);
                }
                if (!word.contains(" ")) {
                    INDEX.computeIfAbsent(word, k -> new LinkedHashSet<>()).addAll(
                            normalized.stream().filter(w -> !w.contains(" ") && !w.equals(word)).toList());
                }
            }
        }
    }

    private SearchSynonyms() {
    }

    /** Replaces known multi-word phrases of a normalized query by one word, so "cooking oil" finds "ձեթ". */
    static String rewritePhrases(String normalizedQuery) {
        String padded = " " + normalizedQuery + " ";
        List<Map.Entry<String, String>> byLength = new java.util.ArrayList<>(PHRASES.entrySet());
        byLength.sort((a, b) -> b.getKey().length() - a.getKey().length());
        for (Map.Entry<String, String> e : byLength) {
            padded = padded.replace(" " + e.getKey() + " ", " " + e.getValue() + " ");
        }
        return padded.trim();
    }

    static Set<String> of(String normalizedToken) {
        return INDEX.getOrDefault(normalizedToken, Set.of());
    }
}
