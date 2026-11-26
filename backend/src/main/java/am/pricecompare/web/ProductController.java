package am.pricecompare.web;

import am.pricecompare.service.CatalogService;
import am.pricecompare.service.ProductSearchService;
import am.pricecompare.web.dto.PageDto;
import am.pricecompare.web.dto.PriceComparisonDto;
import am.pricecompare.web.dto.PriceDropDto;
import am.pricecompare.web.dto.PriceHistoryDto;
import am.pricecompare.web.dto.ProductDetailDto;
import am.pricecompare.web.dto.ProductOffersDto;
import am.pricecompare.web.dto.ProductSummaryDto;
import am.pricecompare.web.dto.SearchResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Validated
@Tag(name = "Products", description = "Search canonical products and compare real store prices")
public class ProductController {

    private final ProductSearchService searchService;
    private final CatalogService catalogService;

    public ProductController(ProductSearchService searchService, CatalogService catalogService) {
        this.searchService = searchService;
        this.catalogService = catalogService;
    }

    @GetMapping("/search")
    @Operation(summary = "Search products (Armenian, English or Russian)",
            description = "Examples: `coca cola`, `milk`, `կաթ`, `բրինձ`, `ձու`, `կոկա կոլա`. Units are normalized (1.5L = 1500ml). "
                    + "Searches every store; each item carries one offer per store and `stores` reports per-store match counts "
                    + "and catalog status.")
    public SearchResultDto search(
            @RequestParam @NotBlank @Size(max = 200) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return searchService.search(q, categoryId, storeId, page, size);
    }

    @GetMapping
    @Operation(summary = "Browse products, optionally by category (products sold by most stores first)")
    public PageDto<ProductSummaryDto> browse(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalogService.browse(categoryId, page, size);
    }

    @GetMapping("/price-drops")
    @Operation(summary = "Largest recent price drops")
    public List<PriceDropDto> priceDrops(
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return catalogService.priceDrops(days, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Product details")
    public ProductDetailDto detail(@PathVariable long id) {
        return catalogService.detail(id);
    }

    @GetMapping("/{id}/offers")
    @Operation(summary = "Store-by-store comparison: one row per supermarket with its own listing and price, or why it has none")
    public ProductOffersDto offers(@PathVariable long id) {
        return catalogService.offers(id);
    }

    @GetMapping("/{id}/prices")
    @Operation(summary = "Current price at every store, cheapest first",
            description = "Each price carries capturedAt, ageHours and freshness (FRESH <24h, RECENT 24-72h, STALE 3-7d, OUTDATED >7d). "
                    + "OUTDATED prices are listed but never chosen as cheapest.")
    public PriceComparisonDto prices(@PathVariable long id) {
        return catalogService.compare(id);
    }

    @GetMapping("/{id}/price-history")
    @Operation(summary = "Price history per store (alias of /history)")
    public PriceHistoryDto priceHistory(@PathVariable long id, @RequestParam(defaultValue = "90") @Min(1) @Max(730) int days) {
        return catalogService.history(id, days);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Price history per store")
    public PriceHistoryDto history(@PathVariable long id,
                                   @Parameter(description = "Look-back window in days")
                                   @RequestParam(defaultValue = "90") @Min(1) @Max(730) int days) {
        return catalogService.history(id, days);
    }
}
