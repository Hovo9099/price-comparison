package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "stores")
@Getter
@Setter
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String slug;
    private String websiteUrl;
    private String logoUrl;
    private boolean active = true;
    /** Known delivery terms; null = unknown (never assumed). */
    private java.math.BigDecimal deliveryFee;
    private java.math.BigDecimal freeDeliveryFrom;
    private java.math.BigDecimal minOrderAmount;
    /** Orders below this amount pay {@link #smallOrderFee} instead of the regular fee (e.g. SAS: < 5000 AMD -> 1500 AMD). */
    private java.math.BigDecimal smallOrderBelow;
    private java.math.BigDecimal smallOrderFee;
    private String deliveryTermsSource;
    private Instant deliveryTermsCheckedAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
