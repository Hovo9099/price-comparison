package am.pricecompare.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Minimal robots.txt evaluator for the "User-agent: *" group: supports Allow/Disallow,
 * '*' wildcards, '$' anchors and longest-match precedence.
 */
public final class RobotsRules {

    private record Rule(boolean allow, String raw, Pattern pattern) {
    }

    private final List<Rule> rules;

    private RobotsRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static RobotsRules allowAll() {
        return new RobotsRules(List.of());
    }

    public static RobotsRules parse(String robotsTxt) {
        List<Rule> rules = new ArrayList<>();
        if (robotsTxt == null) {
            return new RobotsRules(rules);
        }
        boolean inStarGroup = false;
        boolean lastWasAgent = false;
        for (String line : robotsTxt.split("\\r?\\n")) {
            String l = line.replaceAll("#.*", "").trim();
            if (l.isEmpty()) {
                continue;
            }
            int idx = l.indexOf(':');
            if (idx < 0) {
                continue;
            }
            String key = l.substring(0, idx).trim().toLowerCase();
            String value = l.substring(idx + 1).trim();
            if (key.equals("user-agent")) {
                boolean star = value.equals("*");
                inStarGroup = lastWasAgent ? (inStarGroup || star) : star;
                lastWasAgent = true;
                continue;
            }
            lastWasAgent = false;
            if (!inStarGroup || value.isEmpty()) {
                continue;
            }
            if (key.equals("disallow") || key.equals("allow")) {
                rules.add(new Rule(key.equals("allow"), value, toPattern(value)));
            }
        }
        return new RobotsRules(rules);
    }

    private static Pattern toPattern(String rule) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < rule.length(); i++) {
            char c = rule.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else if (c == '$' && i == rule.length() - 1) {
                sb.append('$');
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.toString());
    }

    /** @param pathAndQuery e.g. "/catalog/sous/?offset=24" */
    public boolean isAllowed(String pathAndQuery) {
        Rule best = null;
        for (Rule r : rules) {
            if (r.pattern().matcher(pathAndQuery).find()) {
                if (best == null || r.raw().length() > best.raw().length()
                        || (r.raw().length() == best.raw().length() && r.allow())) {
                    best = r;
                }
            }
        }
        return best == null || best.allow();
    }
}
