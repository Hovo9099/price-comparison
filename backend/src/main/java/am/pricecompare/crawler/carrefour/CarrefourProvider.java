package am.pricecompare.crawler.carrefour;

import am.pricecompare.crawler.CrawlContext;
import am.pricecompare.crawler.CrawlerProperties;
import am.pricecompare.crawler.DiscoveredCategory;
import am.pricecompare.crawler.DiscoveredProduct;
import am.pricecompare.crawler.PoliteHttpClient;
import am.pricecompare.crawler.SupermarketProvider;
import am.pricecompare.normalize.ProductNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Crawls Carrefour Armenia (https://carrefour.am, Magento 2). robots.txt disallows every URL with a query
 * string, so category pagination cannot be used; instead product URLs are taken from the published sitemap
 * ({@code /sitemap_hy.xml}) and each product page is read (schema.org Offer price, SKU, brand, per-kg price).
 */
@Component
public class CarrefourProvider implements SupermarketProvider {

    private static final Logger log = LoggerFactory.getLogger(CarrefourProvider.class);
    public static final String CODE = "carrefour";

    private final PoliteHttpClient http;
    private final String baseUrl;

    public CarrefourProvider(PoliteHttpClient http, CrawlerProperties props) {
        this.http = http;
        this.baseUrl = props.provider(CODE).getOptions().getOrDefault("base-url", "https://carrefour.am");
    }

    @Override
    public String getStoreCode() {
        return CODE;
    }

    @Override
    public String getStoreName() {
        return "Carrefour Armenia";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<DiscoveredCategory> discoverCategories() {
        // Category pages exist but cannot be paginated within robots.txt rules; products come from the sitemap.
        return List.of();
    }

    @Override
    public void discoverProducts(CrawlContext ctx) {
        String sitemapUrl = baseUrl + "/sitemap_hy.xml";
        List<String> urls = List.copyOf(new LinkedHashSet<>(CarrefourParser.productUrls(http.get(sitemapUrl), "am")));
        ctx.pageVisited();
        log.info("Carrefour: {} product URLs in sitemap", urls.size());
        boolean english = ctx.settings().isFetchEnglishNames();
        for (String url : urls) {
            if (ctx.shouldStop()) {
                break;
            }
            try {
                CarrefourParser.Product p = CarrefourParser.parseProduct(http.get(url), url);
                ctx.pageVisited();
                if (p == null) {
                    continue; // single-segment category landing pages are listed in the sitemap too
                }
                if (p.name() == null || p.sku() == null) {
                    ctx.error(url, null, "Product page without name or SKU");
                    continue;
                }
                String nameEn = null;
                if (english) {
                    String enUrl = url.replace("/am/", "/en/");
                    try {
                        CarrefourParser.Product en = CarrefourParser.parseProduct(http.get(enUrl), enUrl);
                        ctx.pageVisited();
                        nameEn = en == null ? null : en.name();
                    } catch (RuntimeException e) {
                        ctx.error(enUrl, p.sku(), "English page: " + e.getMessage());
                    }
                }
                if (p.price() == null && p.available()) {
                    ctx.error(url, p.sku(), "No price on product page");
                    continue;
                }
                ctx.emit(toProduct(p, nameEn, url, Instant.now()));
            } catch (PoliteHttpClient.FetchException e) {
                if (e.status() == 404 || e.status() == 410) {
                    log.debug("Carrefour: sitemap URL no longer exists: {}", url);
                    continue; // the sitemap still lists delisted products
                }
                ctx.error(url, null, e.getMessage());
            } catch (RuntimeException e) {
                ctx.error(url, null, "Parse error: " + e);
            }
        }
    }

    static DiscoveredProduct toProduct(CarrefourParser.Product p, String nameEn, String url, Instant now) {
        BigDecimal price = p.price();
        BigDecimal old = p.oldPrice() != null && price != null && p.oldPrice().compareTo(price) > 0 ? p.oldPrice() : null;
        if (p.soldByWeight() && price != null) {
            // The displayed price is for one weight step; compare weighted goods per kilogram.
            if (old != null && price.signum() > 0) {
                old = old.multiply(p.perKgPrice()).divide(price, 0, RoundingMode.HALF_UP);
            }
            price = p.perKgPrice();
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("sku", p.sku());
        raw.put("entityId", p.entityId());
        raw.put("price", p.price());
        raw.put("oldPrice", p.oldPrice());
        raw.put("perKgPrice", p.perKgPrice());
        raw.put("brand", p.brand());
        raw.put("available", p.available());
        return new DiscoveredProduct(p.sku(), p.sku(), null, p.name(), nameEn, ProductNormalizer.cleanBrand(p.brand()),
                null, url, p.imageUrl(), price, old, p.perKgPrice(), p.perKgPrice() == null ? null : "kg", "AMD",
                p.available() && price != null, p.soldByWeight(), null, p.soldByWeight() ? "kg" : null, true, now, raw);
    }
}
