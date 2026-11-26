package am.pricecompare.crawler;

import am.pricecompare.domain.CrawlError;
import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.domain.Store;
import am.pricecompare.ingest.ProductIngestionService;
import am.pricecompare.repository.CrawlErrorRepository;
import am.pricecompare.repository.CrawlRunRepository;
import am.pricecompare.repository.StoreProductRepository;
import am.pricecompare.repository.StoreRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs supermarket providers, feeds discovered products into ingestion, and records crawl statistics and
 * errors. Each store runs on its own thread; a failing store never affects the others.
 */
@Service
public class CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);
    private static final int ERROR_MESSAGE_MAX = 4000;

    private final Map<String, SupermarketProvider> providers;
    private final StoreRepository storeRepo;
    private final CrawlRunRepository runRepo;
    private final CrawlErrorRepository errorRepo;
    private final StoreProductRepository storeProductRepo;
    private final ProductIngestionService ingestion;
    private final CrawlerProperties props;
    private final TransactionTemplate tx;
    private final LocationSyncService locationSync;
    private final ApplicationEventPublisher events;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "crawler");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Long> runningByStore = new ConcurrentHashMap<>();
    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public CrawlerService(List<SupermarketProvider> providerList, StoreRepository storeRepo, CrawlRunRepository runRepo,
                          CrawlErrorRepository errorRepo, StoreProductRepository storeProductRepo,
                          ProductIngestionService ingestion, CrawlerProperties props, TransactionTemplate tx,
                          LocationSyncService locationSync, ApplicationEventPublisher events) {
        this.providers = providerList.stream().collect(Collectors.toMap(SupermarketProvider::getStoreCode, Function.identity()));
        this.storeRepo = storeRepo;
        this.runRepo = runRepo;
        this.errorRepo = errorRepo;
        this.storeProductRepo = storeProductRepo;
        this.ingestion = ingestion;
        this.props = props;
        this.tx = tx;
        this.locationSync = locationSync;
        this.events = events;
    }

    public List<SupermarketProvider> providers() {
        return providers.values().stream().sorted((a, b) -> a.getStoreCode().compareTo(b.getStoreCode())).toList();
    }

    public SupermarketProvider provider(String code) {
        return providers.get(code);
    }

    public boolean isRunning(String storeCode) {
        return runningByStore.containsKey(storeCode);
    }

    /**
     * RUNNING crawls whose heartbeat stopped (the process running them died) are marked FAILED. Crawls of other live
     * processes (e.g. a CLI import next to the API server) keep their heartbeat fresh and are left alone.
     */
    @EventListener(ApplicationReadyEvent.class)
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 120_000, initialDelay = 120_000)
    public void failOrphanedRuns() {
        Instant cutoff = Instant.now().minusSeconds(300);
        tx.executeWithoutResult(s -> runRepo.findByStatus(CrawlStatus.RUNNING).forEach(r -> {
            Instant beat = r.getHeartbeatAt() != null ? r.getHeartbeatAt() : r.getStartedAt();
            if (!runningByStore.containsValue(r.getId()) && beat.isBefore(cutoff)) {
                r.setStatus(CrawlStatus.FAILED);
                r.setFinishedAt(Instant.now());
                r.setErrorMessage("Interrupted: application stopped while the crawl was running");
                runRepo.save(r);
            }
        }));
    }

    /** Starts a crawl in the background. @return the crawl run id */
    public long startAsync(String storeCode, Integer maxProducts) {
        CrawlRun run = createRun(storeCode, maxProducts);
        executor.submit(() -> execute(storeCode, run.getId(), maxProducts));
        return run.getId();
    }

    /** Runs a crawl on the calling thread (CLI usage). */
    public CrawlRun runSync(String storeCode, Integer maxProducts) {
        CrawlRun run = createRun(storeCode, maxProducts);
        execute(storeCode, run.getId(), maxProducts);
        return runRepo.findById(run.getId()).orElseThrow();
    }

    public boolean cancel(long runId) {
        AtomicBoolean flag = cancelFlags.get(runId);
        if (flag == null) {
            return false;
        }
        flag.set(true);
        return true;
    }

    private synchronized CrawlRun createRun(String storeCode, Integer maxProducts) {
        SupermarketProvider provider = providers.get(storeCode);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown store code: " + storeCode + ". Known: " + providers.keySet());
        }
        if (runningByStore.containsKey(storeCode)) {
            throw new IllegalStateException("A crawl for " + storeCode + " is already running (run " + runningByStore.get(storeCode) + ")");
        }
        Store store = storeRepo.findBySlug(storeCode)
                .orElseThrow(() -> new IllegalStateException("Store " + storeCode + " missing in database"));
        runRepo.findFirstByStoreIdOrderByStartedAtDesc(store.getId())
                .filter(r -> r.getStatus() == CrawlStatus.RUNNING && r.getHeartbeatAt() != null
                        && r.getHeartbeatAt().isAfter(Instant.now().minusSeconds(120)))
                .ifPresent(r -> {
                    throw new IllegalStateException("A crawl for " + storeCode + " is already running in another process (run " + r.getId() + ")");
                });
        CrawlRun run = new CrawlRun();
        run.setStore(store);
        run.setRunType("CRAWL");
        run.setMaxProducts(effectiveMax(maxProducts));
        run.setStatus(CrawlStatus.RUNNING);
        run.setHeartbeatAt(Instant.now());
        run = runRepo.save(run);
        runningByStore.put(storeCode, run.getId());
        cancelFlags.put(run.getId(), new AtomicBoolean(false));
        return run;
    }

    private int effectiveMax(Integer maxProducts) {
        return maxProducts != null ? Math.max(0, maxProducts) : props.getMaxProducts();
    }

    private void execute(String storeCode, long runId, Integer maxProducts) {
        SupermarketProvider provider = providers.get(storeCode);
        CrawlerProperties.ProviderSettings settings = props.provider(storeCode);
        int max = effectiveMax(maxProducts);
        AtomicBoolean cancelled = cancelFlags.get(runId);
        Instant startedAt = Instant.now();
        RunContext ctx = null;
        try {
            if (!provider.isAvailable() || !settings.isEnabled()) {
                String reason = !settings.isEnabled() ? "Disabled by configuration" : provider.unavailableReason();
                finish(runId, CrawlStatus.UNAVAILABLE, reason, null);
                log.info("Store {} unavailable: {}", storeCode, reason);
                return;
            }
            Store store = storeRepo.findBySlug(storeCode).orElseThrow();
            Set<String> withDetails = new HashSet<>(storeProductRepo.findExternalIdsWithDetails(store.getId()));
            ctx = new RunContext(store.getId(), runId, max, settings, withDetails, cancelled);
            log.info("Crawl {} started for {} (max products: {})", runId, storeCode, max == 0 ? "unlimited" : max);
            try {
                int locations = locationSync.sync(store.getId(), provider.discoverLocations());
                if (locations > 0) {
                    log.info("Crawl {}: {} store locations synced", runId, locations);
                }
            } catch (RuntimeException e) {
                ctx.error(null, null, "Store locations: " + e.getMessage());
            }
            provider.discoverProducts(ctx);
            ctx.flushStats();

            boolean complete = max == 0 && !cancelled.get();
            CrawlStatus status = ctx.discovered.get() == 0 ? CrawlStatus.FAILED
                    : ctx.errors.get() == 0 ? CrawlStatus.SUCCESS : CrawlStatus.PARTIAL;
            if (complete && status != CrawlStatus.FAILED && props.isDeactivateMissing()
                    && ctx.errors.get() < Math.max(50, ctx.discovered.get() / 20)) {
                int gone = deactivateMissing(store.getId(), runId, startedAt);
                log.info("Crawl {}: {} products no longer listed were marked unavailable", runId, gone);
            }
            String msg = cancelled.get() ? "Cancelled before completion" : ctx.discovered.get() == 0 ? "No products discovered" : null;
            finish(runId, status, msg, ctx);
            log.info("Crawl {} for {} finished: {} (discovered={}, updated={}, prices={}, errors={})", runId, storeCode,
                    status, ctx.discovered.get(), ctx.updated.get(), ctx.pricesUpdated.get(), ctx.errors.get());
            publishFinished(new CrawlFinishedEvent(store.getId(), store.getName(), runId, status, ctx.discovered.get(),
                    ctx.pricesUpdated.get(), ctx.errors.get(), max, msg));
        } catch (RuntimeException e) {
            log.error("Crawl {} for {} failed", runId, storeCode, e);
            if (ctx != null) {
                ctx.flushStats();
            }
            finish(runId, CrawlStatus.FAILED, e.toString(), ctx);
            final RunContext failed = ctx;
            storeRepo.findBySlug(storeCode).ifPresent(st -> publishFinished(new CrawlFinishedEvent(st.getId(), st.getName(), runId,
                    CrawlStatus.FAILED, failed == null ? 0 : failed.discovered.get(), failed == null ? 0 : failed.pricesUpdated.get(),
                    failed == null ? 0 : failed.errors.get(), max, e.toString())));
        } finally {
            runningByStore.remove(storeCode, runId);
            cancelFlags.remove(runId);
        }
    }

    /** Listeners (e.g. price-watch notifications) must never turn a finished crawl into a failed one. */
    private void publishFinished(CrawlFinishedEvent event) {
        try {
            events.publishEvent(event);
        } catch (RuntimeException e) {
            log.warn("Crawl {}: post-crawl listener failed: {}", event.runId(), e.toString());
        }
    }

    private int deactivateMissing(long storeId, long runId, Instant since) {
        int count = 0;
        for (Long id : storeProductRepo.findIdsNotSeenSince(storeId, since)) {
            try {
                boolean changed;
                synchronized (ProductIngestionService.LOCK) {
                    changed = ingestion.markUnavailable(id, runId);
                }
                if (changed) {
                    count++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not mark store product {} unavailable: {}", id, e.toString());
            }
        }
        return count;
    }

    private void finish(long runId, CrawlStatus status, String message, RunContext ctx) {
        tx.executeWithoutResult(s -> runRepo.findById(runId).ifPresent(r -> {
            if (ctx != null) {
                ctx.copyTo(r);
            }
            r.setStatus(status);
            r.setFinishedAt(Instant.now());
            r.setErrorMessage(message == null ? null : message.length() > ERROR_MESSAGE_MAX ? message.substring(0, ERROR_MESSAGE_MAX) : message);
            runRepo.save(r);
        }));
    }

    /** On shutdown running crawls are cancelled and get a moment to record their final status. */
    @PreDestroy
    void shutdown() {
        cancelFlags.values().forEach(f -> f.set(true));
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Crawl context bound to one run. */
    private final class RunContext implements CrawlContext {
        final long storeId;
        final long runId;
        final int max;
        final CrawlerProperties.ProviderSettings settings;
        final Set<String> withDetails;
        final AtomicBoolean cancelled;
        final Set<String> emitted = ConcurrentHashMap.newKeySet();
        final AtomicInteger pages = new AtomicInteger();
        final AtomicInteger discovered = new AtomicInteger();
        final AtomicInteger updated = new AtomicInteger();
        final AtomicInteger pricesUpdated = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();
        volatile long lastFlush = System.currentTimeMillis();

        RunContext(long storeId, long runId, int max, CrawlerProperties.ProviderSettings settings,
                   Set<String> withDetails, AtomicBoolean cancelled) {
            this.storeId = storeId;
            this.runId = runId;
            this.max = max;
            this.settings = settings;
            this.withDetails = withDetails;
            this.cancelled = cancelled;
        }

        @Override
        public void emit(DiscoveredProduct product) {
            boolean firstTime = emitted.add(product.externalId());
            if (firstTime && max > 0 && discovered.get() >= max) {
                emitted.remove(product.externalId());
                return;
            }
            try {
                ProductIngestionService.Result r;
                synchronized (ProductIngestionService.LOCK) {
                    r = ingestion.ingest(storeId, runId, product, props.isStoreRawSnapshots());
                }
                if (firstTime) {
                    discovered.incrementAndGet();
                    updated.incrementAndGet();
                }
                if (r.priceChanged()) {
                    pricesUpdated.incrementAndGet();
                }
            } catch (RuntimeException e) {
                if (firstTime) {
                    emitted.remove(product.externalId());
                }
                error(product.url(), product.externalId(), "Ingestion failed: " + e);
            }
            maybeFlush();
        }

        @Override
        public void error(String url, String externalId, String message) {
            errors.incrementAndGet();
            log.warn("Crawl {} error [{}] {}: {}", runId, externalId, url, message);
            try {
                CrawlError err = new CrawlError();
                err.setCrawlRunId(runId);
                err.setUrl(url == null ? null : url.length() > 1000 ? url.substring(0, 1000) : url);
                err.setExternalId(externalId);
                err.setMessage(message == null ? "unknown" : message.length() > ERROR_MESSAGE_MAX ? message.substring(0, ERROR_MESSAGE_MAX) : message);
                errorRepo.save(err);
            } catch (RuntimeException e) {
                log.error("Could not persist crawl error", e);
            }
        }

        @Override
        public void pageVisited() {
            pages.incrementAndGet();
            maybeFlush();
        }

        @Override
        public boolean shouldStop() {
            return isCancelled() || (max > 0 && discovered.get() >= max);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() || Thread.currentThread().isInterrupted();
        }

        @Override
        public int maxProducts() {
            return max;
        }

        @Override
        public Set<String> productsWithDetails() {
            return withDetails;
        }

        @Override
        public CrawlerProperties.ProviderSettings settings() {
            return settings;
        }

        void maybeFlush() {
            if (System.currentTimeMillis() - lastFlush > 5000) {
                flushStats();
            }
        }

        synchronized void flushStats() {
            lastFlush = System.currentTimeMillis();
            try {
                tx.executeWithoutResult(s -> runRepo.findById(runId).ifPresent(r -> {
                    copyTo(r);
                    runRepo.save(r);
                }));
            } catch (RuntimeException e) {
                log.warn("Could not update crawl stats: {}", e.toString());
            }
        }

        void copyTo(CrawlRun r) {
            r.setHeartbeatAt(Instant.now());
            r.setPagesVisited(pages.get());
            r.setProductsDiscovered(discovered.get());
            r.setProductsUpdated(updated.get());
            r.setPricesUpdated(pricesUpdated.get());
            r.setErrors(errors.get());
        }
    }
}
