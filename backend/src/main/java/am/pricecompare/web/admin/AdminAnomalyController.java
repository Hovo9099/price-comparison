package am.pricecompare.web.admin;

import am.pricecompare.domain.PriceAnomaly;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.repository.PriceAnomalyRepository;
import am.pricecompare.service.PriceAnomalyService;
import am.pricecompare.web.dto.PageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/anomalies")
@Validated
@Tag(name = "Admin: price anomalies", description = "Review suspicious prices (X-Admin-Token)")
public class AdminAnomalyController {

    private final PriceAnomalyRepository repo;
    private final PriceAnomalyService service;

    public AdminAnomalyController(PriceAnomalyRepository repo, PriceAnomalyService service) {
        this.repo = repo;
        this.service = service;
    }

    public record AnomalyDto(Long id, String kind, String status, BigDecimal newPrice, BigDecimal referencePrice,
                             Long storeProductId, String storeName, String productName, String productUrl,
                             Long canonicalProductId, Instant detectedAt, Instant reviewedAt) {
        static AnomalyDto of(PriceAnomaly a) {
            StoreProduct sp = a.getStoreProduct();
            return new AnomalyDto(a.getId(), a.getKind().name(), a.getStatus().name(), a.getNewPrice(), a.getReferencePrice(),
                    sp.getId(), sp.getStore().getName(), sp.getExternalName(), sp.getExternalUrl(),
                    sp.getCanonicalProduct() == null ? null : sp.getCanonicalProduct().getId(), a.getDetectedAt(), a.getReviewedAt());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Anomalies by status (newest first)")
    public PageDto<AnomalyDto> list(@RequestParam(defaultValue = "PENDING") PriceAnomaly.Status status,
                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        Page<PriceAnomaly> p = repo.findByStatus(status, PageRequest.of(page, size));
        return new PageDto<>(p.getContent().stream().map(AnomalyDto::of).toList(), page, size, p.getTotalElements());
    }

    @GetMapping("/counts")
    public Map<String, Long> counts() {
        return Map.of("pending", repo.countByStatus(PriceAnomaly.Status.PENDING));
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    @Operation(summary = "The price is real: use it again")
    public AnomalyDto confirm(@PathVariable long id) {
        return AnomalyDto.of(service.confirm(id));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    @Operation(summary = "The price is wrong: keep it excluded")
    public AnomalyDto reject(@PathVariable long id) {
        return AnomalyDto.of(service.reject(id));
    }
}
