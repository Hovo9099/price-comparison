package am.pricecompare.normalize;

import java.util.Map;

/**
 * Transliterates Armenian and Cyrillic text to Latin and produces a "phonetic key" so that
 * spellings such as "Կոկա-Կոլա", "Кока-Кола" and "Coca-Cola" compare equal.
 */
public final class Transliterator {

    private static final Map<Character, String> ARMENIAN = Map.ofEntries(
            Map.entry('ա', "a"), Map.entry('բ', "b"), Map.entry('գ', "g"), Map.entry('դ', "d"),
            Map.entry('ե', "e"), Map.entry('զ', "z"), Map.entry('է', "e"), Map.entry('ը', "y"),
            Map.entry('թ', "t"), Map.entry('ժ', "zh"), Map.entry('ի', "i"), Map.entry('լ', "l"),
            Map.entry('խ', "kh"), Map.entry('ծ', "ts"), Map.entry('կ', "k"), Map.entry('հ', "h"),
            Map.entry('ձ', "dz"), Map.entry('ղ', "gh"), Map.entry('ճ', "ch"), Map.entry('մ', "m"),
            Map.entry('յ', "y"), Map.entry('ն', "n"), Map.entry('շ', "sh"), Map.entry('ո', "o"),
            Map.entry('չ', "ch"), Map.entry('պ', "p"), Map.entry('ջ', "j"), Map.entry('ռ', "r"),
            Map.entry('ս', "s"), Map.entry('վ', "v"), Map.entry('տ', "t"), Map.entry('ր', "r"),
            Map.entry('ց', "ts"), Map.entry('ւ', "v"), Map.entry('փ', "p"), Map.entry('ք', "k"),
            Map.entry('և', "ev"), Map.entry('օ', "o"), Map.entry('ֆ', "f"));

    private static final Map<Character, String> CYRILLIC = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"),
            Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ё', "e"), Map.entry('ж', "zh"),
            Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "sch"), Map.entry('ъ', ""),
            Map.entry('ы', "y"), Map.entry('ь', ""), Map.entry('э', "e"), Map.entry('ю', "yu"),
            Map.entry('я', "ya"));

    private Transliterator() {
    }

    public static boolean containsNonLatinLetters(String s) {
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(s.charAt(i));
            if (block == Character.UnicodeBlock.ARMENIAN || block == Character.UnicodeBlock.CYRILLIC) {
                return true;
            }
        }
        return false;
    }

    /** Lower-case input expected. */
    public static String toLatin(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Armenian digraph "ու" -> "u"
            if (c == 'ո' && i + 1 < s.length() && s.charAt(i + 1) == 'ւ') {
                sb.append('u');
                i++;
                continue;
            }
            String a = ARMENIAN.get(c);
            if (a != null) {
                sb.append(a);
                continue;
            }
            String r = CYRILLIC.get(c);
            if (r != null) {
                sb.append(r);
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Phonetic key of a single latin token: folds letters that transliterations commonly confuse
     * (c/k/q, w/v, ph/f, y/i, x/ks) and collapses doubled letters.
     */
    public static String phonetic(String latinToken) {
        String t = latinToken
                .replace("ph", "f")
                .replace("ck", "k")
                .replace("x", "ks")
                .replace('c', 'k')
                .replace('q', 'k')
                .replace('w', 'v')
                .replace('y', 'i')
                .replace("kh", "h");
        StringBuilder sb = new StringBuilder(t.length());
        char prev = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c != prev || Character.isDigit(c)) {
                sb.append(c);
            }
            prev = c;
        }
        return sb.toString();
    }
}
