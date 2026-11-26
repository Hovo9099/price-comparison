package am.pricecompare.crawler.zovq;

import am.pricecompare.crawler.CrawlContext;
import am.pricecompare.crawler.CrawlerProperties;
import am.pricecompare.crawler.DiscoveredCategory;
import am.pricecompare.crawler.DiscoveredProduct;
import am.pricecompare.crawler.PoliteHttpClient;
import am.pricecompare.crawler.SupermarketProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nor Zovq. norzovq.am (Cloudflare 525 in October 2026) now redirects to zovq.am, a public Shopware 6 storefront whose
 * robots.txt sets no rules. Phase 1 reads every leaf category from the sitemap page by page ({@code ?p=N}, 24 boxes:
 * name, price, product number); phase 2 opens product pages only for products never enriched before, for the
 * GTIN-13 barcode (enables exact matching with Yerevan City), SKU and breadcrumb.
 */
@Component
public class ZovqProvider implements SupermarketProvider {

    private static final Logger log = LoggerFactory.getLogger(ZovqProvider.class);
    public static final String CODE = "nor-zovq";
    private static final int MAX_PAGES_PER_CATEGORY = 100;
    /** "manufacturer" pages list products by producer; "Datark-1" ("Empty") is an internal placeholder tree. */
    private static final String DEFAULT_SKIPPED = "manufacturer,datark-1,akcia-ak";

    private final PoliteHttpClient http;
    private final String baseUrl;
    private final Set<String> skipped;

    public ZovqProvider(PoliteHttpClient http, CrawlerProperties props) {
        this.http = http;
        Map<String, String> options = props.provider(CODE).getOptions();
        this.baseUrl = options.getOrDefault("base-url", "https://zovq.am").replaceAll("/+$", "");
        this.skipped = Arrays.stream(options.getOrDefault("skip-sections", DEFAULT_SKIPPED).split(","))
                .map(String::trim).map(String::toLowerCase).filter(x -> !x.isEmpty()).collect(Collectors.toSet());
    }

    @Override
    public String getStoreCode() {
        return CODE;
    }

    @Override
    public String getStoreName() {
        return "Nor Zovq";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /** All sitemap URLs (the index points to gzip-compressed child sitemaps). */
    List<String> sitemapUrls() {
        List<String> out = new ArrayList<>();
        for (String loc : ZovqParser.locs(http.get(baseUrl + "/sitemap.xml"))) {
            if (loc.contains(".xml")) {
                out.addAll(ZovqParser.locs(http.get(loc)));
            } else {
                out.add(loc);
            }
        }
        return out;
    }

    @Override
    public List<DiscoveredCategory> discoverCategories() {
        return ZovqParser.leafCategories(sitemapUrls(), baseUrl, skipped).stream()
                .map(u -> new DiscoveredCategory(u, categoryName(u), u, null, 1, true)).toList();
    }

    @Override
    public void discoverProducts(CrawlContext ctx) {
        List<String> urls = sitemapUrls();
        ctx.pageVisited();
        List<String> categories = ZovqParser.leafCategories(urls, baseUrl, skipped);
        if (categories.isEmpty()) {
            throw new IllegalStateException("Nor Zovq: no categories in the sitemap (site layout changed?)");
        }
        log.info("Nor Zovq: {} sitemap URLs, {} leaf categories", urls.size(), categories.size());

        Map<String, DiscoveredProduct> seen = new LinkedHashMap<>();
        for (String category : categories) {
            if (ctx.shouldStop()) {
                break;
            }
            String path = categoryName(category);
            int lastPage = 1;
            for (int page = 1; page <= lastPage && page <= MAX_PAGES_PER_CATEGORY && !ctx.shouldStop(); page++) {
                String url = page == 1 ? category : category + "?p=" + page;
                ZovqParser.ListingPage lp;
                try {
                    lp = ZovqParser.parseListing(http.get(url));
                    ctx.pageVisited();
                } catch (PoliteHttpClient.FetchException e) {
                    if (e.status() != 404) {
                        ctx.error(url, null, "Listing: " + e.getMessage());
                    }
                    break;
                } catch (RuntimeException e) {
                    ctx.error(url, null, "Listing: " + e.getMessage());
                    break;
                }
                lastPage = Math.max(lastPage, lp.lastPage());
                Instant now = Instant.now();
                for (ZovqParser.Card c : lp.cards()) {
                    if (ctx.shouldStop() || seen.containsKey(c.id())) {
                        continue;
                    }
                    if (c.price() == null || c.price().signum() <= 0) {
                        ctx.error(c.url(), c.id(), "Listing card without price");
                        continue;
                    }
                    DiscoveredProduct p = fromCard(c, path, now);
                    seen.put(c.id(), p);
                    ctx.emit(p);
                }
                if (lp.cards().isEmpty()) {
                    break;
                }
            }
        }
        log.info("Nor Zovq: listing phase finished, {} unique products", seen.size());

        CrawlerProperties.ProviderSettings settings = ctx.settings();
        Set<String> withDetails = ctx.productsWithDetails();
        for (DiscoveredProduct p : seen.values()) {
            if (ctx.isCancelled()) {
                break;
            }
            if (!settings.detailsFor(p.externalId(), withDetails)) {
                continue;
            }
            try {
                ZovqParser.Detail d = ZovqParser.parseDetail(http.get(p.url()), p.url());
                ctx.pageVisited();
                if (d == null) {
                    ctx.error(p.url(), p.externalId(), "Product page without product data");
                    continue;
                }
                ctx.emit(enrich(p, d, Instant.now()));
            } catch (PoliteHttpClient.FetchException e) {
                ctx.error(p.url(), p.externalId(), e.getMessage());
            } catch (RuntimeException e) {
                ctx.error(p.url(), p.externalId(), "Detail parse error: " + e);
            }
        }
    }

    static DiscoveredProduct fromCard(ZovqParser.Card c, String categoryPath, Instant now) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("price", c.price());
        raw.put("listPrice", c.listPrice());
        raw.put("shopwareId", c.shopwareId());
        return new DiscoveredProduct(c.id(), null, null, c.name(), null, null, categoryPath, c.url(), null, c.price(),
                c.listPrice(), null, null, "AMD", true, c.soldByWeight(), null, c.soldByWeight() ? "kg" : null, false, now, raw);
    }

    static DiscoveredProduct enrich(DiscoveredProduct p, ZovqParser.Detail d, Instant now) {
        Map<String, Object> raw = new LinkedHashMap<>(p.rawData());
        raw.put("sku", d.sku());
        raw.put("gtin13", d.gtin13());
        raw.put("manufacturer", d.manufacturer()); // legal producer name, not a brand: never used for matching
        raw.put("breadcrumb", d.breadcrumb());
        String path = d.breadcrumb().isEmpty() ? p.categoryPath() : String.join(" > ", d.breadcrumb());
        return new DiscoveredProduct(p.externalId(), d.sku(), d.gtin13(), d.name().isBlank() ? p.name() : d.name(), null, null,
                path, p.url(), null, d.price() != null ? d.price() : p.price(), p.originalPrice(), null, null, d.currency(),
                true, p.soldByWeight(), null, p.packageUnit(), true, now, raw);
    }

    /** Readable name from the category URL slug (the sitemap carries no titles). */
    static String categoryName(String url) {
        String path = URI.create(url).getPath();
        String[] parts = path.replaceAll("^/|/$", "").split("/");
        return Arrays.stream(parts).map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8).replace('-', ' '))
                .collect(Collectors.joining(" > "));
    }
}
