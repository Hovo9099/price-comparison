package am.pricecompare.crawler.zovq;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the public pages of zovq.am (Shopware 6 storefront; Nor Zovq's online shop since late 2026): the sitemap,
 * category listings (24 product boxes per page, {@code ?p=N}) and product pages (schema.org microdata with GTIN-13).
 */
public final class ZovqParser {

    /** Product URLs end with the product number, e.g. {@code /Kath-Ani-3.2-1l/00-00018964} or {@code .../CB-00026033}. */
    static final Pattern PRODUCT_NUMBER = Pattern.compile("/([A-Z0-9]{2}-\\d{5,})/?$");
    private static final Pattern LOC = Pattern.compile("<loc>\\s*([^<\\s]+)\\s*</loc>");
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d\\s\\u00a0]*(?:[.,]\\d+)?");
    /** "1կգ" / "2 կգ" is a pack size; a bare "կգ" means the price is per kg. */
    private static final Pattern PACK_KG = Pattern.compile("\\d\\s*կգ");

    private ZovqParser() {
    }

    public record Card(String id, String url, String name, BigDecimal price, BigDecimal listPrice, boolean soldByWeight,
                       String shopwareId) {
    }

    public record ListingPage(List<Card> cards, int lastPage) {
    }

    public record Detail(String id, String name, BigDecimal price, String currency, String gtin13, String sku,
                         String manufacturer, List<String> breadcrumb) {
    }

    /** {@code <loc>} values of a sitemap or sitemap index. */
    public static List<String> locs(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = LOC.matcher(xml);
        while (m.find()) {
            out.add(m.group(1).replace("&amp;", "&"));
        }
        return out;
    }

    public static String productNumber(String url) {
        Matcher m = PRODUCT_NUMBER.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Category listing URLs from the sitemap: same host, ending with "/", not a product, not under a skipped first
     * segment (e.g. {@code manufacturer}). Only leaf categories are returned (no other category below them).
     */
    public static List<String> leafCategories(List<String> urls, String baseUrl, Set<String> skippedFirstSegments) {
        String host = URI.create(baseUrl).getHost();
        Set<String> cats = new LinkedHashSet<>();
        for (String u : urls) {
            URI uri;
            try {
                uri = URI.create(u);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String path = uri.getPath();
            if (!host.equals(uri.getHost()) || path == null || !path.endsWith("/") || path.equals("/") || productNumber(u) != null) {
                continue;
            }
            String first = path.substring(1).split("/")[0];
            if (skippedFirstSegments.contains(first.toLowerCase())) {
                continue;
            }
            cats.add(baseUrl + path);
        }
        List<String> leaves = new ArrayList<>();
        for (String c : cats) {
            boolean hasChild = cats.stream().anyMatch(o -> !o.equals(c) && o.startsWith(c));
            if (!hasChild) {
                leaves.add(c);
            }
        }
        return leaves;
    }

    public static ListingPage parseListing(String html) {
        Document doc = Jsoup.parse(html);
        List<Card> cards = new ArrayList<>();
        for (Element box : doc.select(".product-box")) {
            Element link = box.selectFirst("a.product-name");
            if (link == null) {
                continue;
            }
            String url = link.absUrl("href").isEmpty() ? link.attr("href") : link.absUrl("href");
            String id = productNumber(url);
            if (id == null) {
                continue;
            }
            String name = link.hasAttr("title") && !link.attr("title").isBlank() ? link.attr("title").trim() : link.text().trim();
            Element priceEl = box.selectFirst(".product-price .price-unit-main");
            if (priceEl == null) {
                priceEl = box.selectFirst(".product-price");
            }
            BigDecimal price = priceEl == null ? null : amount(priceEl.ownText().isBlank() ? priceEl.text() : priceEl.ownText());
            Element list = box.selectFirst(".list-price .list-price-price, .list-price-price, .product-price .list-price");
            BigDecimal listPrice = list == null ? null : amount(list.text());
            if (listPrice != null && price != null && listPrice.compareTo(price) <= 0) {
                listPrice = null;
            }
            cards.add(new Card(id, url, name, price, listPrice, soldByWeight(name), shopwareId(box)));
        }
        int last = 1;
        Element pLast = doc.selectFirst("input#p-last");
        if (pLast != null) {
            BigDecimal v = amount(pLast.attr("value"));
            if (v != null) {
                last = Math.max(1, v.intValue());
            }
        }
        return new ListingPage(cards, last);
    }

    public static Detail parseDetail(String html, String url) {
        Document doc = Jsoup.parse(html);
        Element offer = doc.selectFirst("[itemprop=offers]");
        Element h1 = doc.selectFirst("h1.product-detail-name, h1[itemprop=name]");
        if (offer == null || h1 == null) {
            return null;
        }
        Element price = offer.selectFirst("meta[itemprop=price]");
        Element currency = offer.selectFirst("meta[itemprop=priceCurrency]");
        Element gtin = doc.selectFirst("meta[itemprop=gtin13]");
        Element sku = doc.selectFirst("[itemprop=sku]");
        Element manufacturer = doc.selectFirst("[itemprop=brand] meta[itemprop=name], [itemprop=manufacturer] meta[itemprop=name]");
        List<String> crumbs = new ArrayList<>();
        Elements items = doc.select("[itemprop=itemListElement] [itemprop=name]");
        for (Element e : items) {
            String t = e.hasAttr("content") ? e.attr("content") : e.text();
            if (!t.isBlank()) {
                crumbs.add(t.trim());
            }
        }
        String g = gtin == null ? null : gtin.attr("content").trim();
        if (g != null && !g.matches("\\d{8,14}")) {
            g = null;
        }
        return new Detail(productNumber(url), h1.text().trim(), price == null ? null : amount(price.attr("content")),
                currency == null || currency.attr("content").isBlank() ? "AMD" : currency.attr("content").trim(), g,
                sku == null ? null : sku.text().trim(),
                manufacturer == null ? null : manufacturer.attr("content").trim(), crumbs);
    }

    static boolean soldByWeight(String name) {
        if (name == null) {
            return false;
        }
        String n = name.replace(' ', ' ');
        return n.contains("կգ") && !PACK_KG.matcher(n).find();
    }

    private static String shopwareId(Element box) {
        String info = box.attr("data-product-information");
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f]{32})\"").matcher(info);
        return m.find() ? m.group(1) : null;
    }

    static BigDecimal amount(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(text);
        if (!m.find()) {
            return null;
        }
        String n = m.group().replaceAll("[\\s\\u00a0]", "").replace(',', '.');
        try {
            return new BigDecimal(n);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
