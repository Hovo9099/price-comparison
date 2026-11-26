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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static am.pricecompare.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Suspicious prices are kept but never chosen as cheapest until an admin confirms them. Fictional products only. */
@IntegrationTest
@AutoConfigureMockMvc
class PriceAnomalyApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductIngestionService ingestion;
    @Autowired StoreRepository storeRepo;
    @Autowired MatchingService matchingService;

    long sas;
    long yc;
    long carrefour;
    long productId;
    final Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

    @BeforeEach
    void seed() throws Exception {
        matchingService.resetIndex();
        sas = storeRepo.findBySlug("sas").orElseThrow().getId();
        yc = storeRepo.findBySlug("yerevan-city").orElseThrow().getId();
        carrefour = storeRepo.findBySlug("carrefour").orElseThrow().getId();
        ingestion.ingest(sas, null, product("S1", "Սուրճ «Թեստկոֆի» 250գ", "Coffee \"Testcoffee\" 250g", null, null, "2400", yesterday), false);
        ingestion.ingest(yc, null, product("Y1", "Սուրճ «Թեստկոֆի» 250գ", "Coffee \"Testcoffee\" 250g", null, null, "2500", yesterday), false);
        ingestion.ingest(carrefour, null, product("C1", "Սուրճ «Թեստկոֆի» 250գ", "Coffee \"Testcoffee\" 250g", null, null, "2600", yesterday), false);
        productId = get("/api/products/search?q=testcoffee").path("items").get(0).path("id").asLong();
    }

    private JsonNode get(String url) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url));
    }

    private JsonNode send(MockHttpServletRequestBuilder b) throws Exception {
        return json.readTree(mvc.perform(b.header("X-Admin-Token", "test-admin-token")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode cheapest() throws Exception {
        return get("/api/products/" + productId + "/prices").path("cheapest");
    }

    @Test
    void suddenDropIsFlaggedExcludedAndUsableAfterConfirmation() throws Exception {
        // SAS suddenly shows 240 (per 25 g?) – a 90 % drop
        ingestion.ingest(sas, null, product("S1", "Սուրճ «Թեստկոֆի» 250գ", "Coffee \"Testcoffee\" 250g", null, null, "240", Instant.now()), false);
        assertThat(cheapest().path("price").asInt()).isEqualTo(2500);
        assertThat(get("/api/products/price-drops").size()).isZero();

        JsonNode pending = get("/api/admin/anomalies");
        assertThat(pending.path("total").asInt()).isEqualTo(1);
        JsonNode a = pending.path("items").get(0);
        assertThat(a.path("kind").asText()).isEqualTo("SUDDEN_DROP");
        assertThat(a.path("referencePrice").asInt()).isEqualTo(2400);
        assertThat(get("/api/admin/anomalies/counts").path("pending").asInt()).isEqualTo(1);

        send(post("/api/admin/anomalies/" + a.path("id").asLong() + "/confirm"));
        assertThat(cheapest().path("price").asInt()).isEqualTo(240);
        mvc.perform(post("/api/admin/anomalies/" + a.path("id").asLong() + "/reject").header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isConflict());
    }

    @Test
    void newListingFarBelowOtherStoresIsFlagged() throws Exception {
        long parma = storeRepo.findBySlug("parma").map(s -> s.getId()).orElse(storeRepo.findBySlug("supermarket-am").orElseThrow().getId());
        ingestion.ingest(parma, null, product("P1", "Սուրճ «Թեստկոֆի» 250գ", "Coffee \"Testcoffee\" 250g", null, null, "500", Instant.now()), false);
        JsonNode a = get("/api/admin/anomalies").path("items").get(0);
        assertThat(a.path("kind").asText()).isEqualTo("BELOW_MARKET");
        assertThat(a.path("referencePrice").asInt()).isEqualTo(2500);
        assertThat(cheapest().path("price").asInt()).isEqualTo(2400);

        send(post("/api/admin/anomalies/" + a.path("id").asLong() + "/reject"));
        assertThat(get("/api/admin/anomalies?status=REJECTED").path("total").asInt()).isEqualTo(1);
        assertThat(cheapest().path("price").asInt()).isEqualTo(2400);
    }

    @Test
    void aNewPriceSupersedesThePendingAnomaly() throws Exception {
        ingestion.ingest(sas, null, product("S1", "Սուրճ «Թեստկոֆի» 250գ", "Coffee \"Testcoffee\" 250g", null, null, "240", Instant.now()), false);
        ingestion.ingest(sas, null, product("S1", "Սուրճ «Թեստկոֆի» 250գ", "Coffee \"Testcoffee\" 250g", null, null, "2350",
                Instant.now().plusSeconds(60)), false);
        assertThat(get("/api/admin/anomalies").path("total").asInt()).isZero();
        assertThat(get("/api/admin/anomalies?status=SUPERSEDED").path("total").asInt()).isEqualTo(1);
        assertThat(cheapest().path("price").asInt()).isEqualTo(2350); // measured against 2400, not the suspect 240
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/anomalies"))
                .andExpect(status().is4xxClientError());
    }
}
