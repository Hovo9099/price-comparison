package am.pricecompare.web;

import am.pricecompare.ingest.ProductIngestionService;
import am.pricecompare.matching.MatchingService;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static am.pricecompare.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smart shopping list end to end with FICTIONAL test-only products and prices (names start with TEST).
 * Traps: a cheaper 500 ml milk, coconut milk, cheaper margarine, cheaper Coca-Cola Zero, a stale egg price.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SmartListApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductIngestionService ingestion;
    @Autowired StoreRepository storeRepo;
    @Autowired MatchingService matchingService;
    @Autowired JdbcTemplate jdbc;

    long sas;
    long yc;
    long cf;

    @BeforeEach
    void seed() {
        matchingService.resetIndex();
        sas = storeRepo.findBySlug("sas").orElseThrow().getId();
        yc = storeRepo.findBySlug("yerevan-city").orElseThrow().getId();
        cf = storeRepo.findBySlug("carrefour").orElseThrow().getId();
        Instant now = Instant.now();
        Instant fourDaysAgo = now.minus(4, ChronoUnit.DAYS);
        // milk 1 L 3.2 %: four brands -> price per litre 500..1200
        add(sas, "M1", "TEST Կաթ «Ալֆա» 3.2% 1լ", "TEST Milk \"Alfa\" 3.2% 1l", "500", now);
        add(yc, "M1y", "TEST Կաթ «Ալֆա» 3.2% 1լ", "TEST Milk \"Alfa\" 3.2% 1l", "520", now);
        add(sas, "M2", "TEST Կաթ «Բետա» 3.2% 1լ", "TEST Milk \"Beta\" 3.2% 1l", "650", now);
        add(cf, "M2c", "TEST Կաթ «Բետա» 3.2% 1լ", "TEST Milk \"Beta\" 3.2% 1l", "640", now);
        add(yc, "M3", "TEST Կաթ «Գամմա» 3.2% 1լ", "TEST Milk \"Gamma\" 3.2% 1l", "800", now);
        add(cf, "M4", "TEST Կաթ «Դելտա» 3.2% 1լ", "TEST Milk \"Delta\" 3.2% 1l", "1200", now);
        add(sas, "M5", "TEST Կաթ «Ալֆա» 3.2% 0.5լ", "TEST Milk \"Alfa\" 3.2% 0.5l", "300", now);        // other size
        add(sas, "M6", "TEST Կաթ կոկոսի «Ալֆա» 1լ", "TEST Coconut milk \"Alfa\" 1l", "400", now);     // not milk
        // butter vs margarine
        add(sas, "B1", "TEST Կարագ «Ալֆա» 82% 200գ", "TEST Butter \"Alfa\" 82% 200g", "900", now);
        add(sas, "B2", "TEST Մարգարին «Ալֆա» 200գ", "TEST Margarine \"Alfa\" 200g", "300", now);
        // eggs: one stale price
        add(sas, "E1", "TEST Ձու «Ալֆա» 10 հատ", "TEST Eggs \"Alfa\" 10 pcs", "700", now);
        add(yc, "E2", "TEST Ձու «Բետա» 10 հատ", "TEST Eggs \"Beta\" 10 pcs", "600", fourDaysAgo);
        // cola vs zero
        add(sas, "C1", "TEST Զովացուցիչ ըմպելիք «Coca-Cola» 1.5լ", "TEST Soft drink \"Coca-Cola\" 1.5l", "700", now);
        add(yc, "C2", "TEST Զովացուցիչ ըմպելիք «Coca-Cola Zero» 1.5լ", "TEST Soft drink \"Coca-Cola Zero\" 1.5l", "600", now);
    }

    private void add(long store, String id, String name, String nameEn, String price, Instant at) {
        ingestion.ingest(store, null, product(id, name, nameEn, null, null, price, at), false);
    }

    private JsonNode postJson(String url, String body) throws Exception {
        String r = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(r);
    }

    private long newList() throws Exception {
        return postJson("/api/shopping-lists", "{\"name\":\"TEST\"}").path("id").asLong();
    }

    private static JsonNode strategy(JsonNode comparison, String name) {
        for (JsonNode s : comparison.path("strategies")) {
            if (s.path("strategy").asText().equals(name)) {
                return s;
            }
        }
        throw new AssertionError("no strategy " + name);
    }

    private static List<JsonNode> lines(JsonNode option) {
        List<JsonNode> out = new ArrayList<>();
        option.path("stores").forEach(st -> st.path("lines").forEach(out::add));
        return out;
    }

    private static JsonNode lineFor(JsonNode option, String labelStart) {
        return lines(option).stream().filter(l -> l.path("itemLabel").asText().startsWith(labelStart)).findFirst()
                .orElseThrow(() -> new AssertionError("no line " + labelStart));
    }

    @Test
    void parsesMixedScriptsAmbiguityAndUnknownWords() throws Exception {
        JsonNode r = postJson("/api/shopping-lists/parse", "{\"text\":\"kat, dzu, panir, karag, coca cola, масло, xyzzyq\"}");
        JsonNode items = r.path("items");
        assertThat(items.size()).isEqualTo(7);
        assertThat(items.get(0).path("concept").path("code").asText()).isEqualTo("milk");
        assertThat(items.get(0).path("status").asText()).isEqualTo("RECOGNIZED");
        assertThat(items.get(0).path("sizeValue").asInt()).isEqualTo(1000);
        // suggestions are real (seeded) 1 L milks only: never the 0.5 L or coconut milk
        assertThat(items.get(0).path("matchingProducts").asInt()).isEqualTo(4);
        items.get(0).path("productSuggestions").forEach(s -> assertThat(s.path("nameEn").asText()).doesNotContain("Coconut").doesNotContain("0.5"));
        assertThat(items.get(1).path("concept").path("code").asText()).isEqualTo("eggs");
        assertThat(items.get(2).path("status").asText()).isEqualTo("NEEDS_CHOICE");
        assertThat(items.get(2).path("concept").path("choices").size()).isGreaterThan(3);
        assertThat(items.get(3).path("concept").path("code").asText()).isEqualTo("butter");
        assertThat(items.get(3).path("productSuggestions").toString()).doesNotContain("Margarine");
        assertThat(items.get(4).path("concept").path("code").asText()).isEqualTo("coca-cola");
        assertThat(items.get(5).path("status").asText()).isEqualTo("NEEDS_CHOICE");
        assertThat(items.get(5).path("concept").path("choices").toString()).contains("butter").contains("oil");
        assertThat(items.get(6).path("status").asText()).isEqualTo("UNRECOGNIZED");
    }

    @Test
    void threeStrategiesFromRealPricesWithCriticalTrapsAvoided() throws Exception {
        long list = newList();
        JsonNode added = postJson("/api/shopping-lists/" + list + "/parse", "{\"text\":\"kat, karag, dzu, coca cola, panir\",\"add\":true}");
        assertThat(added.path("list").path("items").size()).isEqualTo(5);

        JsonNode c = postJson("/api/shopping-lists/" + list + "/compare", "{}");
        assertThat(c.path("comparisonId").asLong()).isPositive();

        JsonNode cheapest = strategy(c, "CHEAPEST");
        JsonNode multi = cheapest.path("multiStore");
        // milk Alfa 500 + butter 900 + eggs Beta 600 (stale but allowed by default) + cola 700 = 2700 at SAS + Yerevan City
        assertThat(multi.path("total").asInt()).isEqualTo(2700);
        assertThat(cheapest.path("singleStore").path("total").asInt()).isEqualTo(2800); // all at SAS
        assertThat(cheapest.path("recommended").asText()).isEqualTo("MULTI_STORE");
        assertThat(lineFor(multi, "Կաթ").path("productName").asText()).contains("Ալֆա").doesNotContain("0.5");
        assertThat(lineFor(multi, "Կարագ").path("productName").asText()).doesNotContain("Մարգարին");
        assertThat(lineFor(multi, "Կոկա").path("productName").asText()).doesNotContain("Zero");
        assertThat(lineFor(multi, "Ձու").path("freshness").asText()).isEqualTo("STALE");

        // cheese needs a type: listed as not included, never priced
        boolean cheeseNeedsChoice = false;
        for (JsonNode i : c.path("items")) {
            if ("cheese".equals(i.path("conceptCode").asText())) {
                cheeseNeedsChoice = i.path("status").asText().equals("NEEDS_CHOICE");
            }
        }
        assertThat(cheeseNeedsChoice).isTrue();
        assertThat(cheapest.path("missingItemIds").size()).isEqualTo(1);

        // mid-range milk: mainstream band (Beta or Gamma), premium: top quartile (Delta)
        JsonNode midMilk = lineFor(strategy(c, "MID_RANGE").path("multiStore"), "Կաթ");
        assertThat(midMilk.path("productName").asText()).matches(".*(Բետա|Գամմա).*");
        assertThat(midMilk.path("reasonCode").asText()).isEqualTo("MID_RANGE");
        assertThat(midMilk.path("comparableProducts").asInt()).isEqualTo(4);
        JsonNode premiumMilk = lineFor(strategy(c, "PREMIUM").path("multiStore"), "Կաթ");
        assertThat(premiumMilk.path("productName").asText()).contains("Դելտա");
        assertThat(premiumMilk.path("price").asInt()).isEqualTo(1200);
        assertThat(premiumMilk.path("pricePercentile").asInt()).isEqualTo(100);
        // butter has a single product: no premium alternative is reported, not invented
        assertThat(strategy(c, "PREMIUM").path("notes").toString()).contains("պրեմիում այլընտրանք չկա");

        // strategies are ordered by cost and differences are reported
        int cheap = strategy(c, "CHEAPEST").path("total").asInt();
        int mid = strategy(c, "MID_RANGE").path("total").asInt();
        int prem = strategy(c, "PREMIUM").path("total").asInt();
        assertThat(cheap).isLessThanOrEqualTo(mid);
        assertThat(mid).isLessThanOrEqualTo(prem);
        assertThat(strategy(c, "PREMIUM").path("differenceVsOthers").path("CHEAPEST").asInt()).isEqualTo(prem - cheap);

        // every priced line is a seeded price; the store without data (Nor Zovq) never appears
        for (String s : List.of("CHEAPEST", "MID_RANGE", "PREMIUM")) {
            for (JsonNode l : lines(strategy(c, s).path("multiStore"))) {
                assertThat(List.of(500, 520, 650, 640, 800, 1200, 900, 700, 600)).contains(l.path("price").asInt());
            }
            strategy(c, s).path("multiStore").path("stores").forEach(st -> assertThat(st.path("storeSlug").asText()).isNotEqualTo("nor-zovq"));
        }
    }

    @Test
    void stalePricesCanBeExcluded() throws Exception {
        long list = newList();
        postJson("/api/shopping-lists/" + list + "/parse", "{\"text\":\"dzu\",\"add\":true}");
        JsonNode c = postJson("/api/shopping-lists/" + list + "/compare", "{\"allowStalePrices\":false}");
        JsonNode line = lineFor(strategy(c, "CHEAPEST").path("multiStore"), "Ձու");
        assertThat(line.path("price").asInt()).isEqualTo(700);
        assertThat(line.path("freshness").asText()).isEqualTo("FRESH");
    }

    @Test
    void differentSizeRequestUsesOnlyThatSize() throws Exception {
        long list = newList();
        postJson("/api/shopping-lists/" + list + "/parse", "{\"text\":\"kat 0.5l\",\"add\":true}");
        JsonNode c = postJson("/api/shopping-lists/" + list + "/compare", "{}");
        JsonNode line = lineFor(strategy(c, "CHEAPEST").path("multiStore"), "Կաթ");
        assertThat(line.path("price").asInt()).isEqualTo(300);
        assertThat(line.path("productName").asText()).contains("0.5");
    }

    @Test
    void knownDeliveryFeesChangeTheRecommendation() throws Exception {
        // flat fee only (SAS's real small-order tier is covered by CoverageRulesTest)
        jdbc.update("update stores set delivery_fee = 1000, small_order_below = null, small_order_fee = null "
                + "where slug in ('sas', 'yerevan-city', 'carrefour')");
        long list = newList();
        postJson("/api/shopping-lists/" + list + "/parse", "{\"text\":\"kat, karag, dzu, coca cola\",\"add\":true}");
        JsonNode c = postJson("/api/shopping-lists/" + list + "/compare", "{\"includeDelivery\":true}");
        JsonNode cheapest = strategy(c, "CHEAPEST");
        // split 2700 + 2 x 1000 = 4700 > SAS alone 2800 + 1000 = 3800
        assertThat(cheapest.path("recommended").asText()).isEqualTo("SINGLE_STORE");
        assertThat(cheapest.path("total").asInt()).isEqualTo(3800);
        assertThat(cheapest.path("singleStore").path("stores").get(0).path("deliveryFee").asInt()).isEqualTo(1000);
    }

    @Test
    void oneStorePreferenceAndSpecificBrand() throws Exception {
        long list = newList();
        postJson("/api/shopping-lists/" + list + "/parse", "{\"text\":\"kat, dzu\",\"add\":true}");
        JsonNode oneStore = postJson("/api/shopping-lists/" + list + "/compare", "{\"preferOneStore\":true}");
        assertThat(strategy(oneStore, "CHEAPEST").path("multiStore").path("storeCount").asInt()).isEqualTo(1);

        JsonNode branded = postJson("/api/shopping-lists/" + list + "/compare", "{\"preferredBrands\":[\"Գամմա\"]}");
        assertThat(lineFor(strategy(branded, "CHEAPEST").path("multiStore"), "Կաթ").path("productName").asText()).contains("Գամմա");
    }

    @Test
    void lexiconSuggestCrawlerStatusAndPriceHistory() throws Exception {
        String s = mvc.perform(get("/api/lexicon/suggest").param("q", "kar")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(s).contains("butter");
        String st = mvc.perform(get("/api/crawler/status")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(st).size()).isEqualTo(6);
        long milk = postJson("/api/shopping-lists/parse", "{\"text\":\"kat\"}").path("items").get(0).path("productSuggestions").get(0).path("id").asLong();
        mvc.perform(get("/api/products/" + milk + "/price-history")).andExpect(status().isOk());
    }
}
