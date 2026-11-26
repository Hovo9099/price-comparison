package am.pricecompare.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Scores how likely two products are the same item. Pure function, no I/O.
 *
 * <p>Order of evidence: barcode (decisive) -> hard attribute constraints (size, unit, multipack,
 * percentage such as milk fat, sold-by-weight) -> brand -> name token similarity.
 * A hard-constraint violation always yields score 0: "Coca-Cola 0.5L" never matches "Coca-Cola 1.5L",
 * "Milk 2.5% 1L" never matches "Milk 3.2% 1L".
 */
public final class ProductMatcher {

    /** 95–100: automatic merge (when nothing conflicts); 80–94: review; below 80: never merged. */
    public static final double AUTO_MATCH_THRESHOLD = 0.95;
    public static final double REVIEW_THRESHOLD = 0.80;

    /**
     * Critical attributes: a product carrying one of these markers is never the same product as one without it, no
     * matter how similar the names are (regular vs sugar-free, lactose-free, margarine/spread vs butter).
     */
    static final List<java.util.Set<String>> CRITICAL_MARKERS = List.of(
            markerKeys("zero", "զերո", "զիրո", "зеро", "light", "լայթ", "лайт", "diet", "դիետիկ", "диет", "sugarfree", "без сахара",
                    "առանց շաքարի", "sugar free", "no sugar"),
            markerKeys("lactose free", "անլակտոզ", "безлактоз", "lactosefree", "без лактозы"),
            markerKeys("margarine", "մարգարին", "маргарин", "spread", "սպրեդ", "спред"));
    private static final BigDecimal SIZE_TOLERANCE = new BigDecimal("0.01");

    private ProductMatcher() {
    }

    /**
     * Descriptive words that never distinguish two products of the same brand, size and percentage (product type and
     * processing words), as phonetic Latin keys. Variant words such as "zero", "banana", "lactose-free" are NOT here.
     */
    static final java.util.Set<String> DESCRIPTORS = descriptorKeys(
            "milk", "կաթ", "молоко", "ultrapasteurized", "ultra", "pasteurized", "uht", "ուլտրապաստերիզացված",
            "ուլտրապաստերացված", "ուլտրապաստ", "պաստերիզացված", "стерилизованное", "ультрапастеризованное",
            "пастеризованное", "refreshing", "զովացուցիչ", "carbonated", "գազավորված", "газированный", "drink",
            "beverage", "soft", "ըմպելիք", "напиток", "product", "մթերք", "продукт", "pet", "bottle", "շիշ", "пэт");

    private static java.util.Set<String> markerKeys(String... phrases) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String p : phrases) {
            keys.add(String.join(" ", am.pricecompare.normalize.ProductNormalizer.parse(p).keys()));
        }
        return keys;
    }

    /** A critical marker present in one product's names but not the other's; null when consistent. */
    static String criticalMismatch(ProductFeatures a, ProductFeatures b) {
        String ka = " " + allKeys(a) + " ";
        String kb = " " + allKeys(b) + " ";
        for (java.util.Set<String> group : CRITICAL_MARKERS) {
            boolean inA = group.stream().anyMatch(m -> !m.isBlank() && ka.contains(" " + m + " ") || containsPrefix(ka, m));
            boolean inB = group.stream().anyMatch(m -> !m.isBlank() && kb.contains(" " + m + " ") || containsPrefix(kb, m));
            if (inA != inB) {
                return group.iterator().next();
            }
        }
        return null;
    }

    private static boolean containsPrefix(String keys, String marker) {
        // single-word markers of 5+ letters also match as a word prefix ("անլակտոզ" in "անլակտոզային")
        return !marker.contains(" ") && marker.length() >= 5 && keys.contains(" " + marker);
    }

    private static String allKeys(ProductFeatures f) {
        String hy = String.join(" ", f.name().keys());
        return f.nameEn() == null ? hy : hy + " " + String.join(" ", f.nameEn().keys());
    }

    private static java.util.Set<String> descriptorKeys(String... words) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String w : words) {
            keys.addAll(am.pricecompare.normalize.ProductNormalizer.parse(w).keys());
        }
        return keys;
    }

    public enum Decision { AUTO_MATCH, REVIEW, NO_MATCH }

    public record MatchResult(double score, Decision decision, String reason) {
        static MatchResult reject(String reason) {
            return new MatchResult(0, Decision.NO_MATCH, reason);
        }
    }

    /** Weight of a token key; rare (distinctive) tokens weigh more than generic ones such as "wine" or "milk". */
    @FunctionalInterface
    public interface TokenWeights {
        double weight(String key);

        TokenWeights UNIFORM = k -> 1.0;
    }

    public static MatchResult score(ProductFeatures a, ProductFeatures b) {
        return score(a, b, TokenWeights.UNIFORM);
    }

    public static MatchResult score(ProductFeatures a, ProductFeatures b, TokenWeights weights) {
        if (a.barcode() != null && b.barcode() != null) {
            if (a.barcode().equals(b.barcode())) {
                return new MatchResult(1.0, Decision.AUTO_MATCH, "barcode " + a.barcode());
            }
            return MatchResult.reject("different barcodes");
        }
        if (a.soldByWeight() != b.soldByWeight()) {
            return MatchResult.reject("sold-by-weight differs");
        }
        if (!Objects.equals(a.packCount(), b.packCount())) {
            return MatchResult.reject("multipack differs");
        }

        double penalty = 1.0;
        StringBuilder reason = new StringBuilder();

        boolean aSize = a.sizeValue() != null && a.sizeUnit() != null;
        boolean bSize = b.sizeValue() != null && b.sizeUnit() != null;
        if (aSize && bSize) {
            if (!a.sizeUnit().equals(b.sizeUnit()) || !sameSize(a.sizeValue(), b.sizeValue())) {
                return MatchResult.reject("size differs");
            }
            reason.append("size ").append(a.sizeValue().stripTrailingZeros().toPlainString()).append(a.sizeUnit()).append("; ");
        } else if (aSize != bSize && !a.soldByWeight()) {
            penalty *= 0.8;
            reason.append("size missing on one side; ");
        }

        BigDecimal pa = a.percent();
        BigDecimal pb = b.percent();
        if (pa != null && pb != null) {
            if (pa.compareTo(pb) != 0) {
                return MatchResult.reject("percentage differs (" + pa.toPlainString() + "% vs " + pb.toPlainString() + "%)");
            }
        } else if (pa != null || pb != null) {
            penalty *= 0.85;
            reason.append("percentage missing on one side; ");
        }

        Boolean brandMatch = brandMatch(a, b);
        if (Boolean.FALSE.equals(brandMatch)) {
            return MatchResult.reject("brand differs");
        }
        if (quotedNamesConflict(a, b)) {
            return MatchResult.reject("quoted product names differ");
        }
        String critical = criticalMismatch(a, b);
        if (critical != null) {
            return MatchResult.reject("critical attribute differs: " + critical);
        }

        double text = textSimilarity(a, b, weights);
        double score = Boolean.TRUE.equals(brandMatch) ? 0.25 + 0.75 * text : text;
        score *= penalty;
        score = Math.min(1.0, score);
        // Same brand, same size and percentage, and one name is the other plus only descriptive words
        // ("Milk «Marianna» 3.2% 1l" vs "Milk «Marianna» ultra-pasteurized 3.2% 1l").
        if (Boolean.TRUE.equals(brandMatch) && aSize && bSize && penalty == 1.0 && contained(a, b)) {
            score = Math.max(score, 0.96);
            reason.append("same product, descriptive words only; ");
        } else if (variantOfEachOther(a, b)) {
            // "Coca-Cola" vs "Coca-Cola Zero": one name is the other plus a distinguishing word
            score = Math.min(score, 0.85);
            reason.append("possible variant; ");
        }
        if (Boolean.TRUE.equals(brandMatch)) {
            reason.append("brand; ");
        }
        reason.append("name similarity ").append(Math.round(text * 100)).append('%');

        Decision d = score >= AUTO_MATCH_THRESHOLD ? Decision.AUTO_MATCH
                : score >= REVIEW_THRESHOLD ? Decision.REVIEW : Decision.NO_MATCH;
        return new MatchResult(round(score), d, reason.toString());
    }

    private static boolean sameSize(BigDecimal x, BigDecimal y) {
        BigDecimal max = x.max(y);
        if (max.signum() == 0) {
            return true;
        }
        return x.subtract(y).abs().divide(max, 6, RoundingMode.HALF_UP).compareTo(SIZE_TOLERANCE) <= 0;
    }

    /** TRUE = same brand, FALSE = different brands, null = unknown on at least one side. */
    static Boolean brandMatch(ProductFeatures a, ProductFeatures b) {
        if (a.brandKey() != null && b.brandKey() != null) {
            String x = a.brandKey();
            String y = b.brandKey();
            if (x.equals(y) || (Math.min(x.length(), y.length()) >= 4 && (x.contains(y) || y.contains(x)))) {
                return true;
            }
            return false;
        }
        // Brand known on one side only: it should appear in the other product's name.
        ProductFeatures known = a.brandKey() != null ? a : b.brandKey() != null ? b : null;
        if (known == null) {
            return null;
        }
        ProductFeatures other = known == a ? b : a;
        String joined = String.join("", other.name().keys()) + (other.nameEn() != null ? String.join("", other.nameEn().keys()) : "");
        return joined.contains(known.brandKey()) ? Boolean.TRUE : null;
    }

    /**
     * Both products name a product line in quotes/guillemets (e.g. Wine "Cape Spring" vs Wine "Akoom") and the quoted
     * texts share no token: they are different products even if the generic words agree.
     */
    static boolean quotedNamesConflict(ProductFeatures a, ProductFeatures b) {
        java.util.Set<String> qa = quotedKeys(a);
        java.util.Set<String> qb = quotedKeys(b);
        if (qa.isEmpty() || qb.isEmpty()) {
            return false;
        }
        for (String x : qa) {
            for (String y : qb) {
                if (tokenScore(x, y) > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static java.util.Set<String> quotedKeys(ProductFeatures f) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (am.pricecompare.normalize.ParsedName n : new am.pricecompare.normalize.ParsedName[]{f.name(), f.nameEn()}) {
            if (n != null && n.quoted() != null) {
                am.pricecompare.normalize.ParsedName q = am.pricecompare.normalize.ProductNormalizer.parse(n.quoted());
                keys.addAll(q.keys());
            }
        }
        return keys;
    }

    /** Some name pair where every token of the shorter name matches, and the longer adds only descriptors. */
    static boolean contained(ProductFeatures a, ProductFeatures b) {
        List<List<String>> as = new java.util.ArrayList<>();
        List<List<String>> bs = new java.util.ArrayList<>();
        as.add(a.name().keys());
        bs.add(b.name().keys());
        if (a.nameEn() != null) {
            as.add(a.nameEn().keys());
        }
        if (b.nameEn() != null) {
            bs.add(b.nameEn().keys());
        }
        for (List<String> x : as) {
            for (List<String> y : bs) {
                if (containedOneWay(x, y) || containedOneWay(y, x)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One name's tokens all appear in the other, which adds at least one non-descriptive word. */
    static boolean variantOfEachOther(ProductFeatures a, ProductFeatures b) {
        List<String> x = a.nameEn() != null && b.nameEn() != null ? a.nameEn().keys() : a.name().keys();
        List<String> y = a.nameEn() != null && b.nameEn() != null ? b.nameEn().keys() : b.name().keys();
        return extraVariantWord(x, y) || extraVariantWord(y, x);
    }

    private static boolean extraVariantWord(List<String> small, List<String> large) {
        if (small.isEmpty()) {
            return false;
        }
        for (String t : small) {
            if (bestTokenScore(t, large) < 0.8) {
                return false;
            }
        }
        for (String t : large) {
            if (bestTokenScore(t, small) < 0.8 && !DESCRIPTORS.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containedOneWay(List<String> small, List<String> large) {
        if (small.isEmpty()) {
            return false;
        }
        boolean distinctive = false;
        for (String t : small) {
            if (bestTokenScore(t, large) < 0.8) {
                return false;
            }
            distinctive |= !DESCRIPTORS.contains(t);
        }
        for (String t : large) {
            if (bestTokenScore(t, small) < 0.8 && !DESCRIPTORS.contains(t)) {
                return false; // a variant word ("zero", "banana") only on one side
            }
        }
        return distinctive;
    }

    static double textSimilarity(ProductFeatures a, ProductFeatures b) {
        return textSimilarity(a, b, TokenWeights.UNIFORM);
    }

    static double textSimilarity(ProductFeatures a, ProductFeatures b, TokenWeights w) {
        double best = 0;
        if (a.nameEn() != null && b.nameEn() != null) {
            best = Math.max(best, tokenSimilarity(a.nameEn().keys(), b.nameEn().keys(), w));
        }
        best = Math.max(best, tokenSimilarity(a.name().keys(), b.name().keys(), w));
        // Cross-language: Armenian names often embed the Latin brand/product line, and CSV rows may carry one name only.
        if (a.nameEn() != null) {
            best = Math.max(best, tokenSimilarity(a.nameEn().keys(), b.name().keys(), w));
        }
        if (b.nameEn() != null) {
            best = Math.max(best, tokenSimilarity(a.name().keys(), b.nameEn().keys(), w));
        }
        return best;
    }

    static double tokenSimilarity(List<String> a, List<String> b) {
        return tokenSimilarity(a, b, TokenWeights.UNIFORM);
    }

    /** Weighted soft Dice coefficient over phonetic token keys. */
    static double tokenSimilarity(List<String> a, List<String> b, TokenWeights w) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        double sumA = 0;
        double totalA = 0;
        for (String t : a) {
            double wt = w.weight(t);
            sumA += wt * bestTokenScore(t, b);
            totalA += wt;
        }
        double sumB = 0;
        double totalB = 0;
        for (String t : b) {
            double wt = w.weight(t);
            sumB += wt * bestTokenScore(t, a);
            totalB += wt;
        }
        return totalA + totalB == 0 ? 0 : (sumA + sumB) / (totalA + totalB);
    }

    private static double bestTokenScore(String t, List<String> others) {
        double best = 0;
        for (String o : others) {
            best = Math.max(best, tokenScore(t, o));
            if (best == 1.0) {
                break;
            }
        }
        return best;
    }

    static double tokenScore(String x, String y) {
        if (x.equals(y)) {
            return 1.0;
        }
        int min = Math.min(x.length(), y.length());
        if (min >= 3 && (x.startsWith(y) || y.startsWith(x))) {
            return 0.85; // abbreviation, e.g. "ultrapast" vs "ultrapasteurized"
        }
        if (min >= 5 && levenshtein(x, y, 1) <= 1) {
            return 0.8;
        }
        if (min >= 8 && commonPrefix(x, y) >= Math.max(7, (int) Math.ceil(0.6 * min))) {
            return 0.8; // misspelling of a long word: "ultrapasteratsvats" vs "ultrapasterizatsvats"
        }
        return 0;
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    static int levenshtein(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) {
            return limit + 1;
        }
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
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    /** Convenience for tests and admin tools. */
    public static MatchResult score(String nameA, String nameB) {
        return score(ProductFeatures.of(nameA, null, null, null, null, null, false),
                ProductFeatures.of(nameB, null, null, null, null, null, false));
    }
}
