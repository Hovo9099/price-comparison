package am.pricecompare.crawler.carrefour;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses carrefour.am (Magento 2) sitemaps and product pages (schema.org Offer microdata). */
public final class CarrefourParser {

    private static final Pattern LOC = Pattern.compile("<loc>\\s*([^<\\s]+)\\s*</loc>");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private CarrefourParser() {
    }

    public record Product(String sku, String entityId, String name, String brand, BigDecimal price, BigDecimal oldPrice,
                          BigDecimal perKgPrice, boolean available, String imageUrl, String description) {
        public boolean soldByWeight() {
            return perKgPrice != null;
        }
    }

    /** Product URLs are single-segment paths below the language prefix, e.g. https://carrefour.am/am/coca-cola-pet-1l. */
    public static List<String> productUrls(String sitemapXml, String languagePrefix) {
        List<String> out = new ArrayList<>();
        Matcher m = LOC.matcher(sitemapXml);
        while (m.find()) {
            String url = m.group(1);
            int idx = url.indexOf("/" + languagePrefix + "/");
            if (idx < 0) {
                continue;
            }
            String rest = url.substring(idx + languagePrefix.length() + 2);
            if (!rest.isEmpty() && !rest.contains("/") && !rest.contains("?") && !rest.contains("privacy")
                    && !rest.contains("cookie")) {
                out.add(url);
            }
        }
        return out;
    }

    public static Product parseProduct(String html, String url) {
        Document doc = Jsoup.parse(html, url);
        Element main = doc.selectFirst(".product-info-main");
        if (main == null) {
            return null;
        }
        String name = text(main.selectFirst("h1.page-title [itemprop=name]"));
        if (name == null) {
            name = text(main.selectFirst("h1.page-title"));
        }
        String sku = text(main.selectFirst("[itemprop=sku]"));
        Element box = main.selectFirst(".price-box[data-product-id]");
        String entityId = box == null ? null : box.attr("data-product-id");
        BigDecimal price = amount(main.selectFirst("[data-price-type=finalPrice]"));
        if (price == null) {
            Element meta = main.selectFirst("meta[itemprop=price]");
            price = meta == null ? null : parse(meta.attr("content"));
        }
        BigDecimal old = amount(main.selectFirst("[data-price-type=oldPrice]"));
        BigDecimal perKg = null;
        Element weight = doc.selectFirst(".attribute-weight");
        if (weight != null && weight.text().contains("կգ")) {
            perKg = parse(weight.text().replace(" ", "").replace(" ", ""));
        }
        String brand = text(main.selectFirst(".product-attribute-manufacturer"));
        boolean unavailable = doc.selectFirst(".stock.unavailable") != null || price == null;
        Element og = doc.selectFirst("meta[property=og:image]");
        String desc = text(main.selectFirst("[itemprop=description]"));
        return new Product(sku, entityId, name, brand, price, old, perKg, !unavailable,
                og == null ? null : og.attr("content"), desc);
    }

    private static BigDecimal amount(Element el) {
        return el == null ? null : parse(el.attr("data-price-amount"));
    }

    private static BigDecimal parse(String s) {
        if (s == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(s.replace(",", ""));
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private static String text(Element el) {
        if (el == null) {
            return null;
        }
        String t = el.text().trim();
        return t.isEmpty() ? null : t;
    }
}
