package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_anomalies")
@Getter
@Setter
public class PriceAnomaly {

    public enum Kind { SUDDEN_DROP, SUDDEN_RISE, BELOW_MARKET, ABOVE_MARKET }

    public enum Status { PENDING, CONFIRMED, REJECTED, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Price price;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private StoreProduct storeProduct;
    @Enumerated(EnumType.STRING)
    private Kind kind;
    private BigDecimal newPrice;
    private BigDecimal referencePrice;
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;
    private Instant detectedAt = Instant.now();
    private Instant reviewedAt;
}
