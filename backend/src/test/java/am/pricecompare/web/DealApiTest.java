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
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static am.pricecompare.support.TestProducts.discounted;
import static am.pricecompare.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Deals with fake-discount detection, fictional products only. */
@IntegrationTest
@AutoConfigureMockMvc
class DealApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductIngestionService ingestion;
    @Autowired StoreRepository storeRepo;
    @Autowired MatchingService matchingService;

    long sas;
    long yc;

    @BeforeEach
    void seed() {
        matchingService.resetIndex();
        sas = storeRepo.findBySlug("sas").orElseThrow().getId();
        yc = storeRepo.findBySlug("yerevan-city").orElseThrow().getId();
        Instant now = Instant.now();
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);
        // GENUINE: sold at 600 a month ago, now 500 (was 600)
        ingestion.ingest(sas, null, product("G1", "Պանիր «Թեստգենուին» 400գ", "600", monthAgo), false);
        ingestion.ingest(sas, null, discounted("G1", "Պանիր «Թեստգենուին» 400գ", "500", "600", now), false);
        // INFLATED: "on sale" for a month at 500 from a 700 nobody ever paid
        ingestion.ingest(sas, null, discounted("F1", "Թեյ «Թեստֆեյք» 100գ", "500", "700", monthAgo), false);
        ingestion.ingest(sas, null, discounted("F1", "Թեյ «Թեստֆեյք» 100գ", "500", "700", now), false);
        // UNVERIFIED: first seen today
        ingestion.ingest(yc, null, discounted("N1", "Սուրճ «Թեստնյու» 250գ", "900", "1000", now), false);
        // no discount: never listed
        ingestion.ingest(yc, null, product("P1", "Ջուր «Թեստվոթեր» 1լ", "200", now), false);
    }

    private JsonNode deals(String query) throws Exception {
        return json.readTree(mvc.perform(get("/api/deals" + query)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static Map<String, JsonNode> byName(JsonNode page) {
        Map<String, JsonNode> out = new HashMap<>();
        page.path("items").forEach(d -> out.put(d.path("product").path("name").asText(), d));
        return out;
    }

    @Test
    void inflatedDiscountsAreHiddenByDefaultAndLabelledOnRequest() throws Exception {
        JsonNode page = deals("");
        assertThat(page.path("total").asInt()).isEqualTo(2);
        Map<String, JsonNode> d = byName(page);
        JsonNode genuine = d.get("Պանիր «Թեստգենուին» 400գ");
        assertThat(genuine.path("verdict").asText()).isEqualTo("GENUINE");
        assertThat(genuine.path("discountPercent").asInt()).isEqualTo(17);
        assertThat(genuine.path("maxPaid90Days").asInt()).isEqualTo(600);
        assertThat(genuine.path("cheapestAnywhere").asBoolean()).isTrue();
        assertThat(d.get("Սուրճ «Թեստնյու» 250գ").path("verdict").asText()).isEqualTo("UNVERIFIED");
        // biggest real discount first
        assertThat(page.path("items").get(0).path("discountPercent").asInt()).isEqualTo(17);

        JsonNode all = deals("?includeInflated=true");
        assertThat(all.path("total").asInt()).isEqualTo(3);
        JsonNode fake = byName(all).get("Թեյ «Թեստֆեյք» 100գ");
        assertThat(fake.path("verdict").asText()).isEqualTo("INFLATED");
        assertThat(fake.path("historyDays").asInt()).isGreaterThanOrEqualTo(29);
        // labelled fakes go last even though their percentage is the largest (29 %)
        assertThat(all.path("items").get(2).path("verdict").asText()).isEqualTo("INFLATED");
    }

    @Test
    void filtersByStoreAndPages() throws Exception {
        assertThat(deals("?storeId=" + yc).path("total").asInt()).isEqualTo(1);
        JsonNode p0 = deals("?size=1&page=0");
        JsonNode p1 = deals("?size=1&page=1");
        assertThat(p0.path("items").size()).isEqualTo(1);
        assertThat(p1.path("items").get(0).path("product").path("id").asLong())
                .isNotEqualTo(p0.path("items").get(0).path("product").path("id").asLong());
        mvc.perform(get("/api/deals?size=500")).andExpect(status().isBadRequest());
    }
}
