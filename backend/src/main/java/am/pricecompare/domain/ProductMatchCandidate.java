package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_match_candidates")
@Getter
@Setter
public class ProductMatchCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private StoreProduct storeProduct;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CanonicalProduct canonicalProduct;
    private BigDecimal confidence;
    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.PENDING;
    private String reason;
    private Instant createdAt = Instant.now();
    private Instant reviewedAt;
}
