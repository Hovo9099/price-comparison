package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable record of a single price observation. */
@Entity
@Table(name = "price_history")
@Getter
@Setter
public class PriceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private StoreProduct storeProduct;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private boolean available;
    private Instant capturedAt;
    private Long sourceRunId;
}
