package am.pricecompare.config;

import am.pricecompare.crawler.CrawlerProperties;
import am.pricecompare.crawler.CrawlerService;
import am.pricecompare.crawler.SupermarketProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/** Schedules crawls of all available stores when {@code crawler.schedule-cron} is set. */
@Configuration
public class CrawlScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);
    private final CrawlerService crawlerService;
    private final CrawlerProperties props;

    public CrawlScheduler(CrawlerService crawlerService, CrawlerProperties props) {
        this.crawlerService = crawlerService;
        this.props = props;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        String cron = props.getScheduleCron();
        if (cron == null || cron.isBlank()) {
            return;
        }
        log.info("Scheduled crawls enabled: {}", cron);
        registrar.addTriggerTask(this::crawlAll, new CronTrigger(cron, java.time.ZoneId.of("Asia/Yerevan")));
    }

    void crawlAll() {
        for (SupermarketProvider p : crawlerService.providers()) {
            if (!crawlerService.isRunning(p.getStoreCode()) && props.provider(p.getStoreCode()).isEnabled()) {
                try {
                    crawlerService.startAsync(p.getStoreCode(), null);
                } catch (RuntimeException e) {
                    log.warn("Scheduled crawl of {} not started: {}", p.getStoreCode(), e.getMessage());
                }
            }
        }
    }
}
