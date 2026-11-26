package am.pricecompare.web;

import am.pricecompare.service.ShoppingListService;
import am.pricecompare.web.dto.ShoppingListDtos.AddItemRequest;
import am.pricecompare.web.dto.ShoppingListDtos.BasketOptimizationDto;
import am.pricecompare.web.dto.ShoppingListDtos.CreateShoppingListRequest;
import am.pricecompare.web.dto.ShoppingListDtos.OptimizeRequest;
import am.pricecompare.web.dto.ShoppingListDtos.ShoppingListDto;
import am.pricecompare.web.dto.ShoppingListDtos.UpdateItemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shopping-lists")
@Tag(name = "Shopping lists", description = "Shopping lists and basket optimization")
public class ShoppingListController {

    private final ShoppingListService service;
    private final am.pricecompare.service.SmartListService smart;

    public ShoppingListController(ShoppingListService service, am.pricecompare.service.SmartListService smart) {
        this.service = service;
        this.smart = smart;
    }

    @GetMapping
    @Operation(summary = "Several saved lists by id (the app remembers its list ids)")
    public java.util.List<ShoppingListDto> lists(@org.springframework.web.bind.annotation.RequestParam java.util.Set<Long> ids) {
        if (ids.size() > 50) {
            throw new IllegalArgumentException("At most 50 ids");
        }
        return smart.lists(ids);
    }

    @PostMapping("/parse")
    @Operation(summary = "Recognise free text without a list",
            description = "Splits text like `kat, dzu 10, 2 hac, karag` and recognises each item in Armenian, Latin-letter Armenian, Russian or English.")
    public am.pricecompare.web.dto.SmartListDtos.ParseResultDto parseOnly(@Valid @RequestBody am.pricecompare.web.dto.SmartListDtos.ParseRequest req) {
        return new am.pricecompare.web.dto.SmartListDtos.ParseResultDto(smart.parse(req.text()), null);
    }

    @PostMapping("/{id}/parse")
    @Operation(summary = "Recognise free text and (with add=true) add the items to the list",
            description = "Returns, per item: the recognised concept, status (RECOGNIZED / AMBIGUOUS / NEEDS_CHOICE / UNRECOGNIZED), quantity, size and real product suggestions.")
    public am.pricecompare.web.dto.SmartListDtos.ParseResultDto parse(@PathVariable long id,
                                                                      @Valid @RequestBody am.pricecompare.web.dto.SmartListDtos.ParseRequest req) {
        return smart.parseInto(id, req.text(), Boolean.TRUE.equals(req.add()));
    }

    @PostMapping("/{id}/compare")
    @Operation(summary = "Compare the list with three strategies: CHEAPEST, MID_RANGE, PREMIUM",
            description = "Each strategy has a single-store and a multi-store basket computed from real current prices, with per-line reasons, freshness, missing items and differences between strategies.")
    public am.pricecompare.web.dto.SmartListDtos.ComparisonDto compare(@PathVariable long id,
                                                                       @Valid @RequestBody(required = false) am.pricecompare.web.dto.SmartListDtos.ComparePreferences prefs) {
        return smart.compare(id, prefs);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a shopping list")
    public ShoppingListDto create(@Valid @RequestBody CreateShoppingListRequest req) {
        return service.create(req.name());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a shopping list with the cheapest current price of each item")
    public ShoppingListDto get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a product (productId) or a free-text item; adding the same product again increases quantity")
    public ShoppingListDto addItem(@PathVariable long id, @Valid @RequestBody AddItemRequest req) {
        return service.addItem(id, req);
    }

    @PatchMapping("/{id}/items/{itemId}")
    @Operation(summary = "Change quantity, package size, type (concept) or switch the item to a specific product")
    public ShoppingListDto updateItem(@PathVariable long id, @PathVariable long itemId, @Valid @RequestBody UpdateItemRequest req) {
        return service.updateItem(id, itemId, req);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Remove an item")
    public ShoppingListDto removeItem(@PathVariable long id, @PathVariable long itemId) {
        return service.removeItem(id, itemId);
    }

    @PostMapping("/{id}/optimize")
    @Operation(summary = "Cheapest single-store and multi-store basket",
            description = "Uses current, available, non-outdated prices. Optional body: {\"maxStores\": 2} limits the multi-store basket.")
    public BasketOptimizationDto optimize(@PathVariable long id, @RequestBody(required = false) OptimizeRequest req) {
        return service.optimize(id, req == null ? null : req.maxStores());
    }
}
