package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_aliases")
@Getter
@Setter
public class ProductAlias {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CanonicalProduct canonicalProduct;
    private String alias;
    private String normalizedAlias;
    private String source;
}
