package am.pricecompare.crawler.parma;

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

/**
 * Parses the public HTML of parma.am (server-rendered Yii2 shop): the category menu accordion and the 60-per-page
 * product cards of a category listing.
 */
public final class ParmaParser {

    private static final Pattern CATEGORY_SLUG = Pattern.compile("[?&]slug=([^&#]+)");
    /** Product slugs end with the store's product code: {@code ...-1.5-270g_19648}. */
    private static final Pattern PRODUCT_CODE = Pattern.compile("[?&]slug=[^&#]*_(\\d+)(?:[&#]|$)");
    private static final Pattern PAGE = Pattern.compile("[?&]page=(\\d+)");
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d\\s]*(?:[.,]\\d+)?)");
    /** "1 կգ. = 1380 ֏" on cards of products sold by weight. */
    private static final Pattern PER_KG = Pattern.compile("1\\s*կգ\\.?\\s*=\\s*(\\d[\\d\\s]*(?:[.,]\\d+)?)");

    private ParmaParser() {
    }

    /**
     * @param price    current price; for products sold by weight the price per kg
     * @param oldPrice price before the discount (same unit as {@code price}), null without a discount
     */
    public record Card(String id, String url, String name, BigDecimal price, BigDecimal oldPrice, boolean soldByWeight,
                       String unitPriceText, String discountText, String imageUrl) {
    }

    /** @param lastPage highest page number linked from the pagination (1 when there is no pagination) */
    public record ListingPage(List<Card> cards, int lastPage) {
    }

    /**
     * Every category link of the menu accordion. A category's parent is the category whose collapse panel contains
     * it; a category whose own panel holds no further category links is a leaf.
     */
    public static List<DiscoveredCategory> parseMenu(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        Map<String, DiscoveredCategory> out = new LinkedHashMap<>();
        for (Element a : doc.select("a.mob_menu_heading[href*=/product/category?slug=]")) {
            String slug = slug(a.attr("href"));
            if (slug == null || out.containsKey(slug)) {
                continue;
            }
            Element card = a.closest("div.card");
            Element panel = ownPanel(card);
            boolean leaf = panel == null || panel.selectFirst("a.mob_menu_heading[href*=/product/category?slug=]") == null;
            String parent = null;
            int level = 1;
            Element up = card == null ? null : card.parent();
            while (up != null) {
                if (up.hasClass("collapse")) {
                    Element parentCard = up.parent();
                    Element parentLink = parentCard == null ? null
                            : parentCard.selectFirst("> div.card-header a.mob_menu_heading[href*=/product/category?slug=]");
                    if (parentLink != null) {
                        if (parent == null) {
                            parent = slug(parentLink.attr("href"));
                        }
                        level++;
                    }
                }
                up = up.parent();
            }
            String name = a.selectFirst("span.menuItemName") != null ? a.selectFirst("span.menuItemName").text() : a.text();
            out.put(slug, new DiscoveredCategory(slug, name.trim(), a.absUrl("href"), parent, level, leaf));
        }
        return new ArrayList<>(out.values());
    }

    /** The collapse panel that follows a menu card's header (its children), if any. */
    private static Element ownPanel(Element card) {
        if (card == null) {
            return null;
        }
        for (Element child : card.children()) {
            if (child.hasClass("collapse")) {
                return child;
            }
        }
        return null;
    }

    public static ListingPage parseListing(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        List<Card> cards = new ArrayList<>();
        for (Element item : doc.select("div.product_item")) {
            Element link = item.selectFirst("a.item_name[href*=/product/product?slug=]");
            if (link == null) {
                continue;
            }
            String url = link.absUrl("href");
            Matcher id = PRODUCT_CODE.matcher(link.attr("href"));
            if (!id.find()) {
                continue;
            }
            String name = link.text().trim();
            Element priceEl = item.selectFirst("span.product_price[data-price]");
            BigDecimal price = priceEl == null ? null : number(priceEl.attr("data-price"));
            BigDecimal old = null;
            Element oldEl = item.selectFirst("div.discount_item span");
            if (oldEl != null) {
                old = number(oldEl.text());
            }
            boolean byWeight = false;
            Element perKgEl = item.selectFirst("div.price-with-gramm");
            if (perKgEl != null) {
                Matcher m = PER_KG.matcher(perKgEl.text());
                if (m.find()) {
                    BigDecimal perKg = number(m.group(1));
                    if (perKg != null && price != null && price.signum() > 0) {
                        // the card price is for one step (e.g. 0.5 kg); store the price per kg like other stores
                        if (old != null) {
                            old = old.multiply(perKg).divide(price, 0, java.math.RoundingMode.HALF_UP);
                        }
                        price = perKg;
                        byWeight = true;
                    }
                }
            }
            if (old != null && price != null && old.compareTo(price) <= 0) {
                old = null;
            }
            Element unit = item.selectFirst("span.unitCoefficientText");
            Element discount = item.parent() == null ? null : item.parent().selectFirst("div.discount-container");
            Element img = item.selectFirst("img.product-image");
            String image = img == null ? null : img.absUrl("src");
            if (image != null && image.contains("?")) {
                image = image.substring(0, image.indexOf('?'));
            }
            cards.add(new Card(id.group(1), url, name, price, old, byWeight, unit == null ? null : unit.text().trim(),
                    discount == null ? null : discount.text().trim(), image == null || image.isBlank() ? null : image));
        }
        int last = 1;
        for (Element a : doc.select("a.page-item[href], li.page-link a[href]")) {
            Matcher m = PAGE.matcher(a.attr("href"));
            if (m.find()) {
                last = Math.max(last, Integer.parseInt(m.group(1)));
            }
        }
        return new ListingPage(cards, last);
    }

    static String slug(String href) {
        Matcher m = CATEGORY_SLUG.matcher(href);
        return m.find() ? m.group(1) : null;
    }

    private static BigDecimal number(String s) {
        if (s == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(s);
        if (!m.find()) {
            return null;
        }
        return new BigDecimal(m.group(1).replaceAll("\\s", "").replace(',', '.'));
    }
}
