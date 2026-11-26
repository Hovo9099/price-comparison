package am.pricecompare.web;

import am.pricecompare.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ListSharingApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode ok(MockHttpServletRequestBuilder b) throws Exception {
        String body = mvc.perform(b.contentType(MediaType.APPLICATION_JSON)).andExpect(status().is2xxSuccessful()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isEmpty() ? null : json.readTree(body);
    }

    @Test
    void shareJoinEditTogetherAndRevoke() throws Exception {
        long id = ok(post("/api/shopping-lists").content("{\"name\":\"Ընտանիք\"}")).path("id").asLong();
        String code = ok(post("/api/shopping-lists/" + id + "/share")).path("code").asText();
        assertThat(code).matches("[A-HJ-KM-NP-Z2-9]{8}");
        assertThat(ok(post("/api/shopping-lists/" + id + "/share")).path("code").asText()).isEqualTo(code); // stable

        // the second phone opens it (lower case and a dash are fine) and adds an item
        String typed = code.substring(0, 4).toLowerCase() + "-" + code.substring(4).toLowerCase();
        JsonNode joined = ok(get("/api/shopping-lists/shared/" + typed));
        assertThat(joined.path("id").asLong()).isEqualTo(id);
        ok(post("/api/shopping-lists/" + id + "/items").content("{\"freeText\":\"հաց\"}"));
        assertThat(ok(get("/api/shopping-lists/shared/" + code)).path("items").size()).isEqualTo(1);

        mvc.perform(get("/api/shopping-lists/shared/ZZZZ2222")).andExpect(status().isNotFound());
        mvc.perform(get("/api/shopping-lists/shared/bad")).andExpect(status().isNotFound());

        ok(delete("/api/shopping-lists/" + id + "/share"));
        mvc.perform(get("/api/shopping-lists/shared/" + code)).andExpect(status().isNotFound());
        assertThat(ok(post("/api/shopping-lists/" + id + "/share")).path("code").asText()).isNotEqualTo(code);
        mvc.perform(post("/api/shopping-lists/999999/share")).andExpect(status().isNotFound());
    }
}
