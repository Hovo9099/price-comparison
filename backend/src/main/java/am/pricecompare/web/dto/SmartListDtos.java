package am.pricecompare.web.dto;

import am.pricecompare.service.Freshness;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DTOs of the smart shopping list (parse, concepts, three-strategy comparison). */
public final class SmartListDtos {

    private SmartListDtos() {
    }

    public record ConceptDto(String code, String nameHy, String nameEn, String nameRu, String category, BigDecimal defaultSizeValue,
                             String defaultSizeUnit, List<BigDecimal> sizes, boolean soldByWeight, boolean requiresChoice,
                             List<ConceptRefDto> choices, List<ConceptRefDto> alternatives) {
    }

    public record ConceptRefDto(String code, String nameHy, String nameEn) {
    }

    public record ParseRequest(@NotBlank @Size(max = 4000) String text, Boolean add) {
    }

    public enum ParseStatus { RECOGNIZED, AMBIGUOUS, NEEDS_CHOICE, UNRECOGNIZED }

    /**
     * One recognised request. {@code quantity} is a number of packages, or kilograms for products sold by weight.
     * {@code productSuggestions} are real products (with current prices) that satisfy the request.
     */
    public record ParsedItemDto(String raw, String phrase, ParseStatus status, double confidence, ConceptDto concept,
                                List<ConceptDto> alternatives, BigDecimal quantity, String quantityUnit, BigDecimal sizeValue,
                                String sizeUnit, BigDecimal percent, String hint, int matchingProducts,
                                List<ProductSummaryDto> productSuggestions) {
    }

    public record ParseResultDto(List<ParsedItemDto> items, ShoppingListDtos.ShoppingListDto list) {
    }

    public record ComparePreferences(
            @Min(0) @Max(5) Integer maxStores,
            Boolean preferOneStore,
            Boolean includeDelivery,
            Boolean allowStalePrices,
            @Size(max = 20) List<@Size(max = 60) String> preferredBrands) {

        public int effectiveMaxStores() {
            if (Boolean.TRUE.equals(preferOneStore)) {
                return 1;
            }
            return maxStores == null ? 0 : maxStores;
        }
    }

    public enum ItemStatus { OK, NEEDS_CHOICE, NO_MATCHING_PRODUCT, NO_CURRENT_PRICE, UNRECOGNIZED }

    public record ItemStatusDto(Long itemId, String label, String conceptCode, Long productId, BigDecimal quantity, String quantityUnit,
                                BigDecimal sizeValue, String sizeUnit, ItemStatus status, int comparableProducts, String note,
                                List<ConceptRefDto> choices) {
    }

    public record LineDto(Long itemId, String itemLabel, Long productId, String productName, String brand, Long storeProductId,
                          String externalName, String externalUrl, String imageUrl, BigDecimal price, BigDecimal pricePerUnit,
                          String perUnit, BigDecimal quantity, String quantityUnit, BigDecimal lineTotal, Instant capturedAt,
                          Freshness freshness, Integer pricePercentile, int comparableProducts, BigDecimal medianPricePerUnit,
                          String reasonCode, String reason) {
    }

    public record StoreOptionDto(Long storeId, String storeName, String storeSlug, BigDecimal subtotal, BigDecimal deliveryFee,
                                 boolean deliveryFeeKnown, BigDecimal total, List<LineDto> lines) {
    }

    public record OptionDto(BigDecimal total, int storeCount, boolean complete, List<StoreOptionDto> stores, List<Long> missingItemIds) {
    }

    public record StrategyDto(String strategy, String recommended, BigDecimal total, OptionDto singleStore, OptionDto multiStore,
                              List<Long> missingItemIds, Map<String, BigDecimal> differenceVsOthers, List<String> notes) {
    }

    public record ComparisonDto(Long comparisonId, Long shoppingListId, ComparePreferences preferences, List<ItemStatusDto> items,
                                List<StrategyDto> strategies, List<String> warnings, Instant calculatedAt) {
    }
}
