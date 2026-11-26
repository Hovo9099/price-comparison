package am.pricecompare.crawler.supermarketam;

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
 * Crawls Supermarket.am (https://supermarket.am, 1C-Bitrix). The site has no robots.txt restrictions. Level-2
 * categories of the main menu aggregate their sub-categories and are paged with the storefront's own parameters
 * {@code ?TOVGRUPCODE={id}&PAGE_ELEMENT_COUNT=100&PAGEN_1=N}. Listing cards carry id, name, price, old price,
 * weighted-goods flag and image; the site publishes neither barcodes nor brands separately.
 */
@Component
public class SupermarketAmProvider implements SupermarketProvider {

    private static final Logger log = LoggerFactory.getLogger(SupermarketAmProvider.class);
    public static final String CODE = "supermarket-am";
    private static final int PAGE_SIZE = 100;
    /** New arrivals and daily discounts repeat regular products; tobacco is not groceries. */
    private static final String DEFAULT_SKIP = "novinki_8102,save_5543,tabachnye-izdeliya-i-prinadlezhnosti_1242";
    /** Diet groupings repeat products of regular categories: crawled last so real categories win. */
    private static final String DEFAULT_LATE = "kholesterin_6454,diabeticheskie-produkty_1057";

    private final PoliteHttpClient http;
    private final String baseUrl;

    public SupermarketAmProvider(PoliteHttpClient http, CrawlerProperties props) {
        this.http = http;
        this.baseUrl = props.provider(CODE).getOptions().getOrDefault("base-url", "https://supermarket.am");
    }

    @Override
    public String getStoreCode() {
        return CODE;
    }

    @Override
    public String getStoreName() {
        return "Supermarket.am";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<DiscoveredCategory> discoverCategories() {
        return SupermarketAmParser.parseMenu(http.get(baseUrl + "/"), baseUrl);
    }

    @Override
    public void discoverProducts(CrawlContext ctx) {
        List<DiscoveredCategory> categories = discoverCategories();
        ctx.pageVisited();
        Map<String, DiscoveredCategory> bySlug = categories.stream()
                .collect(Collectors.toMap(DiscoveredCategory::externalId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<String> skip = csv(ctx.settings().getOptions().getOrDefault("skip-categories", DEFAULT_SKIP));
        Set<String> late = csv(ctx.settings().getOptions().getOrDefault("late-categories", DEFAULT_LATE));
        List<DiscoveredCategory> units = new java.util.ArrayList<>(categories.stream()
                .filter(DiscoveredCategory::leaf)
                .filter(c -> !skip.contains(top(c, bySlug)))
                .toList());
        units.sort(java.util.Comparator.comparingInt(c -> late.contains(top(c, bySlug)) ? 1 : 0));
        log.info("Supermarket.am: {} categories, {} crawl units", categories.size(), units.size());
        Set<String> seen = new HashSet<>();
        for (DiscoveredCategory unit : units) {
            if (ctx.shouldStop()) {
                break;
            }
            String path = path(unit, bySlug);
            String code = SupermarketAmParser.categoryCode(unit.externalId());
            int pages = 1;
            for (int page = 1; page <= pages && !ctx.shouldStop(); page++) {
                String url = baseUrl + "/categories/" + unit.externalId() + "/?TOVGRUPCODE=" + code
                        + "&PAGE_ELEMENT_COUNT=" + PAGE_SIZE + "&PAGEN_1=" + page;
                SupermarketAmParser.ListingPage lp;
                try {
                    lp = SupermarketAmParser.parseListing(http.get(url), baseUrl);
                    ctx.pageVisited();
                } catch (PoliteHttpClient.FetchException e) {
                    if (e.status() == 404) {
                        log.debug("Supermarket.am: empty category {}", unit.externalId());
                        break; // the menu links categories that currently have no products; they answer 404
                    }
                    ctx.error(url, null, "Listing: " + e.getMessage());
                    break;
                } catch (RuntimeException e) {
                    ctx.error(url, null, "Listing: " + e.getMessage());
                    break;
                }
                pages = Math.max(1, (int) Math.ceil(lp.total() / (double) PAGE_SIZE));
                Instant now = Instant.now();
                for (SupermarketAmParser.Card c : lp.cards()) {
                    if (ctx.shouldStop()) {
                        break;
                    }
                    if (!seen.add(c.id())) {
                        continue;
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

    static DiscoveredProduct toProduct(SupermarketAmParser.Card c, String path, Instant now) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("price", c.price());
        raw.put("oldPrice", c.oldPrice());
        raw.put("soldByWeight", c.soldByWeight());
        raw.put("maxAllowed", c.maxAllowed());
        raw.put("adult", c.adult());
        boolean available = c.maxAllowed() == null || c.maxAllowed().signum() > 0;
        return new DiscoveredProduct(c.id(), null, null, c.name(), null, null, path, c.url(), c.imageUrl(), c.price(),
                c.oldPrice(), null, null, "AMD", available, c.soldByWeight(), null, c.soldByWeight() ? "kg" : null,
                false, now, raw);
    }

    private static String top(DiscoveredCategory c, Map<String, DiscoveredCategory> bySlug) {
        DiscoveredCategory cur = c;
        int guard = 0;
        while (cur.parentExternalId() != null && bySlug.containsKey(cur.parentExternalId()) && guard++ < 5) {
            cur = bySlug.get(cur.parentExternalId());
        }
        return cur.externalId();
    }

    private static Set<String> csv(String s) {
        Set<String> out = new HashSet<>();
        for (String part : s.split(",")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
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
