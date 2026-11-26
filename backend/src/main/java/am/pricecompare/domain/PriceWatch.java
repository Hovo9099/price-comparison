package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_watches")
@Getter
@Setter
public class PriceWatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id")
    private WatchDevice device;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CanonicalProduct canonicalProduct;
    /** Notify when the cheapest price is at or below this; null = notify on any significant drop. */
    private BigDecimal targetPrice;
    private BigDecimal startPrice;
    private BigDecimal referencePrice;
    private BigDecimal lastNotifiedPrice;
    private Instant lastNotifiedAt;
    private Instant createdAt = Instant.now();
}
