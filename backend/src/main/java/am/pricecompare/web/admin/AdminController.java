package am.pricecompare.web.admin;

import am.pricecompare.crawler.CrawlerService;
import am.pricecompare.crawler.SupermarketProvider;
import am.pricecompare.csv.CsvImportService;
import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.domain.MatchStatus;
import am.pricecompare.domain.Price;
import am.pricecompare.domain.ProductMatchCandidate;
import am.pricecompare.domain.Store;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.matching.MatchingService;
import am.pricecompare.repository.CanonicalProductRepository;
import am.pricecompare.repository.CategoryRepository;
import am.pricecompare.repository.CrawlErrorRepository;
import am.pricecompare.repository.CrawlRunRepository;
import am.pricecompare.repository.PriceRepository;
import am.pricecompare.repository.ProductMatchCandidateRepository;
import am.pricecompare.repository.StoreProductRepository;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.web.NotFoundException;
import am.pricecompare.web.admin.AdminDtos.*;
import am.pricecompare.web.dto.PageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Crawlers, product mapping, match review, CSV import (requires X-Admin-Token)")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "adminToken")
public class AdminController {

    private final CrawlerService crawlerService;
    private final StoreRepository storeRepo;
    private final CrawlRunRepository runRepo;
    private final CrawlErrorRepository errorRepo;
    private final StoreProductRepository storeProductRepo;
    private final CanonicalProductRepository canonicalRepo;
    private final CategoryRepository categoryRepo;
    private final PriceRepository priceRepo;
    private final ProductMatchCandidateRepository candidateRepo;
    private final MatchingService matchingService;
    private final CsvImportService csvImportService;
    private final am.pricecompare.service.CatalogStatusService catalogStatus;
    private final EntityManager em;

    public AdminController(CrawlerService crawlerService, StoreRepository storeRepo, CrawlRunRepository runRepo,
                           CrawlErrorRepository errorRepo, StoreProductRepository storeProductRepo,
                           CanonicalProductRepository canonicalRepo, CategoryRepository categoryRepo, PriceRepository priceRepo,
                           ProductMatchCandidateRepository candidateRepo, MatchingService matchingService,
                           CsvImportService csvImportService, am.pricecompare.service.CatalogStatusService catalogStatus,
                           EntityManager em) {
        this.crawlerService = crawlerService;
        this.storeRepo = storeRepo;
        this.runRepo = runRepo;
        this.errorRepo = errorRepo;
        this.storeProductRepo = storeProductRepo;
        this.canonicalRepo = canonicalRepo;
        this.categoryRepo = categoryRepo;
        this.priceRepo = priceRepo;
        this.candidateRepo = candidateRepo;
        this.matchingService = matchingService;
        this.csvImportService = csvImportService;
        this.catalogStatus = catalogStatus;
        this.em = em;
    }

    // ---- dashboard & crawlers -------------------------------------------------------------------

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    @Operation(summary = "Crawler dashboard: per-store status, last crawl, product counts")
    public DashboardDto dashboard() {
        List<StoreStatusDto> stores = new ArrayList<>();
        catalogStatus.invalidate();
        for (var c : catalogStatus.all()) {
            Store s = c.store();
            SupermarketProvider p = crawlerService.provider(s.getSlug());
            CrawlRunDto last = c.lastRun() == null ? null : toDto(c.lastRun());
            CrawlRunDto lastOk = runRepo.findFirstByStoreIdAndStatusInOrderByStartedAtDesc(s.getId(),
                    List.of(CrawlStatus.SUCCESS, CrawlStatus.PARTIAL)).map(AdminController::toDto).orElse(null);
            boolean available = p != null && c.implemented() && s.isActive() && providerAvailableCached(p);
            // A crawl may run in another process (e.g. a CLI full import): trust a fresh heartbeat.
            boolean running = crawlerService.isRunning(s.getSlug()) || (c.lastRun() != null
                    && c.lastRun().getStatus() == CrawlStatus.RUNNING && c.lastRun().getHeartbeatAt() != null
                    && c.lastRun().getHeartbeatAt().isAfter(Instant.now().minusSeconds(120)));
            String reason = p == null ? "No crawler exists for this store" : !c.implemented() ? c.reason()
                    : available ? null : p.unavailableReason();
            stores.add(new StoreStatusDto(s.getId(), s.getName(), s.getSlug(), s.getWebsiteUrl(), s.isActive(), available,
                    reason, running, c.listings(), c.activeListings(), last, lastOk, c.implemented(), c.state(), c.reason(),
                    c.pricedListings(), c.lastCompleteRun() == null ? null : toDto(c.lastCompleteRun()), c.lastPriceUpdate()));
        }
        return new DashboardDto(stores, canonicalRepo.count(), storeProductRepo.count(), priceRepo.countByValidToIsNull(),
                candidateRepo.countByStatus(MatchStatus.PENDING), storeProductRepo.countByCanonicalProductIsNull());
    }

    /** Providers that need a network probe (Nor Zovq) are only probed by explicit crawl runs. */
    private static boolean providerAvailableCached(SupermarketProvider p) {
        return !"nor-zovq".equals(p.getStoreCode()) && p.isAvailable();
    }

    @PostMapping("/crawlers/{store}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start a crawl in the background; {store} is the store slug or id; maxProducts=0 imports the full catalog")
    public StartCrawlResponse run(@PathVariable String store, @RequestParam(required = false) Integer maxProducts) {
        String slug = store.chars().allMatch(Character::isDigit)
                ? storeRepo.findById(Long.parseLong(store)).map(Store::getSlug)
                .orElseThrow(() -> new am.pricecompare.web.NotFoundException("Store " + store + " not found"))
                : store;
        // A provider without a parser (Nor Zovq) only re-probes the site and records an UNAVAILABLE run with the reason.
        long runId = crawlerService.startAsync(slug, maxProducts);
        catalogStatus.invalidate();
        return new StartCrawlResponse(runId, slug);
    }

    @PostMapping("/crawl-runs/{id}/cancel")
    @Operation(summary = "Cancel a running crawl")
    public Map<String, Object> cancel(@PathVariable long id) {
        return Map.of("runId", id, "cancelled", crawlerService.cancel(id));
    }

    @GetMapping("/crawl-runs")
    @Transactional(readOnly = true)
    @Operation(summary = "Crawl history")
    public PageDto<CrawlRunDto> runs(@RequestParam(required = false) Long storeId,
                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        Page<CrawlRun> p = runRepo.findHistory(storeId, PageRequest.of(page, Math.min(size, 200)));
        return new PageDto<>(p.map(AdminController::toDto).getContent(), page, size, p.getTotalElements());
    }

    @GetMapping("/crawl-runs/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "One crawl run with its statistics")
    public CrawlRunDto runDetail(@PathVariable long id) {
        return runRepo.findById(id).map(AdminController::toDto).orElseThrow(() -> new NotFoundException("Run " + id + " not found"));
    }

    @GetMapping("/crawl-runs/{id}/errors")
    @Operation(summary = "Errors logged during a crawl")
    public PageDto<CrawlErrorDto> errors(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        var p = errorRepo.findByCrawlRunIdOrderByIdAsc(id, PageRequest.of(page, Math.min(size, 500)));
        return new PageDto<>(p.map(e -> new CrawlErrorDto(e.getId(), e.getUrl(), e.getExternalId(), e.getMessage(), e.getCreatedAt()))
                .getContent(), page, size, p.getTotalElements());
    }

    static CrawlRunDto toDto(CrawlRun r) {
        return new CrawlRunDto(r.getId(), r.getStore().getId(), r.getStore().getName(), r.getStore().getSlug(), r.getRunType(),
                r.getStatus(), r.getStartedAt(), r.getFinishedAt(), r.getMaxProducts(), r.getPagesVisited(),
                r.getProductsDiscovered(), r.getProductsUpdated(), r.getPricesUpdated(), r.getErrors(), r.getErrorMessage());
    }

    // ---- canonical products ---------------------------------------------------------------------

    @GetMapping("/canonical-products")
    @Transactional(readOnly = true)
    @Operation(summary = "Search canonical products by name or barcode")
    public PageDto<CanonicalProductAdminDto> canonicalProducts(@RequestParam(defaultValue = "") String q,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "30") int size) {
        Page<CanonicalProduct> p = canonicalRepo.adminSearch(q.trim(), PageRequest.of(page, Math.min(size, 200), Sort.by("id").descending()));
        Map<Long, Long> counts = storeProductCounts(p.getContent().stream().map(CanonicalProduct::getId).toList());
        return new PageDto<>(p.getContent().stream().map(c -> toDto(c, counts.getOrDefault(c.getId(), 0L).intValue())).toList(),
                page, size, p.getTotalElements());
    }

    @GetMapping("/canonical-products/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "A canonical product with all linked store products and their current prices")
    public Map<String, Object> canonicalProduct(@PathVariable long id) {
        CanonicalProduct c = canonicalRepo.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        List<StoreProduct> sps = storeProductRepo.findByCanonicalProductIdWithStore(id);
        return Map.of("product", toDto(c, sps.size()), "storeProducts", storeProductDtos(sps));
    }

    @PutMapping("/canonical-products/{id}")
    @Transactional
    @Operation(summary = "Edit a canonical product")
    public CanonicalProductAdminDto updateCanonical(@PathVariable long id, @Valid @RequestBody UpdateCanonicalProductRequest req) {
        CanonicalProduct c = canonicalRepo.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        c.setName(req.name().trim());
        c.setNameEn(blankToNull(req.nameEn()));
        c.setBrand(blankToNull(req.brand()));
        c.setBarcode(blankToNull(req.barcode()));
        c.setSizeValue(req.sizeValue());
        c.setSizeUnit(blankToNull(req.sizeUnit()));
        c.setVariant(blankToNull(req.variant()));
        c.setImageUrl(blankToNull(req.imageUrl()));
        if (req.categoryId() != null) {
            c.setCategory(categoryRepo.findById(req.categoryId()).orElseThrow(() -> new NotFoundException("Category not found")));
        }
        if (req.active() != null) {
            c.setActive(req.active());
        }
        c.setNormalizedName(am.pricecompare.normalize.ProductNormalizer.normalize(c.getName()));
        c.setUpdatedAt(Instant.now());
        matchingService.refreshSearchText(c);
        matchingService.reindex(c);
        return toDto(c, storeProductRepo.findByCanonicalProductIdWithStore(id).size());
    }

    private Map<Long, Long> storeProductCounts(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery("select sp.canonicalProduct.id, count(sp) from StoreProduct sp "
                + "where sp.canonicalProduct.id in :ids group by sp.canonicalProduct.id", Object[].class).setParameter("ids", ids).getResultList();
        return rows.stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    static CanonicalProductAdminDto toDto(CanonicalProduct c, int storeProducts) {
        return new CanonicalProductAdminDto(c.getId(), c.getName(), c.getNameEn(), c.getBrand(), c.getBarcode(), c.getSizeValue(),
                c.getSizeUnit(), c.getVariant(), c.isSoldByWeight(), c.getCategory() == null ? null : c.getCategory().getId(),
                c.getImageUrl(), c.isActive(), storeProducts);
    }

    // ---- store products & mapping ----------------------------------------------------------------

    @GetMapping("/store-products")
    @Transactional(readOnly = true)
    @Operation(summary = "Store products, optionally only unmatched ones")
    public PageDto<StoreProductAdminDto> storeProducts(@RequestParam(required = false) Long storeId,
                                                       @RequestParam(defaultValue = "false") boolean unmatched,
                                                       @RequestParam(required = false) String q,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "30") int size) {
        Page<StoreProduct> p = storeProductRepo.adminSearch(storeId, unmatched, q == null || q.isBlank() ? null : q.trim(),
                PageRequest.of(page, Math.min(size, 200), Sort.by("id").descending()));
        return new PageDto<>(storeProductDtos(p.getContent()), page, size, p.getTotalElements());
    }

    @PostMapping("/store-products/{id}/map")
    @Transactional
    @Operation(summary = "Map a store product to a canonical product manually")
    public StoreProductAdminDto map(@PathVariable long id, @Valid @RequestBody MapStoreProductRequest req) {
        StoreProduct sp = storeProductRepo.findById(id).orElseThrow(() -> new NotFoundException("Store product " + id + " not found"));
        CanonicalProduct target = canonicalRepo.findById(req.canonicalProductId())
                .orElseThrow(() -> new NotFoundException("Canonical product " + req.canonicalProductId() + " not found"));
        matchingService.mapManually(sp, target);
        return storeProductDtos(List.of(sp)).get(0);
    }

    private List<StoreProductAdminDto> storeProductDtos(List<StoreProduct> sps) {
        if (sps.isEmpty()) {
            return List.of();
        }
        Map<Long, Price> current = priceRepo.findCurrentByStoreProductIds(sps.stream().map(StoreProduct::getId).toList())
                .stream().collect(Collectors.toMap(p -> p.getStoreProduct().getId(), Function.identity(), (a, b) -> a));
        return sps.stream().map(sp -> {
            Price p = current.get(sp.getId());
            CanonicalProduct c = sp.getCanonicalProduct();
            return new StoreProductAdminDto(sp.getId(), sp.getStore().getId(), sp.getStore().getName(), sp.getExternalProductId(),
                    sp.getExternalSku(), sp.getExternalName(), sp.getExternalNameEn(), sp.getExternalUrl(), sp.getBarcode(),
                    sp.getBrand(), sp.getPackageSize(), sp.getPackageUnit(), sp.isActive(), c == null ? null : c.getId(),
                    c == null ? null : c.getName(), p == null ? null : p.getPrice(), p == null ? null : p.getCapturedAt(),
                    sp.getLastSeenAt());
        }).toList();
    }

    // ---- match review -----------------------------------------------------------------------------

    @GetMapping("/match-candidates")
    @Transactional(readOnly = true)
    @Operation(summary = "Uncertain matches waiting for review (highest confidence first)")
    public PageDto<MatchCandidateDto> candidates(@RequestParam(defaultValue = "PENDING") MatchStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "30") int size) {
        Page<ProductMatchCandidate> p = candidateRepo.findByStatusWithDetails(status, PageRequest.of(page, Math.min(size, 200)));
        List<StoreProductAdminDto> sps = storeProductDtos(p.getContent().stream().map(ProductMatchCandidate::getStoreProduct).toList());
        List<MatchCandidateDto> items = new ArrayList<>();
        for (int i = 0; i < p.getContent().size(); i++) {
            ProductMatchCandidate c = p.getContent().get(i);
            items.add(new MatchCandidateDto(c.getId(), c.getConfidence(), c.getStatus(), c.getReason(), c.getCreatedAt(),
                    sps.get(i), toDto(c.getCanonicalProduct(), 0)));
        }
        return new PageDto<>(items, page, size, p.getTotalElements());
    }

    @PostMapping("/match-candidates/{id}/approve")
    @Transactional
    @Operation(summary = "Approve: the store product joins the candidate canonical product")
    public Map<String, Object> approve(@PathVariable long id) {
        ProductMatchCandidate c = candidateRepo.findById(id).orElseThrow(() -> new NotFoundException("Candidate " + id + " not found"));
        if (c.getStatus() != MatchStatus.PENDING) {
            throw new IllegalStateException("Candidate " + id + " is already " + c.getStatus());
        }
        matchingService.approve(c);
        return Map.of("id", id, "status", c.getStatus());
    }

    @PostMapping("/match-candidates/{id}/reject")
    @Transactional
    @Operation(summary = "Reject: the products stay separate")
    public Map<String, Object> reject(@PathVariable long id) {
        ProductMatchCandidate c = candidateRepo.findById(id).orElseThrow(() -> new NotFoundException("Candidate " + id + " not found"));
        if (c.getStatus() != MatchStatus.PENDING) {
            throw new IllegalStateException("Candidate " + id + " is already " + c.getStatus());
        }
        matchingService.reject(c);
        return Map.of("id", id, "status", c.getStatus());
    }

    // ---- CSV import -------------------------------------------------------------------------------

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import real prices from CSV",
            description = "Columns: store, externalProductId, name, brand, barcode, price, currency, available, capturedAt "
                    + "(optional: nameEn, originalPrice, url, category, imageUrl)")
    public CsvImportService.ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Empty file");
        }
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return csvImportService.importCsv(reader, file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
