package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "store_locations")
@Getter
@Setter
public class StoreLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Store store;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean active = true;
}
