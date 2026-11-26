package am.pricecompare.web;

import am.pricecompare.crawler.CrawlFinishedEvent;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.ingest.ProductIngestionService;
import am.pricecompare.matching.MatchingRebuildService;
import am.pricecompare.matching.MatchingService;
import am.pricecompare.push.PushMessage;
import am.pricecompare.push.PushSender;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.repository.WatchDeviceRepository;
import am.pricecompare.service.PriceWatchService;
import am.pricecompare.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static am.pricecompare.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Price watches end to end, with fictional products and a fake push service. */
@IntegrationTest
@AutoConfigureMockMvc
class PriceWatchApiTest {

    static final String DEVICE = "test-device-0000-0000-000000000001";
    static final String TOKEN = "ExponentPushToken[test_token_0001]";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductIngestionService ingestion;
    @Autowired StoreRepository storeRepo;
    @Autowired MatchingService matchingService;
    @Autowired MatchingRebuildService rebuildService;
    @Autowired ApplicationEventPublisher events;
    @Autowired WatchDeviceRepository deviceRepo;
    @MockBean PushSender push;

    long sas;
    long yc;
    long productId;

    @BeforeEach
    void seed() throws Exception {
        matchingService.resetIndex();
        sas = storeRepo.findBySlug("sas").orElseThrow().getId();
        yc = storeRepo.findBySlug("yerevan-city").orElseThrow().getId();
        Instant now = Instant.now();
        ingestion.ingest(sas, null, product("S1", "Ըմպելիք «TestCola» 1.5լ", "TestCola 1.5L", null, null, "600", now), false);
        ingestion.ingest(yc, null, product("Y1", "Ըմպելիք «Թեսթկոլա» 1.5լ", "TestCola 1.5L", null, null, "570", now), false);
        JsonNode r = json.readTree(mvc.perform(get("/api/products/search").param("q", "testcola")).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        productId = r.path("items").get(0).path("id").asLong();
    }

    private MockHttpServletRequestBuilder dev(MockHttpServletRequestBuilder b) {
        return b.header("X-Device-Id", DEVICE).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode watches() throws Exception {
        return json.readTree(mvc.perform(dev(get("/api/watches"))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void priceAtYc(String price) {
        ingestion.ingest(yc, null, product("Y1", "Ըմպելիք «Թեսթկոլա» 1.5լ", "TestCola 1.5L", null, null, price, Instant.now()), false);
        events.publishEvent(new CrawlFinishedEvent(yc, 1, CrawlStatus.SUCCESS, 1));
    }

    @SuppressWarnings("unchecked")
    private List<PushMessage> lastPush() {
        ArgumentCaptor<List<PushMessage>> c = ArgumentCaptor.forClass(List.class);
        verify(push, atLeastOnce()).send(c.capture());
        return c.getValue();
    }

    @Test
    void watchListAndUnwatch() throws Exception {
        mvc.perform(dev(put("/api/watches/" + productId))).andExpect(status().isOk());
        JsonNode list = watches();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("startPrice").asInt()).isEqualTo(570);
        assertThat(list.get(0).path("cheapest").path("storeSlug").asText()).isEqualTo("yerevan-city");
        assertThat(list.get(0).path("dropped").asBoolean()).isFalse();

        // watching again only updates the target
        mvc.perform(dev(put("/api/watches/" + productId)).content("{\"targetPrice\":500}")).andExpect(status().isOk());
        assertThat(watches().get(0).path("targetPrice").asInt()).isEqualTo(500);

        mvc.perform(dev(delete("/api/watches/" + productId))).andExpect(status().isNoContent());
        assertThat(watches().size()).isZero();
    }

    @Test
    void validation() throws Exception {
        mvc.perform(get("/api/watches")).andExpect(status().isBadRequest());                       // no device header
        mvc.perform(get("/api/watches").header("X-Device-Id", "short")).andExpect(status().isBadRequest());
        mvc.perform(dev(put("/api/watches/999999"))).andExpect(status().isNotFound());
        mvc.perform(dev(put("/api/watches/" + productId)).content("{\"targetPrice\":-5}")).andExpect(status().isBadRequest());
        mvc.perform(dev(put("/api/devices/me")).content("{\"pushToken\":\"not-a-token\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void priceDropAfterCrawlSendsPushInDeviceLanguage() throws Exception {
        mvc.perform(dev(put("/api/devices/me")).content("{\"pushToken\":\"" + TOKEN + "\",\"language\":\"hy\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(dev(put("/api/watches/" + productId))).andExpect(status().isOk());

        priceAtYc("565"); // < 2 %: no notification
        verify(push, never()).send(argThat(l -> !l.isEmpty()));

        priceAtYc("540"); // -5 %
        PushMessage m = lastPush().get(0);
        assertThat(m.to()).isEqualTo(TOKEN);
        assertThat(m.title()).startsWith("Գինն իջավ").contains("TestCola");
        assertThat(m.body()).contains("540 ֏").contains("570 ֏");
        assertThat(m.data()).containsEntry("productId", productId);
        assertThat(watches().get(0).path("dropped").asBoolean()).isTrue();

        reset(push);
        priceAtYc("540"); // same price again: nothing new
        verify(push, never()).send(argThat(l -> !l.isEmpty()));
    }

    @Test
    void targetPriceAndUnregisteredTokensAreForgotten() throws Exception {
        mvc.perform(dev(put("/api/devices/me")).content("{\"pushToken\":\"" + TOKEN + "\",\"language\":\"en\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(dev(put("/api/watches/" + productId)).content("{\"targetPrice\":500}")).andExpect(status().isOk());
        when(push.send(argThat(l -> !l.isEmpty()))).thenReturn(Set.of(TOKEN));

        priceAtYc("520");
        verify(push, never()).send(argThat(l -> !l.isEmpty()));
        priceAtYc("495");
        PushMessage m = lastPush().get(0);
        assertThat(m.title()).startsWith("Price drop:").contains("TestCola");
        assertThat(m.body()).contains("your target 500 ֏");
        assertThat(deviceRepo.findById(DEVICE).orElseThrow().getPushToken()).isNull();
    }

    @Test
    void watchesSurviveMatchingRebuild() throws Exception {
        mvc.perform(dev(put("/api/watches/" + productId))).andExpect(status().isOk());
        rebuildService.rebuild(List.of("yerevan-city", "sas"));
        JsonNode list = watches();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("nameEn").asText()).contains("TestCola");
        assertThat(list.get(0).path("cheapest").path("price").asInt()).isEqualTo(570);
    }
}
