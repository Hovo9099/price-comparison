package am.pricecompare.crawler.supermarketam;

import am.pricecompare.crawler.DiscoveredCategory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the public HTML of supermarket.am (1C-Bitrix): category menu and product listing cards. */
public final class SupermarketAmParser {

    private static final Pattern CATEGORY = Pattern.compile("/categories/([^/?#]+)/?");
    private static final Pattern PRODUCT_ID = Pattern.compile("/products/(?:[^/]*_)?(\\d+)/?");
    private static final Pattern TOTAL = Pattern.compile("/\\s*(\\d+)");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private SupermarketAmParser() {
    }

    public record Card(String id, String url, String name, BigDecimal price, BigDecimal oldPrice, boolean soldByWeight,
                       BigDecimal maxAllowed, boolean adult, String imageUrl) {
    }

    public record ListingPage(List<Card> cards, int total) {
    }

    /**
     * Top-level menu items ({@code li > a > p}) and their sub-menus: {@code li.title} entries are level 2,
     * the following plain entries are level 3 children of the preceding title.
     */
    public static List<DiscoveredCategory> parseMenu(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        Map<String, DiscoveredCategory> out = new LinkedHashMap<>();
        for (Element top : doc.select("li:has(> a[href^=/categories/] > p):has(> div.sub)")) {
            Element a = top.selectFirst("> a");
            String topSlug = slug(a.attr("href"));
            if (topSlug == null || out.containsKey(topSlug)) {
                continue;
            }
            List<DiscoveredCategory> children = new ArrayList<>();
            String currentTitle = null;
            for (Element li : top.select("div.sub li")) {
                Element link = li.selectFirst("> a[href^=/categories/]");
                if (link == null) {
                    continue;
                }
                String s = slug(link.attr("href"));
                if (s == null) {
                    continue;
                }
                if (li.hasClass("title")) {
                    currentTitle = s;
                    children.add(new DiscoveredCategory(s, link.text().trim(), link.absUrl("href"), topSlug, 2, true));
                } else {
                    children.add(new DiscoveredCategory(s, link.text().trim(), link.absUrl("href"),
                            currentTitle != null ? currentTitle : topSlug, currentTitle != null ? 3 : 2, true));
                }
            }
            boolean hasTitles = children.stream().anyMatch(c -> c.level() == 2);
            out.put(topSlug, new DiscoveredCategory(topSlug, a.selectFirst("p").text().trim(), a.absUrl("href"), null, 1,
                    children.isEmpty()));
            for (DiscoveredCategory c : children) {
                // Level-2 pages aggregate their level-3 children, so level 2 are the crawl units.
                boolean leaf = c.level() == 2 || !hasTitles;
                out.putIfAbsent(c.externalId(), new DiscoveredCategory(c.externalId(), c.name(), c.url(),
                        c.parentExternalId(), c.level(), leaf && c.level() <= 2));
            }
        }
        return new ArrayList<>(out.values());
    }

    static String slug(String href) {
        Matcher m = CATEGORY.matcher(href == null ? "" : href);
        return m.find() ? m.group(1) : null;
    }

    /** Numeric category id at the end of the slug: "bakaleya_1046" -> "1046". */
    static String categoryCode(String slug) {
        int i = slug.lastIndexOf('_');
        return i >= 0 ? slug.substring(i + 1) : slug;
    }

    public static ListingPage parseListing(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        List<Card> cards = new ArrayList<>();
        for (Element card : doc.select("div.td-overally")) {
            Element link = card.selectFirst(".h3 a[href]");
            if (link == null) {
                continue;
            }
            Matcher m = PRODUCT_ID.matcher(link.attr("href"));
            if (!m.find()) {
                continue;
            }
            BigDecimal price = number(hidden(card, "price"));
            if (price == null || price.signum() == 0) {
                Element shown = card.selectFirst(".priceValue");
                price = shown == null ? null : number(shown.text());
            }
            BigDecimal old = number(hidden(card, "old_price"));
            if (old != null && old.signum() == 0) {
                old = null;
            }
            Element img = card.selectFirst(".image img[src]");
            cards.add(new Card(m.group(1), link.absUrl("href"), link.text().trim(), price, old,
                    "1".equals(hidden(card, "isnotquntity")), number(hidden(card, "max_value_allowed")),
                    "Y".equals(hidden(card, "for_adult")), img == null ? null : img.absUrl("src")));
        }
        int total = 0;
        Element pages = doc.selectFirst(".pages_block .pages");
        if (pages != null) {
            Matcher t = TOTAL.matcher(pages.text());
            if (t.find()) {
                total = Integer.parseInt(t.group(1));
            }
        }
        return new ListingPage(cards, total);
    }

    private static String hidden(Element card, String cls) {
        Element in = card.selectFirst("input." + cls + "[type=hidden]");
        return in == null ? null : in.attr("value");
    }

    static BigDecimal number(String s) {
        if (s == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(s.replace(",", "").replace(" ", "").replace(" ", ""));
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }
}
