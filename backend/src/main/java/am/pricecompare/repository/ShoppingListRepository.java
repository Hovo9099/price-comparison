package am.pricecompare.repository;

import am.pricecompare.domain.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {

    java.util.Optional<ShoppingList> findByShareCode(String shareCode);
}
