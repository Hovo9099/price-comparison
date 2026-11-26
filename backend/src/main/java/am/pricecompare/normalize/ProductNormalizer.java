package am.pricecompare.normalize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes product names in Armenian, Russian and English: case, punctuation, whitespace,
 * units ("1.5L", "1,5 լ", "1500 մլ" -> "1500ml"), percentages and common abbreviations.
 * Stateless and thread-safe.
 */
public final class ProductNormalizer {

    private static final Map<String, Unit> UNITS = Map.ofEntries(
            // volume
            Map.entry("ml", new Unit("ml", 1)), Map.entry("мл", new Unit("ml", 1)), Map.entry("մլ", new Unit("ml", 1)),
            Map.entry("cl", new Unit("ml", 10)),
            Map.entry("l", new Unit("ml", 1000)), Map.entry("л", new Unit("ml", 1000)), Map.entry("լ", new Unit("ml", 1000)),
            Map.entry("լտր", new Unit("ml", 1000)), Map.entry("ltr", new Unit("ml", 1000)), Map.entry("lt", new Unit("ml", 1000)),
            Map.entry("liter", new Unit("ml", 1000)), Map.entry("litre", new Unit("ml", 1000)), Map.entry("литр", new Unit("ml", 1000)),
            // mass
            Map.entry("g", new Unit("g", 1)), Map.entry("gr", new Unit("g", 1)), Map.entry("гр", new Unit("g", 1)),
            Map.entry("г", new Unit("g", 1)), Map.entry("գ", new Unit("g", 1)), Map.entry("գր", new Unit("g", 1)),
            Map.entry("kg", new Unit("g", 1000)), Map.entry("кг", new Unit("g", 1000)), Map.entry("կգ", new Unit("g", 1000)),
            // count
            Map.entry("pcs", new Unit("pcs", 1)), Map.entry("pc", new Unit("pcs", 1)), Map.entry("шт", new Unit("pcs", 1)),
            Map.entry("հատ", new Unit("pcs", 1)), Map.entry("հտ", new Unit("pcs", 1)),
            // Armenian / Russian units typed in Latin letters in shopping lists
            Map.entry("hat", new Unit("pcs", 1)), Map.entry("sht", new Unit("pcs", 1)), Map.entry("litr", new Unit("ml", 1000)),
            Map.entry("kilo", new Unit("g", 1000)), Map.entry("gram", new Unit("g", 1)));

    private static final String UNIT_ALT;

    static {
        List<String> keys = new ArrayList<>(UNITS.keySet());
        keys.sort((a, b) -> b.length() - a.length()); // longest first
        UNIT_ALT = String.join("|", keys.stream().map(Pattern::quote).toList());
    }

    private static final String NUM = "(\\d+(?:[.,]\\d+)?)";
    private static final Pattern PACK_BEFORE = Pattern.compile(
            "(?<![\\p{L}\\d])(\\d{1,3})\\s*[x×х*]\\s*" + NUM + "\\s*(" + UNIT_ALT + ")\\.?(?![\\p{L}])");
    private static final Pattern PACK_AFTER = Pattern.compile(
            "(?<![\\p{L}\\d.,])" + NUM + "\\s*(" + UNIT_ALT + ")\\.?\\s*[x×х*]\\s*(\\d{1,3})(?![\\p{L}\\d])");
    /** Armenian nouns may carry the definite article: "50հատը", "1լ-ն". */
    private static final Pattern SIZE = Pattern.compile(
            "(?<![\\p{L}\\d.,])" + NUM + "\\s*(" + UNIT_ALT + ")\\.?(?:ը|ն)?(?![\\p{L}])");
    private static final Pattern PERCENT = Pattern.compile("(?<![\\d.,])" + NUM + "\\s*%");
    private static final Pattern QUOTED = Pattern.compile("[«\"“„]([^»\"”“]{1,120})[»\"”“]");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}%.\\s]");
    private static final Pattern STRAY_DOT = Pattern.compile("(?<!\\d)\\.|\\.(?!\\d)");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern COMBINING = Pattern.compile("\\p{M}+");

    private static final Set<String> STOPWORDS = Set.of(
            "and", "with", "the", "of", "in", "for", "a",
            "և", "եվ", "ու", "հետ", "համով",
            "и", "с", "в", "со", "для",
            // "fat content" labels: the percentage itself is compared as an attribute
            "յուղայնությունը", "յուղայնություն", "յուղայնությամբ", "richness", "fat", "жирность");

    private static final Set<String> BRAND_NOISE = Set.of("ապրանքանիշ", "brand", "тм", "tm", "бренд");

    private ProductNormalizer() {
    }

    /** Lower-case, strip diacritics and punctuation, collapse spaces, canonicalise units. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = basicClean(text);
        s = canonicaliseUnits(s);
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    private static String basicClean(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKD);
        s = COMBINING.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace("եւ", "և")
                .replace(',', '.') // decimal comma; stray commas removed below
                .replace(' ', ' ');
        s = NON_WORD.matcher(s).replaceAll(" ");
        s = STRAY_DOT.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    private static String canonicaliseUnits(String s) {
        Matcher m = SIZE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Unit u = UNITS.get(m.group(2));
            BigDecimal base = toBase(m.group(1), u);
            m.appendReplacement(sb, Matcher.quoteReplacement(base.toPlainString() + u.base()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static BigDecimal toBase(String number, Unit u) {
        BigDecimal v = new BigDecimal(number.replace(',', '.')).multiply(BigDecimal.valueOf(u.factor()));
        return v.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** Parses the structured attributes of a product name. */
    public static ParsedName parse(String name) {
        if (name == null || name.isBlank()) {
            return new ParsedName("", "", List.of(), List.of(), null, null, null, null, null);
        }
        String quoted = extractQuoted(name);
        String s = basicClean(name);

        BigDecimal sizeValue = null;
        String sizeUnit = null;
        Integer pack = null;
        String remaining = s;

        Matcher pb = PACK_BEFORE.matcher(remaining);
        Matcher pa = PACK_AFTER.matcher(remaining);
        if (pb.find()) {
            Unit u = UNITS.get(pb.group(3));
            pack = Integer.parseInt(pb.group(1));
            sizeValue = toBase(pb.group(2), u);
            sizeUnit = u.base();
            remaining = remaining.substring(0, pb.start()) + " " + remaining.substring(pb.end());
        } else if (pa.find()) {
            Unit u = UNITS.get(pa.group(2));
            pack = Integer.parseInt(pa.group(3));
            sizeValue = toBase(pa.group(1), u);
            sizeUnit = u.base();
            remaining = remaining.substring(0, pa.start()) + " " + remaining.substring(pa.end());
        }

        // Plain sizes: prefer volume/mass; a count next to a volume/mass is a multipack.
        Matcher m = SIZE.matcher(remaining);
        StringBuilder rest = new StringBuilder();
        BigDecimal countValue = null;
        while (m.find()) {
            Unit u = UNITS.get(m.group(2));
            BigDecimal v = toBase(m.group(1), u);
            if ("pcs".equals(u.base())) {
                if (countValue == null) {
                    countValue = v;
                }
            } else if (sizeValue == null) {
                sizeValue = v;
                sizeUnit = u.base();
            }
            m.appendReplacement(rest, " ");
        }
        m.appendTail(rest);
        remaining = rest.toString();
        if (countValue != null) {
            if (sizeValue == null) {
                sizeValue = countValue;
                sizeUnit = "pcs";
            } else if (pack == null && countValue.compareTo(BigDecimal.ONE) > 0) {
                pack = countValue.intValue();
            }
        }
        if (pack != null && pack <= 1) {
            pack = null;
        }

        BigDecimal percent = null;
        Matcher pm = PERCENT.matcher(remaining);
        if (pm.find()) {
            percent = new BigDecimal(pm.group(1).replace(',', '.')).stripTrailingZeros();
            remaining = pm.replaceAll(" ");
        }

        remaining = remaining.replace("%", " ").replace(".", " ");
        String core = SPACES.matcher(remaining).replaceAll(" ").trim();
        List<String> tokens = tokenize(core);
        List<String> keys = tokens.stream().map(t -> Transliterator.phonetic(Transliterator.toLatin(t))).toList();
        return new ParsedName(normalize(name), core, tokens, keys, sizeValue, sizeUnit, pack, percent, quoted);
    }

    public static List<String> tokenize(String normalizedText) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : normalizedText.split(" ")) {
            if (t.isBlank() || STOPWORDS.contains(t)) {
                continue;
            }
            if (t.length() == 1 && !Character.isDigit(t.charAt(0))) {
                continue;
            }
            out.add(t);
        }
        return new ArrayList<>(out);
    }

    public static String extractQuoted(String name) {
        Matcher m = QUOTED.matcher(name);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Script-independent comparison key of a brand, e.g. "Kühne ապրանքանիշ" -> "kuhne", "Կոկա-Կոլա" -> "kokakola". */
    public static String brandKey(String brand) {
        if (brand == null || brand.isBlank()) {
            return null;
        }
        String s = basicClean(brand);
        StringBuilder sb = new StringBuilder();
        for (String t : s.split(" ")) {
            if (!t.isBlank() && !BRAND_NOISE.contains(t)) {
                sb.append(Transliterator.phonetic(Transliterator.toLatin(t)));
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /** Clean display form of a brand (removes "ապրանքանիշ"/"brand" noise words). */
    public static String cleanBrand(String brand) {
        if (brand == null) {
            return null;
        }
        String s = brand.trim();
        for (String noise : BRAND_NOISE) {
            s = s.replaceAll("(?iu)\\s*\\b?" + Pattern.quote(noise) + "\\b?\\s*", " ");
        }
        s = SPACES.matcher(s).replaceAll(" ").trim();
        return s.isEmpty() ? null : s;
    }

    /** Text used for search indexing: normalized forms plus latin/phonetic variants of every input. */
    public static String searchText(String... texts) {
        Set<String> parts = new LinkedHashSet<>();
        for (String t : texts) {
            if (t == null || t.isBlank()) {
                continue;
            }
            String n = normalize(t);
            parts.add(n);
            String latin = Transliterator.toLatin(n);
            parts.add(latin);
            parts.add(phoneticPhrase(latin));
        }
        return String.join(" | ", parts);
    }

    public static String phoneticPhrase(String latinText) {
        StringBuilder sb = new StringBuilder();
        for (String t : latinText.split(" ")) {
            if (!t.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(Transliterator.phonetic(t));
            }
        }
        return sb.toString();
    }

    /** Human readable size, e.g. (1500, ml) -> "1.5 լ". */
    public static String formatSize(BigDecimal value, String unit) {
        if (value == null || unit == null) {
            return null;
        }
        return switch (unit) {
            case "ml" -> value.compareTo(BigDecimal.valueOf(1000)) >= 0
                    ? value.divide(BigDecimal.valueOf(1000)).stripTrailingZeros().toPlainString() + " լ"
                    : value.stripTrailingZeros().toPlainString() + " մլ";
            case "g" -> value.compareTo(BigDecimal.valueOf(1000)) >= 0
                    ? value.divide(BigDecimal.valueOf(1000)).stripTrailingZeros().toPlainString() + " կգ"
                    : value.stripTrailingZeros().toPlainString() + " գ";
            case "pcs" -> value.stripTrailingZeros().toPlainString() + " հատ";
            default -> value.stripTrailingZeros().toPlainString() + " " + unit;
        };
    }

    private record Unit(String base, int factor) {
    }
}
