package am.pricecompare.crawler.sas;

import am.pricecompare.crawler.CrawlContext;
import am.pricecompare.crawler.CrawlerProperties;
import am.pricecompare.crawler.DiscoveredCategory;
import am.pricecompare.crawler.DiscoveredProduct;
import am.pricecompare.crawler.PoliteHttpClient;
import am.pricecompare.crawler.SupermarketProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Crawls the public catalog of SAS Supermarket (https://www.sas.am).
 *
 * <p>Phase 1 walks every leaf section of the catalog menu with the storefront's own page-size and offset
 * parameters ({@code ?LIMIT=60&offset=N}); listing cards already carry name, price, discount, unit price and
 * stock, so all prices are captured quickly. English names come from the same listing under {@code /en/}.
 * Phase 2 visits product pages (only for products never enriched, by default) to add the SKU ("Ապրանքի կոդը")
 * and brand. robots.txt disallows {@code PAGEN_*} pagination, which is why offset pagination is used.
 */
@Component
public class SasProvider implements SupermarketProvider {

    private static final Logger log = LoggerFactory.getLogger(SasProvider.class);
    public static final String CODE = "sas";
    private static final int PAGE_SIZE = 60;
    private static final Set<String> DEFAULT_SKIPPED_SECTIONS = Set.of("new_products", "discount");
    /** Thematic groupings that repeat products from regular sections; crawled last so real categories win. */
    private static final String DEFAULT_LATE_SECTIONS = "sas_product,mm_*,sas_home,sas_sweet";

    private final PoliteHttpClient http;
    private final String baseUrl;

    public SasProvider(PoliteHttpClient http, CrawlerProperties props) {
        this.http = http;
        this.baseUrl = props.provider(CODE).getOptions().getOrDefault("base-url", "https://www.sas.am");
    }

    @Override
    public String getStoreCode() {
        return CODE;
    }

    @Override
    public String getStoreName() {
        return "SAS Supermarket";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<DiscoveredCategory> discoverCategories() {
        String html = http.get(baseUrl + "/catalog/");
        return SasParser.parseMenu(html, baseUrl);
    }

    @Override
    public void discoverProducts(CrawlContext ctx) {
        CrawlerProperties.ProviderSettings settings = ctx.settings();
        List<DiscoveredCategory> categories = discoverCategories();
        ctx.pageVisited();
        Map<String, String> englishNames = new HashMap<>();
        if (settings.isFetchEnglishNames()) {
            try {
                for (DiscoveredCategory c : SasParser.parseMenu(http.get(baseUrl + "/en/catalog/"), baseUrl)) {
                    englishNames.put(c.externalId(), c.name());
                }
                ctx.pageVisited();
            } catch (RuntimeException e) {
                ctx.error(baseUrl + "/en/catalog/", null, "English menu: " + e.getMessage());
            }
        }
        Map<String, DiscoveredCategory> bySlug = categories.stream()
                .collect(Collectors.toMap(DiscoveredCategory::externalId, c -> c, (a, b) -> a, LinkedHashMap::new));
        Set<String> skipped = skippedSections(settings);
        List<String> late = Arrays.stream(settings.getOptions().getOrDefault("late-sections", DEFAULT_LATE_SECTIONS).split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).toList();
        List<DiscoveredCategory> leaves = new ArrayList<>(categories.stream()
                .filter(DiscoveredCategory::leaf)
                .filter(c -> !isUnder(c, bySlug, skipped))
                .toList());
        leaves.sort(java.util.Comparator.comparingInt(c -> isUnderPattern(c, bySlug, late) ? 1 : 0));
        log.info("SAS: {} categories, {} leaf sections to crawl", categories.size(), leaves.size());

        Map<String, DiscoveredProduct> seen = new LinkedHashMap<>();
        for (DiscoveredCategory leaf : leaves) {
            if (ctx.shouldStop()) {
                break;
            }
            crawlSection(ctx, leaf, categoryPath(leaf, bySlug, englishNames), seen);
        }
        log.info("SAS: listing phase finished, {} unique products", seen.size());

        // Phase 2: product pages for SKU / brand / stock.
        Set<String> withDetails = ctx.productsWithDetails();
        for (DiscoveredProduct p : seen.values()) {
            if (ctx.isCancelled()) {
                break;
            }
            if (!settings.detailsFor(p.externalId(), withDetails)) {
                continue;
            }
            try {
                String html = http.get(p.url());
                ctx.pageVisited();
                SasParser.Detail d = SasParser.parseDetail(html, baseUrl);
                if (d == null) {
                    ctx.error(p.url(), p.externalId(), "Product page has no product card");
                    continue;
                }
                ctx.emit(enrich(p, d));
            } catch (PoliteHttpClient.FetchException e) {
                ctx.error(p.url(), p.externalId(), e.getMessage());
            } catch (RuntimeException e) {
                ctx.error(p.url(), p.externalId(), "Detail parse error: " + e);
            }
        }
    }

    private void crawlSection(CrawlContext ctx, DiscoveredCategory leaf, String path, Map<String, DiscoveredProduct> seen) {
        String firstIdOfSection = null;
        int offset = 0;
        while (!ctx.shouldStop()) {
            String query = "?LIMIT=" + PAGE_SIZE + (offset > 0 ? "&offset=" + offset : "");
            String url = baseUrl + "/catalog/" + leaf.externalId() + "/" + query;
            SasParser.ListingPage page;
            try {
                page = SasParser.parseListing(http.get(url), baseUrl);
                ctx.pageVisited();
            } catch (RuntimeException e) {
                ctx.error(url, null, "Listing: " + e.getMessage());
                return;
            }
            if (page.items().isEmpty()) {
                return;
            }
            String first = page.items().get(0).id();
            if (offset > 0 && first.equals(firstIdOfSection)) {
                return; // storefront wraps around to page 1 past the last page
            }
            if (offset == 0) {
                firstIdOfSection = first;
            }
            Map<String, String> en = englishNames(ctx, leaf, query);
            Instant now = Instant.now();
            for (SasParser.ListingItem item : page.items()) {
                if (ctx.shouldStop()) {
                    return;
                }
                if (seen.containsKey(item.id())) {
                    continue; // the same product is linked from several sections
                }
                try {
                    DiscoveredProduct p = fromListing(item, en.get(item.id()), path, now);
                    if (p == null) {
                        ctx.error(item.url(), item.id(), "Listing card without price");
                        continue;
                    }
                    seen.put(item.id(), p);
                    ctx.emit(p);
                } catch (RuntimeException e) {
                    ctx.error(item.url(), item.id(), "Listing card parse error: " + e);
                }
            }
            offset += PAGE_SIZE;
            if (offset > page.maxOffset()) {
                return;
            }
        }
    }

    private Map<String, String> englishNames(CrawlContext ctx, DiscoveredCategory leaf, String query) {
        if (!ctx.settings().isFetchEnglishNames()) {
            return Map.of();
        }
        String url = baseUrl + "/en/catalog/" + leaf.externalId() + "/" + query;
        try {
            SasParser.ListingPage en = SasParser.parseListing(http.get(url), baseUrl);
            ctx.pageVisited();
            Map<String, String> names = new HashMap<>();
            en.items().forEach(i -> {
                if (i.name() != null) {
                    names.put(i.id(), i.name());
                }
            });
            return names;
        } catch (RuntimeException e) {
            ctx.error(url, null, "English listing: " + e.getMessage());
            return Map.of();
        }
    }

    static DiscoveredProduct fromListing(SasParser.ListingItem item, String nameEn, String categoryPath, Instant capturedAt) {
        if (item.name() == null || (item.price() == null && !item.outOfStock())) {
            return null;
        }
        if (item.price() == null) {
            // Listed but not available: no price is shown, availability is still a real observation.
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("source", "listing");
            raw.put("outOfStock", true);
            raw.put("nameEn", nameEn);
            return new DiscoveredProduct(item.id(), null, null, item.name(), nameEn, null, categoryPath, item.url(),
                    item.imageUrl(), null, null, null, null, "AMD", false, false, null, null, false, capturedAt, raw);
        }
        boolean byWeight = item.soldByWeight();
        BigDecimal price = item.price();
        BigDecimal old = item.oldPrice();
        if (byWeight) {
            // Normalise weighted goods to the price of 1 kg so they compare across stores.
            BigDecimal perKg = item.unitPrice() != null && "kg".equals(item.unitPriceUnit()) ? item.unitPrice()
                    : perKg(price, item.saleStep(), item.saleStepUnit());
            if (old != null && price.signum() > 0) {
                old = old.multiply(perKg).divide(price, 0, RoundingMode.HALF_UP);
            }
            price = perKg;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("source", "listing");
        raw.put("price", item.price());
        raw.put("oldPrice", item.oldPrice());
        raw.put("unitPrice", item.unitPrice());
        raw.put("unitPriceUnit", item.unitPriceUnit());
        raw.put("saleStep", item.saleStep());
        raw.put("saleStepUnit", item.saleStepUnit());
        raw.put("unitLabel", item.unitLabel());
        raw.put("maxQuantity", item.maxQuantity());
        raw.put("nameEn", nameEn);
        boolean available = !item.outOfStock() && (item.maxQuantity() == null || item.maxQuantity() > 0);
        return new DiscoveredProduct(item.id(), null, null, item.name(), nameEn, null, categoryPath, item.url(),
                item.imageUrl(), price, old, item.unitPrice(), item.unitPriceUnit(), "AMD", available, byWeight,
                null, byWeight ? "kg" : null, false, capturedAt, raw);
    }

    private static BigDecimal perKg(BigDecimal price, BigDecimal step, String stepUnit) {
        if (step == null || step.signum() == 0) {
            return price;
        }
        BigDecimal kg = stepUnit != null && (stepUnit.equals("գ") || stepUnit.equals("g"))
                ? step.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP) : step;
        return price.divide(kg, 0, RoundingMode.HALF_UP);
    }

    static DiscoveredProduct enrich(DiscoveredProduct p, SasParser.Detail d) {
        Map<String, Object> raw = new LinkedHashMap<>(p.rawData());
        raw.put("source", "listing+detail");
        raw.put("sku", d.sku());
        raw.put("brand", d.brand());
        raw.put("stockText", d.stockText());
        raw.put("breadcrumbs", d.breadcrumbs());
        raw.put("detailPrice", d.price());
        raw.put("detailOldPrice", d.oldPrice());
        boolean available = d.stockQuantity() == null ? p.available() : d.stockQuantity().signum() > 0;
        String brand = d.brand() == null ? null : am.pricecompare.normalize.ProductNormalizer.cleanBrand(d.brand());
        return new DiscoveredProduct(p.externalId(), d.sku(), null, p.name(), p.nameEn(), brand, p.categoryPath(),
                p.url(), d.imageUrl() != null ? d.imageUrl() : p.imageUrl(), p.price(), p.originalPrice(),
                p.unitPrice(), p.unitPriceUnit(), p.currency(), available, p.soldByWeight(), p.packageSize(),
                p.packageUnit(), true, p.capturedAt(), raw);
    }

    private static Set<String> skippedSections(CrawlerProperties.ProviderSettings settings) {
        String opt = settings.getOptions().get("skip-sections");
        if (opt == null) {
            return DEFAULT_SKIPPED_SECTIONS;
        }
        return new HashSet<>(Arrays.stream(opt.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private static boolean isUnder(DiscoveredCategory c, Map<String, DiscoveredCategory> bySlug, Set<String> slugs) {
        DiscoveredCategory cur = c;
        int guard = 0;
        while (cur != null && guard++ < 10) {
            if (slugs.contains(cur.externalId())) {
                return true;
            }
            cur = cur.parentExternalId() == null ? null : bySlug.get(cur.parentExternalId());
        }
        return false;
    }

    private static boolean isUnderPattern(DiscoveredCategory c, Map<String, DiscoveredCategory> bySlug, List<String> patterns) {
        DiscoveredCategory cur = c;
        int guard = 0;
        while (cur != null && guard++ < 10) {
            String slug = cur.externalId();
            for (String p : patterns) {
                if (p.endsWith("*") ? slug.startsWith(p.substring(0, p.length() - 1)) : slug.equals(p)) {
                    return true;
                }
            }
            cur = cur.parentExternalId() == null ? null : bySlug.get(cur.parentExternalId());
        }
        return false;
    }

    private static String categoryPath(DiscoveredCategory leaf, Map<String, DiscoveredCategory> bySlug, Map<String, String> en) {
        List<String> hy = new ArrayList<>();
        List<String> eng = new ArrayList<>();
        DiscoveredCategory cur = leaf;
        int guard = 0;
        while (cur != null && guard++ < 10) {
            hy.add(0, cur.name());
            String e = en.get(cur.externalId());
            if (e != null) {
                eng.add(0, e);
            }
            cur = cur.parentExternalId() == null ? null : bySlug.get(cur.parentExternalId());
        }
        String path = String.join(" > ", hy);
        return eng.isEmpty() ? path : path + " | " + String.join(" > ", eng);
    }
}
