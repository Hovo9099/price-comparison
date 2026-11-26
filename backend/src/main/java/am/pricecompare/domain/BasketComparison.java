package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A stored shopping-list comparison (preferences and full result), for auditing what was recommended. */
@Entity
@Table(name = "basket_comparisons")
@Getter
@Setter
public class BasketComparison {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long shoppingListId;
    @JdbcTypeCode(SqlTypes.JSON)
    private String preferences;
    @JdbcTypeCode(SqlTypes.JSON)
    private String result;
    private Instant createdAt = Instant.now();
}
