package am.pricecompare.web.dto;

/** A store's own listing with its current price, and the canonical product it is linked to (if any). */
public record StoreListingDto(Long canonicalProductId, OfferDto offer) {
}
