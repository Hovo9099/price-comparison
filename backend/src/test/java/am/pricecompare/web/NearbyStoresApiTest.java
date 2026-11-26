package am.pricecompare.web;

import am.pricecompare.crawler.DiscoveredLocation;
import am.pricecompare.crawler.LocationSyncService;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fictional branch coordinates. */
@IntegrationTest
@AutoConfigureMockMvc
class NearbyStoresApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StoreRepository storeRepo;
    @Autowired LocationSyncService locationSync;
    @Autowired TransactionTemplate tx;

    private JsonNode call(MockHttpServletRequestBuilder b) throws Exception {
        String body = mvc.perform(b.header("X-Admin-Token", "test-admin-token")).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isEmpty() ? null : json.readTree(body);
    }

    @Test
    void nearestBranchPerStoreWithManualEntriesSurvivingSync() throws Exception {
        long sas = storeRepo.findBySlug("sas").orElseThrow().getId();
        long yc = storeRepo.findBySlug("yerevan-city").orElseThrow().getId();
        // crawled branches for YC (one near, one far)
        tx.executeWithoutResult(s -> locationSync.sync(yc, List.of(
                new DiscoveredLocation("YC Test Near", "Test street 1", new BigDecimal("40.1800"), new BigDecimal("44.5100")),
                new DiscoveredLocation("YC Test Far", "Test street 2", new BigDecimal("40.2500"), new BigDecimal("44.6000")))));
        // manual SAS branch
        String body = "{\"name\":\"SAS Test\",\"address\":\"Test avenue 5\",\"latitude\":40.19,\"longitude\":44.52,"
                + "\"source\":\"https://test.invalid/branches\"}";
        JsonNode added = call(post("/api/admin/stores/" + sas + "/locations").contentType(MediaType.APPLICATION_JSON).content(body));
        assertThat(added.path("manual").asBoolean()).isTrue();
        mvc.perform(post("/api/admin/stores/" + sas + "/locations").header("X-Admin-Token", "test-admin-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"latitude\":99,\"longitude\":0,\"source\":\"s\"}"))
                .andExpect(status().isBadRequest());

        JsonNode near = json.readTree(mvc.perform(get("/api/stores/nearby?lat=40.1810&lon=44.5110")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(near.size()).isEqualTo(2);
        assertThat(near.get(0).path("name").asText()).isEqualTo("YC Test Near");
        assertThat(near.get(0).path("distanceKm").asDouble()).isLessThan(0.3);
        assertThat(near.get(1).path("storeSlug").asText()).isEqualTo("sas");

        // a later crawl sync for SAS (with its own list) must not touch the manual branch
        tx.executeWithoutResult(s -> locationSync.sync(sas, List.of(
                new DiscoveredLocation("SAS Crawled", null, new BigDecimal("40.3"), new BigDecimal("44.7")))));
        JsonNode list = call(get("/api/admin/stores/" + sas + "/locations"));
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.findValuesAsText("active")).containsOnly("true");

        // crawled rows are read-only in the admin; manual ones can be deactivated
        long crawledId = 0;
        for (JsonNode l : list) {
            if (!l.path("manual").asBoolean()) crawledId = l.path("id").asLong();
        }
        mvc.perform(delete("/api/admin/stores/" + sas + "/locations/" + crawledId).header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isConflict());
        call(delete("/api/admin/stores/" + sas + "/locations/" + added.path("id").asLong()));
        mvc.perform(get("/api/stores/nearby?lat=95&lon=0")).andExpect(status().isBadRequest());
    }
}
