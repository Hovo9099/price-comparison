package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "canonical_products")
@Getter
@Setter
public class CanonicalProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String nameEn;
    private String normalizedName;
    private String brand;
    private String barcode;
    private BigDecimal sizeValue;
    private String sizeUnit;
    private String variant;
    private boolean soldByWeight;
    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;
    private String imageUrl;
    private String searchText = "";
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
