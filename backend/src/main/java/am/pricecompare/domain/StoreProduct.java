package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "store_products")
@Getter
@Setter
public class StoreProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Store store;
    @ManyToOne(fetch = FetchType.LAZY)
    private CanonicalProduct canonicalProduct;
    private String externalProductId;
    private String externalSku;
    private String externalName;
    private String externalNameEn;
    private String externalUrl;
    private String externalCategory;
    private String barcode;
    private String brand;
    private BigDecimal packageSize;
    private String packageUnit;
    private boolean soldByWeight;
    private String imageUrl;
    private Instant detailsFetchedAt;
    private Instant lastSeenAt;
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
