package am.pricecompare.lexicon;

import am.pricecompare.normalize.ProductNormalizer;
import am.pricecompare.normalize.Transliterator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recognises grocery words written in Armenian, Armenian in Latin letters ("kat", "dzu", "hac"), Russian or English.
 *
 * <p>Every alias and every input is reduced to a script-independent key: normalized, transliterated to Latin and
 * phonetically folded ({@code կաթ}, {@code kat} → "kat"; {@code Кока-Кола}, {@code coca cola} → "koka kola").
 * Lookup prefers the longest exact alias span in the input (extra words become a brand/description hint), then a
 * controlled edit-distance match for typos. Several concepts at the same best distance → ambiguous.
 */
@Component
public class GroceryLexicon {

    private final Map<String, GroceryConcept> concepts = new LinkedHashMap<>();
    /** alias key → concept codes */
    private final Map<String, Set<String>> aliasIndex = new HashMap<>();
    private int maxAliasTokens = 1;

    public GroceryLexicon() {
        this(new ClassPathResource("lexicon/grocery-concepts.json"));
    }

    GroceryLexicon(ClassPathResource resource) {
        try (InputStream in = resource.getInputStream()) {
            load(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load grocery lexicon", e);
        }
    }

    private void load(JsonNode root) {
        for (JsonNode c : root.path("concepts")) {
            GroceryConcept concept = new GroceryConcept(
                    c.path("code").asText(), c.path("nameHy").asText(), c.path("nameEn").asText(), c.path("nameRu").asText(null),
                    c.path("category").asText(null), strings(c.path("aliases")), groups(c.path("include")), strings(c.path("exclude")),
                    c.path("defaultSize").isMissingNode() ? null : c.path("defaultSize").path("value").decimalValue(),
                    c.path("defaultSize").isMissingNode() ? null : c.path("defaultSize").path("unit").asText(),
                    strings(c.path("sizes")).stream().map(BigDecimal::new).toList(),
                    c.path("soldByWeight").asBoolean(false), c.path("requiresChoice").asBoolean(false),
                    strings(c.path("children")), strings(c.path("alternatives")));
            concepts.put(concept.code(), concept);
            List<String> names = new ArrayList<>(concept.aliases());
            names.add(concept.nameHy());
            names.add(concept.nameEn());
            if (concept.nameRu() != null) {
                names.add(concept.nameRu());
            }
            for (String alias : names) {
                String key = key(alias);
                if (!key.isEmpty()) {
                    aliasIndex.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(concept.code());
                    maxAliasTokens = Math.max(maxAliasTokens, key.split(" ").length);
                }
            }
        }
    }

    private static List<String> strings(JsonNode n) {
        List<String> out = new ArrayList<>();
        n.forEach(x -> out.add(x.asText()));
        return out;
    }

    private static List<List<String>> groups(JsonNode n) {
        List<List<String>> out = new ArrayList<>();
        n.forEach(g -> out.add(strings(g)));
        return out;
    }

    /** Script-independent key: "Կաթ" → "kat", "Кока-Кола" → "koka kola", "hac" → "hak". */
    public static String key(String text) {
        String normalized = ProductNormalizer.normalize(text);
        StringBuilder sb = new StringBuilder();
        for (String t : normalized.split(" ")) {
            if (t.isBlank()) {
                continue;
            }
            String k = Transliterator.phonetic(Transliterator.toLatin(t))
                    .replace("th", "t").replace("dzh", "j").replace("zh", "j");
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(k);
        }
        return sb.toString();
    }

    public Optional<GroceryConcept> get(String code) {
        return Optional.ofNullable(code == null ? null : concepts.get(code));
    }

    public Collection<GroceryConcept> all() {
        return concepts.values();
    }

    public enum MatchType { EXACT, FUZZY, NONE }

    /**
     * @param concepts matching concepts, best first (more than one = ambiguous)
     * @param hint     input words outside the recognised alias (e.g. a brand: "kat marianna" → "marianna")
     */
    public record Resolution(List<GroceryConcept> concepts, MatchType type, String hint, double confidence) {
        public boolean recognised() {
            return !concepts.isEmpty();
        }

        public boolean ambiguous() {
            return concepts.size() > 1;
        }
    }

    /** Resolves a single product phrase (quantities and sizes already removed). */
    public Resolution resolve(String phrase) {
        String[] tokens = key(phrase).split(" ");
        String[] original = ProductNormalizer.normalize(phrase).split(" ");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return new Resolution(List.of(), MatchType.NONE, null, 0);
        }
        // 1. Longest exact alias span
        for (int len = Math.min(tokens.length, maxAliasTokens); len >= 1; len--) {
            for (int start = 0; start + len <= tokens.length; start++) {
                String span = String.join(" ", Arrays.copyOfRange(tokens, start, start + len));
                Set<String> codes = aliasIndex.get(span);
                if (codes != null) {
                    String hint = hint(original, start, len);
                    double conf = len == tokens.length ? 1.0 : 0.9;
                    return new Resolution(codes.stream().map(concepts::get).toList(), MatchType.EXACT, hint, conf);
                }
            }
        }
        // 2. Typos: edit distance on the whole phrase, then on single words
        String whole = String.join(" ", tokens);
        Resolution fuzzy = fuzzy(whole, null);
        if (fuzzy.recognised()) {
            return fuzzy;
        }
        for (int i = 0; i < tokens.length; i++) {
            Resolution r = fuzzy(tokens[i], hint(original, i, 1));
            if (r.recognised()) {
                return r;
            }
        }
        return new Resolution(List.of(), MatchType.NONE, null, 0);
    }

    private static String hint(String[] original, int start, int len) {
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < original.length; i++) {
            if ((i < start || i >= start + len) && i < original.length && !original[i].isBlank()) {
                rest.add(original[i]);
            }
        }
        return rest.isEmpty() ? null : String.join(" ", rest);
    }

    private Resolution fuzzy(String key, String hint) {
        int allowed = key.length() >= 8 ? 2 : key.length() >= 4 ? 1 : 0;
        if (allowed == 0) {
            return new Resolution(List.of(), MatchType.NONE, null, 0);
        }
        int best = Integer.MAX_VALUE;
        Set<String> codes = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> e : aliasIndex.entrySet()) {
            String alias = e.getKey();
            if (Math.abs(alias.length() - key.length()) > allowed || alias.length() < 3) {
                continue;
            }
            int d = levenshtein(key, alias, allowed);
            if (d <= allowed) {
                if (d < best) {
                    best = d;
                    codes.clear();
                }
                if (d == best) {
                    codes.addAll(e.getValue());
                }
            }
        }
        if (codes.isEmpty()) {
            return new Resolution(List.of(), MatchType.NONE, null, 0);
        }
        double conf = best == 1 ? 0.8 : 0.65;
        return new Resolution(codes.stream().map(concepts::get).toList(), MatchType.FUZZY, hint, conf);
    }

    /** Concepts whose names or aliases start with the typed text (for suggestions while typing). */
    public List<GroceryConcept> suggest(String prefix, int limit) {
        String k = key(prefix);
        if (k.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> score = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : aliasIndex.entrySet()) {
            int s = e.getKey().equals(k) ? 0 : e.getKey().startsWith(k) ? 1 : e.getKey().contains(" " + k) ? 2 : -1;
            if (s >= 0) {
                for (String code : e.getValue()) {
                    score.merge(code, s, Math::min);
                }
            }
        }
        if (score.isEmpty()) {
            resolve(prefix).concepts().forEach(c -> score.put(c.code(), 3));
        }
        return score.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .limit(limit).map(e -> concepts.get(e.getKey())).toList();
    }

    static int levenshtein(String a, String b, int limit) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > limit) {
                return limit + 1;
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    static Comparator<GroceryConcept> byCode() {
        return Comparator.comparing(GroceryConcept::code);
    }
}
