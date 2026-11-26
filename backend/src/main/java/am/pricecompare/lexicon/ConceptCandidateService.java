package am.pricecompare.lexicon;

import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.normalize.ProductNormalizer;
import am.pricecompare.repository.CanonicalProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the real canonical products that satisfy a shopping-list request: the concept's word rules, the requested
 * package size (within ±10 %, so 1 L never matches 500 ml), percentage (milk 3.2 % ≠ 2.5 %), sold-by-weight flag and
 * an optional brand/description hint. Only products that currently have a price somewhere are returned.
 */
@Service
@Transactional(readOnly = true)
public class ConceptCandidateService {

    static final BigDecimal SIZE_TOLERANCE = new BigDecimal("0.10");
    private static final int SQL_LIMIT = 10000;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConceptCandidateService.class);

    private final EntityManager em;
    private final CanonicalProductRepository canonicalRepo;

    public ConceptCandidateService(EntityManager em, CanonicalProductRepository canonicalRepo) {
        this.em = em;
        this.canonicalRepo = canonicalRepo;
    }

    public record Criteria(GroceryConcept concept, BigDecimal sizeValue, String sizeUnit, BigDecimal percent, String hint) {
    }

    public List<CanonicalProduct> candidates(Criteria c) {
        GroceryConcept concept = c.concept();
        if (concept == null || concept.requiresChoice() || concept.include().isEmpty()) {
            return List.of();
        }
        List<Long> ids = prefilter(concept);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<CanonicalProduct> out = new ArrayList<>();
        for (CanonicalProduct p : canonicalRepo.findAllById(ids)) {
            if (p.isActive() && accepts(c, p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Pure acceptance rule (exposed for tests). */
    public static boolean accepts(Criteria c, CanonicalProduct p) {
        GroceryConcept concept = c.concept();
        if (!ConceptMatcher.matches(concept, p.getName(), p.getNameEn())) {
            return false;
        }
        // The product's category must agree ("հաց" inside a yogurt name does not make it bread)
        if (concept.category() != null && p.getCategory() != null
                && !concept.category().equals(p.getCategory().getSlug()) && !"other".equals(p.getCategory().getSlug())) {
            return false;
        }
        if (concept.soldByWeight() != p.isSoldByWeight()) {
            return false;
        }
        if (p.getVariant() != null && p.getVariant().contains("x")) {
            return false; // multipacks are a different offer
        }
        if (!concept.soldByWeight() && c.sizeValue() != null && c.sizeUnit() != null) {
            if (p.getSizeValue() == null || !c.sizeUnit().equals(p.getSizeUnit())) {
                return false;
            }
            BigDecimal diff = p.getSizeValue().subtract(c.sizeValue()).abs();
            if (diff.divide(c.sizeValue(), 4, RoundingMode.HALF_UP).compareTo(SIZE_TOLERANCE) > 0) {
                return false;
            }
        }
        if (c.percent() != null) {
            BigDecimal pp = ProductNormalizer.parse(p.getNameEn() != null ? p.getNameEn() : p.getName()).percent();
            if (pp == null) {
                pp = ProductNormalizer.parse(p.getName()).percent();
            }
            if (pp == null || pp.compareTo(c.percent()) != 0) {
                return false;
            }
        }
        return ConceptMatcher.matchesHint(c.hint(), p.getName(), p.getNameEn(), p.getBrand());
    }

    /** Cheap SQL prefilter: products whose search text contains a word of the concept's first include group. */
    @SuppressWarnings("unchecked")
    private List<Long> prefilter(GroceryConcept concept) {
        Set<String> stems = new LinkedHashSet<>();
        for (String term : concept.include().get(0)) {
            String stem = ProductNormalizer.normalize(term.endsWith("*") ? term.substring(0, term.length() - 1) : term);
            if (!stem.isBlank()) {
                stems.add(stem);
            }
        }
        StringBuilder sql = new StringBuilder("select cp.id from canonical_products cp where cp.active and (");
        Map<String, Object> params = new HashMap<>();
        int i = 0;
        for (String stem : stems) {
            if (i > 0) {
                sql.append(" or ");
            }
            sql.append("cp.search_text like :s").append(i);
            params.put("s" + i, "%" + stem.replace("%", "\\%").replace("_", "\\_") + "%");
            i++;
        }
        sql.append(")");
        if (concept.category() != null) {
            // same rule as accepts(): the product's category must agree (or be unknown), checked in SQL so that the
            // limit below is never reached by products that would be rejected anyway
            sql.append(" and (cp.category_id is null or cp.category_id in (select c.id from categories c where c.slug in (:cat, 'other')))");
            params.put("cat", concept.category());
        }
        sql.append(" and exists (select 1 from store_products sp join prices p on p.store_product_id = sp.id and p.valid_to is null "
                + "where sp.canonical_product_id = cp.id) order by cp.id limit ").append(SQL_LIMIT + 1);
        Query q = em.createNativeQuery(sql.toString());
        params.forEach(q::setParameter);
        List<Long> ids = ((List<Number>) q.getResultList()).stream().map(Number::longValue).toList();
        if (ids.size() > SQL_LIMIT) {
            log.warn("Concept {} matches more than {} products; comparing the first {} only", concept.code(), SQL_LIMIT, SQL_LIMIT);
            return ids.subList(0, SQL_LIMIT);
        }
        return ids;
    }
}
