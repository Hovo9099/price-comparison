package am.pricecompare.service;

import am.pricecompare.domain.BasketComparison;
import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.domain.ShoppingList;
import am.pricecompare.domain.ShoppingListItem;
import am.pricecompare.domain.Store;
import am.pricecompare.lexicon.ConceptCandidateService;
import am.pricecompare.lexicon.ConceptMatcher;
import am.pricecompare.lexicon.GroceryConcept;
import am.pricecompare.lexicon.GroceryLexicon;
import am.pricecompare.lexicon.ShoppingListParser;
import am.pricecompare.normalize.ProductNormalizer;
import am.pricecompare.repository.BasketComparisonRepository;
import am.pricecompare.repository.ShoppingListRepository;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.service.StrategySelector.Strategy;
import am.pricecompare.web.NotFoundException;
import am.pricecompare.web.dto.ProductSummaryDto;
import am.pricecompare.web.dto.SmartListDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Smart shopping list: parses free text into product requests and compares the list across supermarkets with three
 * strategies (cheapest, mid-range, premium). All totals come from real current store prices; nothing is estimated.
 */
@Service
public class SmartListService {

    private final GroceryLexicon lexicon;
    private final ConceptCandidateService candidateService;
    private final OfferService offerService;
    private final ShoppingListRepository listRepo;
    private final StoreRepository storeRepo;
    private final BasketComparisonRepository comparisonRepo;
    private final ShoppingListService shoppingListService;
    private final ObjectMapper json;

    public SmartListService(GroceryLexicon lexicon, ConceptCandidateService candidateService, OfferService offerService,
                            ShoppingListRepository listRepo, StoreRepository storeRepo, BasketComparisonRepository comparisonRepo,
                            ShoppingListService shoppingListService, ObjectMapper json) {
        this.lexicon = lexicon;
        this.candidateService = candidateService;
        this.offerService = offerService;
        this.listRepo = listRepo;
        this.storeRepo = storeRepo;
        this.comparisonRepo = comparisonRepo;
        this.shoppingListService = shoppingListService;
        this.json = json;
    }

    // ---- concepts & parsing -----------------------------------------------------------------------

    public ConceptDto conceptDto(GroceryConcept c) {
        return new ConceptDto(c.code(), c.nameHy(), c.nameEn(), c.nameRu(), c.category(), c.defaultSizeValue(), c.defaultSizeUnit(),
                c.sizes(), c.soldByWeight(), c.requiresChoice(), refs(c.children()), refs(c.alternatives()));
    }

    private List<ConceptRefDto> refs(List<String> codes) {
        return codes.stream().map(lexicon::get).flatMap(java.util.Optional::stream)
                .map(x -> new ConceptRefDto(x.code(), x.nameHy(), x.nameEn())).toList();
    }

    public List<ConceptDto> suggest(String q, int limit) {
        return lexicon.suggest(q, limit).stream().map(this::conceptDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ParsedItemDto> parse(String text) {
        List<ParsedItemDto> out = new ArrayList<>();
        for (String raw : ShoppingListParser.split(text)) {
            out.add(parseOne(raw));
        }
        return out;
    }

    private ParsedItemDto parseOne(String raw) {
        ShoppingListParser.Request req = ShoppingListParser.parseItem(raw);
        GroceryLexicon.Resolution res = lexicon.resolve(req.phrase());
        if (!res.recognised()) {
            ShoppingListParser.Request r = ShoppingListParser.adaptToConcept(req, null);
            return new ParsedItemDto(raw, req.phrase(), ParseStatus.UNRECOGNIZED, 0, null, List.of(), r.quantity(), "pcs",
                    r.sizeValue(), r.sizeUnit(), r.percent(), null, 0, List.of());
        }
        GroceryConcept concept = res.concepts().get(0);
        ShoppingListParser.Request r = ShoppingListParser.adaptToConcept(req, concept);
        List<ConceptDto> alternatives = res.concepts().stream().skip(1).map(this::conceptDto).toList();
        ParseStatus status = res.ambiguous() ? ParseStatus.AMBIGUOUS
                : concept.requiresChoice() ? ParseStatus.NEEDS_CHOICE : ParseStatus.RECOGNIZED;
        List<CanonicalProduct> candidates = status == ParseStatus.RECOGNIZED
                ? candidateService.candidates(new ConceptCandidateService.Criteria(concept, r.sizeValue(), r.sizeUnit(), r.percent(), res.hint()))
                : List.of();
        List<ProductSummaryDto> suggestions = offerService.summaries(candidates).stream()
                .filter(s -> s.cheapest() != null)
                .sorted(Comparator.comparingInt(ProductSummaryDto::storeCount).reversed()
                        .thenComparing(s -> s.cheapest().price()))
                .limit(6).toList();
        return new ParsedItemDto(raw, req.phrase(), status, res.confidence(), conceptDto(concept), alternatives, r.quantity(),
                concept.soldByWeight() ? "kg" : "pcs", r.sizeValue(), r.sizeUnit(), r.percent(), res.hint(), candidates.size(), suggestions);
    }

    /** Parses text and (optionally) adds recognised requests to the list; unrecognised text is kept as a note. */
    @Transactional
    public ParseResultDto parseInto(long listId, String text, boolean add) {
        List<ParsedItemDto> parsed = parse(text);
        if (add) {
            ShoppingList list = listRepo.findById(listId).orElseThrow(() -> new NotFoundException("Shopping list " + listId + " not found"));
            for (ParsedItemDto p : parsed) {
                ShoppingListItem item = new ShoppingListItem();
                item.setShoppingList(list);
                item.setRawText(trim(p.raw(), 300));
                item.setQuantity(p.quantity() == null ? BigDecimal.ONE : p.quantity());
                if (p.concept() != null) {
                    item.setConceptCode(p.concept().code());
                    item.setSizeValue(p.sizeValue());
                    item.setSizeUnit(p.sizeUnit());
                    item.setPercent(p.percent());
                    item.setPreferredBrand(trim(p.hint(), 100));
                } else {
                    item.setFreeText(trim(p.raw(), 300));
                }
                list.getItems().add(item);
            }
            list.setUpdatedAt(Instant.now());
            listRepo.saveAndFlush(list);
        }
        return new ParseResultDto(parsed, add ? shoppingListService.get(listId) : null);
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }

    // ---- comparison ---------------------------------------------------------------------------------

    /** Everything known about one list item for comparison. */
    private record ItemContext(ShoppingListItem item, String label, String quantityUnit, ItemStatus status, String note,
                               List<CanonicalProduct> candidates, List<ConceptRefDto> choices, boolean fixedProduct) {
    }

    @Transactional
    public ComparisonDto compare(long listId, ComparePreferences prefsIn) {
        ComparePreferences prefs = prefsIn == null ? new ComparePreferences(null, null, null, null, null) : prefsIn;
        ShoppingList list = listRepo.findById(listId).orElseThrow(() -> new NotFoundException("Shopping list " + listId + " not found"));
        Instant now = Instant.now();
        boolean allowStale = !Boolean.FALSE.equals(prefs.allowStalePrices());
        boolean includeDelivery = Boolean.TRUE.equals(prefs.includeDelivery());
        List<String> brands = prefs.preferredBrands() == null ? List.of() : prefs.preferredBrands().stream()
                .filter(b -> b != null && !b.isBlank()).toList();

        // 1. candidates per item
        List<ItemContext> contexts = new ArrayList<>();
        for (ShoppingListItem item : list.getItems()) {
            contexts.add(context(item, brands));
        }
        List<Long> allIds = contexts.stream().flatMap(c -> c.candidates().stream()).map(CanonicalProduct::getId).distinct().toList();
        Map<Long, List<OfferService.Offer>> offers = offerService.currentOffers(allIds);
        Map<Long, CanonicalProduct> productById = contexts.stream().flatMap(c -> c.candidates().stream())
                .collect(Collectors.toMap(CanonicalProduct::getId, Function.identity(), (a, b) -> a));

        // 2. usable offers per item
        Map<Long, List<OfferService.Offer>> usableByItem = new LinkedHashMap<>();
        List<ItemStatusDto> statuses = new ArrayList<>();
        for (ItemContext c : contexts) {
            List<OfferService.Offer> usable = new ArrayList<>();
            for (CanonicalProduct p : c.candidates()) {
                for (OfferService.Offer o : offers.getOrDefault(p.getId(), List.of())) {
                    if (o.usable() && (allowStale || o.freshness() != Freshness.STALE)) {
                        usable.add(o);
                    }
                }
            }
            ItemStatus status = c.status();
            String note = c.note();
            if (status == ItemStatus.OK && usable.isEmpty()) {
                status = ItemStatus.NO_CURRENT_PRICE;
                note = "Matching products exist but none has a current, available price";
            }
            long comparable = usable.stream().map(o -> o.storeProduct().getCanonicalProduct().getId()).distinct().count();
            statuses.add(new ItemStatusDto(c.item().getId(), c.label(), c.item().getConceptCode(),
                    c.item().getCanonicalProduct() == null ? null : c.item().getCanonicalProduct().getId(), c.item().getQuantity(),
                    c.quantityUnit(), c.item().getSizeValue(), c.item().getSizeUnit(), status, (int) comparable, note, c.choices()));
            if (status == ItemStatus.OK) {
                usableByItem.put(c.item().getId(), usable);
            }
        }

        Map<Long, Store> stores = storeRepo.findAll().stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        Map<Long, BasketOptimizer.DeliveryTerms> delivery = new HashMap<>();
        stores.values().forEach(s -> delivery.put(s.getId(), new BasketOptimizer.DeliveryTerms(s.getDeliveryFee(), s.getFreeDeliveryFrom(),
                s.getMinOrderAmount(), s.getSmallOrderBelow(), s.getSmallOrderFee())));
        Map<Long, ItemContext> contextById = contexts.stream().collect(Collectors.toMap(c -> c.item().getId(), Function.identity()));

        // 3. each strategy
        List<StrategyDto> strategies = new ArrayList<>();
        Map<Strategy, BigDecimal> totals = new LinkedHashMap<>();
        for (Strategy strategy : Strategy.values()) {
            StrategyDto dto = strategy(strategy, usableByItem, contextById, productById, stores, delivery, includeDelivery,
                    prefs.effectiveMaxStores(), now);
            strategies.add(dto);
            totals.put(strategy, dto.total());
        }
        // 4. differences between strategies
        List<StrategyDto> withDiff = new ArrayList<>();
        for (StrategyDto s : strategies) {
            Map<String, BigDecimal> diff = new LinkedHashMap<>();
            totals.forEach((k, v) -> {
                if (!k.name().equals(s.strategy()) && v != null && s.total() != null) {
                    diff.put(k.name(), s.total().subtract(v));
                }
            });
            withDiff.add(new StrategyDto(s.strategy(), s.recommended(), s.total(), s.singleStore(), s.multiStore(), s.missingItemIds(),
                    diff, s.notes()));
        }

        List<String> warnings = new ArrayList<>();
        if (statuses.stream().anyMatch(s -> s.status() != ItemStatus.OK)) {
            warnings.add("Որոշ ապրանքներ ներառված չեն ընդհանուր գումարում (տե՛ս ապրանքների կարգավիճակները)։");
        }
        if (!includeDelivery) {
            warnings.add("Առաքման վճարները և ճանապարհի ծախսերը ներառված չեն։");
        } else if (stores.values().stream().anyMatch(s -> s.getDeliveryFee() == null)) {
            warnings.add("Որոշ խանութների առաքման վճարները հայտնի չեն և չեն ավելացվել․ ճանապարհի ծախսերը երբեք չեն ներառվում։");
        }
        ComparisonDto result = new ComparisonDto(null, listId, prefs, statuses, withDiff, warnings, now);
        return save(result);
    }

    private ComparisonDto save(ComparisonDto result) {
        try {
            BasketComparison bc = new BasketComparison();
            bc.setShoppingListId(result.shoppingListId());
            bc.setPreferences(json.writeValueAsString(result.preferences()));
            bc.setResult(json.writeValueAsString(result));
            bc = comparisonRepo.save(bc);
            return new ComparisonDto(bc.getId(), result.shoppingListId(), result.preferences(), result.items(), result.strategies(),
                    result.warnings(), result.calculatedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize comparison", e);
        }
    }

    private ItemContext context(ShoppingListItem item, List<String> brands) {
        if (item.getCanonicalProduct() != null) {
            CanonicalProduct p = item.getCanonicalProduct();
            return new ItemContext(item, p.getName(), p.isSoldByWeight() ? "kg" : "pcs", ItemStatus.OK, "Specific product chosen",
                    List.of(p), List.of(), true);
        }
        if (item.getConceptCode() != null) {
            GroceryConcept concept = lexicon.get(item.getConceptCode()).orElse(null);
            if (concept == null) {
                return new ItemContext(item, item.getConceptCode(), "pcs", ItemStatus.UNRECOGNIZED, "Unknown concept", List.of(), List.of(), false);
            }
            String label = concept.nameHy() + sizeLabel(item);
            String unit = concept.soldByWeight() ? "kg" : "pcs";
            if (concept.requiresChoice()) {
                return new ItemContext(item, label, unit, ItemStatus.NEEDS_CHOICE, "Choose a type of " + concept.nameEn().toLowerCase(),
                        List.of(), refs(concept.children()), false);
            }
            List<CanonicalProduct> candidates = candidateService.candidates(new ConceptCandidateService.Criteria(concept,
                    item.getSizeValue(), item.getSizeUnit(), item.getPercent(), item.getPreferredBrand()));
            if (!brands.isEmpty()) {
                List<CanonicalProduct> preferred = candidates.stream()
                        .filter(p -> brands.stream().anyMatch(b -> ConceptMatcher.matchesHint(b, p.getName(), p.getNameEn(), p.getBrand())))
                        .toList();
                if (!preferred.isEmpty()) {
                    candidates = preferred;
                }
            }
            if (candidates.isEmpty()) {
                return new ItemContext(item, label, unit, ItemStatus.NO_MATCHING_PRODUCT,
                        "No product in the catalog matches this request" + (item.getSizeValue() != null ? " in this size" : ""),
                        List.of(), List.of(), false);
            }
            String note = null;
            if (!concept.soldByWeight() && item.getSizeValue() == null) {
                // No size requested: compare like with like (a 85 g bun is not a cheaper 500 g loaf)
                CommonSize common = commonSize(candidates);
                if (common != null) {
                    candidates = candidates.stream().filter(p -> common.includes(p.getSizeValue(), p.getSizeUnit())).toList();
                    String size = ProductNormalizer.formatSize(common.value(), common.unit());
                    label = label + " " + size;
                    note = "No size given: compared at the most common package size (" + size + "); add a size to change it";
                }
            }
            return new ItemContext(item, label, unit, ItemStatus.OK, note, candidates, List.of(), false);
        }
        return new ItemContext(item, item.getFreeText(), "pcs", ItemStatus.UNRECOGNIZED,
                "Not recognised; search for the product and add it", List.of(), List.of(), false);
    }

    /** A package size and the ±10 % band around it that counts as the same size. */
    record CommonSize(BigDecimal value, String unit) {
        boolean includes(BigDecimal v, String u) {
            return v != null && unit.equals(u) && v.subtract(value).abs()
                    .divide(value, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.10")) <= 0;
        }
    }

    /** The package size shared by the most candidates (ties: the smaller size), or null when sizes are unknown. */
    static CommonSize commonSize(List<CanonicalProduct> candidates) {
        Map<CommonSize, Long> counts = candidates.stream()
                .filter(p -> p.getSizeValue() != null && p.getSizeValue().signum() > 0 && p.getSizeUnit() != null)
                .map(p -> new CommonSize(p.getSizeValue().stripTrailingZeros(), p.getSizeUnit()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        CommonSize best = null;
        long bestCount = 0;
        for (Map.Entry<CommonSize, Long> e : counts.entrySet()) {
            long n = counts.entrySet().stream().filter(x -> e.getKey().includes(x.getKey().value(), x.getKey().unit()))
                    .mapToLong(Map.Entry::getValue).sum();
            if (n > bestCount || (n == bestCount && best != null && e.getKey().value().compareTo(best.value()) < 0)) {
                best = e.getKey();
                bestCount = n;
            }
        }
        return best;
    }

    private static String sizeLabel(ShoppingListItem item) {
        String size = ProductNormalizer.formatSize(item.getSizeValue(), item.getSizeUnit());
        String pct = item.getPercent() == null ? "" : " " + item.getPercent().stripTrailingZeros().toPlainString() + "%";
        String brand = item.getPreferredBrand() == null ? "" : " «" + item.getPreferredBrand() + "»";
        return brand + pct + (size == null ? "" : " " + size);
    }

    /** Price per litre / kg / piece used to rank comparable products. */
    static BigDecimal pricePerUnit(CanonicalProduct p, BigDecimal price) {
        if (p.isSoldByWeight() || p.getSizeValue() == null || p.getSizeUnit() == null || p.getSizeValue().signum() == 0) {
            return price;
        }
        BigDecimal base = switch (p.getSizeUnit()) {
            case "ml", "g" -> p.getSizeValue().divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            default -> p.getSizeValue();
        };
        return price.divide(base, 2, RoundingMode.HALF_UP);
    }

    static String perUnitLabel(CanonicalProduct p) {
        if (p.isSoldByWeight()) {
            return "kg";
        }
        if (p.getSizeUnit() == null) {
            return "pack";
        }
        return switch (p.getSizeUnit()) {
            case "ml" -> "l";
            case "g" -> "kg";
            default -> "pcs";
        };
    }

    private StrategyDto strategy(Strategy strategy, Map<Long, List<OfferService.Offer>> usableByItem, Map<Long, ItemContext> contexts,
                                 Map<Long, CanonicalProduct> productById, Map<Long, Store> stores,
                                 Map<Long, BasketOptimizer.DeliveryTerms> delivery, boolean includeDelivery, int maxStores, Instant now) {
        List<BasketOptimizer.Item> items = new ArrayList<>();
        Map<Long, List<BasketOptimizer.Offer>> optimizerOffers = new HashMap<>();
        Map<Long, OfferService.Offer> offerByStoreProduct = new HashMap<>();
        Map<Long, StrategySelector.Selection> selections = new HashMap<>();
        List<String> notes = new ArrayList<>();

        for (Map.Entry<Long, List<OfferService.Offer>> e : usableByItem.entrySet()) {
            ItemContext ctx = contexts.get(e.getKey());
            // typical price per unit of each comparable product = median of its store prices
            Map<Long, List<BigDecimal>> perProduct = new LinkedHashMap<>();
            for (OfferService.Offer o : e.getValue()) {
                CanonicalProduct p = productById.get(o.storeProduct().getCanonicalProduct().getId());
                perProduct.computeIfAbsent(p.getId(), k -> new ArrayList<>()).add(pricePerUnit(p, o.price().getPrice()));
            }
            List<StrategySelector.ProductPrice> prices = perProduct.entrySet().stream()
                    .map(x -> new StrategySelector.ProductPrice(x.getKey(), median(x.getValue()))).toList();
            StrategySelector.Selection sel = StrategySelector.select(strategy, prices);
            selections.put(e.getKey(), sel);
            if (strategy == Strategy.PREMIUM && sel.noPremiumAlternative() && !ctx.fixedProduct()) {
                notes.add(ctx.label() + "․ համեմատելի միայն մեկ ապրանք կա, պրեմիում այլընտրանք չկա");
            }
            // per store: the cheapest eligible offer
            Map<Long, OfferService.Offer> bestPerStore = new HashMap<>();
            for (OfferService.Offer o : e.getValue()) {
                if (!sel.eligible().contains(o.storeProduct().getCanonicalProduct().getId())) {
                    continue;
                }
                bestPerStore.merge(o.store().getId(), o, (a, b) -> a.price().getPrice().compareTo(b.price().getPrice()) <= 0 ? a : b);
            }
            List<BasketOptimizer.Offer> list = new ArrayList<>();
            for (OfferService.Offer o : bestPerStore.values()) {
                list.add(new BasketOptimizer.Offer(o.store().getId(), o.price().getPrice(), o.storeProduct().getId()));
                offerByStoreProduct.put(o.storeProduct().getId(), o);
            }
            optimizerOffers.put(e.getKey(), list);
            items.add(new BasketOptimizer.Item(e.getKey(), ctx.label(), ctx.item().getQuantity()));
        }

        BasketOptimizer.Result r = BasketOptimizer.optimize(items, optimizerOffers, maxStores, delivery, includeDelivery);
        Function<BasketOptimizer.StoreBasket, StoreOptionDto> storeDto = b -> {
            Store s = stores.get(b.storeId());
            List<LineDto> lines = new ArrayList<>();
            for (BasketOptimizer.Line l : b.lines()) {
                OfferService.Offer o = offerByStoreProduct.get(l.storeProductId());
                CanonicalProduct p = productById.get(o.storeProduct().getCanonicalProduct().getId());
                ItemContext ctx = contexts.get(l.itemId());
                StrategySelector.Selection sel = selections.get(l.itemId());
                Integer pct = sel.percentile().get(p.getId());
                BigDecimal ppu = pricePerUnit(p, o.price().getPrice());
                String[] reason = reason(strategy, ctx, sel, pct, ppu, perUnitLabel(p));
                lines.add(new LineDto(l.itemId(), ctx.label(), p.getId(), p.getName(), p.getBrand(), o.storeProduct().getId(),
                        o.storeProduct().getExternalName(), o.storeProduct().getExternalUrl(), p.getImageUrl(), o.price().getPrice(), ppu,
                        perUnitLabel(p), l.quantity(), ctx.quantityUnit(), l.total(), o.price().getCapturedAt(), o.freshness(), pct,
                        sel.comparableCount(), sel.medianPricePerUnit(), reason[0], reason[1]));
            }
            return new StoreOptionDto(s.getId(), s.getName(), s.getSlug(), b.subtotal(), b.deliveryFee(), b.deliveryFeeKnown(), b.total(), lines);
        };

        OptionDto single = r.cheapestSingleStore() == null ? null
                : new OptionDto(r.cheapestSingleStore().total(), 1, true, List.of(storeDto.apply(r.cheapestSingleStore())), List.of());
        BasketOptimizer.MultiStoreBasket m = r.cheapestMultiStore();
        OptionDto multi = new OptionDto(m.total(), m.storeCount(), m.complete(), m.stores().stream().map(storeDto).toList(), m.missingItemIds());

        String recommended;
        BigDecimal total;
        if (single != null && (multi.storeCount() <= 1 || multi.total().compareTo(single.total()) >= 0)) {
            recommended = "SINGLE_STORE";
            total = single.total();
        } else {
            recommended = "MULTI_STORE";
            total = items.isEmpty() ? null : multi.total();
            if (multi.storeCount() > 1) {
                notes.add("Գնումներ " + multi.storeCount() + " խանութում․ ճանապարհի ժամանակն ու ծախսերը ներառված չեն"
                        + (includeDelivery && multi.stores().stream().anyMatch(st -> !st.deliveryFeeKnown()) ? ", և որոշ առաքման վճարներ հայտնի չեն" : ""));
            }
        }
        List<Long> missing = new ArrayList<>(r.unavailableItemIds());
        missing.addAll(contexts.keySet().stream().filter(id -> !usableByItem.containsKey(id)).toList());
        return new StrategyDto(strategy.name(), recommended, total, single, multi, missing, Map.of(), notes);
    }

    private static String[] reason(Strategy strategy, ItemContext ctx, StrategySelector.Selection sel, Integer pct, BigDecimal ppu, String unit) {
        String perUnit = ppu.setScale(0, RoundingMode.HALF_UP).toPlainString() + " AMD/" + unit;
        if (ctx.fixedProduct()) {
            return new String[]{"FIXED_PRODUCT", "You chose this exact product"};
        }
        if (sel.comparableCount() == 1) {
            return new String[]{"ONLY_OPTION", "Only comparable product with a current price (" + perUnit + ")"};
        }
        String position = "price position " + pct + "% among " + sel.comparableCount() + " comparable products (median "
                + sel.medianPricePerUnit().setScale(0, RoundingMode.HALF_UP).toPlainString() + " AMD/" + unit + ")";
        return switch (strategy) {
            case CHEAPEST -> new String[]{"CHEAPEST", "Lowest price for the requested product at this store; " + perUnit + ", " + position};
            case MID_RANGE -> new String[]{"MID_RANGE", "Mainstream price band (25–75th percentile); " + perUnit + ", " + position};
            case PREMIUM -> new String[]{"PREMIUM", "Highest price band of comparable products (by price only, not a quality claim); "
                    + perUnit + ", " + position};
        };
    }

    private static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> s = values.stream().sorted().toList();
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : s.get(n / 2 - 1).add(s.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    /** Lists the user's saved lists (the app keeps their ids on the device). */
    @Transactional(readOnly = true)
    public List<am.pricecompare.web.dto.ShoppingListDtos.ShoppingListDto> lists(Set<Long> ids) {
        return ids.stream().filter(id -> listRepo.existsById(id)).map(shoppingListService::get).toList();
    }
}
