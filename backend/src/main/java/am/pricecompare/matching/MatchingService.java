package am.pricecompare.matching;

import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.domain.Category;
import am.pricecompare.domain.MatchStatus;
import am.pricecompare.domain.ProductAlias;
import am.pricecompare.domain.ProductMatchCandidate;
import am.pricecompare.domain.StoreProduct;
import am.pricecompare.matching.ProductMatcher.Decision;
import am.pricecompare.matching.ProductMatcher.MatchResult;
import am.pricecompare.normalize.ProductNormalizer;
import am.pricecompare.repository.CanonicalProductRepository;
import am.pricecompare.repository.CategoryRepository;
import am.pricecompare.repository.ProductAliasRepository;
import am.pricecompare.repository.ProductMatchCandidateRepository;
import am.pricecompare.repository.StoreProductRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Links store products to canonical products.
 *
 * <ol>
 *   <li>Barcode equality links immediately (highest confidence).</li>
 *   <li>Otherwise candidates in the same size bucket that share a name token or the brand are scored by
 *       {@link ProductMatcher}.</li>
 *   <li>score >= 0.90: linked automatically (recorded as AUTO_APPROVED).</li>
 *   <li>0.60 <= score < 0.90: the product gets its own canonical product (so it stays searchable) and a
 *       PENDING match candidate is created for an admin to approve (merge) or reject.</li>
 *   <li>Below 0.60: a new canonical product is created.</li>
 * </ol>
 * A canonical product never receives two products of the same store automatically.
 * Parsed features of all canonical products are kept in an in-memory {@link CanonicalIndex}.
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final CanonicalProductRepository canonicalRepo;
    private final StoreProductRepository storeProductRepo;
    private final ProductMatchCandidateRepository candidateRepo;
    private final ProductAliasRepository aliasRepo;
    private final CategoryRepository categoryRepo;
    private final EntityManager em;
    private CanonicalIndex index;

    public MatchingService(CanonicalProductRepository canonicalRepo, StoreProductRepository storeProductRepo,
                           ProductMatchCandidateRepository candidateRepo, ProductAliasRepository aliasRepo,
                           CategoryRepository categoryRepo, EntityManager em) {
        this.canonicalRepo = canonicalRepo;
        this.storeProductRepo = storeProductRepo;
        this.candidateRepo = candidateRepo;
        this.aliasRepo = aliasRepo;
        this.categoryRepo = categoryRepo;
        this.em = em;
    }

    public record Outcome(CanonicalProduct canonical, Decision decision, double score, String reason) {
    }

    public static ProductFeatures features(StoreProduct sp) {
        return ProductFeatures.of(sp.getExternalName(), sp.getExternalNameEn(), sp.getBrand(), sp.getBarcode(),
                sp.getPackageSize(), sp.getPackageUnit(), sp.isSoldByWeight());
    }

    public static ProductFeatures features(CanonicalProduct c) {
        return ProductFeatures.of(c.getName(), c.getNameEn(), c.getBrand(), c.getBarcode(),
                c.getSizeValue(), c.getSizeUnit(), c.isSoldByWeight());
    }

    /** Must be called inside a transaction; {@code sp} must be persisted and not yet linked. */
    @Transactional
    public synchronized Outcome matchAndLink(StoreProduct sp) {
        CanonicalIndex idx = index();
        ProductFeatures f = features(sp);
        long storeId = sp.getStore().getId();

        // 1. Barcode
        if (f.barcode() != null) {
            CanonicalProduct byBarcode = resolve(idx.byBarcode(f.barcode()));
            if (byBarcode == null) {
                byBarcode = storeProductRepo.findByBarcodeAndCanonicalProductIsNotNull(f.barcode()).stream()
                        .map(StoreProduct::getCanonicalProduct).filter(CanonicalProduct::isActive).findFirst().orElse(null);
            }
            if (byBarcode != null && !taken(idx, byBarcode.getId(), storeId)) {
                link(sp, byBarcode, 1.0, "barcode " + f.barcode(), MatchStatus.AUTO_APPROVED);
                return new Outcome(byBarcode, Decision.AUTO_MATCH, 1.0, "barcode");
            }
        }

        // 2. Same size bucket, sharing a token or brand, scored by name/brand
        CanonicalIndex.Entry bestEntry = null;
        MatchResult bestResult = null;
        ProductMatcher.TokenWeights weights = idx.weights();
        for (CanonicalIndex.Entry e : idx.candidates(f)) {
            if (e.storeIds.contains(storeId)) {
                continue;
            }
            MatchResult r = ProductMatcher.score(f, e.features, weights);
            if (r.decision() != Decision.NO_MATCH && (bestResult == null || r.score() > bestResult.score())) {
                bestEntry = e;
                bestResult = r;
            }
        }
        CanonicalProduct best = bestEntry == null ? null : resolve(bestEntry.id);

        if (best != null && bestResult.decision() == Decision.AUTO_MATCH) {
            link(sp, best, bestResult.score(), bestResult.reason(), MatchStatus.AUTO_APPROVED);
            return new Outcome(best, Decision.AUTO_MATCH, bestResult.score(), bestResult.reason());
        }

        CanonicalProduct created = createCanonical(sp, f);
        sp.setCanonicalProduct(created);
        addAliases(created, sp);
        idx.put(created.getId(), features(created));
        idx.addStore(created.getId(), storeId);
        if (best != null) {
            ProductMatchCandidate cand = new ProductMatchCandidate();
            cand.setStoreProduct(sp);
            cand.setCanonicalProduct(best);
            cand.setConfidence(BigDecimal.valueOf(bestResult.score()));
            cand.setReason(bestResult.reason());
            cand.setStatus(MatchStatus.PENDING);
            candidateRepo.save(cand);
            return new Outcome(created, Decision.REVIEW, bestResult.score(), bestResult.reason());
        }
        return new Outcome(created, Decision.NO_MATCH, 0, "new canonical product");
    }

    private synchronized CanonicalIndex index() {
        if (index == null) {
            long t = System.currentTimeMillis();
            CanonicalIndex idx = new CanonicalIndex();
            List<Object[]> rows = em.createQuery("select c.id, c.name, c.nameEn, c.brand, c.barcode, c.sizeValue, "
                    + "c.sizeUnit, c.soldByWeight from CanonicalProduct c where c.active = true", Object[].class).getResultList();
            for (Object[] r : rows) {
                idx.put((Long) r[0], ProductFeatures.of((String) r[1], (String) r[2], (String) r[3], (String) r[4],
                        (BigDecimal) r[5], (String) r[6], (Boolean) r[7]));
            }
            List<Object[]> links = em.createQuery("select distinct sp.canonicalProduct.id, sp.store.id from StoreProduct sp "
                    + "where sp.canonicalProduct is not null", Object[].class).getResultList();
            for (Object[] l : links) {
                idx.addStore((Long) l[0], (Long) l[1]);
            }
            index = idx;
            log.info("Canonical match index loaded: {} products in {} ms", idx.size(), System.currentTimeMillis() - t);
        }
        return index;
    }

    /** Drops the in-memory index; it is rebuilt from the database on next use. */
    public synchronized void resetIndex() {
        index = null;
    }

    private boolean taken(CanonicalIndex idx, long canonicalId, long storeId) {
        CanonicalIndex.Entry e = idx.get(canonicalId);
        if (e != null) {
            return e.storeIds.contains(storeId);
        }
        return !canonicalsWithStore(List.of(canonicalId), storeId).isEmpty();
    }

    /** Loads a canonical product; drops stale index entries (e.g. from rolled back transactions). */
    private CanonicalProduct resolve(Long id) {
        if (id == null) {
            return null;
        }
        CanonicalProduct c = em.find(CanonicalProduct.class, id);
        if (c == null || !c.isActive()) {
            if (index != null) {
                index.remove(id);
            }
            return null;
        }
        return c;
    }

    private Set<Long> canonicalsWithStore(Collection<Long> canonicalIds, Long storeId) {
        if (canonicalIds.isEmpty()) {
            return Set.of();
        }
        List<Long> found = em.createQuery("select distinct sp.canonicalProduct.id from StoreProduct sp "
                        + "where sp.store.id = :storeId and sp.canonicalProduct.id in :ids", Long.class)
                .setParameter("storeId", storeId)
                .setParameter("ids", canonicalIds)
                .getResultList();
        return new HashSet<>(found);
    }

    /** Links a store product to a canonical product and records the decision. */
    @Transactional
    public void link(StoreProduct sp, CanonicalProduct c, double confidence, String reason, MatchStatus status) {
        sp.setCanonicalProduct(c);
        sp.setUpdatedAt(Instant.now());
        boolean changed = fillMissing(c, sp);
        addAliases(c, sp);
        synchronized (this) {
            if (index != null) {
                if (changed) {
                    index.put(c.getId(), features(c));
                }
                index.addStore(c.getId(), sp.getStore().getId());
            }
        }
        ProductMatchCandidate cand = candidateRepo.findByStoreProductIdAndCanonicalProductId(sp.getId(), c.getId())
                .orElseGet(ProductMatchCandidate::new);
        cand.setStoreProduct(sp);
        cand.setCanonicalProduct(c);
        cand.setConfidence(BigDecimal.valueOf(confidence));
        cand.setReason(reason);
        cand.setStatus(status);
        cand.setReviewedAt(status == MatchStatus.APPROVED ? Instant.now() : cand.getReviewedAt());
        candidateRepo.save(cand);
    }

    private CanonicalProduct createCanonical(StoreProduct sp, ProductFeatures f) {
        CanonicalProduct c = new CanonicalProduct();
        c.setName(sp.getExternalName());
        c.setNameEn(sp.getExternalNameEn());
        c.setNormalizedName(truncate(ProductNormalizer.normalize(sp.getExternalName()), 500));
        c.setBrand(sp.getBrand());
        c.setBarcode(f.barcode());
        c.setSizeValue(f.sizeValue());
        c.setSizeUnit(f.sizeUnit());
        c.setSoldByWeight(sp.isSoldByWeight());
        c.setVariant(variant(f));
        c.setImageUrl(sp.getImageUrl());
        c.setCategory(category(sp));
        c.setSearchText(ProductNormalizer.searchText(sp.getExternalName(), sp.getExternalNameEn(), sp.getBrand()));
        return canonicalRepo.save(c);
    }

    private static String variant(ProductFeatures f) {
        StringBuilder sb = new StringBuilder();
        if (f.percent() != null) {
            sb.append(f.percent().toPlainString()).append('%');
        }
        if (f.packCount() != null) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(f.packCount()).append('x');
        }
        return sb.isEmpty() ? null : truncate(sb.toString(), 100);
    }

    private Category category(StoreProduct sp) {
        String slug = CategoryClassifier.classify(sp.getExternalCategory(), sp.getExternalNameEn(), sp.getExternalName());
        return categoryRepo.findBySlug(slug).orElseGet(() -> categoryRepo.findBySlug("other").orElse(null));
    }

    private boolean fillMissing(CanonicalProduct c, StoreProduct sp) {
        boolean changed = false;
        if (c.getNameEn() == null && sp.getExternalNameEn() != null) {
            c.setNameEn(sp.getExternalNameEn());
            changed = true;
        }
        if (c.getBrand() == null && sp.getBrand() != null) {
            c.setBrand(sp.getBrand());
            changed = true;
        }
        if (c.getBarcode() == null && sp.getBarcode() != null) {
            c.setBarcode(sp.getBarcode());
            changed = true;
        }
        if (c.getImageUrl() == null && sp.getImageUrl() != null) {
            c.setImageUrl(sp.getImageUrl());
            changed = true;
        }
        if (changed) {
            c.setUpdatedAt(Instant.now());
        }
        return changed;
    }

    /** Stores every store-specific name as an alias and refreshes the canonical search text. */
    public void addAliases(CanonicalProduct c, StoreProduct sp) {
        String source = sp.getStore().getSlug();
        boolean added = addAlias(c, sp.getExternalName(), source);
        added |= addAlias(c, sp.getExternalNameEn(), source);
        if (added || c.getSearchText() == null || c.getSearchText().isBlank()) {
            refreshSearchText(c);
        }
    }

    /** Called when a store product's names changed (e.g. English name discovered later). */
    public void onStoreProductNamesChanged(StoreProduct sp) {
        if (sp.getCanonicalProduct() != null) {
            boolean changed = fillMissing(sp.getCanonicalProduct(), sp);
            addAliases(sp.getCanonicalProduct(), sp);
            if (changed) {
                reindex(sp.getCanonicalProduct());
            }
        }
    }

    private boolean addAlias(CanonicalProduct c, String alias, String source) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        String norm = truncate(ProductNormalizer.normalize(alias), 500);
        if (c.getId() != null && aliasRepo.existsByCanonicalProductIdAndNormalizedAlias(c.getId(), norm)) {
            return false;
        }
        ProductAlias a = new ProductAlias();
        a.setCanonicalProduct(c);
        a.setAlias(truncate(alias, 500));
        a.setNormalizedAlias(norm);
        a.setSource(source);
        aliasRepo.save(a);
        return true;
    }

    public void refreshSearchText(CanonicalProduct c) {
        List<String> texts = new ArrayList<>();
        texts.add(c.getName());
        texts.add(c.getNameEn());
        texts.add(c.getBrand());
        if (c.getId() != null) {
            aliasRepo.findByCanonicalProductId(c.getId()).forEach(a -> texts.add(a.getAlias()));
        }
        c.setSearchText(ProductNormalizer.searchText(texts.toArray(String[]::new)));
    }

    /**
     * Admin approval of a candidate: moves the store product to the candidate canonical product. A canonical
     * product left without any store products is deactivated (never deleted) and its shopping-list references
     * are moved to the target.
     */
    @Transactional
    public void approve(ProductMatchCandidate cand) {
        StoreProduct sp = cand.getStoreProduct();
        CanonicalProduct previous = sp.getCanonicalProduct();
        CanonicalProduct target = cand.getCanonicalProduct();
        detachFromIndex(previous, sp);
        link(sp, target, cand.getConfidence().doubleValue(), cand.getReason(), MatchStatus.APPROVED);
        cand.setStatus(MatchStatus.APPROVED);
        cand.setReviewedAt(Instant.now());
        if (previous != null && !previous.getId().equals(target.getId())) {
            retireIfOrphan(previous, target);
        }
        log.info("Approved match store product {} -> canonical {}", sp.getId(), target.getId());
    }

    @Transactional
    public void reject(ProductMatchCandidate cand) {
        cand.setStatus(MatchStatus.REJECTED);
        cand.setReviewedAt(Instant.now());
    }

    /** Manual mapping from the admin UI. */
    @Transactional
    public void mapManually(StoreProduct sp, CanonicalProduct target) {
        CanonicalProduct previous = sp.getCanonicalProduct();
        detachFromIndex(previous, sp);
        link(sp, target, 1.0, "manual mapping", MatchStatus.APPROVED);
        if (previous != null && !previous.getId().equals(target.getId())) {
            retireIfOrphan(previous, target);
        }
    }

    private synchronized void detachFromIndex(CanonicalProduct previous, StoreProduct sp) {
        if (previous != null && index != null) {
            index.removeStore(previous.getId(), sp.getStore().getId());
        }
    }

    /** Re-indexes a canonical product after an edit. */
    public synchronized void reindex(CanonicalProduct c) {
        if (index != null) {
            if (c.isActive()) {
                index.put(c.getId(), features(c));
            } else {
                index.remove(c.getId());
            }
        }
    }

    private void retireIfOrphan(CanonicalProduct previous, CanonicalProduct target) {
        em.flush();
        Long remaining = em.createQuery("select count(sp) from StoreProduct sp where sp.canonicalProduct.id = :id", Long.class)
                .setParameter("id", previous.getId()).getSingleResult();
        if (remaining == 0) {
            synchronized (this) {
                if (index != null) {
                    index.remove(previous.getId());
                }
            }
            previous.setActive(false);
            previous.setUpdatedAt(Instant.now());
            em.createQuery("update ShoppingListItem i set i.canonicalProduct = :target where i.canonicalProduct = :prev")
                    .setParameter("target", target).setParameter("prev", previous).executeUpdate();
            // price watches follow the product; a device already watching the target keeps its own watch
            em.createQuery("delete from PriceWatch w where w.canonicalProduct = :prev and exists "
                            + "(select o.id from PriceWatch o where o.id <> w.id and o.device = w.device and o.canonicalProduct = :target)")
                    .setParameter("target", target).setParameter("prev", previous).executeUpdate();
            em.createQuery("update PriceWatch w set w.canonicalProduct = :target where w.canonicalProduct = :prev")
                    .setParameter("target", target).setParameter("prev", previous).executeUpdate();
            em.createQuery("update ProductMatchCandidate c set c.status = :rejected, c.reviewedAt = :now "
                            + "where c.canonicalProduct = :prev and c.status = :pending")
                    .setParameter("rejected", MatchStatus.REJECTED).setParameter("now", Instant.now())
                    .setParameter("prev", previous).setParameter("pending", MatchStatus.PENDING).executeUpdate();
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
