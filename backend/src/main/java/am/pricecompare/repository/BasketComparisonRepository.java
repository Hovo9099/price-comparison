package am.pricecompare.repository;

import am.pricecompare.domain.BasketComparison;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BasketComparisonRepository extends JpaRepository<BasketComparison, Long> {
}
