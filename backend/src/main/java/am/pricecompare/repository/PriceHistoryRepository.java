package am.pricecompare.repository;

import am.pricecompare.domain.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    @Query("select h from PriceHistory h where h.storeProduct.id in :ids and h.capturedAt >= :since order by h.capturedAt asc")
    List<PriceHistory> findForProducts(@Param("ids") Collection<Long> ids, @Param("since") Instant since);

    List<PriceHistory> findByStoreProductIdOrderByCapturedAtAsc(Long storeProductId);

    @Query("select count(h) > 0 from PriceHistory h where h.storeProduct.id = :spId and h.sourceRunId = :runId "
            + "and h.price = :price and h.available = :available")
    boolean existsSameObservationInRun(@Param("spId") Long storeProductId, @Param("runId") Long runId,
                                       @Param("price") java.math.BigDecimal price, @Param("available") boolean available);
}
