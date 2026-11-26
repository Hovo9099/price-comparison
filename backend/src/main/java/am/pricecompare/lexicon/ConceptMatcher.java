package am.pricecompare.lexicon;

import am.pricecompare.normalize.ProductNormalizer;
import am.pricecompare.normalize.Transliterator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides whether a real product belongs to a concept using the concept's word rules: the product's Armenian or English
 * name must contain a word from every include group and no exclude word ("butter" never matches margarine,
 * "Coca-Cola" never matches Coca-Cola Zero).
 */
public final class ConceptMatcher {

    private ConceptMatcher() {
    }

    public static boolean matches(GroceryConcept concept, String name, String nameEn) {
        if (concept.include().isEmpty()) {
            return false;
        }
        Set<String> words = words(name, nameEn);
        for (String ex : concept.exclude()) {
            if (anyMatch(words, ex)) {
                return false;
            }
        }
        for (List<String> group : concept.include()) {
            boolean hit = false;
            for (String term : group) {
                if (anyMatch(words, term)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    static Set<String> words(String... texts) {
        Set<String> out = new HashSet<>();
        for (String t : texts) {
            if (t != null) {
                for (String w : ProductNormalizer.normalize(t).split(" ")) {
                    if (!w.isBlank()) {
                        out.add(w);
                        out.add(Transliterator.phonetic(Transliterator.toLatin(w)));
                    }
                }
            }
        }
        return out;
    }

    private static boolean anyMatch(Set<String> words, String term) {
        boolean prefix = term.endsWith("*");
        String stem = ProductNormalizer.normalize(prefix ? term.substring(0, term.length() - 1) : term);
        if (stem.isEmpty()) {
            return false;
        }
        if (stem.contains(" ")) {
            // multi-word term: every word must be present
            for (String part : stem.split(" ")) {
                if (!words.contains(part)) {
                    return false;
                }
            }
            return true;
        }
        if (prefix) {
            for (String w : words) {
                if (w.startsWith(stem)) {
                    return true;
                }
            }
            return false;
        }
        return words.contains(stem);
    }

    /** True when every word of the hint (a brand or description the user typed) occurs in the product. */
    public static boolean matchesHint(String hint, String name, String nameEn, String brand) {
        if (hint == null || hint.isBlank()) {
            return true;
        }
        Set<String> words = words(name, nameEn, brand);
        for (String h : ProductNormalizer.normalize(hint).split(" ")) {
            if (h.length() < 2) {
                continue;
            }
            String key = Transliterator.phonetic(Transliterator.toLatin(h));
            boolean found = false;
            for (String w : words) {
                if (w.equals(h) || w.equals(key) || (key.length() >= 4 && w.startsWith(key))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
