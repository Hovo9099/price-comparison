package am.pricecompare.alert;

import am.pricecompare.crawler.CrawlFinishedEvent;
import am.pricecompare.crawler.CrawlerProperties;
import am.pricecompare.crawler.CrawlerService;
import am.pricecompare.crawler.SupermarketProvider;
import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.domain.Store;
import am.pricecompare.repository.CrawlRunRepository;
import am.pricecompare.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrawlAlertsTest {

    AlertService alerts = mock(AlertService.class);
    CrawlRunRepository runs = mock(CrawlRunRepository.class);
    StoreRepository stores = mock(StoreRepository.class);
    CrawlerService crawler = mock(CrawlerService.class);
    CrawlerProperties props = new CrawlerProperties();
    CrawlAlerts crawlAlerts;

    @BeforeEach
    void setUp() {
        crawlAlerts = new CrawlAlerts(alerts, runs, stores, crawler, props, 36);
    }

    static CrawlRun run(long id, int discovered, Instant finishedAt) {
        CrawlRun r = new CrawlRun();
        r.setId(id);
        r.setProductsDiscovered(discovered);
        r.setFinishedAt(finishedAt);
        return r;
    }

    CrawlFinishedEvent event(long runId, CrawlStatus status, int discovered, int errors, int max, String message) {
        return new CrawlFinishedEvent(1, "TestMart", runId, status, discovered, 10, errors, max, message);
    }

    @Test
    void failedRunAlerts() {
        crawlAlerts.onCrawlFinished(event(5, CrawlStatus.FAILED, 0, 0, 0, "java.io.IOException: 403"));
        verify(alerts).alert(argThat(s -> s.contains("TestMart") && s.contains("FAILED") && s.contains("403")));
    }

    @Test
    void shrunkCatalogAlertsButNormalRunDoesNot() {
        when(runs.findCompleteCrawls(eq(1L), any())).thenReturn(List.of(run(9, 300, Instant.now()), run(8, 1000, Instant.now())));
        crawlAlerts.onCrawlFinished(event(9, CrawlStatus.SUCCESS, 300, 0, 0, null));
        verify(alerts).alert(argThat(s -> s.contains("300") && s.contains("1000") && s.contains("may have changed")));

        reset(alerts);
        when(runs.findCompleteCrawls(eq(1L), any())).thenReturn(List.of(run(10, 950, Instant.now()), run(9, 1000, Instant.now())));
        crawlAlerts.onCrawlFinished(event(10, CrawlStatus.SUCCESS, 950, 0, 0, null));
        // a limited dev run is never compared
        crawlAlerts.onCrawlFinished(event(11, CrawlStatus.SUCCESS, 100, 0, 100, null));
        verifyNoInteractions(alerts);
    }

    @Test
    void manyErrorsAlert() {
        crawlAlerts.onCrawlFinished(event(5, CrawlStatus.PARTIAL, 1000, 120, 100, null));
        verify(alerts).alert(argThat(s -> s.contains("120 errors")));
    }

    @Test
    void staleStoreAlertsOncePerDayOnlyWithScheduledCrawls() {
        SupermarketProvider p = mock(SupermarketProvider.class);
        when(p.getStoreCode()).thenReturn("sas");
        when(p.isAvailable()).thenReturn(true);
        when(crawler.providers()).thenReturn(List.of(p));
        Store s = new Store();
        s.setId(1L);
        s.setName("TestMart");
        when(stores.findBySlug("sas")).thenReturn(Optional.of(s));
        when(runs.findFirstByStoreIdAndStatusInOrderByStartedAtDesc(eq(1L), any()))
                .thenReturn(Optional.of(run(1, 100, Instant.now().minus(50, ChronoUnit.HOURS))));

        crawlAlerts.checkStale(); // no schedule configured
        verifyNoInteractions(alerts);

        props.setScheduleCron("0 0 5 * * *");
        crawlAlerts.checkStale();
        crawlAlerts.checkStale();
        verify(alerts, times(1)).alert(argThat(m -> m.contains("TestMart") && m.contains("50 h")));
    }
}
