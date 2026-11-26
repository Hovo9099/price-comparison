package am.pricecompare.web;

import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.domain.Store;
import am.pricecompare.repository.CrawlRunRepository;
import am.pricecompare.repository.StoreProductRepository;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.service.Freshness;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/crawler")
@Tag(name = "Crawler status", description = "Public data-source freshness per supermarket")
public class CrawlerStatusController {

    private final StoreRepository storeRepo;
    private final CrawlRunRepository runRepo;
    private final StoreProductRepository storeProductRepo;

    public CrawlerStatusController(StoreRepository storeRepo, CrawlRunRepository runRepo, StoreProductRepository storeProductRepo) {
        this.storeRepo = storeRepo;
        this.runRepo = runRepo;
        this.storeProductRepo = storeProductRepo;
    }

    /**
     * @param lastSuccessfulCollection end of the last successful (or partially successful) crawl/CSV import
     * @param lastAttemptStatus        status of the most recent attempt; FAILED/UNAVAILABLE are source problems, not
     *                                 product unavailability
     */
    public record SourceStatus(Long storeId, String store, String slug, String websiteUrl, boolean active, long products,
                               long activeProducts, Instant lastSuccessfulCollection, Freshness freshness, String lastRunType,
                               CrawlStatus lastAttemptStatus, Instant lastAttemptAt, String lastAttemptMessage) {
    }

    @GetMapping("/status")
    @Transactional(readOnly = true)
    @Operation(summary = "Per store: products, when prices were last collected successfully, freshness and last attempt")
    public List<SourceStatus> status() {
        List<SourceStatus> out = new ArrayList<>();
        Instant now = Instant.now();
        for (Store s : storeRepo.findAllByOrderByIdAsc()) {
            CrawlRun last = runRepo.findFirstByStoreIdOrderByStartedAtDesc(s.getId()).orElse(null);
            CrawlRun ok = runRepo.findFirstByStoreIdAndStatusInOrderByStartedAtDesc(s.getId(),
                    List.of(CrawlStatus.SUCCESS, CrawlStatus.PARTIAL)).orElse(null);
            Instant collected = ok == null ? null : ok.getFinishedAt() != null ? ok.getFinishedAt() : ok.getStartedAt();
            out.add(new SourceStatus(s.getId(), s.getName(), s.getSlug(), s.getWebsiteUrl(), s.isActive(),
                    storeProductRepo.countByStoreId(s.getId()), storeProductRepo.countByStoreIdAndActiveTrue(s.getId()), collected,
                    collected == null ? null : Freshness.of(collected, now), last == null ? null : last.getRunType(),
                    last == null ? null : last.getStatus(), last == null ? null : last.getStartedAt(),
                    last == null ? null : last.getErrorMessage()));
        }
        return out;
    }
}
