package am.pricecompare.crawler.parma;

import am.pricecompare.crawler.CrawlContext;
import am.pricecompare.crawler.CrawlerProperties;
import am.pricecompare.crawler.DiscoveredCategory;
import am.pricecompare.crawler.DiscoveredProduct;
import am.pricecompare.crawler.PoliteHttpClient;
import am.pricecompare.crawler.SupermarketProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Parma (https://parma.am, robots.txt allows everything). Public server-rendered catalog: the menu lists ~170
 * categories; every leaf category is read page by page ({@code &page=N&per-page=60}) until the last page linked from
 * its pagination. Cards carry name, price (per kg for products sold by weight), old price and promotion period, image
 * and the store's product code. Brand/country are only on product pages and are not fetched (names carry the brand
 * in «»).
 */
@Component
public class ParmaProvider implements SupermarketProvider {

    private static final Logger log = LoggerFactory.getLogger(ParmaProvider.class);
    public static final String CODE = "parma";
    private static final int PAGE_SIZE = 60;
    /** Safety stop for a pagination that never ends. */
    private static final int MAX_PAGES_PER_CATEGORY = 200;

    private final PoliteHttpClient http;
    private final String baseUrl;

    public ParmaProvider(PoliteHttpClient http, CrawlerProperties props) {
        this.http = http;
        this.baseUrl = props.provider(CODE).getOptions().getOrDefault("base-url", "https://parma.am");
    }

    @Override
    public String getStoreCode() {
        return CODE;
    }

    @Override
    public String getStoreName() {
        return "Parma";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<DiscoveredCategory> discoverCategories() {
        return ParmaParser.parseMenu(http.get(baseUrl + "/hy/"), baseUrl);
    }

    @Override
    public void discoverProducts(CrawlContext ctx) {
        List<DiscoveredCategory> categories = discoverCategories();
        ctx.pageVisited();
        if (categories.isEmpty()) {
            throw new IllegalStateException("Parma: no categories found in the menu (page layout changed?)");
        }
        Map<String, DiscoveredCategory> bySlug = categories.stream()
                .collect(Collectors.toMap(DiscoveredCategory::externalId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<DiscoveredCategory> leaves = categories.stream().filter(DiscoveredCategory::leaf).toList();
        log.info("Parma: {} categories, {} leaf categories to crawl", categories.size(), leaves.size());
        Set<String> seen = new HashSet<>();
        for (DiscoveredCategory leaf : leaves) {
            if (ctx.shouldStop()) {
                break;
            }
            String path = path(leaf, bySlug);
            int lastPage = 1;
            for (int page = 1; page <= lastPage && page <= MAX_PAGES_PER_CATEGORY && !ctx.shouldStop(); page++) {
                String url = baseUrl + "/hy/product/category?slug=" + leaf.externalId() + "&page=" + page + "&per-page=" + PAGE_SIZE;
                ParmaParser.ListingPage lp;
                try {
                    lp = ParmaParser.parseListing(http.get(url), baseUrl);
                    ctx.pageVisited();
                } catch (PoliteHttpClient.FetchException e) {
                    if (e.status() == 404) {
                        break;
                    }
                    ctx.error(url, null, "Listing: " + e.getMessage());
                    break;
                } catch (RuntimeException e) {
                    ctx.error(url, null, "Listing: " + e.getMessage());
                    break;
                }
                lastPage = Math.max(lastPage, lp.lastPage());
                Instant now = Instant.now();
                for (ParmaParser.Card c : lp.cards()) {
                    if (ctx.shouldStop()) {
                        break;
                    }
                    if (!seen.add(c.id())) {
                        continue; // the same product listed in several categories
                    }
                    if (c.price() == null || c.price().signum() <= 0) {
                        ctx.error(c.url(), c.id(), "Card without price");
                        continue;
                    }
                    ctx.emit(toProduct(c, path, now));
                }
                if (lp.cards().isEmpty()) {
                    break;
                }
            }
        }
    }

    static DiscoveredProduct toProduct(ParmaParser.Card c, String path, Instant now) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("price", c.price());
        raw.put("oldPrice", c.oldPrice());
        raw.put("soldByWeight", c.soldByWeight());
        raw.put("unitPrice", c.unitPriceText());
        raw.put("promotion", c.discountText());
        return new DiscoveredProduct(c.id(), c.id(), null, c.name(), null, null, path, c.url(), c.imageUrl(), c.price(),
                c.oldPrice(), null, null, "AMD", true, c.soldByWeight(), null, c.soldByWeight() ? "kg" : null,
                false, now, raw);
    }

    private static String path(DiscoveredCategory c, Map<String, DiscoveredCategory> bySlug) {
        StringBuilder sb = new StringBuilder(c.name());
        DiscoveredCategory cur = c.parentExternalId() == null ? null : bySlug.get(c.parentExternalId());
        int guard = 0;
        while (cur != null && guard++ < 5) {
            sb.insert(0, cur.name() + " > ");
            cur = cur.parentExternalId() == null ? null : bySlug.get(cur.parentExternalId());
        }
        return sb.toString();
    }
}
