package am.pricecompare.ingest;

import am.pricecompare.domain.Price;
import am.pricecompare.domain.PriceHistory;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.matching.MatchingService;
import am.pricecompare.repository.PriceHistoryRepository;
import am.pricecompare.repository.PriceRepository;
import am.pricecompare.repository.StoreProductRepository;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static am.pricecompare.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PriceHistoryIntegrationTest {

    @Autowired ProductIngestionService ingestion;
    @Autowired StoreRepository storeRepo;
    @Autowired StoreProductRepository storeProductRepo;
    @Autowired PriceRepository priceRepo;
    @Autowired PriceHistoryRepository historyRepo;
    @Autowired MatchingService matchingService;

    long storeId;

    @BeforeEach
    void setUp() {
        matchingService.resetIndex();
        storeId = storeRepo.findBySlug("sas").orElseThrow().getId();
    }

    @Test
    void priceChangesAreNeverOverwritten() {
        Instant d1 = Instant.parse("2026-10-01T08:00:00Z");
        Instant d3 = Instant.parse("2026-10-03T08:00:00Z");
        Instant d6 = Instant.parse("2026-10-06T08:00:00Z");
        ingestion.ingest(storeId, null, product("T-1", "TEST Cola 1.5L", "620", d1), false);
        ingestion.ingest(storeId, null, product("T-1", "TEST Cola 1.5L", "600", d3), false);
        ingestion.ingest(storeId, null, product("T-1", "TEST Cola 1.5L", "590", d6), false);

        StoreProduct sp = storeProductRepo.findByStoreIdAndExternalProductId(storeId, "T-1").orElseThrow();
        List<Price> periods = priceRepo.findByStoreProductIdOrderByValidFromAsc(sp.getId());
        assertThat(periods).extracting(p -> p.getPrice().intValue()).containsExactly(620, 600, 590);
        assertThat(periods.get(0).getValidTo()).isEqualTo(d3);
        assertThat(periods.get(1).getValidTo()).isEqualTo(d6);
        assertThat(periods.get(2).getValidTo()).isNull();
        assertThat(priceRepo.findCurrent(sp.getId()).orElseThrow().getPrice()).isEqualByComparingTo("590");

        List<PriceHistory> history = historyRepo.findByStoreProductIdOrderByCapturedAtAsc(sp.getId());
        assertThat(history).extracting(h -> h.getPrice().intValue()).containsExactly(620, 600, 590);
    }

    @Test
    void unchangedPriceOnlyRefreshesCapturedAt() {
        Instant t1 = Instant.parse("2026-10-01T08:00:00Z");
        Instant t2 = Instant.parse("2026-10-02T08:00:00Z");
        ingestion.ingest(storeId, null, product("T-2", "TEST Water 0.5L", "150", t1), false);
        ProductIngestionService.Result r = ingestion.ingest(storeId, null, product("T-2", "TEST Water 0.5L", "150", t2), false);
        assertThat(r.priceChanged()).isFalse();
        StoreProduct sp = storeProductRepo.findByStoreIdAndExternalProductId(storeId, "T-2").orElseThrow();
        List<Price> periods = priceRepo.findByStoreProductIdOrderByValidFromAsc(sp.getId());
        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).getCapturedAt()).isEqualTo(t2);
        assertThat(periods.get(0).getValidFrom()).isEqualTo(t1);
        assertThat(historyRepo.findByStoreProductIdOrderByCapturedAtAsc(sp.getId())).hasSize(2);
    }

    @Test
    void disappearedProductBecomesUnavailableButKeepsHistory() {
        ingestion.ingest(storeId, null, product("T-3", "TEST Juice 1L", "800", Instant.parse("2026-10-01T08:00:00Z")), false);
        StoreProduct sp = storeProductRepo.findByStoreIdAndExternalProductId(storeId, "T-3").orElseThrow();
        assertThat(ingestion.markUnavailable(sp.getId(), null)).isTrue();

        StoreProduct after = storeProductRepo.findById(sp.getId()).orElseThrow();
        assertThat(after.isActive()).isFalse();
        Price current = priceRepo.findCurrent(sp.getId()).orElseThrow();
        assertThat(current.isAvailable()).isFalse();
        assertThat(current.getPrice()).isEqualByComparingTo("800");
        assertThat(priceRepo.findByStoreProductIdOrderByValidFromAsc(sp.getId())).hasSize(2);
        assertThat(historyRepo.findByStoreProductIdOrderByCapturedAtAsc(sp.getId())).hasSize(2);
    }

    @Test
    void discountIsStoredAsOriginalPrice() {
        var p = product("T-4", "TEST Biscuit 200g", "450", Instant.parse("2026-10-01T08:00:00Z"));
        var discounted = new am.pricecompare.crawler.DiscoveredProduct(p.externalId(), null, null, p.name(), null, null, null, null,
                null, p.price(), new java.math.BigDecimal("520"), null, null, "AMD", true, false, null, null, false, p.capturedAt(), p.rawData());
        ingestion.ingest(storeId, null, discounted, false);
        StoreProduct sp = storeProductRepo.findByStoreIdAndExternalProductId(storeId, "T-4").orElseThrow();
        Price current = priceRepo.findCurrent(sp.getId()).orElseThrow();
        assertThat(current.getOriginalPrice()).isEqualByComparingTo("520");
        assertThat(sp.getPackageSize()).isEqualByComparingTo("200");
        assertThat(sp.getPackageUnit()).isEqualTo("g");
    }
}
