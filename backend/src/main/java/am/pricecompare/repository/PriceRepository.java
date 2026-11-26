package am.pricecompare.repository;

import am.pricecompare.domain.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, Long> {

    @Query("select p from Price p where p.storeProduct.id = :storeProductId and p.validTo is null")
    Optional<Price> findCurrent(@Param("storeProductId") Long storeProductId);

    @Query("select p from Price p where p.storeProduct.id in :ids and p.validTo is null")
    List<Price> findCurrentByStoreProductIds(@Param("ids") Collection<Long> ids);

    List<Price> findByStoreProductIdOrderByValidFromAsc(Long storeProductId);

    long countByValidToIsNull();

    /**
     * Current prices that dropped compared to the previous period, opened since {@code since}.
     * Returns [currentPriceId, previousPrice].
     */
    @Query(value = """
            select cur.id, prev.price
            from prices cur
            join prices prev on prev.store_product_id = cur.store_product_id and prev.valid_to = cur.valid_from
            join store_products sp on sp.id = cur.store_product_id
            where cur.valid_to is null and cur.available and prev.available
              and cur.price < prev.price and cur.valid_from >= :since
              and sp.canonical_product_id is not null and sp.active
            order by (prev.price - cur.price) / prev.price desc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> findRecentDrops(@Param("since") Instant since, @Param("limit") int limit);
}
