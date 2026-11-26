package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "crawl_runs")
@Getter
@Setter
public class CrawlRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Store store;
    /** CRAWL or CSV. */
    private String runType = "CRAWL";
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
    @Enumerated(EnumType.STRING)
    private CrawlStatus status = CrawlStatus.RUNNING;
    private Integer maxProducts;
    private int pagesVisited;
    private int productsDiscovered;
    private int productsUpdated;
    private int pricesUpdated;
    private int errors;
    private String errorMessage;
    private Instant heartbeatAt;
}
