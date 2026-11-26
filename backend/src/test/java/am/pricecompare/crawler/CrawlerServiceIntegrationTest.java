package am.pricecompare.crawler;

import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.matching.MatchingService;
import am.pricecompare.repository.CrawlErrorRepository;
import am.pricecompare.repository.StoreProductRepository;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Import(CrawlerServiceIntegrationTest.FakeProviders.class)
class CrawlerServiceIntegrationTest {

    /** Extra providers with test-only store codes (no network). */
    @TestConfiguration
    static class FakeProviders {
        @Bean
        SupermarketProvider fakeGood() {
            return new FakeProvider("test-good", false);
        }

        @Bean
        SupermarketProvider fakeBroken() {
            return new FakeProvider("test-broken", true);
        }
    }

    static class FakeProvider implements SupermarketProvider {
        final String code;
        final boolean explode;

        FakeProvider(String code, boolean explode) {
            this.code = code;
            this.explode = explode;
        }

        @Override public String getStoreCode() { return code; }
        @Override public String getStoreName() { return code; }
        @Override public boolean isAvailable() { return true; }
        @Override public List<DiscoveredCategory> discoverCategories() { return List.of(); }

        @Override
        public void discoverProducts(CrawlContext ctx) {
            if (explode) {
                throw new IllegalStateException("site layout changed (test)");
            }
            for (int i = 1; i <= 5 && !ctx.shouldStop(); i++) {
                ctx.pageVisited();
                // product 3 is broken: no price
                BigDecimal price = i == 3 ? null : BigDecimal.valueOf(100L * i);
                ctx.emit(new DiscoveredProduct("F" + i, null, null, "TEST Fake product " + i + " 1L", null, null, null,
                        null, null, price, null, null, null, "AMD", true, false, null, null, false, Instant.now(), Map.of()));
            }
            ctx.error("https://test.invalid/x", null, "simulated parse warning");
        }
    }

    @Autowired CrawlerService crawlerService;
    @Autowired StoreRepository storeRepo;
    @Autowired StoreProductRepository storeProductRepo;
    @Autowired CrawlErrorRepository errorRepo;
    @Autowired MatchingService matchingService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void stores() {
        matchingService.resetIndex();
        jdbc.update("insert into stores (name, slug, active) values ('Test good', 'test-good', true), ('Test broken', 'test-broken', true)");
    }

    @Test
    void oneBadProductDoesNotStopTheCrawl() {
        CrawlRun run = crawlerService.runSync("test-good", 0);
        assertThat(run.getStatus()).isEqualTo(CrawlStatus.PARTIAL);
        assertThat(run.getProductsDiscovered()).isEqualTo(4);
        assertThat(run.getPricesUpdated()).isEqualTo(4);
        assertThat(run.getPagesVisited()).isEqualTo(5);
        assertThat(run.getErrors()).isEqualTo(2);
        assertThat(errorRepo.findByCrawlRunIdOrderByIdAsc(run.getId(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        long storeId = storeRepo.findBySlug("test-good").orElseThrow().getId();
        assertThat(storeProductRepo.countByStoreId(storeId)).isEqualTo(4);
    }

    @Test
    void productLimitIsRespected() {
        CrawlRun run = crawlerService.runSync("test-good", 2);
        assertThat(run.getProductsDiscovered()).isEqualTo(2);
        assertThat(run.getMaxProducts()).isEqualTo(2);
    }

    @Test
    void failingStoreIsRecordedAndDoesNotAffectOthers() {
        CrawlRun broken = crawlerService.runSync("test-broken", 0);
        assertThat(broken.getStatus()).isEqualTo(CrawlStatus.FAILED);
        assertThat(broken.getErrorMessage()).contains("site layout changed");
        CrawlRun good = crawlerService.runSync("test-good", 0);
        assertThat(good.getProductsDiscovered()).isEqualTo(4);
    }

    @Test
    void productsMissingFromACompleteRunBecomeUnavailable() {
        long storeId = storeRepo.findBySlug("test-good").orElseThrow().getId();
        jdbc.update("insert into store_products (store_id, external_product_id, external_name, last_seen_at, active) "
                + "values (?, 'GONE', 'TEST old product', now() - interval '3 days', true)", storeId);
        crawlerService.runSync("test-good", 0);
        Boolean active = jdbc.queryForObject("select active from store_products where external_product_id = 'GONE'", Boolean.class);
        assertThat(active).isFalse();
    }
}
