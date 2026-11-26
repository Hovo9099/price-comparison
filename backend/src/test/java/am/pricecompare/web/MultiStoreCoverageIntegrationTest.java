package am.pricecompare.web;

import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.ingest.ProductIngestionService;
import am.pricecompare.matching.MatchingService;
import am.pricecompare.repository.CrawlRunRepository;
import am.pricecompare.repository.StoreProductRepository;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.service.CatalogStatusService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static am.pricecompare.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Products from several supermarkets (fictional fixture data): store listings stay separate with their own prices,
 * search returns every store's offers, and missing stores are reported honestly.
 */
@IntegrationTest
@AutoConfigureMockMvc
class MultiStoreCoverageIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductIngestionService ingestion;
    @Autowired StoreRepository storeRepo;
    @Autowired StoreProductRepository storeProductRepo;
    @Autowired CrawlRunRepository runRepo;
    @Autowired MatchingService matchingService;
    @Autowired CatalogStatusService catalogStatus;

    long sas;
    long yc;
    long carrefour;
    long smam;
    long norZovq;
    Instant now;

    @BeforeEach
    void seed() {
        matchingService.resetIndex();
        catalogStatus.invalidate();
        sas = storeRepo.findBySlug("sas").orElseThrow().getId();
        yc = storeRepo.findBySlug("yerevan-city").orElseThrow().getId();
        carrefour = storeRepo.findBySlug("carrefour").orElseThrow().getId();
        smam = storeRepo.findBySlug("supermarket-am").orElseThrow().getId();
        norZovq = storeRepo.findBySlug("nor-zovq").orElseThrow().getId();
        now = Instant.now();
        // The same milk at four stores, each with its own id, URL and price
        ingestion.ingest(sas, null, product("SAS-77", "Կաթ «Թեստկաթ» 3.2% 1լ", "Milk \"Testkat\" 3.2% 1l", null, null, "640", now), false);
        ingestion.ingest(yc, null, product("YC-1001", "Կաթ «Թեստկաթ» 3.2% 1լ", "Milk \"Testkat\" 3.2% 1l", null, null, "620", now), false);
        ingestion.ingest(carrefour, null, product("CF-milk-1l", "Կաթ «Թեստկաթ» 3.2% 1լ", null, null, null, "655", now), false);
        ingestion.ingest(smam, null, product("SM-5", "Կաթ «Թեստկաթ» 3.2% 1լ", null, null, null, "610", now), false);
    }

    private JsonNode getJson(String url, Object... params) throws Exception {
        var req = get(url);
        for (int i = 0; i < params.length; i += 2) {
            req = req.param(params[i].toString(), params[i + 1].toString());
        }
        String body = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private void completeCrawl(long storeId) {
        CrawlRun r = new CrawlRun();
        r.setStore(storeRepo.findById(storeId).orElseThrow());
        r.setRunType("CRAWL");
        r.setStatus(CrawlStatus.SUCCESS);
        r.setMaxProducts(0);
        r.setStartedAt(now.minusSeconds(3600));
        r.setFinishedAt(now.minusSeconds(60));
        runRepo.save(r);
        catalogStatus.invalidate();
    }

    @Test
    void sameProductAtFourStoresKeepsSeparateListingsAndPrices() throws Exception {
        StoreProduct atSas = storeProductRepo.findByStoreIdAndExternalProductId(sas, "SAS-77").orElseThrow();
        StoreProduct atYc = storeProductRepo.findByStoreIdAndExternalProductId(yc, "YC-1001").orElseThrow();
        long canonical = atSas.getCanonicalProduct().getId();
        assertThat(atYc.getCanonicalProduct().getId()).isEqualTo(canonical);
        assertThat(atYc.getId()).isNotEqualTo(atSas.getId());

        JsonNode offers = getJson("/api/products/" + canonical + "/offers");
        Map<String, JsonNode> rows = new HashMap<>();
        offers.path("stores").forEach(r -> rows.put(r.path("storeSlug").asText(), r));
        assertThat(rows).containsOnlyKeys("sas", "yerevan-city", "carrefour", "supermarket-am", "nor-zovq", "parma");
        assertThat(offers.path("storesWithOffer").asInt()).isEqualTo(4);
        assertThat(rows.get("sas").path("offers").get(0).path("price").decimalValue()).isEqualByComparingTo("640");
        assertThat(rows.get("sas").path("offers").get(0).path("externalProductId").asText()).isEqualTo("SAS-77");
        assertThat(rows.get("yerevan-city").path("offers").get(0).path("price").decimalValue()).isEqualByComparingTo("620");
        assertThat(rows.get("carrefour").path("offers").get(0).path("price").decimalValue()).isEqualByComparingTo("655");
        assertThat(rows.get("supermarket-am").path("offers").get(0).path("price").decimalValue()).isEqualByComparingTo("610");
        assertThat(rows.get("supermarket-am").path("offers").get(0).path("externalUrl").asText()).endsWith("/SM-5");
        assertThat(rows.get("nor-zovq").path("status").asText()).isEqualTo("NOT_INTEGRATED");
        assertThat(offers.path("cheapest").path("storeSlug").asText()).isEqualTo("supermarket-am");

        // A new SAS price never touches another store's price
        ingestion.ingest(sas, null, product("SAS-77", "Կաթ «Թեստկաթ» 3.2% 1լ", "Milk \"Testkat\" 3.2% 1l", null, null, "590", now.plusSeconds(5)), false);
        offers = getJson("/api/products/" + canonical + "/offers");
        rows.clear();
        offers.path("stores").forEach(r -> rows.put(r.path("storeSlug").asText(), r));
        assertThat(rows.get("sas").path("offers").get(0).path("price").decimalValue()).isEqualByComparingTo("590");
        assertThat(rows.get("yerevan-city").path("offers").get(0).path("price").decimalValue()).isEqualByComparingTo("620");
        assertThat(rows.get("carrefour").path("offers").get(0).path("price").decimalValue()).isEqualByComparingTo("655");
    }

    @Test
    void searchReturnsEachStoresOwnOfferInEveryLanguage() throws Exception {
        for (String q : new String[]{"testkat", "թեստկաթ", "milk testkat", "կաթ թեստկաթ", "молоко testkat"}) {
            JsonNode r = getJson("/api/products/search", "q", q);
            assertThat(r.path("items").size()).as(q).isGreaterThan(0);
            JsonNode item = r.path("items").get(0);
            Map<String, java.math.BigDecimal> prices = new HashMap<>();
            item.path("offers").forEach(o -> prices.put(o.path("storeSlug").asText(), o.path("price").decimalValue()));
            assertThat(prices).as(q).containsOnlyKeys("sas", "yerevan-city", "carrefour", "supermarket-am");
            assertThat(prices.get("yerevan-city")).as(q).isEqualByComparingTo("620");
            assertThat(item.path("cheapest").path("storeSlug").asText()).as(q).isEqualTo("supermarket-am");
            Map<String, JsonNode> stores = new HashMap<>();
            r.path("stores").forEach(s -> stores.put(s.path("storeSlug").asText(), s));
            assertThat(stores.get("carrefour").path("matches").asLong()).as(q).isEqualTo(1);
            assertThat(stores.get("nor-zovq").path("catalogState").asText()).isEqualTo("NOT_IMPLEMENTED");
            assertThat(stores.get("nor-zovq").path("matches").asLong()).isZero();
        }
        // Restricting to one store
        JsonNode onlyCarrefour = getJson("/api/products/search", "q", "testkat", "storeId", carrefour);
        assertThat(onlyCarrefour.path("items").size()).isEqualTo(1);
    }

    @Test
    void oneStoreWithManyMatchesNeverCrowdsOutTheOthers() throws Exception {
        // 700 matching Yerevan City products are ingested first (lower ids); SAS has the only plain one, ingested last
        for (int i = 0; i < 700; i++) {
            ingestion.ingest(yc, null, product("YC-G" + i, "Թեստգրեյն համեմունքով " + (100 + i) + "գ", null, null, null,
                    String.valueOf(300 + i), now), false);
        }
        ingestion.ingest(sas, null, product("SAS-G", "Թեստգրեյն 2000գ", "Testgrain 2000g", null, null, "900", now), false);

        JsonNode first = getJson("/api/products/search", "q", "թեստգրեյն", "size", "20");
        assertThat(first.path("totalMatches").asLong()).isEqualTo(701);
        Map<String, JsonNode> stores = new HashMap<>();
        first.path("stores").forEach(s -> stores.put(s.path("storeSlug").asText(), s));
        assertThat(stores.get("sas").path("matches").asLong()).isEqualTo(1);
        assertThat(stores.get("yerevan-city").path("matches").asLong()).isEqualTo(700);
        // the SAS product is a candidate (it was cut off by the old unordered LIMIT 600) and, as the closest match, ranks first
        Set<String> firstPageStores = new HashSet<>();
        first.path("items").forEach(i -> i.path("offers").forEach(o -> firstPageStores.add(o.path("storeSlug").asText())));
        assertThat(firstPageStores).contains("sas", "yerevan-city");
        assertThat(first.path("items").get(0).path("name").asText()).isEqualTo("Թեստգրեյն 2000գ");
    }

    @Test
    void notFoundIsOnlyReportedForStoresWhoseCatalogWasFullyCrawled() throws Exception {
        ingestion.ingest(sas, null, product("SAS-ONLY", "Մեղր «Թեստմեղր» 500գ", "Honey \"Testhoney\" 500g", null, null, "2500", now), false);
        long canonical = storeProductRepo.findByStoreIdAndExternalProductId(sas, "SAS-ONLY").orElseThrow().getCanonicalProduct().getId();

        // no complete crawl anywhere: absence proves nothing
        Map<String, String> status = rowStatuses(canonical);
        assertThat(status.get("sas")).isEqualTo("AVAILABLE");
        assertThat(status.get("carrefour")).isEqualTo("NOT_YET_IMPORTED");
        assertThat(status.get("nor-zovq")).isEqualTo("NOT_INTEGRATED");

        // Carrefour's whole catalog was crawled -> "not found"
        completeCrawl(carrefour);
        assertThat(rowStatuses(canonical).get("carrefour")).isEqualTo("NOT_FOUND");
        // A limited (dev) crawl does not count as complete
        CrawlRun limited = new CrawlRun();
        limited.setStore(storeRepo.findById(yc).orElseThrow());
        limited.setRunType("CRAWL");
        limited.setStatus(CrawlStatus.SUCCESS);
        limited.setMaxProducts(100);
        limited.setFinishedAt(now);
        runRepo.save(limited);
        catalogStatus.invalidate();
        assertThat(rowStatuses(canonical).get("yerevan-city")).isEqualTo("NOT_YET_IMPORTED");

        JsonNode stores = getJson("/api/stores");
        Map<String, JsonNode> bySlug = new HashMap<>();
        stores.forEach(s -> bySlug.put(s.path("slug").asText(), s));
        assertThat(bySlug.get("carrefour").path("catalogState").asText()).isEqualTo("COMPLETE");
        assertThat(bySlug.get("yerevan-city").path("catalogState").asText()).isEqualTo("PARTIAL");
        assertThat(bySlug.get("nor-zovq").path("integrated").asBoolean()).isFalse();
        assertThat(bySlug.get("nor-zovq").path("catalogState").asText()).isEqualTo("NOT_IMPLEMENTED");
    }

    @Test
    void storeListingsEndpointReturnsOnlyThatStoresOwnListings() throws Exception {
        JsonNode page = getJson("/api/stores/" + carrefour + "/products", "q", "թեստկաթ");
        assertThat(page.path("total").asLong()).isEqualTo(1);
        JsonNode offer = page.path("items").get(0).path("offer");
        assertThat(offer.path("storeSlug").asText()).isEqualTo("carrefour");
        assertThat(offer.path("externalProductId").asText()).isEqualTo("CF-milk-1l");
        assertThat(offer.path("price").decimalValue()).isEqualByComparingTo("655");
        assertThat(page.path("items").get(0).path("canonicalProductId").asLong()).isPositive();
    }

    private Map<String, String> rowStatuses(long canonical) throws Exception {
        Map<String, String> out = new HashMap<>();
        getJson("/api/products/" + canonical + "/offers").path("stores")
                .forEach(r -> out.put(r.path("storeSlug").asText(), r.path("status").asText()));
        return out;
    }
}
