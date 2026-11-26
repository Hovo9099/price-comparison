package am.pricecompare.web;

import am.pricecompare.repository.StoreRepository;
import am.pricecompare.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class AdminStoreDeliveryTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StoreRepository storeRepo;

    private JsonNode publicStore(String slug) throws Exception {
        JsonNode all = json.readTree(mvc.perform(get("/api/stores")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode s : all) {
            if (s.path("slug").asText().equals(slug)) {
                return s;
            }
        }
        throw new AssertionError(slug);
    }

    @Test
    void setAndClearDeliveryTerms() throws Exception {
        long yc = storeRepo.findBySlug("yerevan-city").orElseThrow().getId();
        assertThat(publicStore("yerevan-city").path("delivery").isNull()).isTrue();
        assertThat(publicStore("sas").path("delivery").path("smallOrderFee").asInt()).isEqualTo(1500); // seeded in V6

        String url = "/api/admin/stores/" + yc + "/delivery";
        mvc.perform(put(url).header("X-Admin-Token", "test-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fee\":500}")).andExpect(status().isBadRequest());                // no source
        mvc.perform(put(url).header("X-Admin-Token", "test-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fee\":500,\"smallOrderBelow\":3000,\"source\":\"https://test.invalid/delivery\"}"))
                .andExpect(status().isBadRequest());                                              // tier half-filled
        mvc.perform(put(url).header("X-Admin-Token", "test-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fee\":-1,\"source\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).with(r -> { r.setRemoteAddr("203.0.113.5"); return r; })
                .content("{}")).andExpect(status().isUnauthorized());

        mvc.perform(put(url).header("X-Admin-Token", "test-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fee\":500,\"freeFrom\":15000,\"source\":\"https://test.invalid/delivery\"}"))
                .andExpect(status().isOk());
        JsonNode d = publicStore("yerevan-city").path("delivery");
        assertThat(d.path("fee").asInt()).isEqualTo(500);
        assertThat(d.path("freeFrom").asInt()).isEqualTo(15000);
        assertThat(d.path("checkedAt").isNull()).isFalse();

        mvc.perform(put(url).header("X-Admin-Token", "test-admin-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(publicStore("yerevan-city").path("delivery").isNull()).isTrue();
    }
}
