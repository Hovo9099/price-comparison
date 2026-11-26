package am.pricecompare.service;

import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.domain.ShoppingList;
import am.pricecompare.domain.ShoppingListItem;
import am.pricecompare.domain.Store;
import am.pricecompare.repository.CanonicalProductRepository;
import am.pricecompare.repository.ShoppingListRepository;
import am.pricecompare.repository.StoreRepository;
import am.pricecompare.web.NotFoundException;
import am.pricecompare.web.dto.ShoppingListDtos.AddItemRequest;
import am.pricecompare.web.dto.ShoppingListDtos.BasketLineDto;
import am.pricecompare.web.dto.ShoppingListDtos.BasketOptimizationDto;
import am.pricecompare.web.dto.ShoppingListDtos.MultiStoreBasketDto;
import am.pricecompare.web.dto.ShoppingListDtos.ShoppingListDto;
import am.pricecompare.web.dto.ShoppingListDtos.ShoppingListItemDto;
import am.pricecompare.web.dto.ShoppingListDtos.StoreBasketDto;
import am.pricecompare.web.dto.ShoppingListDtos.UnavailableItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShoppingListService {

    private final ShoppingListRepository listRepo;
    private final CanonicalProductRepository canonicalRepo;
    private final StoreRepository storeRepo;
    private final OfferService offerService;
    private final am.pricecompare.lexicon.GroceryLexicon lexicon;
    private final am.pricecompare.lexicon.ConceptCandidateService candidateService;

    public ShoppingListService(ShoppingListRepository listRepo, CanonicalProductRepository canonicalRepo,
                               StoreRepository storeRepo, OfferService offerService, am.pricecompare.lexicon.GroceryLexicon lexicon,
                               am.pricecompare.lexicon.ConceptCandidateService candidateService) {
        this.listRepo = listRepo;
        this.canonicalRepo = canonicalRepo;
        this.storeRepo = storeRepo;
        this.offerService = offerService;
        this.lexicon = lexicon;
        this.candidateService = candidateService;
    }

    public ShoppingListDto create(String name) {
        ShoppingList l = new ShoppingList();
        l.setName(name.trim());
        return toDto(listRepo.save(l));
    }

    @Transactional(readOnly = true)
    public ShoppingListDto get(long id) {
        return toDto(load(id));
    }

    public ShoppingListDto addItem(long listId, AddItemRequest req) {
        ShoppingList l = load(listId);
        BigDecimal qty = req.quantity() == null ? BigDecimal.ONE : req.quantity();
        if (req.productId() != null) {
            CanonicalProduct c = canonicalRepo.findById(req.productId())
                    .orElseThrow(() -> new NotFoundException("Product " + req.productId() + " not found"));
            for (ShoppingListItem existing : l.getItems()) {
                if (existing.getCanonicalProduct() != null && existing.getCanonicalProduct().getId().equals(c.getId())) {
                    existing.setQuantity(existing.getQuantity().add(qty));
                    l.setUpdatedAt(Instant.now());
                    return toDto(l);
                }
            }
            ShoppingListItem item = new ShoppingListItem();
            item.setShoppingList(l);
            item.setCanonicalProduct(c);
            item.setQuantity(qty);
            l.getItems().add(item);
        } else if (req.conceptCode() != null && !req.conceptCode().isBlank()) {
            am.pricecompare.lexicon.GroceryConcept c = lexicon.get(req.conceptCode())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown concept " + req.conceptCode()));
            ShoppingListItem item = new ShoppingListItem();
            item.setShoppingList(l);
            item.setConceptCode(c.code());
            item.setQuantity(qty);
            if (req.sizeValue() != null && req.sizeUnit() != null) {
                item.setSizeValue(req.sizeValue());
                item.setSizeUnit(req.sizeUnit());
            } else if (c.hasDefaultSize() && !c.soldByWeight()) {
                item.setSizeValue(c.defaultSizeValue());
                item.setSizeUnit(c.defaultSizeUnit());
            }
            item.setPercent(req.percent());
            item.setPreferredBrand(blankToNull(req.preferredBrand()));
            item.setRawText(blankToNull(req.rawText()));
            l.getItems().add(item);
        } else {
            ShoppingListItem item = new ShoppingListItem();
            item.setShoppingList(l);
            item.setFreeText(req.freeText().trim());
            item.setQuantity(qty);
            l.getItems().add(item);
        }
        l.setUpdatedAt(Instant.now());
        return toDto(listRepo.saveAndFlush(l));
    }

    public ShoppingListDto updateItem(long listId, long itemId, BigDecimal quantity) {
        return updateItem(listId, itemId, new am.pricecompare.web.dto.ShoppingListDtos.UpdateItemRequest(quantity));
    }

    public ShoppingListDto updateItem(long listId, long itemId, am.pricecompare.web.dto.ShoppingListDtos.UpdateItemRequest req) {
        ShoppingList l = load(listId);
        ShoppingListItem item = l.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Item " + itemId + " not found in list " + listId));
        if (req.quantity() != null) {
            item.setQuantity(req.quantity());
        }
        if (req.productId() != null) {
            item.setCanonicalProduct(canonicalRepo.findById(req.productId())
                    .orElseThrow(() -> new NotFoundException("Product " + req.productId() + " not found")));
        }
        if (req.conceptCode() != null) {
            am.pricecompare.lexicon.GroceryConcept c = lexicon.get(req.conceptCode())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown concept " + req.conceptCode()));
            item.setConceptCode(c.code());
            item.setCanonicalProduct(null);
            if (req.sizeValue() == null && !Boolean.TRUE.equals(req.clearSize())) {
                item.setSizeValue(c.hasDefaultSize() && !c.soldByWeight() ? c.defaultSizeValue() : null);
                item.setSizeUnit(c.hasDefaultSize() && !c.soldByWeight() ? c.defaultSizeUnit() : null);
            }
        }
        if (Boolean.TRUE.equals(req.clearSize())) {
            item.setSizeValue(null);
            item.setSizeUnit(null);
        } else if (req.sizeValue() != null && req.sizeUnit() != null) {
            item.setSizeValue(req.sizeValue());
            item.setSizeUnit(req.sizeUnit());
        }
        if (req.preferredBrand() != null) {
            item.setPreferredBrand(blankToNull(req.preferredBrand()));
        }
        l.setUpdatedAt(Instant.now());
        return toDto(l);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public ShoppingListDto removeItem(long listId, long itemId) {
        ShoppingList l = load(listId);
        boolean removed = l.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) {
            throw new NotFoundException("Item " + itemId + " not found in list " + listId);
        }
        l.setUpdatedAt(Instant.now());
        return toDto(l);
    }

    private ShoppingList load(long id) {
        return listRepo.findById(id).orElseThrow(() -> new NotFoundException("Shopping list " + id + " not found"));
    }

    private ShoppingListDto toDto(ShoppingList l) {
        List<Long> ids = l.getItems().stream().filter(i -> i.getCanonicalProduct() != null)
                .map(i -> i.getCanonicalProduct().getId()).toList();
        Map<Long, List<OfferService.Offer>> offers = offerService.currentOffers(ids);
        List<ShoppingListItemDto> items = new ArrayList<>();
        for (ShoppingListItem i : l.getItems()) {
            CanonicalProduct c = i.getCanonicalProduct();
            if (c == null && i.getConceptCode() != null) {
                items.add(conceptItem(i));
            } else if (c == null) {
                items.add(new ShoppingListItemDto(i.getId(), null, i.getFreeText(), null, null, null, i.getFreeText(), i.getQuantity(), null,
                        null, null, null, null, null, i.getRawText(), false, false));
            } else {
                OfferService.Offer cheapest = OfferService.cheapest(offers.getOrDefault(c.getId(), List.of())).orElse(null);
                items.add(new ShoppingListItemDto(i.getId(), c.getId(), c.getName(), c.getNameEn(), OfferService.displaySize(c),
                        c.getImageUrl(), null, i.getQuantity(), OfferService.toCheapest(cheapest), i.getConceptCode(), null, null, null,
                        null, i.getRawText(), false, c.isSoldByWeight()));
            }
        }
        return new ShoppingListDto(l.getId(), l.getName(), l.getCreatedAt(), l.getUpdatedAt(), items);
    }

    /** A concept item ("milk 1 L"): shows the cheapest current price among matching products. */
    private ShoppingListItemDto conceptItem(ShoppingListItem i) {
        am.pricecompare.lexicon.GroceryConcept concept = lexicon.get(i.getConceptCode()).orElse(null);
        if (concept == null) {
            return new ShoppingListItemDto(i.getId(), null, i.getConceptCode(), null, null, null, null, i.getQuantity(), null,
                    i.getConceptCode(), i.getSizeValue(), i.getSizeUnit(), i.getPercent(), i.getPreferredBrand(), i.getRawText(), false, false);
        }
        OfferService.Offer cheapest = null;
        String image = null;
        if (!concept.requiresChoice()) {
            List<CanonicalProduct> candidates = candidateService.candidates(new am.pricecompare.lexicon.ConceptCandidateService.Criteria(
                    concept, i.getSizeValue(), i.getSizeUnit(), i.getPercent(), i.getPreferredBrand()));
            Map<Long, List<OfferService.Offer>> offers = offerService.currentOffers(candidates.stream().map(CanonicalProduct::getId).toList());
            cheapest = offers.values().stream().flatMap(List::stream).filter(OfferService.Offer::usable)
                    .min(java.util.Comparator.comparing(o -> o.price().getPrice())).orElse(null);
            image = cheapest == null ? null : cheapest.storeProduct().getCanonicalProduct().getImageUrl();
        }
        String size = concept.soldByWeight() ? "1 կգ" : am.pricecompare.normalize.ProductNormalizer.formatSize(i.getSizeValue(), i.getSizeUnit());
        return new ShoppingListItemDto(i.getId(), null, concept.nameHy(), concept.nameEn(), size, image, null, i.getQuantity(),
                OfferService.toCheapest(cheapest), concept.code(), i.getSizeValue(), i.getSizeUnit(), i.getPercent(), i.getPreferredBrand(),
                i.getRawText(), concept.requiresChoice(), concept.soldByWeight());
    }

    /** Cheapest single-store and multi-store baskets using current, non-outdated prices. */
    @Transactional(readOnly = true)
    public BasketOptimizationDto optimize(long listId, Integer maxStores) {
        ShoppingList l = load(listId);
        Map<Long, Store> stores = storeRepo.findAll().stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        List<Long> canonicalIds = l.getItems().stream().filter(i -> i.getCanonicalProduct() != null)
                .map(i -> i.getCanonicalProduct().getId()).toList();
        Map<Long, List<OfferService.Offer>> offers = offerService.currentOffers(canonicalIds);

        List<BasketOptimizer.Item> items = new ArrayList<>();
        Map<Long, List<BasketOptimizer.Offer>> itemOffers = new HashMap<>();
        Map<Long, ShoppingListItem> itemById = new HashMap<>();
        Map<Long, OfferService.Offer> offerByStoreProduct = new HashMap<>();
        List<UnavailableItemDto> unavailable = new ArrayList<>();
        for (ShoppingListItem i : l.getItems()) {
            itemById.put(i.getId(), i);
            if (i.getCanonicalProduct() == null) {
                unavailable.add(new UnavailableItemDto(i.getId(), null, i.getFreeText(), "Not linked to a product; search and add the product"));
                continue;
            }
            items.add(new BasketOptimizer.Item(i.getId(), i.getCanonicalProduct().getName(), i.getQuantity()));
            List<BasketOptimizer.Offer> list = new ArrayList<>();
            for (OfferService.Offer o : OfferService.bestPerStore(offers.getOrDefault(i.getCanonicalProduct().getId(), List.of())).values()) {
                list.add(new BasketOptimizer.Offer(o.store().getId(), o.price().getPrice(), o.storeProduct().getId()));
                offerByStoreProduct.put(o.storeProduct().getId(), o);
            }
            itemOffers.put(i.getId(), list);
        }

        BasketOptimizer.Result r = BasketOptimizer.optimize(items, itemOffers, maxStores);
        for (Long id : r.unavailableItemIds()) {
            ShoppingListItem i = itemById.get(id);
            unavailable.add(new UnavailableItemDto(id, i.getCanonicalProduct().getId(), i.getCanonicalProduct().getName(),
                    "No store currently has a recent price for this product"));
        }
        Mapper m = new Mapper(stores, itemById, offerByStoreProduct);
        List<StoreBasketDto> options = r.singleStoreRanking().stream().map(m::store).toList();
        MultiStoreBasketDto multi = new MultiStoreBasketDto(r.cheapestMultiStore().total(), r.cheapestMultiStore().storeCount(),
                r.cheapestMultiStore().complete(), r.cheapestMultiStore().stores().stream().map(m::store).toList(),
                r.cheapestMultiStore().missingItemIds());
        return new BasketOptimizationDto(l.getId(), "AMD", r.cheapestSingleStore() == null ? null : m.store(r.cheapestSingleStore()),
                options, multi, r.savings(), unavailable, m.stale, Instant.now());
    }

    private static final class Mapper {
        final Map<Long, Store> stores;
        final Map<Long, ShoppingListItem> items;
        final Map<Long, OfferService.Offer> offers;
        boolean stale;

        Mapper(Map<Long, Store> stores, Map<Long, ShoppingListItem> items, Map<Long, OfferService.Offer> offers) {
            this.stores = stores;
            this.items = items;
            this.offers = offers;
        }

        StoreBasketDto store(BasketOptimizer.StoreBasket b) {
            Store s = stores.get(b.storeId());
            List<BasketLineDto> lines = new ArrayList<>();
            for (BasketOptimizer.Line line : b.lines()) {
                ShoppingListItem i = items.get(line.itemId());
                OfferService.Offer o = offers.get(line.storeProductId());
                if (o != null && o.freshness() == Freshness.STALE) {
                    stale = true;
                }
                lines.add(new BasketLineDto(line.itemId(), i.getCanonicalProduct().getId(), i.getCanonicalProduct().getName(),
                        line.quantity(), line.unitPrice(), line.total(), o == null ? null : o.storeProduct().getExternalName(),
                        o == null ? null : o.price().getCapturedAt(), o == null ? null : o.freshness()));
            }
            return new StoreBasketDto(s.getId(), s.getName(), s.getSlug(), b.total(), b.lines().size(), b.complete(), lines,
                    b.missingItemIds());
        }
    }
}
