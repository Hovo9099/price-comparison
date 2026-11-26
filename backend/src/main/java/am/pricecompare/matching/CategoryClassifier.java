package am.pricecompare.matching;

import am.pricecompare.normalize.ProductNormalizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a store's own category path (Armenian/English/Russian) and product name onto the app's
 * category slugs. The store taxonomy is checked first because it is more reliable than names.
 * Keywords ending in '*' are prefixes; others must equal a whole token.
 */
public final class CategoryClassifier {

    private static final Map<String, List<String>> RULES = new LinkedHashMap<>();

    static {
        // Order matters: the first matching rule wins.
        RULES.put("drinks#non-alcoholic", List.of("non", "nonalcoholic", "ոչ", "безалкогольные", "безалкогольный", "անալկոհոլ*"));
        RULES.put("alcohol", List.of("alcohol*", "wine*", "vodka", "cognac", "brandy", "beer*", "whisk*", "liqueur*", "champagne",
                "rum", "gin", "tequila", "ալկոհոլ*", "գինի*", "օղի*", "կոնյակ*", "գարեջուր*", "շամպայն*", "վիսկի", "լիկյոր*",
                "вино", "водка", "коньяк", "пиво", "алкогол*"));
        RULES.put("baby", List.of("baby", "infant*", "diaper*", "մանկական*", "տակդիր*", "детск*"));
        RULES.put("pets", List.of("pet", "pets", "cat", "cats", "dog", "dogs", "animal*", "կենդանի*", "կատու*", "շան", "շների"));
        RULES.put("personal-care", List.of("shampoo*", "soap*", "toothpaste*", "toothbrush*", "deodorant*", "antiperspirant*",
                "cosmetic*", "hygien*", "shower", "razor*", "shaving", "hair", "skin", "շամպուն*", "օճառ*", "ատամ*", "հիգիենա*",
                "դեզոդորանտ*", "կոսմետիկ*", "գեղեցկ*", "խնամք*", "սափր*", "մազ*"));
        RULES.put("household", List.of("household", "cleaning", "cleaner*", "detergent*", "laundry", "dishwash*", "napkin*",
                "toilet", "chemical*", "կենցաղ*", "լվացք*", "մաքրող*", "անձեռոցիկ*", "զուգարան*", "լվացող*", "տնտեսական*",
                "բյուրեղ*", "бытов*"));
        RULES.put("eggs", List.of("egg", "eggs", "ձու", "ձվեր", "яйц*", "яйца"));
        RULES.put("frozen", List.of("frozen", "ice", "сառեցված*", "սառեցված*", "պաղպաղակ*", "կիսաֆաբրիկատ*", "dumpling*",
                "заморож*", "мороженое"));
        RULES.put("fish", List.of("fish", "seafood*", "shrimp*", "caviar", "salmon", "tuna", "herring", "ձուկ", "ձկ*",
                "ծովամթերք*", "խավիար*", "рыб*", "морепродукт*"));
        RULES.put("meat", List.of("meat*", "beef", "pork", "lamb", "veal", "chicken", "poultry", "turkey", "sausage*", "ham",
                "basturma", "sujukh", "mince*", "delicac*", "միս", "մս*", "հավ*", "տավար*", "խոզ*", "գառ*", "երշիկ*",
                "նրբերշիկ*", "բաստուրմա*", "սուջուխ*", "աղացած*", "մսամթերք*", "мясо", "мясн*", "колбас*", "курин*"));
        RULES.put("dairy", List.of("dairy", "milk", "cheese*", "yogurt*", "yoghurt*", "kefir", "butter", "cream", "matsun",
                "cottage", "curd*", "sour", "կաթ", "կաթի", "կաթնամթերք*", "պանիր*", "մածուն*", "թան", "կարագ*", "սերուցք*",
                "թթվասեր*", "կաթնաշոռ*", "յոգուրտ*", "կեֆիր*", "молоч*", "молоко", "сыр*", "творог*", "сметан*", "кефир"));
        RULES.put("coffee-tea", List.of("coffee", "tea", "teas", "cocoa", "սուրճ*", "թեյ*", "կակաո*", "кофе", "чай"));
        RULES.put("bread", List.of("bread", "bakery", "pastry", "pastries", "bun", "buns", "lavash", "croissant*", "հաց*",
                "լավաշ*", "թխվածք", "բուլկի*", "խմորեղեն*", "кондитер*", "хлеб*", "выпечк*"));
        RULES.put("produce", List.of("fruit*", "vegetable*", "greens", "berry", "berries", "mushroom*", "միրգ*", "մրգեր*",
                "բանջարեղեն*", "կանաչի*", "սունկ*", "հատապտուղ*", "фрукт*", "овощ*", "зелень"));
        RULES.put("grains", List.of("rice", "grain*", "cereal*", "groats", "buckwheat", "pasta", "macaroni", "spaghetti",
                "noodle*", "bulgur", "oat*", "lentil*", "bean*", "բրինձ*", "ձավար*", "հնդկաձավար*", "մակարոն*", "սպագետի*",
                "բլղուր*", "ոսպ*", "լոբի*", "վարսակ*", "կորեկ*", "крупа", "круп*", "рис", "макарон*"));
        RULES.put("sweets", List.of("sweet*", "candy", "candies", "chocolate*", "biscuit*", "cookie*", "wafer*", "cake*",
                "snack*", "chips", "nuts", "dried", "dessert*", "քաղցրավենիք*", "շոկոլադ*", "կոնֆետ*", "թխվածքաբլիթ*",
                "վաֆլի*", "չիպս*", "ընդեղեն*", "չիր*", "աղանդեր*", "տորթ*", "սլադ*", "сладост*", "шоколад*", "печенье"));
        RULES.put("pantry", List.of("oil", "sugar", "salt", "spice*", "sauce*", "ketchup", "mayonnaise", "canned", "conserv*",
                "preserv*", "honey", "jam", "flour", "vinegar", "grocery", "groceries", "համեմունք*", "սոուս*", "կետչուպ*",
                "մայոնեզ*", "պահածո*", "շաքար*", "աղ", "ձեթ*", "ալյուր*", "մեղր*", "մուրաբա*", "նպարեղեն*", "օշարակ*",
                "консерв*", "соус*", "специ*", "бакале*"));
        RULES.put("drinks", List.of("water", "juice*", "soda", "lemonade*", "drink*", "beverage*", "cola", "energy", "nectar*",
                "ջուր*", "հյութ*", "ըմպելիք*", "լիմոնադ*", "напит*", "сок*", "вода"));
    }

    private CategoryClassifier() {
    }

    /**
     * Kitchenware sections and items named after food ("Bread boxes and baskets", "Հացաման" = bread box) must not land
     * in a food category, where they would be compared with real bread.
     */
    private static final java.util.regex.Pattern NON_FOOD_PATH = java.util.regex.Pattern.compile(
            "tableware|kitchen (tools|accessories|appliances|utensils)|\\bsas home\\b|սպասք|խոհանոցային|посуда|кухонн",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern NON_FOOD_NAME = java.util.regex.Pattern.compile(
            "bread ?(box|bin|pin|basket)|հացաման|хлебниц",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    static boolean isNonFood(String storeCategoryPath, String nameEn, String name) {
        return (storeCategoryPath != null && NON_FOOD_PATH.matcher(storeCategoryPath).find())
                || (nameEn != null && NON_FOOD_NAME.matcher(nameEn).find())
                || (name != null && NON_FOOD_NAME.matcher(name).find());
    }

    /** @return category slug, never null ("other" when nothing matches) */
    public static String classify(String storeCategoryPath, String nameEn, String name) {
        String slug = classifyByText(storeCategoryPath, nameEn, name);
        return FOOD.contains(slug) && isNonFood(storeCategoryPath, nameEn, name) ? "household" : slug;
    }

    private static final java.util.Set<String> FOOD = java.util.Set.of("dairy", "eggs", "meat", "fish", "bread", "produce", "grains",
            "pantry", "sweets", "drinks", "coffee-tea", "alcohol", "frozen");

    private static String classifyByText(String storeCategoryPath, String nameEn, String name) {
        String byPath = classifyText(storeCategoryPath);
        if (byPath != null) {
            return byPath;
        }
        String byEn = classifyText(nameEn);
        if (byEn != null) {
            return byEn;
        }
        String byName = classifyText(name);
        return byName != null ? byName : "other";
    }

    public static String classifyText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // Use the most specific (last) segment of a path first, then the whole path.
        String[] alternatives = text.split("\\|");
        for (String alt : alternatives) {
            String[] segments = alt.split(">");
            for (int i = segments.length - 1; i >= 0; i--) {
                String slug = match(segments[i]);
                if (slug != null) {
                    return slug;
                }
            }
        }
        return null;
    }

    /** The rule whose keyword occurs earliest in the text wins (rule order breaks ties). */
    private static String match(String text) {
        List<String> tokens = ProductNormalizer.tokenize(ProductNormalizer.normalize(text));
        if (tokens.isEmpty()) {
            return null;
        }
        for (int pos = 0; pos < tokens.size(); pos++) {
            String t = tokens.get(pos);
            for (Map.Entry<String, List<String>> rule : RULES.entrySet()) {
                for (String kw : rule.getValue()) {
                    boolean hit = kw.endsWith("*") ? t.startsWith(kw.substring(0, kw.length() - 1)) : t.equals(kw);
                    if (hit) {
                        String key = rule.getKey();
                        return key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
                    }
                }
            }
        }
        return null;
    }
}
