package am.pricecompare.repository;

import am.pricecompare.domain.CrawlError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlErrorRepository extends JpaRepository<CrawlError, Long> {
    Page<CrawlError> findByCrawlRunIdOrderByIdAsc(Long crawlRunId, Pageable pageable);
}
