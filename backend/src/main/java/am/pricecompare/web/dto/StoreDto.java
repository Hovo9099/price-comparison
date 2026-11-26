package am.pricecompare.web.dto;

import am.pricecompare.service.CatalogStatusService.CatalogState;

import java.time.Instant;

/**
 * A supermarket and what is actually known about its catalog.
 *
 * @param catalogState     COMPLETE only after a crawl walked the whole public catalog
 * @param catalogMessage   why the catalog is incomplete/unavailable (null when complete and the last crawl worked)
 * @param listings         store listings imported so far
 * @param pricedListings   active listings with a current, available price
 * @param lastCompleteCrawlAt end of the last crawl that covered the whole catalog
 * @param lastPriceUpdateAt newest price observation
 * @param delivery         published delivery terms, null when unknown
 */
public record StoreDto(Long id, String name, String slug, String websiteUrl, String logoUrl, boolean active,
                       boolean integrated, CatalogState catalogState, String catalogMessage, long listings, long pricedListings,
                       Instant lastCompleteCrawlAt, Instant lastPriceUpdateAt, DeliveryTermsDto delivery) {
}
