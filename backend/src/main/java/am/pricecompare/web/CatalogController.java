package am.pricecompare.web;

import am.pricecompare.service.CatalogService;
import am.pricecompare.web.dto.CategoryDto;
import am.pricecompare.web.dto.StoreDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Catalog", description = "Stores and categories")
@org.springframework.validation.annotation.Validated
public class CatalogController {

    private final CatalogService catalogService;
    private final am.pricecompare.service.NearbyService nearbyService;

    public CatalogController(CatalogService catalogService, am.pricecompare.service.NearbyService nearbyService) {
        this.catalogService = catalogService;
        this.nearbyService = nearbyService;
    }

    @GetMapping("/stores")
    @Operation(summary = "Supermarkets with their real catalog status (complete / partial / unavailable / not integrated)")
    public List<StoreDto> stores() {
        return catalogService.stores();
    }

    @GetMapping("/stores/nearby")
    @io.swagger.v3.oas.annotations.Operation(summary = "Nearest known branch of each store, closest first",
            description = "Coordinates are used for this calculation only and are not stored.")
    public List<am.pricecompare.web.dto.NearbyStoreDto> nearby(@org.springframework.web.bind.annotation.RequestParam double lat,
                                                               @org.springframework.web.bind.annotation.RequestParam double lon) {
        return nearbyService.nearestPerStore(lat, lon);
    }

    @GetMapping("/stores/{id}/locations")
    @Operation(summary = "Physical branches of a store (as published by the store)")
    public List<am.pricecompare.web.dto.StoreLocationDto> locations(@org.springframework.web.bind.annotation.PathVariable long id) {
        return catalogService.locations(id);
    }

    @GetMapping("/stores/{id}/products")
    @Operation(summary = "A store's own listings with current prices (paged; q filters by name, barcode or store product id)")
    public am.pricecompare.web.dto.PageDto<am.pricecompare.web.dto.StoreListingDto> storeProducts(
            @org.springframework.web.bind.annotation.PathVariable long id,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean availableOnly,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(200) int size) {
        return catalogService.storeListings(id, q, availableOnly, page, size);
    }

    @GetMapping("/categories")
    @Operation(summary = "Categories with the number of products that currently have prices")
    public List<CategoryDto> categories() {
        return catalogService.categories();
    }
}
