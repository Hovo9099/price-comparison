package am.pricecompare.crawler.sas;

import am.pricecompare.crawler.DiscoveredCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the public HTML of sas.am (1C-Bitrix storefront).
 *
 * <ul>
 *   <li>Category tree: the main menu ({@code li.main-menu__item-level-N}) on {@code /catalog/}.</li>
 *   <li>Listings: {@code div.product.js-product} cards on {@code /catalog/{section}/?LIMIT=60&offset=N}.</li>
 *   <li>Product page: {@code div.card} with SKU ("Ապրանքի կոդը"), brand, price, stock.</li>
 * </ul>
 */
public final class SasParser {

    private static final Pattern CATALOG_SLUG = Pattern.compile("/catalog/([^/?#]+)/?$");
    private static final Pattern PRODUCT_URL = Pattern.compile("/catalog/([^/]+)/(\\d+)/?");
    private static final Pattern OFFSET = Pattern.compile("[?&]offset=(\\d+)");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern STEP = Pattern.compile("/\\s*(\\d+(?:[.,]\\d+)?)\\s*(կգ|կգ\\.|kg|кг|գ|g|լ|l|հատ)");
    private static final Pattern STOCK = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final ObjectMapper JSON = new ObjectMapper();

    private SasParser() {
    }

    public record ListingItem(
            String id,
            String url,
            String name,
            BigDecimal price,
            BigDecimal oldPrice,
            BigDecimal unitPrice,
            String unitPriceUnit,
            BigDecimal saleStep,
            String saleStepUnit,
            String unitLabel,
            Integer maxQuantity,
            String imageUrl,
            boolean outOfStock
    ) {
        public boolean soldByWeight() {
            return saleStepUnit != null && (saleStepUnit.startsWith("կգ") || saleStepUnit.equals("kg") || saleStepUnit.equals("кг"));
        }
    }

    public record ListingPage(List<ListingItem> items, int maxOffset) {
    }

    public record Detail(
            String id,
            String sku,
            String name,
            String brand,
            BigDecimal price,
            BigDecimal oldPrice,
            BigDecimal unitPrice,
            String unitPriceUnit,
            String stockText,
            BigDecimal stockQuantity,
            String imageUrl,
            List<String> breadcrumbs,
            String description
    ) {
    }

    /** Parses the 4-level main menu into a flat list of categories with parent references. */
    public static List<DiscoveredCategory> parseMenu(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        Map<String, DiscoveredCategory> out = new LinkedHashMap<>();
        for (int level = 1; level <= 4; level++) {
            for (Element li : doc.select("li.main-menu__item-level-" + level)) {
                Element a = li.selectFirst("> a.main-menu__link-level-" + level);
                if (a == null) {
                    continue;
                }
                String slug = slugOf(a.attr("href"));
                if (slug == null || out.containsKey(slug)) {
                    continue;
                }
                Element textEl = a.selectFirst("span");
                String name = (textEl != null ? textEl.text() : a.text()).trim();
                String parent = null;
                if (level > 1) {
                    Element parentLi = li.parent() == null ? null : li.parent().closest("li.main-menu__item-level-" + (level - 1));
                    if (parentLi != null) {
                        Element pa = parentLi.selectFirst("> a.main-menu__link-level-" + (level - 1));
                        parent = pa == null ? null : slugOf(pa.attr("href"));
                    }
                }
                boolean hasChildren = !li.select("li.main-menu__item-level-" + (level + 1)).isEmpty();
                out.put(slug, new DiscoveredCategory(slug, name, a.absUrl("href"), parent, level, !hasChildren));
            }
        }
        return new ArrayList<>(out.values());
    }

    static String slugOf(String href) {
        if (href == null) {
            return null;
        }
        Matcher m = CATALOG_SLUG.matcher(href.trim());
        return m.find() ? m.group(1) : null;
    }

    public static ListingPage parseListing(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        List<ListingItem> items = new ArrayList<>();
        for (Element card : doc.select("div.product.js-product")) {
            Element link = card.selectFirst("a.product__cover-link[href], a.product__images[href]");
            Element idInput = card.selectFirst("input[name=id]");
            String url = link == null ? null : link.absUrl("href");
            String id = idInput != null ? idInput.attr("value").trim() : null;
            if ((id == null || id.isEmpty()) && link != null) {
                Matcher m = PRODUCT_URL.matcher(link.attr("href"));
                id = m.find() ? m.group(2) : null;
            }
            if (id == null || id.isEmpty()) {
                continue;
            }
            Element nameEl = card.selectFirst(".product__name");
            String name = nameEl == null ? null : nameEl.text().trim();

            Element priceBox = card.selectFirst(".product__price");
            BigDecimal price = null;
            BigDecimal old = null;
            BigDecimal step = null;
            String stepUnit = null;
            BigDecimal unitPrice = null;
            String unitPriceUnit = null;
            if (priceBox != null) {
                price = number(priceBox.selectFirst(".price__new .price__text"));
                old = number(priceBox.selectFirst(".price__old .price__text"));
                for (Element cur : priceBox.select(".price__new > span.price__currency")) {
                    Matcher sm = STEP.matcher(cur.text());
                    if (sm.find()) {
                        step = new BigDecimal(sm.group(1).replace(',', '.'));
                        stepUnit = sm.group(2).replace(".", "");
                    }
                }
                Element kg = priceBox.selectFirst(".card__kg-price");
                if (kg != null) {
                    unitPrice = number(kg);
                    Element cur = kg.selectFirst(".price__currency");
                    unitPriceUnit = cur == null ? null : unitOf(cur.text());
                }
            }
            Element unitEl = card.selectFirst(".product__unit");
            Element counter = card.selectFirst("[data-max-quantity]");
            Integer maxQty = null;
            if (counter != null) {
                try {
                    maxQty = (int) Math.floor(Double.parseDouble(counter.attr("data-max-quantity").trim()));
                } catch (NumberFormatException ignored) {
                    // leave null
                }
            }
            // Out-of-stock cards show "Առկա չէ" / "Not available" instead of a price and basket button.
            boolean outOfStock = card.selectFirst(".card__available") != null && card.selectFirst(".basket-counter") == null;
            items.add(new ListingItem(id, url, name, price, old, unitPrice, unitPriceUnit, step, stepUnit,
                    unitEl == null ? null : unitEl.text().trim(), maxQty, image(card, baseUrl), outOfStock));
        }
        int maxOffset = 0;
        for (Element a : doc.select(".pagination__links a[href]")) {
            Matcher m = OFFSET.matcher(a.attr("href"));
            if (m.find()) {
                maxOffset = Math.max(maxOffset, Integer.parseInt(m.group(1)));
            }
        }
        return new ListingPage(items, maxOffset);
    }

    private static String image(Element card, String baseUrl) {
        Element pic = card.selectFirst("v-picture");
        if (pic != null) {
            String sources = pic.attr(":sources");
            if (!sources.isBlank()) {
                try {
                    JsonNode n = JSON.readTree(sources);
                    for (String k : new String[]{"big", "middle", "small"}) {
                        if (n.hasNonNull(k)) {
                            return absolute(n.get(k).asText(), baseUrl);
                        }
                    }
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        Element img = card.selectFirst("img[src]");
        return img == null ? null : img.absUrl("src");
    }

    private static String absolute(String path, String baseUrl) {
        if (path.startsWith("http")) {
            return path;
        }
        return baseUrl.replaceAll("/+$", "") + (path.startsWith("/") ? "" : "/") + path;
    }

    public static Detail parseDetail(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        Element card = doc.selectFirst("div.card");
        if (card == null) {
            return null;
        }
        Element idInput = card.selectFirst("input[name=id]");
        String id = idInput != null ? idInput.attr("value").trim() : text(card.selectFirst(".card__id-value"));
        String sku = text(card.selectFirst(".card__code-value"));
        String name = text(card.selectFirst("h1.card__title"));
        String brand = text(card.selectFirst(".card__brand-link"));
        Element priceBox = card.selectFirst(".card__price .price");
        BigDecimal price = priceBox == null ? null : number(priceBox.selectFirst(".price__new .price__text"));
        BigDecimal old = priceBox == null ? null : number(priceBox.selectFirst(".price__old .price__text"));
        Element kg = card.selectFirst(".card__kg-price");
        BigDecimal unitPrice = number(kg);
        String unitPriceUnit = kg == null || kg.selectFirst(".price__currency") == null ? null
                : unitOf(kg.selectFirst(".price__currency").text());
        String stockText = text(card.selectFirst(".card__availability-text"));
        BigDecimal stock = null;
        if (stockText != null) {
            Matcher m = STOCK.matcher(stockText);
            if (m.find()) {
                stock = new BigDecimal(m.group(1).replace(',', '.'));
            }
        }
        Element og = doc.selectFirst("meta[property=og:image]");
        Element desc = doc.selectFirst("meta[property=og:description]");
        List<String> crumbs = new ArrayList<>();
        for (Element c : doc.select(".breadcrumbs__item a.breadcrumbs__link")) {
            crumbs.add(c.text().trim());
        }
        return new Detail(id, sku, name, brand, price, old, unitPrice, unitPriceUnit, stockText, stock,
                og == null ? null : og.attr("content"), crumbs, desc == null ? null : desc.attr("content"));
    }

    /** "դր./1կգ" -> "kg", "դր./1լ" -> "l". */
    static String unitOf(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replace(" ", "");
        if (t.contains("կգ") || t.contains("kg") || t.contains("кг")) {
            return "kg";
        }
        if (t.contains("լ") || t.endsWith("l") || t.contains("л")) {
            return "l";
        }
        if (t.contains("հատ") || t.contains("pcs") || t.contains("шт")) {
            return "pcs";
        }
        return null;
    }

    static BigDecimal number(Element el) {
        if (el == null) {
            return null;
        }
        String t = el.ownText().isBlank() ? el.text() : el.ownText();
        t = t.replace(' ', ' ').replace(" ", "").replace(",", "");
        Matcher m = NUMBER.matcher(t);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private static String text(Element el) {
        if (el == null) {
            return null;
        }
        String t = el.text().trim();
        return t.isEmpty() ? null : t;
    }

    static Elements cards(Document doc) {
        return doc.select("div.product.js-product");
    }
}
