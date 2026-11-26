package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A price period. A new row is opened whenever the observed price/availability changes;
 * the previous row is closed by setting {@code validTo}. Rows are never deleted.
 */
@Entity
@Table(name = "prices")
@Getter
@Setter
public class Price {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private StoreProduct storeProduct;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private BigDecimal unitPrice;
    private String unitPriceUnit;
    private String currency = "AMD";
    private boolean available = true;
    /** Flagged by anomaly detection: shown, but never used as cheapest / in baskets / for notifications. */
    private boolean suspect;
    /** Last time this price was observed at the store. */
    private Instant capturedAt;
    private Instant validFrom;
    private Instant validTo;
}
