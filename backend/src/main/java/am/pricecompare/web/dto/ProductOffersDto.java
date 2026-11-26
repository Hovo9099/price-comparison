package am.pricecompare.web.dto;

import java.util.List;

/**
 * Store-by-store comparison of one canonical product: one row per supermarket, including the ones that do not sell it.
 */
public record ProductOffersDto(ProductDetailDto product, CheapestDto cheapest, int storesWithOffer, List<StoreRow> stores) {

    public enum RowStatus {
        /** The store lists the product with a current, available price. */
        AVAILABLE,
        /** The store lists it but marks it out of stock (or it disappeared from the catalog). */
        OUT_OF_STOCK,
        /** Listed, but the newest price is too old to compare. */
        OUTDATED,
        /** The store's whole catalog was crawled and it has no listing linked to this product. */
        NOT_FOUND,
        /** Only part of the store's catalog has been imported: absence proves nothing. */
        NOT_YET_IMPORTED,
        /** The store's catalog could not be read (crawler failed / site down). */
        CATALOG_UNAVAILABLE,
        /** No crawler exists for this store. */
        NOT_INTEGRATED
    }

    /** @param offers the store's own listings for this product (usually one), best first */
    public record StoreRow(Long storeId, String storeName, String storeSlug, RowStatus status, String message,
                           List<OfferDto> offers) {
    }
}
