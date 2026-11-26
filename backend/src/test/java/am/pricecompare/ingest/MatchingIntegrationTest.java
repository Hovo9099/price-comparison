package am.pricecompare.ingest;

import am.pricecompare.domain.MatchStatus;
import am.pricecompare.domain.ProductMatchCandidate;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.matching.MatchingService;
import am.pricecompare.matching.ProductMatcher.Decision;
import am.pricecompare.repository.CanonicalProductRepository;
import am.pricecompare.repository.ProductMatchCandidateRepository;
import am.pricecompare.repository.StoreProductRepository;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static am.pricecompare.support.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class MatchingIntegrationTest {

    @Autowired ProductIngestionService ingestion;
    @Autowired StoreRepository storeRepo;
    @Autowired StoreProductRepository storeProductRepo;
    @Autowired CanonicalProductRepository canonicalRepo;
    @Autowired ProductMatchCandidateRepository candidateRepo;
    @Autowired MatchingService matchingService;
    @Autowired TransactionTemplate tx;

    long sas;
    long yc;
    long carrefour;
    final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        matchingService.resetIndex();
        sas = storeRepo.findBySlug("sas").orElseThrow().getId();
        yc = storeRepo.findBySlug("yerevan-city").orElseThrow().getId();
        carrefour = storeRepo.findBySlug("carrefour").orElseThrow().getId();
    }

    private Long canonicalOf(long storeId, String externalId) {
        return tx.execute(s -> storeProductRepo.findByStoreIdAndExternalProductId(storeId, externalId).orElseThrow()
                .getCanonicalProduct().getId());
    }

    @Test
    void sameProductFromThreeStoresSharesOneCanonicalProduct() {
        ingestion.ingest(sas, null, product("A1", "TESTCOLA Coca-Cola 1.5L", "590", now), false);
        ingestion.ingest(yc, null, product("B1", "TESTCOLA Coca Cola 1500ml", "570", now), false);
        ingestion.ingest(carrefour, null, product("C1", "TESTCOLA Coca-Cola 1.5 լ", "600", now), false);
        Long c = canonicalOf(sas, "A1");
        assertThat(canonicalOf(yc, "B1")).isEqualTo(c);
        assertThat(canonicalOf(carrefour, "C1")).isEqualTo(c);
        assertThat(canonicalRepo.count()).isEqualTo(1);
    }

    @Test
    void differentSizesAndFatContentStaySeparate() {
        ingestion.ingest(sas, null, product("A1", "TESTCOLA Coca-Cola 1.5L", "590", now), false);
        ingestion.ingest(yc, null, product("B2", "TESTCOLA Coca-Cola 500ml", "300", now), false);
        ingestion.ingest(sas, null, product("A3", "TESTMILK Milk 2.5% 1L", "450", now), false);
        ingestion.ingest(yc, null, product("B4", "TESTMILK Milk 3.2% 1L", "470", now), false);
        assertThat(canonicalOf(yc, "B2")).isNotEqualTo(canonicalOf(sas, "A1"));
        assertThat(canonicalOf(yc, "B4")).isNotEqualTo(canonicalOf(sas, "A3"));
        assertThat(canonicalRepo.count()).isEqualTo(4);
    }

    @Test
    void barcodeLinksProductsWithUnrelatedNames() {
        ingestion.ingest(yc, null, product("B5", "TEST Կաթ «Պարմալատ» 3.5% 1լ", "Milk Parmalat 3.5% 1l", "Parmalat", "0000000000017", "1090", now), false);
        ingestion.ingest(carrefour, null, product("C5", "TEST Молоко UHT 1л", null, null, "0000000000017", "1150", now), false);
        assertThat(canonicalOf(carrefour, "C5")).isEqualTo(canonicalOf(yc, "B5"));
    }

    @Test
    void twoProductsOfTheSameStoreAreNeverMergedAutomatically() {
        ingestion.ingest(sas, null, product("A1", "TESTCOLA Coca-Cola 1.5L", "590", now), false);
        ingestion.ingest(sas, null, product("A2", "TESTCOLA Coca Cola 1500ml", "595", now), false);
        assertThat(canonicalOf(sas, "A2")).isNotEqualTo(canonicalOf(sas, "A1"));
    }

    @Test
    void uncertainMatchIsQueuedForReviewAndApprovalMerges() {
        ingestion.ingest(sas, null, product("A6", "Sauce Kuhne Black Garlic 235ml", null, "Kühne", null, "1670", now), false);
        ProductIngestionService.Result r = ingestion.ingest(yc, null,
                product("B6", "Sauce Kuhne Garlic 235ml", null, "Kuhne", null, "1600", now), false);
        assertThat(r.match().decision()).isEqualTo(Decision.REVIEW);
        assertThat(canonicalOf(yc, "B6")).isNotEqualTo(canonicalOf(sas, "A6"));

        var pending = candidateRepo.findByStatusWithDetails(MatchStatus.PENDING, PageRequest.of(0, 10));
        assertThat(pending.getTotalElements()).isEqualTo(1);
        Long candidateId = pending.getContent().get(0).getId();
        Long orphan = canonicalOf(yc, "B6");

        tx.executeWithoutResult(s -> {
            ProductMatchCandidate c = candidateRepo.findById(candidateId).orElseThrow();
            matchingService.approve(c);
        });
        assertThat(canonicalOf(yc, "B6")).isEqualTo(canonicalOf(sas, "A6"));
        assertThat(canonicalRepo.findById(orphan).orElseThrow().isActive()).isFalse();
        assertThat(candidateRepo.findById(candidateId).orElseThrow().getStatus()).isEqualTo(MatchStatus.APPROVED);
    }

    @Test
    void rejectedCandidateKeepsProductsSeparate() {
        ingestion.ingest(sas, null, product("A6", "Sauce Kuhne Black Garlic 235ml", null, "Kühne", null, "1670", now), false);
        ingestion.ingest(yc, null, product("B6", "Sauce Kuhne Garlic 235ml", null, "Kuhne", null, "1600", now), false);
        Long candidateId = candidateRepo.findByStatusWithDetails(MatchStatus.PENDING, PageRequest.of(0, 10)).getContent().get(0).getId();
        tx.executeWithoutResult(s -> matchingService.reject(candidateRepo.findById(candidateId).orElseThrow()));
        assertThat(candidateRepo.findById(candidateId).orElseThrow().getStatus()).isEqualTo(MatchStatus.REJECTED);
        assertThat(canonicalOf(yc, "B6")).isNotEqualTo(canonicalOf(sas, "A6"));
    }

    @Test
    void manualMappingMovesStoreProduct() {
        ingestion.ingest(sas, null, product("A7", "TEST Rice Basmati 1kg", "1500", now), false);
        ingestion.ingest(yc, null, product("B7", "TEST Բրինձ բասմատի 1կգ", "1450", now), false);
        Long target = canonicalOf(sas, "A7");
        tx.executeWithoutResult(s -> {
            StoreProduct sp = storeProductRepo.findByStoreIdAndExternalProductId(yc, "B7").orElseThrow();
            matchingService.mapManually(sp, canonicalRepo.findById(target).orElseThrow());
        });
        assertThat(canonicalOf(yc, "B7")).isEqualTo(target);
    }
}
