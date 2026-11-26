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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static am.pricecompare.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end API test with fictional, test-only products. */
@IntegrationTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductIngestionService ingestion;
    @Autowired StoreRepository storeRepo;
    @Autowired MatchingService matchingService;

    long sas;
    long yc;
    long carrefour;

    @BeforeEach
    void seed() {
        matchingService.resetIndex();
        sas = storeRepo.findBySlug("sas").orElseThrow().getId();
        yc = storeRepo.findBySlug("yerevan-city").orElseThrow().getId();
        carrefour = storeRepo.findBySlug("carrefour").orElseThrow().getId();
        Instant now = Instant.now();
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant tenDaysAgo = now.minus(10, ChronoUnit.DAYS);
        // Coca-Cola 1.5L at three stores
        ingestion.ingest(sas, null, product("S1", "Զովացուցիչ ըմպելիք «TestCola» 1.5լ", "TestCola 1.5L", null, null, "590", now), false);
        ingestion.ingest(yc, null, product("Y1", "Ըմպելիք «Թեսթկոլա» 1.5լ", "TestCola 1.5L", null, null, "570", now), false);
        ingestion.ingest(carrefour, null, product("C1", "TestCola 1.5լ", "TestCola 1.5L", null, null, "600", twoDaysAgo), false);
        // Milk at two stores
        ingestion.ingest(sas, null, product("S2", "Կաթ «Թեստմիլք» 3.2% 1լ", "Milk \"Testmilk\" 3.2% 1l", null, null, "480", now), false);
        ingestion.ingest(yc, null, product("Y2", "Կաթ «Թեստմիլք» 3.2% 1լ", "Milk \"Testmilk\" 3.2% 1l", null, null, "520", now), false);
        // Rice only at Carrefour; outdated price at SAS must be ignored
        ingestion.ingest(carrefour, null, product("C3", "Բրինձ «Թեստռայս» 1կգ", null, null, null, "1200", now), false);
        ingestion.ingest(sas, null, product("S3", "Բրինձ «Թեստռայս» 1կգ", "Rice \"Testrice\" 1kg", null, null, "900", tenDaysAgo), false);
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private JsonNode search(String q) throws Exception {
        String body = mvc.perform(get("/api/products/search").param("q", q)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private long productId(String q) throws Exception {
        JsonNode r = search(q);
        assertThat(r.path("items").size()).as("results for " + q).isGreaterThan(0);
        return r.path("items").get(0).path("id").asLong();
    }

    @Test
    void searchWorksInEnglishArmenianAndAcrossScripts() throws Exception {
        assertThat(getJson("/api/products/search?q=testcola").path("items").get(0).path("storeCount").asInt()).isEqualTo(3);
        assertThat(getJson("/api/products/search?q=milk").path("items").get(0).path("nameEn").asText()).contains("Testmilk");
        assertThat(search("կաթ").path("total").asInt()).isEqualTo(1);
        assertThat(search("թեսթկոլա").path("total").asInt()).isEqualTo(1);
        assertThat(getJson("/api/products/search?q=TESTCOLA%201500ml").path("total").asInt()).isEqualTo(1);
        assertThat(getJson("/api/products/search?q=TESTCOLA%201.5").path("total").asInt()).isEqualTo(1);
        assertThat(getJson("/api/products/search?q=nothingmatcheszzz").path("total").asInt()).isZero();
        // bilingual synonyms: English "rice" finds a product with only an Armenian name, and vice versa
        assertThat(search("rice").path("total").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(search("բրինձ").path("total").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void priceComparisonShowsCheapestStoreAndFreshness() throws Exception {
        long id = productId("testcola");
        JsonNode r = getJson("/api/products/" + id + "/prices");
        assertThat(r.path("cheapest").path("price").asInt()).isEqualTo(570);
        assertThat(r.path("cheapest").path("storeSlug").asText()).isEqualTo("yerevan-city");
        assertThat(r.path("prices").size()).isEqualTo(3);
        assertThat(r.path("prices").get(0).path("capturedAt").isMissingNode()).isFalse();
        JsonNode carrefourPrice = null;
        for (JsonNode p : r.path("prices")) {
            if (p.path("storeSlug").asText().equals("carrefour")) {
                carrefourPrice = p;
            }
        }
        assertThat(carrefourPrice.path("freshness").asText()).isEqualTo("RECENT");
        assertThat(r.path("potentialSaving").asInt()).isEqualTo(30);
    }

    @Test
    void outdatedPricesAreNeverChosenAsCheapest() throws Exception {
        long rice = productId("testrice");
        JsonNode r = getJson("/api/products/" + rice + "/prices");
        assertThat(r.path("cheapest").path("price").asInt()).isEqualTo(1200);
        assertThat(r.path("cheapest").path("storeSlug").asText()).isEqualTo("carrefour");
        boolean sawOutdated = false;
        for (JsonNode p : r.path("prices")) {
            if (p.path("freshness").asText().equals("OUTDATED")) {
                sawOutdated = true;
                assertThat(p.path("cheapest").asBoolean()).isFalse();
            }
        }
        assertThat(sawOutdated).isTrue();
    }

    @Test
    void barcodeLookupFindsProductInEanAndUpcForm() throws Exception {
        ingestion.ingest(yc, null, product("Y9", "Մածուն «Թեստմածուն» 500գ", "Matsun \"Testmatsun\" 500g", null,
                "0999999000017", "350", Instant.now()), false);
        JsonNode ean = getJson("/api/products/by-barcode/0999999000017");
        assertThat(ean.path("nameEn").asText()).contains("Testmatsun");
        JsonNode upc = getJson("/api/products/by-barcode/999999000017");
        assertThat(upc.path("id").asLong()).isEqualTo(ean.path("id").asLong());
        mvc.perform(get("/api/products/by-barcode/4000000000000")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/by-barcode/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void historyEndpointReturnsSeriesPerStore() throws Exception {
        long id = productId("testcola");
        JsonNode r = getJson("/api/products/" + id + "/history?days=30");
        assertThat(r.path("series").size()).isEqualTo(3);
    }

    @Test
    void shoppingListAndBasketOptimization() throws Exception {
        long cola = productId("testcola");
        long milk = productId("testmilk");
        long rice = productId("testrice");
        String created = mvc.perform(post("/api/shopping-lists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Իմ ցուցակը\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long listId = json.readTree(created).path("id").asLong();
        for (long p : new long[]{cola, milk, rice}) {
            mvc.perform(post("/api/shopping-lists/" + listId + "/items").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"productId\":" + p + ",\"quantity\":2}")).andExpect(status().isCreated());
        }
        String withText = mvc.perform(post("/api/shopping-lists/" + listId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"freeText\":\"Հաց\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode list = json.readTree(withText);
        assertThat(list.path("items").size()).isEqualTo(4);

        String opt = mvc.perform(post("/api/shopping-lists/" + listId + "/optimize")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode r = json.readTree(opt);
        // Carrefour is the only store with all three products: (600 + 1200) * 2 = 3600; milk is not at Carrefour
        // so no single store is complete; multi-store: cola@YC 570, milk@SAS 480, rice@Carrefour 1200 -> x2 = 4500
        assertThat(r.path("cheapestMultiStore").path("total").asInt()).isEqualTo(4500);
        assertThat(r.path("cheapestMultiStore").path("storeCount").asInt()).isEqualTo(3);
        assertThat(r.path("cheapestMultiStore").path("complete").asBoolean()).isTrue();
        assertThat(r.path("cheapestSingleStore").isNull()).isTrue();
        assertThat(r.path("unavailableItems").size()).isEqualTo(1); // free-text "Հաց"

        // Remove rice -> SAS and YC both have cola + milk
        long riceItem = 0;
        for (JsonNode i : list.path("items")) {
            if (i.path("productId").asLong() == rice) {
                riceItem = i.path("id").asLong();
            }
        }
        mvc.perform(delete("/api/shopping-lists/" + listId + "/items/" + riceItem)).andExpect(status().isOk());
        JsonNode r2 = json.readTree(mvc.perform(post("/api/shopping-lists/" + listId + "/optimize")).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        // SAS: (590+480)*2 = 2140, YC: (570+520)*2 = 2180 -> single SAS 2140; multi: cola YC 570 + milk SAS 480 = 2100
        assertThat(r2.path("cheapestSingleStore").path("storeSlug").asText()).isEqualTo("sas");
        assertThat(r2.path("cheapestSingleStore").path("total").asInt()).isEqualTo(2140);
        assertThat(r2.path("cheapestMultiStore").path("total").asInt()).isEqualTo(2100);
        assertThat(r2.path("savings").asInt()).isEqualTo(40);
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        mvc.perform(post("/api/shopping-lists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/search?q=")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/999999/prices")).andExpect(status().isNotFound());
    }

    @Test
    void adminEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/admin/dashboard").with(r -> { r.setRemoteAddr("203.0.113.5"); return r; }))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard").header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stores.length()").value(6));
    }

    @Test
    void csvImportGoesThroughNormalizationAndMatching() throws Exception {
        String csv = """
                store,externalProductId,name,brand,barcode,price,currency,available,capturedAt
                nor-zovq,NZ-1,TestCola 1500ml,,,580,AMD,true,%s
                nor-zovq,NZ-2,TEST broken row,,,not-a-number,AMD,true,2026-10-01
                """.formatted(Instant.now().toString());
        MockMultipartFile file = new MockMultipartFile("file", "prices.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        JsonNode r = json.readTree(mvc.perform(multipart("/api/admin/import/csv").file(file).header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(r.path("rows").asInt()).isEqualTo(2);
        assertThat(r.path("imported").asInt()).isEqualTo(1);
        assertThat(r.path("errors").size()).isEqualTo(1);
        long cola = productId("testcola");
        assertThat(getJson("/api/products/" + cola + "/prices").path("prices").size()).isEqualTo(4);
    }

    @Test
    void categoriesAndStoresAreListed() throws Exception {
        assertThat(getJson("/api/stores").size()).isEqualTo(6);
        JsonNode cats = getJson("/api/categories");
        assertThat(cats.size()).isGreaterThan(10);
    }
}
