package am.pricecompare.web.admin;

import am.pricecompare.domain.Store;
import am.pricecompare.domain.StoreLocation;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.web.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@RestController
@RequestMapping("/api/admin/stores/{storeId}/locations")
@Tag(name = "Admin: store locations", description = "Branches entered by hand for stores without a crawled branch list")
public class AdminLocationController {

    private final StoreRepository storeRepo;
    private final EntityManager em;

    public AdminLocationController(StoreRepository storeRepo, EntityManager em) {
        this.storeRepo = storeRepo;
        this.em = em;
    }

    public record LocationDto(Long id, String name, String address, BigDecimal latitude, BigDecimal longitude, boolean active,
                              boolean manual, String source) {
        static LocationDto of(StoreLocation l) {
            return new LocationDto(l.getId(), l.getName(), l.getAddress(), l.getLatitude(), l.getLongitude(), l.isActive(),
                    l.isManual(), l.getSource());
        }
    }

    public record LocationRequest(@NotBlank @Size(max = 300) String name, @Size(max = 500) String address,
                                  @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                  @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                                  @NotBlank @Size(max = 500) String source) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<LocationDto> list(@PathVariable long storeId) {
        store(storeId);
        return em.createQuery("select l from StoreLocation l where l.store.id = :id order by l.active desc, l.name", StoreLocation.class)
                .setParameter("id", storeId).getResultList().stream().map(LocationDto::of).toList();
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Add a branch (source = the store page listing it)")
    public LocationDto add(@PathVariable long storeId, @Valid @RequestBody LocationRequest r) {
        StoreLocation l = new StoreLocation();
        l.setStore(store(storeId));
        l.setManual(true);
        apply(l, r);
        em.persist(l);
        return LocationDto.of(l);
    }

    @PutMapping("/{id}")
    @Transactional
    public LocationDto update(@PathVariable long storeId, @PathVariable long id, @Valid @RequestBody LocationRequest r) {
        StoreLocation l = manual(storeId, id);
        apply(l, r);
        return LocationDto.of(l);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Deactivate a manual branch")
    public ResponseEntity<Void> delete(@PathVariable long storeId, @PathVariable long id) {
        manual(storeId, id).setActive(false);
        return ResponseEntity.noContent().build();
    }

    private static void apply(StoreLocation l, LocationRequest r) {
        l.setName(r.name().trim());
        l.setAddress(r.address() == null || r.address().isBlank() ? null : r.address().trim());
        l.setLatitude(r.latitude().setScale(6, RoundingMode.HALF_UP));
        l.setLongitude(r.longitude().setScale(6, RoundingMode.HALF_UP));
        l.setSource(r.source().trim());
        l.setActive(true);
    }

    private Store store(long id) {
        return storeRepo.findById(id).orElseThrow(() -> new NotFoundException("Store " + id + " not found"));
    }

    private StoreLocation manual(long storeId, long id) {
        StoreLocation l = em.find(StoreLocation.class, id);
        if (l == null || !l.getStore().getId().equals(storeId)) {
            throw new NotFoundException("Location " + id + " not found");
        }
        if (!l.isManual()) {
            throw new IllegalStateException("Crawled locations are maintained by the crawler");
        }
        return l;
    }
}
