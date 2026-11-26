package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shopping_list_items")
@Getter
@Setter
public class ShoppingListItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ShoppingList shoppingList;
    @ManyToOne(fetch = FetchType.LAZY)
    private CanonicalProduct canonicalProduct;
    private String freeText;
    private BigDecimal quantity = BigDecimal.ONE;
    /** Requested product concept from the grocery lexicon (e.g. "milk"), when no specific product was chosen. */
    private String conceptCode;
    /** What the user typed, kept for display and auditing. */
    private String rawText;
    private BigDecimal sizeValue;
    private String sizeUnit;
    private String preferredBrand;
    /** Requested fat / alcohol percentage, e.g. 3.2 for milk 3.2%. */
    private BigDecimal percent;
    private Instant createdAt = Instant.now();
}
