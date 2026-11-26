package am.pricecompare.repository;

import am.pricecompare.domain.ProductAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductAliasRepository extends JpaRepository<ProductAlias, Long> {
    boolean existsByCanonicalProductIdAndNormalizedAlias(Long canonicalProductId, String normalizedAlias);

    List<ProductAlias> findByCanonicalProductId(Long canonicalProductId);
}
