package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "crawl_errors")
@Getter
@Setter
public class CrawlError {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long crawlRunId;
    private String url;
    private String externalId;
    private String message;
    private Instant createdAt = Instant.now();
}
