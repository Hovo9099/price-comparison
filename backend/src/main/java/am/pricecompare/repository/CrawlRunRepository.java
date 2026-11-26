package am.pricecompare.repository;

import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CrawlRunRepository extends JpaRepository<CrawlRun, Long> {

    Optional<CrawlRun> findFirstByStoreIdOrderByStartedAtDesc(Long storeId);

    Optional<CrawlRun> findFirstByStoreIdAndStatusInOrderByStartedAtDesc(Long storeId, List<CrawlStatus> statuses);

    @Query("select r from CrawlRun r join fetch r.store where (:storeId is null or r.store.id = :storeId) order by r.startedAt desc")
    Page<CrawlRun> findHistory(@Param("storeId") Long storeId, Pageable pageable);

    List<CrawlRun> findByStatus(CrawlStatus status);

    /**
     * Crawls that walked the whole catalog: unlimited, finished without cancellation/interruption (those set an error
     * message) and not FAILED. Only such a run proves that a product missing from the store is really not sold there.
     */
    @Query("select r from CrawlRun r where r.store.id = :storeId and r.runType = 'CRAWL' and r.maxProducts = 0 "
            + "and r.status in (am.pricecompare.domain.CrawlStatus.SUCCESS, am.pricecompare.domain.CrawlStatus.PARTIAL) "
            + "and r.finishedAt is not null and r.errorMessage is null order by r.startedAt desc")
    List<CrawlRun> findCompleteCrawls(@Param("storeId") Long storeId, Pageable pageable);
}
