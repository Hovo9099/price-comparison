package am.pricecompare.repository;

import am.pricecompare.domain.MatchStatus;
import am.pricecompare.domain.ProductMatchCandidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductMatchCandidateRepository extends JpaRepository<ProductMatchCandidate, Long> {

    @Query(value = "select c from ProductMatchCandidate c join fetch c.storeProduct sp join fetch sp.store "
            + "join fetch c.canonicalProduct where c.status = :status order by c.confidence desc",
            countQuery = "select count(c) from ProductMatchCandidate c where c.status = :status")
    Page<ProductMatchCandidate> findByStatusWithDetails(@Param("status") MatchStatus status, Pageable pageable);

    Optional<ProductMatchCandidate> findByStoreProductIdAndCanonicalProductId(Long storeProductId, Long canonicalProductId);

    List<ProductMatchCandidate> findByStoreProductIdAndStatus(Long storeProductId, MatchStatus status);

    long countByStatus(MatchStatus status);
}
