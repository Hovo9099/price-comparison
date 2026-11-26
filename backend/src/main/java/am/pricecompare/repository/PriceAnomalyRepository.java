package am.pricecompare.repository;

import am.pricecompare.domain.PriceAnomaly;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface PriceAnomalyRepository extends JpaRepository<PriceAnomaly, Long> {

    @Query(value = "select a from PriceAnomaly a join fetch a.storeProduct sp join fetch sp.store join fetch a.price "
            + "where a.status = :status order by a.detectedAt desc",
            countQuery = "select count(a) from PriceAnomaly a where a.status = :status")
    Page<PriceAnomaly> findByStatus(@Param("status") PriceAnomaly.Status status, Pageable pageable);

    long countByStatus(PriceAnomaly.Status status);

    boolean existsByPriceId(long priceId);

    /** Pending anomalies whose price period has been replaced by a newer observation. */
    @Modifying
    @Query("update PriceAnomaly a set a.status = am.pricecompare.domain.PriceAnomaly.Status.SUPERSEDED, a.reviewedAt = :now "
            + "where a.storeProduct.id = :spId and a.status = am.pricecompare.domain.PriceAnomaly.Status.PENDING "
            + "and a.price.validTo is not null")
    int supersede(@Param("spId") long storeProductId, @Param("now") Instant now);
}
