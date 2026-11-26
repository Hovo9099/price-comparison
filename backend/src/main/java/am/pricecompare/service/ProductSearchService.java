package am.pricecompare.service;

import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.normalize.ProductNormalizer;
import am.pricecompare.normalize.Transliterator;
import am.pricecompare.repository.CanonicalProductRepository;
import am.pricecompare.web.dto.PageDto;
import am.pricecompare.web.dto.ProductSummaryDto;
import am.pricecompare.web.dto.SearchResultDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Armenian / English / Russian product search over canonical products.
 *
 * <p>The query is normalized exactly like product names (case, punctuation, units: "1.5L" -> "1500ml"). Every token
 * must occur in the product's search text in one of its forms: as typed, transliterated to Latin, or as a phonetic
 * key (so "կոկա կոլա", "кока кола" and "coca cola" all match). Candidates are found with a trigram-indexed LIKE,
 * ranked in SQL to pick candidates (the best overall plus the best of every store, so no store is crowded out), then
 * ranked in Java (whole-word > prefix > substring, then number of stores), and fall back to trigram similarity
 * for misspellings. Only products with at least one current price are returned.
 */
@Service
@Transactional(readOnly = true)
public class ProductSearchService {

    /** Best matches overall that are ranked in Java. */
    private static final int CANDIDATE_LIMIT = 600;
    /** Best matches of each store that are always ranked too, however many matches other stores have. */
    private static final int PER_STORE_LIMIT = 150;

    private final EntityManager em;
    private final CanonicalProductRepository canonicalRepo;
    private final OfferService offerService;
    private final CatalogStatusService catalogStatus;

    public ProductSearchService(EntityManager em, CanonicalProductRepository canonicalRepo, OfferService offerService,
                                CatalogStatusService catalogStatus) {
        this.em = em;
        this.canonicalRepo = canonicalRepo;
        this.offerService = offerService;
        this.catalogStatus = catalogStatus;
    }

    /** Variants of a query token that may appear in search_text, including bilingual grocery synonyms. */
    static Set<String> variants(String token) {
        Set<String> v = new LinkedHashSet<>(baseVariants(token));
        for (String synonym : SearchSynonyms.of(token)) {
            v.addAll(baseVariants(synonym));
        }
        return v;
    }

    private static final java.util.regex.Pattern BARE_NUMBER = java.util.regex.Pattern.compile("\\d+(\\.\\d+)?");

    private static Set<String> baseVariants(String token) {
        Set<String> v = new LinkedHashSet<>();
        v.add(token);
        if (BARE_NUMBER.matcher(token).matches()) {
            // A number without unit is a size: "1.5" -> 1500ml / 1500g, "500" -> 500ml / 500g, "10" -> 10pcs
            java.math.BigDecimal n = new java.math.BigDecimal(token);
            if (n.compareTo(java.math.BigDecimal.valueOf(20)) <= 0) {
                String thousand = n.multiply(java.math.BigDecimal.valueOf(1000)).stripTrailingZeros().toPlainString();
                v.add(thousand + "ml");
                v.add(thousand + "g");
            }
            String plain = n.stripTrailingZeros().toPlainString();
            v.add(plain + "ml");
            v.add(plain + "g");
            v.add(plain + "pcs");
            return v;
        }
        String latin = Transliterator.toLatin(token);
        v.add(latin);
        v.add(Transliterator.phonetic(latin));
        if (!Transliterator.containsNonLatinLetters(token) && token.length() > 3 && token.endsWith("s")
                && !token.endsWith("ss") && !Character.isDigit(token.charAt(0))) {
            String stem = token.substring(0, token.length() - 1); // eggs -> egg
            v.add(stem);
            v.add(Transliterator.phonetic(stem));
        }
        return v;
    }

    public PageDto<ProductSummaryDto> search(String q, Long categoryId, int page, int size) {
        SearchResultDto r = search(q, categoryId, null, page, size);
        return new PageDto<>(r.items(), r.page(), r.size(), r.total());
    }

    /**
     * @param storeId only products this store currently lists (null = all stores)
     */
    public SearchResultDto search(String q, Long categoryId, Long storeId, int page, int size) {
        String normalized = ProductNormalizer.normalize(q == null ? "" : q);
        List<String> tokens = ProductNormalizer.tokenize(SearchSynonyms.rewritePhrases(normalized)).stream()
                .filter(t -> t.length() >= 2 || Character.isDigit(t.charAt(0))).toList();
        if (tokens.isEmpty()) {
            return result(List.of(), page, size, 0, 0, Map.of());
        }
        Candidates cand = likeCandidates(tokens, categoryId, storeId);
        if (cand.ids().isEmpty()) {
            cand = fuzzyCandidates(normalized, categoryId, storeId);
        }
        if (cand.ids().isEmpty()) {
            return result(List.of(), page, size, 0, 0, cand.perStore());
        }
        Map<Long, CanonicalProduct> byId = canonicalRepo.findAllById(cand.ids()).stream()
                .collect(Collectors.toMap(CanonicalProduct::getId, Function.identity()));
        Map<Long, List<OfferService.Offer>> offers = offerService.currentOffers(byId.keySet());

        // "milk" / "կաթ" -> dairy, "ձու" -> eggs: products of the query's own category rank first.
        String queryCategory = am.pricecompare.matching.CategoryClassifier.classifyText(normalized);
        List<Ranked> ranked = new ArrayList<>();
        for (CanonicalProduct c : byId.values()) {
            List<OfferService.Offer> o = offers.getOrDefault(c.getId(), List.of());
            int stores = OfferService.bestPerStore(o).size();
            double categoryBoost = queryCategory != null && c.getCategory() != null
                    && queryCategory.equals(c.getCategory().getSlug()) ? 3 : 0;
            ranked.add(new Ranked(c, o, score(c, tokens) + categoryBoost + coverage(c, tokens) + stores * 1.5 + (stores == 0 ? -20 : 0)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(r -> r.product().getName().length())
                .thenComparing(r -> r.product().getId()));
        int from = Math.min(page * size, ranked.size());
        int to = Math.min(from + size, ranked.size());
        List<ProductSummaryDto> items = ranked.subList(from, to).stream()
                .map(r -> OfferService.summary(r.product(), r.offers())).toList();
        return result(items, page, size, ranked.size(), cand.totalMatches(), cand.perStore());
    }

    private SearchResultDto result(List<ProductSummaryDto> items, int page, int size, long total, long totalMatches,
                                   Map<Long, Long> perStore) {
        List<SearchResultDto.StoreMatchDto> stores = new ArrayList<>();
        for (CatalogStatusService.StoreCatalog sc : catalogStatus.all()) {
            long matches = perStore.getOrDefault(sc.store().getId(), 0L);
            String message = switch (sc.state()) {
                case COMPLETE -> sc.reason();
                case PARTIAL -> "Catalog only partially imported; results from this store may be incomplete";
                default -> sc.reason();
            };
            stores.add(new SearchResultDto.StoreMatchDto(sc.store().getId(), sc.store().getName(), sc.store().getSlug(), matches,
                    sc.state(), message));
        }
        return new SearchResultDto(items, page, size, total, totalMatches, stores);
    }

    private record Ranked(CanonicalProduct product, List<OfferService.Offer> offers, double score) {
    }

    /** Candidate products (most relevant first, every store represented) and per-store match counts. */
    private record Candidates(List<Long> ids, long totalMatches, Map<Long, Long> perStore) {
    }

    /**
     * Every product whose search text contains all tokens is a match. Matches are ranked in SQL (whole word >
     * word prefix > substring) and the candidate set is the overall best {@link #CANDIDATE_LIMIT} plus the best
     * {@link #PER_STORE_LIMIT} of each store, so a store whose products happen to sort late is never cut off.
     */
    @SuppressWarnings("unchecked")
    private Candidates likeCandidates(List<String> tokens, Long categoryId, Long storeId) {
        StringBuilder where = new StringBuilder("cp.active");
        StringBuilder score = new StringBuilder("0");
        Map<String, Object> params = new HashMap<>();
        int i = 0;
        for (String t : tokens) {
            where.append(" and (");
            score.append(" + greatest(0");
            int j = 0;
            for (String v : variants(t)) {
                String name = "t" + i + "_" + j;
                if (j > 0) {
                    where.append(" or ");
                }
                where.append("cp.search_text like :").append(name);
                params.put(name, "%" + escapeLike(v) + "%");
                String rx = escapeRegex(v);
                score.append(", case when cp.search_text ~ :w").append(name).append(" then 4 when cp.search_text ~ :p").append(name)
                        .append(" then 2.5 when cp.search_text like :").append(name).append(" then 1 else 0 end");
                params.put("w" + name, "(^| )" + rx + "( |$)");
                params.put("p" + name, "(^| )" + rx);
                j++;
            }
            where.append(")");
            score.append(")");
            i++;
        }
        if (categoryId != null) {
            where.append(" and cp.category_id = :categoryId");
            params.put("categoryId", categoryId);
        }
        String sql = "with m as (select cp.id, " + score + " as s from canonical_products cp where " + where + "), "
                + "ps as (select distinct sp.store_id, m.id, m.s from m join store_products sp on sp.canonical_product_id = m.id "
                + "  join prices p on p.store_product_id = sp.id and p.valid_to is null"
                + (storeId != null ? " where m.id in (select sp2.canonical_product_id from store_products sp2 "
                + "  join prices p2 on p2.store_product_id = sp2.id and p2.valid_to is null where sp2.store_id = :storeId)" : "") + "), "
                + "pm as (select id, max(s) s from ps group by id), "
                + "r as (select id, row_number() over (partition by store_id order by s desc, id) rn from ps) "
                + "select 'c', store_id, count(*) from ps group by store_id "
                + "union all select 't', null, count(*) from pm "
                + "union all (select 'i', null, id from pm order by s desc, id limit " + CANDIDATE_LIMIT + ") "
                + "union all select 'i', null, id from r where rn <= " + PER_STORE_LIMIT;
        if (storeId != null) {
            params.put("storeId", storeId);
        }
        Query query = em.createNativeQuery(sql);
        params.forEach(query::setParameter);
        return collect((List<Object[]>) query.getResultList());
    }

    private static Candidates collect(List<Object[]> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        Map<Long, Long> perStore = new HashMap<>();
        long total = 0;
        for (Object[] r : rows) {
            String kind = r[0].toString();
            switch (kind) {
                case "c" -> perStore.put(((Number) r[1]).longValue(), ((Number) r[2]).longValue());
                case "t" -> total = ((Number) r[2]).longValue();
                default -> ids.add(((Number) r[2]).longValue());
            }
        }
        return new Candidates(new ArrayList<>(ids), total, perStore);
    }

    @SuppressWarnings("unchecked")
    private Candidates fuzzyCandidates(String normalized, Long categoryId, Long storeId) {
        String latin = ProductNormalizer.phoneticPhrase(Transliterator.toLatin(normalized));
        StringBuilder where = new StringBuilder("cp.active and (word_similarity(:q, cp.search_text) > 0.45 "
                + "or word_similarity(:ql, cp.search_text) > 0.45)");
        Map<String, Object> params = new HashMap<>();
        params.put("q", normalized);
        params.put("ql", latin);
        if (categoryId != null) {
            where.append(" and cp.category_id = :categoryId");
            params.put("categoryId", categoryId);
        }
        String sql = "with m as (select cp.id, greatest(word_similarity(:q, cp.search_text), word_similarity(:ql, cp.search_text)) s "
                + "from canonical_products cp where " + where + "), "
                + "ps as (select distinct sp.store_id, m.id, m.s from m join store_products sp on sp.canonical_product_id = m.id "
                + "  join prices p on p.store_product_id = sp.id and p.valid_to is null"
                + (storeId != null ? " where sp.store_id = :storeId" : "") + "), "
                + "pm as (select id, max(s) s from ps group by id) "
                + "select 'c', store_id, count(*) from ps group by store_id "
                + "union all select 't', null, count(*) from pm "
                + "union all (select 'i', null, id from pm order by s desc, id limit 100)";
        if (storeId != null) {
            params.put("storeId", storeId);
        }
        Query query = em.createNativeQuery(sql);
        params.forEach(query::setParameter);
        return collect((List<Object[]>) query.getResultList());
    }

    /** Escapes a literal for a PostgreSQL POSIX regular expression. */
    static String escapeRegex(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if ("\\.^$|?*+()[]{}".indexOf(ch) >= 0) {
                sb.append('\\');
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Whole-word > word-prefix > substring matches in the display names; small bonus for matches at the start. */
    static double score(CanonicalProduct c, List<String> tokens) {
        String hy = ProductNormalizer.normalize(c.getName());
        String en = c.getNameEn() == null ? "" : ProductNormalizer.normalize(c.getNameEn());
        String brand = c.getBrand() == null ? "" : ProductNormalizer.normalize(c.getBrand());
        String keys = String.join(" ", ProductNormalizer.parse(c.getName()).keys()) + " "
                + (c.getNameEn() == null ? "" : String.join(" ", ProductNormalizer.parse(c.getNameEn()).keys()));
        double s = 0;
        for (String t : tokens) {
            double best = 0;
            for (String v : variants(t)) {
                best = Math.max(best, wordScore(hy, v));
                best = Math.max(best, wordScore(en, v));
                best = Math.max(best, wordScore(keys, v));
                best = Math.max(best, wordScore(brand, v) * 0.9);
            }
            s += best;
            Set<String> vs = variants(t);
            if (vs.contains(firstWord(hy)) || vs.contains(firstWord(en))) {
                s += 1.5; // the product type usually comes first: "Կաթ …", "Milk …", "Հավկիթ …" (any language)
            }
        }
        return s;
    }

    /** Up to +2 when the query covers most of the product's name (prefer "Milk 1l" over "Milk sausage Bacon 400g"). */
    static double coverage(CanonicalProduct c, List<String> tokens) {
        List<String> nameTokens = ProductNormalizer.parse(c.getNameEn() != null ? c.getNameEn() : c.getName()).tokens();
        if (nameTokens.isEmpty()) {
            return 0;
        }
        return 2.0 * Math.min(1.0, (double) tokens.size() / nameTokens.size());
    }

    private static String firstWord(String text) {
        int i = text.indexOf(' ');
        return i < 0 ? text : text.substring(0, i);
    }

    private static double wordScore(String text, String token) {
        if (text.isEmpty() || token.isEmpty()) {
            return 0;
        }
        for (String w : text.split(" ")) {
            if (w.equals(token)) {
                return 4;
            }
        }
        for (String w : text.split(" ")) {
            if (w.startsWith(token)) {
                return 2.5;
            }
        }
        return text.contains(token) ? 1 : 0;
    }
}
