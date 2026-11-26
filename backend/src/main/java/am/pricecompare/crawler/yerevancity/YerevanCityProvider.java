package am.pricecompare.crawler.yerevancity;

import am.pricecompare.crawler.CrawlContext;
import am.pricecompare.crawler.CrawlerProperties;
import am.pricecompare.crawler.DiscoveredCategory;
import am.pricecompare.crawler.DiscoveredProduct;
import am.pricecompare.crawler.PoliteHttpClient;
import am.pricecompare.crawler.SupermarketProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the public catalog of Yerevan City (https://yerevan-city.am) through the same JSON API its web
 * storefront uses (no authentication). Top-level categories are paged with {@code Product/GetByCategory}
 * (which includes all sub-categories) and return Armenian names. {@code Product/GetPublic/{id}} honours
 * {@code content-language} (1 = English) and adds the English name, Latin brand, barcode and online availability,
 * which makes this the best source for cross-store matching.
 */
@Component
public class YerevanCityProvider implements SupermarketProvider {

    private static final Logger log = LoggerFactory.getLogger(YerevanCityProvider.class);
    public static final String CODE = "yerevan-city";
    static final String LANG_EN = "1";
    static final String LANG_HY = "3";
    private static final int PAGE_SIZE = 100;
    /** 1563 = tobacco (not groceries); 40 = "Dietary, gluten free and bio" repeats products of other categories. */
    private static final String DEFAULT_SKIP = "1563";
    private static final String DEFAULT_LATE = "40";

    private final PoliteHttpClient http;
    private final String apiBase;
    private final String siteBase;
    private final ObjectMapper json = new ObjectMapper();

    public YerevanCityProvider(PoliteHttpClient http, CrawlerProperties props) {
        this.http = http;
        CrawlerProperties.ProviderSettings s = props.provider(CODE);
        this.apiBase = s.getOptions().getOrDefault("api-base", "https://apishopv2.yerevan-city.am/api");
        this.siteBase = s.getOptions().getOrDefault("site-base", "https://yerevan-city.am");
        if (s.getMinDelayMs() != null) {
            http.setHostDelay(URI.create(apiBase).getHost(), s.getMinDelayMs());
        }
    }

    @Override
    public String getStoreCode() {
        return CODE;
    }

    @Override
    public String getStoreName() {
        return "Yerevan City";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private Map<String, String> headers(String lang) {
        return Map.of("Accept", "application/json", "content-language", lang);
    }

    @Override
    public List<DiscoveredCategory> discoverCategories() {
        List<DiscoveredCategory> out = new ArrayList<>();
        for (YerevanCityParser.TopCategory c : topCategories(LANG_HY)) {
            out.add(new DiscoveredCategory(String.valueOf(c.id()), c.name(), siteBase + "/shop/categories/" + c.id(), null, 1, false));
        }
        return out;
    }

    /** {@code GET /api/Store/GetAllWeb} lists every branch with address and coordinates. */
    @Override
    public List<am.pricecompare.crawler.DiscoveredLocation> discoverLocations() {
        return YerevanCityParser.parseStores(http.get(apiBase + "/Store/GetAllWeb", headers(LANG_EN)));
    }

    private List<YerevanCityParser.TopCategory> topCategories(String lang) {
        return YerevanCityParser.parseTopCategories(http.postJson(apiBase + "/Category/GetParentCategories", "{}", headers(lang)));
    }

    @Override
    public void discoverProducts(CrawlContext ctx) {
        CrawlerProperties.ProviderSettings settings = ctx.settings();
        Set<Long> skip = ids(settings.getOptions().getOrDefault("skip-categories", DEFAULT_SKIP));
        Set<Long> late = ids(settings.getOptions().getOrDefault("late-categories", DEFAULT_LATE));
        List<YerevanCityParser.TopCategory> tops = new ArrayList<>(topCategories(LANG_EN));
        ctx.pageVisited();
        tops.removeIf(c -> c.hidden() || skip.contains(c.id()));
        tops.sort((a, b) -> Boolean.compare(late.contains(a.id()), late.contains(b.id())));
        log.info("Yerevan City: {} top-level categories to crawl", tops.size());

        Set<Long> seen = new HashSet<>();
        Set<String> withDetails = ctx.productsWithDetails();
        for (YerevanCityParser.TopCategory top : tops) {
            if (ctx.shouldStop()) {
                break;
            }
            Map<String, String> paths = categoryPaths(ctx, top);
            for (int page = 1; !ctx.shouldStop(); page++) {
                YerevanCityParser.ListPage hy;
                String url = apiBase + "/Product/GetByCategory";
                String body = body(top.id(), page);
                try {
                    hy = YerevanCityParser.parseProductList(http.postJson(url, body, headers(LANG_HY)));
                    ctx.pageVisited();
                } catch (RuntimeException e) {
                    ctx.error(url + " " + body, null, "Listing: " + e.getMessage());
                    break;
                }
                Instant now = Instant.now();
                for (YerevanCityParser.ListItem item : hy.items()) {
                    if (ctx.shouldStop()) {
                        break;
                    }
                    if (!seen.add(item.id())) {
                        continue;
                    }
                    String id = String.valueOf(item.id());
                    try {
                        YerevanCityParser.Details details = null;
                        if (settings.detailsFor(id, withDetails)) {
                            details = details(ctx, item.id());
                        }
                        DiscoveredProduct p = toProduct(item, details == null ? null : details.name(), details, paths, top, now);
                        if (p == null) {
                            ctx.error(productUrl(item.id()), id, "Product without name or price");
                            continue;
                        }
                        ctx.emit(p);
                    } catch (RuntimeException e) {
                        ctx.error(productUrl(item.id()), id, "Parse error: " + e);
                    }
                }
                if (page >= hy.pageCount() || hy.items().isEmpty()) {
                    break;
                }
            }
        }
    }

    private Map<String, String> categoryPaths(CrawlContext ctx, YerevanCityParser.TopCategory top) {
        try {
            String r = http.postJson(apiBase + "/Category/GetCategoryWeb",
                    "{\"categoryId\":" + top.id() + ",\"parentId\":" + top.id() + ",\"count\":1,\"page\":1}", headers(LANG_EN));
            ctx.pageVisited();
            return YerevanCityParser.parseCategoryPaths(r);
        } catch (RuntimeException e) {
            ctx.error(apiBase + "/Category/GetCategoryWeb", String.valueOf(top.id()), "Category tree: " + e.getMessage());
            return Map.of();
        }
    }

    private YerevanCityParser.Details details(CrawlContext ctx, long id) {
        String url = apiBase + "/Product/GetPublic/" + id;
        try {
            YerevanCityParser.Details d = YerevanCityParser.parseDetails(http.get(url, headers(LANG_EN)));
            ctx.pageVisited();
            return d;
        } catch (RuntimeException e) {
            ctx.error(url, String.valueOf(id), "Details: " + e.getMessage());
            return null;
        }
    }

    DiscoveredProduct toProduct(YerevanCityParser.ListItem item, String nameEn, YerevanCityParser.Details d,
                                Map<String, String> paths, YerevanCityParser.TopCategory top, Instant now) {
        String name = item.name() != null ? item.name() : d != null ? d.name() : null;
        if (name == null || item.currentPrice() == null || item.currentPrice().signum() <= 0) {
            return null;
        }
        String leaf = item.categoryName() != null ? item.categoryName() : d != null ? d.category() : null;
        String path = leaf == null ? top.name() : paths.getOrDefault(leaf, top.name() + " > " + leaf);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("list", json.convertValue(item.raw(), Map.class));
        if (d != null) {
            raw.put("details", json.convertValue(d.raw(), Map.class));
        }
        boolean available = d == null || d.online() == null || d.online();
        return new DiscoveredProduct(String.valueOf(item.id()), null, d == null ? null : d.barcode(), name, nameEn,
                d == null ? null : d.brand(), path, productUrl(item.id()), item.photo(), item.currentPrice(),
                item.originalPrice(), null, null, d != null && d.currency() != null ? d.currency() : "AMD", available,
                item.kilogram(), null, item.kilogram() ? "kg" : null, d != null, now, raw);
    }

    private String productUrl(long id) {
        return siteBase + "/shop/product-details/" + id;
    }

    private static String body(long categoryId, int page) {
        return "{\"categoryId\":" + categoryId + ",\"parentId\":" + categoryId + ",\"count\":" + PAGE_SIZE + ",\"page\":" + page + "}";
    }

    private static Set<Long> ids(String csv) {
        Set<Long> out = new HashSet<>();
        Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(s -> out.add(Long.parseLong(s)));
        return out;
    }
}
