package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "raw_product_snapshots")
@Getter
@Setter
public class RawProductSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long storeId;
    private Long crawlRunId;
    private String externalProductId;
    private String sourceUrl;
    private String rawName;
    private String rawPrice;
    private String rawOriginalPrice;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> rawData;
    private Instant capturedAt = Instant.now();
}
