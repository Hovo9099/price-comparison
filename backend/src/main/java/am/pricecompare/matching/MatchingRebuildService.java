package am.pricecompare.matching;

import am.pricecompare.domain.StoreProduct;
import am.pricecompare.ingest.ProductIngestionService;
import am.pricecompare.repository.StoreProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Re-runs canonical matching for every store product with the current matching rules, without re-crawling.
 * Store products, prices and price history are untouched; canonical products, aliases and match candidates are
 * rebuilt. Shopping-list items are re-pointed to the new canonical product of a store product they referred to.
 * Stores are processed in priority order (stores that publish barcodes first) so barcodes seed the catalog.
 */
@Service
public class MatchingRebuildService {

    private static final Logger log = LoggerFactory.getLogger(MatchingRebuildService.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final StoreProductRepository storeProductRepo;
    private final MatchingService matchingService;

    public MatchingRebuildService(JdbcTemplate jdbc, TransactionTemplate tx, StoreProductRepository storeProductRepo,
                                  MatchingService matchingService) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.storeProductRepo = storeProductRepo;
        this.matchingService = matchingService;
    }

    public record Result(int storeProducts, int canonicalProducts, int autoMatched, int pendingReview, long millis) {
    }

    public Result rebuild(List<String> storePriority) {
        long start = System.currentTimeMillis();
        synchronized (ProductIngestionService.LOCK) {
            // Remember one store product per canonical product used by shopping lists.
            Map<Long, Long> listRefs = new HashMap<>();
            jdbc.query("select distinct i.canonical_product_id, (select min(sp.id) from store_products sp "
                            + "where sp.canonical_product_id = i.canonical_product_id) from shopping_list_items i "
                            + "where i.canonical_product_id is not null",
                    rs -> {
                        long sp = rs.getLong(2);
                        if (!rs.wasNull()) {
                            listRefs.put(rs.getLong(1), sp);
                        }
                    });

            tx.executeWithoutResult(s -> {
                jdbc.update("delete from product_match_candidates");
                jdbc.update("delete from product_aliases");
                jdbc.update("update store_products set canonical_product_id = null");
                jdbc.update("update canonical_products set active = false");
            });
            matchingService.resetIndex();

            StringBuilder order = new StringBuilder("case s.slug ");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < storePriority.size(); i++) {
                order.append("when ? then ").append(i).append(' ');
                params.add(storePriority.get(i));
            }
            order.append("else 999 end");
            List<Long> ids = jdbc.queryForList("select sp.id from store_products sp join stores s on s.id = sp.store_id order by "
                    + order + ", sp.id", Long.class, params.toArray());

            int auto = 0;
            int review = 0;
            int done = 0;
            for (Long id : ids) {
                MatchingService.Outcome o = tx.execute(s -> {
                    StoreProduct sp = storeProductRepo.findById(id).orElseThrow();
                    return matchingService.matchAndLink(sp);
                });
                if (o != null && o.decision() == ProductMatcher.Decision.AUTO_MATCH) {
                    auto++;
                } else if (o != null && o.decision() == ProductMatcher.Decision.REVIEW) {
                    review++;
                }
                if (++done % 2000 == 0) {
                    log.info("Rematch progress: {}/{}", done, ids.size());
                }
            }

            tx.executeWithoutResult(s -> {
                listRefs.forEach((oldCanonical, storeProductId) -> jdbc.update(
                        "update shopping_list_items set canonical_product_id = (select canonical_product_id from store_products where id = ?) "
                                + "where canonical_product_id = ?", storeProductId, oldCanonical));
                jdbc.update("delete from canonical_products c where not c.active and not exists "
                        + "(select 1 from shopping_list_items i where i.canonical_product_id = c.id) and not exists "
                        + "(select 1 from store_products sp where sp.canonical_product_id = c.id)");
            });
            Integer canonical = jdbc.queryForObject("select count(*) from canonical_products where active", Integer.class);
            Result r = new Result(ids.size(), canonical == null ? 0 : canonical, auto, review, System.currentTimeMillis() - start);
            log.info("Rematch finished: {}", r);
            return r;
        }
    }
}
