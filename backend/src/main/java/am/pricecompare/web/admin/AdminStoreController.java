package am.pricecompare.web.admin;

import am.pricecompare.domain.Store;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.service.CatalogStatusService;
import am.pricecompare.web.NotFoundException;
import am.pricecompare.web.dto.DeliveryTermsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin/stores")
@Tag(name = "Admin: stores", description = "Store delivery terms (X-Admin-Token)")
public class AdminStoreController {

    private final StoreRepository storeRepo;
    private final CatalogStatusService catalogStatus;

    public AdminStoreController(StoreRepository storeRepo, CatalogStatusService catalogStatus) {
        this.storeRepo = storeRepo;
        this.catalogStatus = catalogStatus;
    }

    public record StoreDeliveryDto(Long id, String name, String slug, String websiteUrl, DeliveryTermsDto delivery) {
        static StoreDeliveryDto of(Store s) {
            return new StoreDeliveryDto(s.getId(), s.getName(), s.getSlug(), s.getWebsiteUrl(), DeliveryTermsDto.of(s));
        }
    }

    /** All amounts in AMD; null = unknown. {@code source} (page URL + what it says) is required when any amount is set. */
    public record DeliveryRequest(@DecimalMin("0") BigDecimal fee, @DecimalMin("0") BigDecimal freeFrom,
                                  @DecimalMin("0") BigDecimal minOrder, @DecimalMin("0") BigDecimal smallOrderBelow,
                                  @DecimalMin("0") BigDecimal smallOrderFee, @Size(max = 500) String source) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<StoreDeliveryDto> list() {
        return storeRepo.findAll().stream().sorted(Comparator.comparing(Store::getName)).map(StoreDeliveryDto::of).toList();
    }

    @PutMapping("/{id}/delivery")
    @Operation(summary = "Set the delivery terms published by the store",
            description = "Send all fields null to mark the terms unknown. Sets the 'checked at' date to now.")
    public StoreDeliveryDto setDelivery(@PathVariable long id, @Valid @RequestBody DeliveryRequest r) {
        Store s = storeRepo.findById(id).orElseThrow(() -> new NotFoundException("Store " + id + " not found"));
        boolean anyAmount = r.fee() != null || r.freeFrom() != null || r.minOrder() != null || r.smallOrderBelow() != null
                || r.smallOrderFee() != null;
        String source = r.source() == null || r.source().isBlank() ? null : r.source().trim();
        if (anyAmount && source == null) {
            throw new IllegalArgumentException("source is required: where the store publishes these terms");
        }
        if ((r.smallOrderBelow() == null) != (r.smallOrderFee() == null)) {
            throw new IllegalArgumentException("smallOrderBelow and smallOrderFee go together");
        }
        s.setDeliveryFee(r.fee());
        s.setFreeDeliveryFrom(r.freeFrom());
        s.setMinOrderAmount(r.minOrder());
        s.setSmallOrderBelow(r.smallOrderBelow());
        s.setSmallOrderFee(r.smallOrderFee());
        s.setDeliveryTermsSource(anyAmount ? source : null);
        s.setDeliveryTermsCheckedAt(anyAmount ? Instant.now() : null);
        s.setUpdatedAt(Instant.now());
        Store saved = storeRepo.save(s);
        catalogStatus.invalidate(); // /api/stores caches store rows briefly
        return StoreDeliveryDto.of(saved);
    }
}
