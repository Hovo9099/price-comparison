package am.pricecompare.crawler;

import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command line crawling, e.g.
 * {@code java -jar backend.jar --spring.main.web-application-type=none --crawler.run=sas --crawler.max-products=100}.
 * {@code --crawler.run=all} crawls every available store sequentially. The JVM exits when done unless
 * {@code --crawler.exit-after-run=false}. {@code --matching.rebuild=true} re-runs product matching for all stored products.
 */
@Component
public class CrawlerCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlerCommandLineRunner.class);

    private final CrawlerService crawlerService;
    private final ApplicationContext context;
    private final String run;
    private final boolean exitAfterRun;
    private final boolean parallel;
    private final am.pricecompare.matching.MatchingRebuildService rebuildService;
    private final String rebuild;
    private final boolean migrateOnly;

    public CrawlerCommandLineRunner(CrawlerService crawlerService, ApplicationContext context,
                                    @Value("${crawler.run:}") String run,
                                    @Value("${crawler.exit-after-run:true}") boolean exitAfterRun,
                                    @Value("${crawler.parallel:false}") boolean parallel,
                                    am.pricecompare.matching.MatchingRebuildService rebuildService,
                                    @Value("${matching.rebuild:}") String rebuild,
                                    @Value("${app.migrate-only:false}") boolean migrateOnly) {
        this.crawlerService = crawlerService;
        this.context = context;
        this.run = run;
        this.exitAfterRun = exitAfterRun;
        this.parallel = parallel;
        this.rebuildService = rebuildService;
        this.rebuild = rebuild;
        this.migrateOnly = migrateOnly;
    }

    @Override
    public void run(String... args) {
        if (migrateOnly) {
            // Flyway has already migrated the database during startup.
            log.info("== migrations applied, exiting (--app.migrate-only) ==");
            System.exit(SpringApplication.exit(context, () -> 0));
            return;
        }
        if (rebuild != null && !rebuild.isBlank()) {
            // --matching.rebuild=yerevan-city,sas,carrefour (store priority) or --matching.rebuild=true
            List<String> priority = rebuild.equalsIgnoreCase("true") ? List.of("yerevan-city", "sas", "carrefour")
                    : Arrays.stream(rebuild.split(",")).map(String::trim).toList();
            log.info("== rematch: {} ==", rebuildService.rebuild(priority));
            if (run == null || run.isBlank()) {
                if (exitAfterRun) {
                    System.exit(SpringApplication.exit(context, () -> 0));
                }
                return;
            }
        }
        if (run == null || run.isBlank()) {
            return;
        }
        List<String> codes = new ArrayList<>();
        if (run.equalsIgnoreCase("all")) {
            crawlerService.providers().stream().filter(SupermarketProvider::isAvailable)
                    .forEach(p -> codes.add(p.getStoreCode()));
        } else {
            codes.addAll(Arrays.stream(run.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        }
        boolean allOk = true;
        if (parallel) {
            allOk = runParallel(codes);
            codes.clear();
        }
        for (String code : codes) {
            try {
                CrawlRun r = crawlerService.runSync(code, null);
                log.info("== {}: {} | products={} pricesChanged={} pages={} errors={} ==", code, r.getStatus(),
                        r.getProductsDiscovered(), r.getPricesUpdated(), r.getPagesVisited(), r.getErrors());
                allOk &= r.getStatus() == CrawlStatus.SUCCESS || r.getStatus() == CrawlStatus.PARTIAL;
            } catch (RuntimeException e) {
                log.error("Crawl of {} could not start: {}", code, e.getMessage());
                allOk = false;
            }
        }
        if (exitAfterRun) {
            int code = allOk ? 0 : 1;
            System.exit(SpringApplication.exit(context, () -> code));
        }
    }

    /** Different stores are different hosts, so they can be crawled concurrently (each host stays rate-limited). */
    private boolean runParallel(List<String> codes) {
        java.util.Map<String, Long> runs = new java.util.LinkedHashMap<>();
        for (String code : codes) {
            try {
                runs.put(code, crawlerService.startAsync(code, null));
            } catch (RuntimeException e) {
                log.error("Crawl of {} could not start: {}", code, e.getMessage());
            }
        }
        while (runs.keySet().stream().anyMatch(crawlerService::isRunning)) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !runs.isEmpty() && runs.size() == codes.size();
    }
}
