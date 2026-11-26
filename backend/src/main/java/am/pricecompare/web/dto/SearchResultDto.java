package am.pricecompare.web.dto;

import am.pricecompare.service.CatalogStatusService.CatalogState;

import java.util.List;

/**
 * Search results plus, for every store, how many matching products it has and whether its catalog was fully imported,
 * so "no results at store X" can be told apart from "store X could not be read".
 *
 * @param total        products available through paging (most relevant first)
 * @param totalMatches every matching product with a current price
 */
public record SearchResultDto(List<ProductSummaryDto> items, int page, int size, long total, long totalMatches,
                              List<StoreMatchDto> stores) {

    /**
     * @param matches matching products with a current price at this store
     * @param message why the store has no (or possibly incomplete) results; null when its catalog is complete
     */
    public record StoreMatchDto(Long storeId, String storeName, String storeSlug, long matches, CatalogState catalogState,
                                String message) {
    }
}
