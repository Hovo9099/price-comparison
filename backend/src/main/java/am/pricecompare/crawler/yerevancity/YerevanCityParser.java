package am.pricecompare.crawler.yerevancity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses the JSON returned by the public storefront API of yerevan-city.am (apishopv2.yerevan-city.am). */
public final class YerevanCityParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private YerevanCityParser() {
    }

    public record TopCategory(long id, String name, boolean hidden, boolean adult) {
    }

    public record ListItem(long id, String name, String photo, BigDecimal price, BigDecimal discountedPrice,
                           boolean kilogram, String categoryName, JsonNode raw) {

        /** Current selling price in whole dram. */
        public BigDecimal currentPrice() {
            BigDecimal p = discountedPrice != null && discountedPrice.signum() > 0 ? discountedPrice : price;
            return p == null ? null : p.setScale(0, RoundingMode.HALF_UP);
        }

        /** Price before discount, null when not discounted. */
        public BigDecimal originalPrice() {
            if (discountedPrice != null && discountedPrice.signum() > 0 && price != null
                    && price.compareTo(discountedPrice) > 0) {
                return price.setScale(0, RoundingMode.HALF_UP);
            }
            return null;
        }
    }

    public record ListPage(List<ListItem> items, int pageCount, int itemCount) {
    }

    public record Details(long id, String name, String category, String brand, String barcode,
                          BigDecimal price, BigDecimal discountedPrice, String currency, Boolean online, JsonNode raw) {
    }

    private static JsonNode data(String json) {
        try {
            JsonNode root = JSON.readTree(json);
            if (root == null || !root.path("success").asBoolean(false)) {
                throw new IllegalStateException("API returned success=false: " + abbreviate(json));
            }
            return root.path("data");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid JSON: " + abbreviate(json), e);
        }
    }

    public static List<TopCategory> parseTopCategories(String json) {
        List<TopCategory> out = new ArrayList<>();
        for (JsonNode c : data(json).path("categories")) {
            out.add(new TopCategory(c.path("id").asLong(), text(c, "name"), c.path("isHidden").asBoolean(false),
                    c.path("isAdult").asBoolean(false)));
        }
        return out;
    }

    /** Flattens {@code categoryWithChildren} into leaf name -> path ("Dairy > Milk and milk cocktail > Milk"). */
    public static Map<String, String> parseCategoryPaths(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode tree = data(json).path("categoryWithChildren");
        if (!tree.isMissingNode() && !tree.isNull()) {
            walk(tree, "", out);
        }
        return out;
    }

    private static void walk(JsonNode node, String prefix, Map<String, String> out) {
        String name = text(node, "categoryName");
        String path = prefix.isEmpty() ? name : prefix + " > " + name;
        if (name != null) {
            out.putIfAbsent(name, path);
        }
        for (JsonNode child : node.path("children")) {
            walk(child, path, out);
        }
    }

    public static ListPage parseProductList(String json) {
        JsonNode d = data(json);
        List<ListItem> items = new ArrayList<>();
        for (JsonNode p : d.path("list")) {
            items.add(new ListItem(p.path("id").asLong(), text(p, "name"), text(p, "photo"), decimal(p, "price"),
                    decimal(p, "discountedPrice"), p.path("isKilogram").asBoolean(false), text(p, "categoryName"), p));
        }
        return new ListPage(items, d.path("pageCount").asInt(0), d.path("itemCount").asInt(0));
    }

    public static List<am.pricecompare.crawler.DiscoveredLocation> parseStores(String json) {
        List<am.pricecompare.crawler.DiscoveredLocation> out = new ArrayList<>();
        for (JsonNode st : data(json).path("stores")) {
            String name = text(st, "name");
            if (name == null) {
                continue;
            }
            out.add(new am.pricecompare.crawler.DiscoveredLocation(name, text(st, "address"), decimal(st, "lat"), decimal(st, "lng")));
        }
        return out;
    }

    public static Details parseDetails(String json) {
        JsonNode d = data(json);
        if (d.isMissingNode() || d.isNull()) {
            return null;
        }
        Boolean online = d.has("isOnline") && !d.get("isOnline").isNull() ? d.get("isOnline").asBoolean() : null;
        return new Details(d.path("id").asLong(), text(d, "name"), text(d, "category"), text(d, "brandName"),
                text(d, "barcode"), decimal(d, "price"), decimal(d, "discountedPrice"), text(d, "currencyCode"), online, d);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal decimal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.decimalValue();
    }

    private static String abbreviate(String s) {
        return s == null ? "null" : s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
