package am.pricecompare.csv;

import am.pricecompare.crawler.DiscoveredProduct;
import am.pricecompare.domain.CrawlError;
import am.pricecompare.domain.CrawlRun;
import am.pricecompare.domain.CrawlStatus;
import am.pricecompare.domain.Store;
import am.pricecompare.ingest.ProductIngestionService;
import am.pricecompare.repository.CrawlErrorRepository;
import am.pricecompare.repository.CrawlRunRepository;
import am.pricecompare.repository.StoreRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports real prices from CSV for stores that cannot be crawled automatically.
 * Required columns: store, externalProductId, name, price, capturedAt. Optional: brand, barcode, currency,
 * available, nameEn, url, originalPrice, category, imageUrl. Rows go through the same normalization, price history
 * and matching pipeline as crawled products; each store in the file gets its own CSV run in crawl_runs.
 */
@Service
public class CsvImportService {

    static final Set<String> REQUIRED = Set.of("store", "externalproductid", "name", "price", "capturedat");
    private static final ZoneId YEREVAN = ZoneId.of("Asia/Yerevan");

    private final StoreRepository storeRepo;
    private final CrawlRunRepository runRepo;
    private final CrawlErrorRepository errorRepo;
    private final ProductIngestionService ingestion;

    public CsvImportService(StoreRepository storeRepo, CrawlRunRepository runRepo, CrawlErrorRepository errorRepo,
                            ProductIngestionService ingestion) {
        this.storeRepo = storeRepo;
        this.runRepo = runRepo;
        this.errorRepo = errorRepo;
        this.ingestion = ingestion;
    }

    public record RowError(long line, String message) {
    }

    public record ImportResult(int rows, int imported, int pricesChanged, List<Long> runIds, List<RowError> errors) {
    }

    /** Parsed CSV row (exposed for tests). */
    record Row(long line, String store, DiscoveredProduct product) {
    }

    public ImportResult importCsv(Reader reader, String fileName) throws IOException {
        List<RowError> errors = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        int total = 0;
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true)
                .setIgnoreEmptyLines(true).setIgnoreSurroundingSpaces(true).build().parse(reader)) {
            Map<String, String> headers = new HashMap<>();
            parser.getHeaderNames().forEach(h -> headers.put(h.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("﻿", ""), h));
            for (String req : REQUIRED) {
                if (!headers.containsKey(req)) {
                    throw new IllegalArgumentException("CSV is missing required column '" + req + "'. Required: " + REQUIRED);
                }
            }
            for (CSVRecord rec : parser) {
                total++;
                try {
                    rows.add(parseRow(rec, headers, fileName));
                } catch (RuntimeException e) {
                    errors.add(new RowError(rec.getRecordNumber() + 1, e.getMessage()));
                }
            }
        }

        Map<String, List<Row>> byStore = new LinkedHashMap<>();
        rows.forEach(r -> byStore.computeIfAbsent(r.store(), k -> new ArrayList<>()).add(r));
        List<Long> runIds = new ArrayList<>();
        int imported = 0;
        int changed = 0;
        for (Map.Entry<String, List<Row>> e : byStore.entrySet()) {
            Store store = resolveStore(e.getKey());
            if (store == null) {
                e.getValue().forEach(r -> errors.add(new RowError(r.line(), "Unknown store '" + e.getKey() + "'")));
                continue;
            }
            CrawlRun run = new CrawlRun();
            run.setStore(store);
            run.setRunType("CSV");
            run.setStatus(CrawlStatus.RUNNING);
            run = runRepo.save(run);
            runIds.add(run.getId());
            int ok = 0;
            int failed = 0;
            for (Row r : e.getValue()) {
                try {
                    ProductIngestionService.Result res;
                    synchronized (ProductIngestionService.LOCK) {
                        res = ingestion.ingest(store.getId(), run.getId(), r.product(), true);
                    }
                    ok++;
                    if (res.priceChanged()) {
                        changed++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    errors.add(new RowError(r.line(), ex.getMessage()));
                    CrawlError err = new CrawlError();
                    err.setCrawlRunId(run.getId());
                    err.setExternalId(r.product().externalId());
                    err.setUrl(fileName + ":" + r.line());
                    err.setMessage(String.valueOf(ex.getMessage()));
                    errorRepo.save(err);
                }
            }
            imported += ok;
            run.setProductsDiscovered(ok);
            run.setProductsUpdated(ok);
            run.setPricesUpdated(changed);
            run.setErrors(failed);
            run.setFinishedAt(Instant.now());
            run.setStatus(failed == 0 ? CrawlStatus.SUCCESS : ok > 0 ? CrawlStatus.PARTIAL : CrawlStatus.FAILED);
            runRepo.save(run);
        }
        return new ImportResult(total, imported, changed, runIds, errors);
    }

    private Store resolveStore(String key) {
        return storeRepo.findBySlug(key.toLowerCase(Locale.ROOT).trim())
                .or(() -> storeRepo.findAll().stream().filter(s -> s.getName().equalsIgnoreCase(key.trim())).findFirst())
                .orElse(null);
    }

    static Row parseRow(CSVRecord rec, Map<String, String> headers, String fileName) {
        String store = required(rec, headers, "store");
        String externalId = required(rec, headers, "externalproductid");
        String name = required(rec, headers, "name");
        BigDecimal price = decimal(required(rec, headers, "price"), "price");
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        Instant capturedAt = parseInstant(required(rec, headers, "capturedat"));
        if (capturedAt.isAfter(Instant.now().plusSeconds(3600))) {
            throw new IllegalArgumentException("capturedAt is in the future");
        }
        String currency = optional(rec, headers, "currency");
        if (currency != null && !currency.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }
        String availableRaw = optional(rec, headers, "available");
        boolean available = availableRaw == null || Set.of("true", "1", "yes", "y", "այո").contains(availableRaw.toLowerCase(Locale.ROOT));
        String original = optional(rec, headers, "originalprice");
        Map<String, Object> raw = new LinkedHashMap<>();
        rec.toMap().forEach(raw::put);
        raw.put("source", "csv:" + fileName);
        DiscoveredProduct p = new DiscoveredProduct(externalId, null, optional(rec, headers, "barcode"), name,
                optional(rec, headers, "nameen"), optional(rec, headers, "brand"), optional(rec, headers, "category"),
                optional(rec, headers, "url"), optional(rec, headers, "imageurl"), price,
                original == null ? null : decimal(original, "originalPrice"), null, null,
                currency == null ? "AMD" : currency.toUpperCase(Locale.ROOT), available, false, null, null, false, capturedAt, raw);
        return new Row(rec.getRecordNumber() + 1, store, p);
    }

    static Instant parseInstant(String s) {
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ignored) {
            // try other formats
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            // try other formats
        }
        try {
            return LocalDateTime.parse(s.replace(' ', 'T')).atZone(YEREVAN).toInstant();
        } catch (DateTimeParseException ignored) {
            // try other formats
        }
        try {
            return LocalDate.parse(s).atStartOfDay(YEREVAN).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("capturedAt '" + s + "' is not ISO-8601 (e.g. 2026-10-01T10:00:00+04:00)");
        }
    }

    private static String required(CSVRecord rec, Map<String, String> headers, String key) {
        String v = optional(rec, headers, key);
        if (v == null) {
            throw new IllegalArgumentException("missing value for '" + key + "'");
        }
        return v;
    }

    private static String optional(CSVRecord rec, Map<String, String> headers, String key) {
        String h = headers.get(key);
        if (h == null || !rec.isMapped(h) || !rec.isSet(h)) {
            return null;
        }
        String v = rec.get(h);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static BigDecimal decimal(String s, String field) {
        try {
            return new BigDecimal(s.replace(" ", "").replace(" ", "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " '" + s + "' is not a number");
        }
    }
}
