package am.pricecompare.web.dto;

import am.pricecompare.service.Freshness;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ShoppingListDtos {

    private ShoppingListDtos() {
    }

    public record CreateShoppingListRequest(@NotBlank @Size(max = 200) String name) {
    }

    /** Add a specific product, a product concept from the lexicon (e.g. "milk" 1000 ml), or a free-text note. */
    public record AddItemRequest(
            Long productId,
            @Size(max = 60) String conceptCode,
            @Size(max = 300) String freeText,
            @DecimalMin(value = "0.001") @DecimalMax(value = "1000") BigDecimal quantity,
            @DecimalMin(value = "0.001") BigDecimal sizeValue,
            @Size(max = 10) String sizeUnit,
            @DecimalMin(value = "0") @DecimalMax(value = "100") BigDecimal percent,
            @Size(max = 100) String preferredBrand,
            @Size(max = 300) String rawText
    ) {
        public AddItemRequest(Long productId, String freeText, BigDecimal quantity) {
            this(productId, null, freeText, quantity, null, null, null, null, null);
        }

        @AssertTrue(message = "one of productId, conceptCode or freeText is required")
        public boolean isTargetPresent() {
            return productId != null || (conceptCode != null && !conceptCode.isBlank()) || (freeText != null && !freeText.isBlank());
        }
    }

    /** Change quantity, package size, the concept (e.g. a cheese type) or switch to one specific product. */
    public record UpdateItemRequest(
            @DecimalMin(value = "0.001") @DecimalMax(value = "1000") BigDecimal quantity,
            @DecimalMin(value = "0.001") BigDecimal sizeValue,
            @Size(max = 10) String sizeUnit,
            Boolean clearSize,
            @Size(max = 60) String conceptCode,
            Long productId,
            @Size(max = 100) String preferredBrand) {
        public UpdateItemRequest(BigDecimal quantity) {
            this(quantity, null, null, null, null, null, null);
        }
    }

    public record OptimizeRequest(Integer maxStores) {
    }

    public record ShoppingListItemDto(Long id, Long productId, String name, String nameEn, String size, String imageUrl,
                                      String freeText, BigDecimal quantity, CheapestDto cheapest, String conceptCode,
                                      BigDecimal sizeValue, String sizeUnit, BigDecimal percent, String preferredBrand,
                                      String rawText, boolean requiresChoice, boolean soldByWeight) {
    }

    public record ShoppingListDto(Long id, String name, Instant createdAt, Instant updatedAt, List<ShoppingListItemDto> items) {
    }

    public record BasketLineDto(Long itemId, Long productId, String name, BigDecimal quantity, BigDecimal unitPrice,
                                BigDecimal total, String externalName, Instant capturedAt, Freshness freshness) {
    }

    public record StoreBasketDto(Long storeId, String storeName, String storeSlug, BigDecimal total, int itemCount,
                                 boolean complete, List<BasketLineDto> items, List<Long> missingItemIds) {
    }

    public record MultiStoreBasketDto(BigDecimal total, int storeCount, boolean complete, List<StoreBasketDto> stores,
                                      List<Long> missingItemIds) {
    }

    public record UnavailableItemDto(Long itemId, Long productId, String name, String reason) {
    }

    public record BasketOptimizationDto(
            Long shoppingListId,
            String currency,
            StoreBasketDto cheapestSingleStore,
            List<StoreBasketDto> singleStoreOptions,
            MultiStoreBasketDto cheapestMultiStore,
            BigDecimal savings,
            List<UnavailableItemDto> unavailableItems,
            boolean containsStalePrices,
            Instant calculatedAt
    ) {
    }
}
