package am.pricecompare.web;

import am.pricecompare.service.DealService;
import am.pricecompare.web.dto.DealDto;
import am.pricecompare.web.dto.PageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deals")
@Validated
@Tag(name = "Deals", description = "Current discounts, checked against each listing's price history")
public class DealController {

    private final DealService deals;

    public DealController(DealService deals) {
        this.deals = deals;
    }

    @GetMapping
    @Operation(summary = "Current discounts (largest first)",
            description = "verdict GENUINE = the store charged the crossed-out price within 90 days; INFLATED = it never did "
                    + "(hidden unless includeInflated=true); UNVERIFIED = under 14 days of history.")
    public PageDto<DealDto> deals(@RequestParam(required = false) Long categoryId,
                                  @RequestParam(required = false) Long storeId,
                                  @RequestParam(defaultValue = "false") boolean includeInflated,
                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return deals.deals(categoryId, storeId, includeInflated, page, size);
    }
}
