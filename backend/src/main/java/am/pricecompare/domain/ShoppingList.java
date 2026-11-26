package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shopping_lists")
@Getter
@Setter
public class ShoppingList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    /** Random code other devices use to open this list; null = not shared. */
    private String shareCode;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @OneToMany(mappedBy = "shoppingList", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ShoppingListItem> items = new ArrayList<>();
}
