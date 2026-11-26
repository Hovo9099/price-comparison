package am.pricecompare.service;

import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.repository.CanonicalProductRepository;
import am.pricecompare.web.dto.DealDto;
import am.pricecompare.web.dto.DealDto.Verdict;
import am.pricecompare.web.dto.PageDto;
import am.pricecompare.web.dto.ProductSummaryDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Current discounts, each checked against the listing's own price history so that a "discount" from a price the
 * store never charged is labelled instead of advertised.
 */
@Service
@Transactional(readOnly = true)
public class DealService {

    static final int HISTORY_WINDOW_DAYS = 90;
    static final int MIN_HISTORY_DAYS = 14;
    /** The old price counts as really charged when an observed price reached it within 1 %. */
    static final BigDecimal TOLERANCE = new BigDecimal("0.99");

    private final NamedParameterJdbcTemplate jdbc;
    private final CanonicalProductRepository canonicalRepo;
    private final OfferService offerService;

    public DealService(NamedParameterJdbcTemplate jdbc, CanonicalProductRepository canonicalRepo, OfferService offerService) {
        this.jdbc = jdbc;
        this.canonicalRepo = canonicalRepo;
        this.offerService = offerService;
    }

    record Row(long canonicalId, long storeId, String storeName, String storeSlug, BigDecimal price, BigDecimal originalPrice,
               int pct, Instant since, BigDecimal maxPaid, Instant firstSeen, BigDecimal cheapestOther) {
    }

    public PageDto<DealDto> deals(Long categoryId, Long storeId, boolean includeInflated, int page, int size) {
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fresh", Timestamp.from(now.minus(7, ChronoUnit.DAYS)))
                .addValue("window", Timestamp.from(now.minus(HISTORY_WINDOW_DAYS, ChronoUnit.DAYS)))
                .addValue("categoryId", categoryId)
                .addValue("storeId", storeId);
        List<Row> rows = jdbc.query("""
                select sp.canonical_product_id, s.id store_id, s.name store_name, s.slug store_slug, p.price, p.original_price,
                       round((p.original_price - p.price) * 100 / p.original_price)::int pct, p.valid_from,
                       (select max(h.price) from price_history h
                         where h.store_product_id = sp.id and h.available and h.captured_at >= :window) max_paid,
                       (select min(h.captured_at) from price_history h where h.store_product_id = sp.id) first_seen,
                       (select min(p2.price) from prices p2 join store_products sp2 on sp2.id = p2.store_product_id
                         where sp2.canonical_product_id = sp.canonical_product_id and sp2.id <> sp.id and sp2.active
                           and p2.valid_to is null and p2.available and p2.captured_at >= :fresh) cheapest_other
                from prices p
                join store_products sp on sp.id = p.store_product_id and sp.active
                join stores s on s.id = sp.store_id
                join canonical_products c on c.id = sp.canonical_product_id and c.active
                where p.valid_to is null and p.available and p.captured_at >= :fresh
                  and p.original_price is not null and p.original_price > p.price
                  and (cast(:categoryId as bigint) is null or c.category_id = :categoryId)
                  and (cast(:storeId as bigint) is null or sp.store_id = :storeId)
                """, params, (rs, i) -> new Row(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7), rs.getTimestamp(8).toInstant(), rs.getBigDecimal(9),
                rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant(), rs.getBigDecimal(11)));

        List<Row> kept = rows.stream()
                .filter(r -> r.pct() >= 1)
                .filter(r -> includeInflated || verdict(r, now) != Verdict.INFLATED)
                // real discounts first, then by size of the discount
                .sorted(Comparator.comparing((Row r) -> verdict(r, now) == Verdict.INFLATED)
                        .thenComparing(Row::pct, Comparator.reverseOrder())
                        .thenComparing(Row::canonicalId))
                .toList();
        List<Row> pageRows = kept.stream().skip((long) page * size).limit(size).toList();

        List<Long> ids = pageRows.stream().map(Row::canonicalId).distinct().toList();
        Map<Long, CanonicalProduct> products = canonicalRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(CanonicalProduct::getId, Function.identity()));
        Map<Long, List<OfferService.Offer>> offers = offerService.currentOffers(ids);
        List<DealDto> items = pageRows.stream().map(r -> {
            ProductSummaryDto summary = OfferService.summary(products.get(r.canonicalId()), offers.getOrDefault(r.canonicalId(), List.of()));
            int historyDays = r.firstSeen() == null ? 0 : (int) Duration.between(r.firstSeen(), now).toDays();
            boolean cheapestAnywhere = r.cheapestOther() == null || r.price().compareTo(r.cheapestOther()) <= 0;
            return new DealDto(summary, r.storeId(), r.storeName(), r.storeSlug(), r.price(), r.originalPrice(), r.pct(),
                    verdict(r, now), r.maxPaid(), historyDays, r.since(), cheapestAnywhere);
        }).toList();
        return new PageDto<>(items, page, size, kept.size());
    }

    static Verdict verdict(Row r, Instant now) {
        return verdict(r.originalPrice(), r.maxPaid(), r.firstSeen(), now);
    }

    static Verdict verdict(BigDecimal originalPrice, BigDecimal maxPaid90Days, Instant firstSeen, Instant now) {
        if (maxPaid90Days != null && maxPaid90Days.compareTo(originalPrice.multiply(TOLERANCE)) >= 0) {
            return Verdict.GENUINE;
        }
        if (firstSeen == null || Duration.between(firstSeen, now).toDays() < MIN_HISTORY_DAYS) {
            return Verdict.UNVERIFIED;
        }
        return Verdict.INFLATED;
    }
}
