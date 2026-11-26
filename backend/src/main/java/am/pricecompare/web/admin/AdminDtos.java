package am.pricecompare.web.admin;

import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.domain.MatchStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record CrawlRunDto(Long id, Long storeId, String storeName, String storeSlug, String runType, CrawlStatus status,
                              Instant startedAt, Instant finishedAt, Integer maxProducts, int pagesVisited,
                              int productsDiscovered, int productsUpdated, int pricesUpdated, int errors, String errorMessage) {
    }

    public record CrawlErrorDto(Long id, String url, String externalId, String message, Instant createdAt) {
    }

    /**
     * @param implemented      a catalog parser exists (false = "not implemented", never shown as integrated)
     * @param catalogState     COMPLETE only after an unlimited crawl walked the whole catalog
     * @param pricedListings   active listings with a current, available price
     * @param lastCompleteRun  last crawl that covered the whole catalog (pagination complete)
     * @param lastPriceUpdate  newest price observation of this store
     */
    public record StoreStatusDto(Long storeId, String name, String slug, String websiteUrl, boolean storeActive,
                                 boolean providerAvailable, String unavailableReason, boolean running,
                                 long products, long activeProducts, CrawlRunDto lastRun, CrawlRunDto lastSuccessfulRun,
                                 boolean implemented, am.pricecompare.service.CatalogStatusService.CatalogState catalogState,
                                 String catalogMessage, long pricedListings, CrawlRunDto lastCompleteRun, Instant lastPriceUpdate) {
    }

    public record DashboardDto(List<StoreStatusDto> stores, long canonicalProducts, long storeProducts,
                               long currentPrices, long pendingMatches, long unmatchedStoreProducts) {
    }

    public record StartCrawlResponse(Long runId, String storeSlug) {
    }

    public record CanonicalProductAdminDto(Long id, String name, String nameEn, String brand, String barcode,
                                           BigDecimal sizeValue, String sizeUnit, String variant, boolean soldByWeight,
                                           Long categoryId, String imageUrl, boolean active, int storeProductCount) {
    }

    public record UpdateCanonicalProductRequest(
            @NotBlank @Size(max = 500) String name,
            @Size(max = 500) String nameEn,
            @Size(max = 200) String brand,
            @Size(max = 50) String barcode,
            BigDecimal sizeValue,
            @Size(max = 10) String sizeUnit,
            @Size(max = 100) String variant,
            Long categoryId,
            @Size(max = 1000) String imageUrl,
            Boolean active) {
    }

    public record StoreProductAdminDto(Long id, Long storeId, String storeName, String externalProductId, String externalSku,
                                       String externalName, String externalNameEn, String externalUrl, String barcode,
                                       String brand, BigDecimal packageSize, String packageUnit, boolean active,
                                       Long canonicalProductId, String canonicalName, BigDecimal currentPrice,
                                       Instant capturedAt, Instant lastSeenAt) {
    }

    public record MapStoreProductRequest(@NotNull Long canonicalProductId) {
    }

    public record MatchCandidateDto(Long id, BigDecimal confidence, MatchStatus status, String reason, Instant createdAt,
                                    StoreProductAdminDto storeProduct, CanonicalProductAdminDto candidate) {
    }
}
