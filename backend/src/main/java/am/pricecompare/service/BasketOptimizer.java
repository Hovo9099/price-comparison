package am.pricecompare.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds the cheapest way to buy a shopping list. Pure and deterministic.
 *
 * <p>Single store: the cheapest store that has every item that is available anywhere. Multi-store: every subset of
 * stores (optionally limited to {@code maxStores}) is evaluated exhaustively; within a subset each item is bought at
 * its cheapest store. The subset covering the most items wins, then the lowest total, then the fewest stores, then
 * the lowest store ids (determinism). With up to ~15 stores brute force is exact and fast (2^15 subsets).
 *
 * <p>Delivery: when {@code includeDelivery} is set, each used store adds its known fee (waived from its free-delivery
 * threshold) and subsets with a store below its minimum order are skipped, so splitting a basket is only
 * recommended when it stays cheaper after fees. Unknown fees are never assumed; baskets report them as unknown.
 */
public final class BasketOptimizer {

    private static final int MAX_EXHAUSTIVE_STORES = 15;

    private BasketOptimizer() {
    }

    public record Item(long id, String label, BigDecimal quantity) {
    }

    /** @param unitPrice price of one unit of the item's quantity (package, or kg for weighed goods) */
    public record Offer(long storeId, BigDecimal unitPrice, Long storeProductId) {
    }

    /**
     * Known delivery terms of a store; any field may be null (unknown). Orders below {@code smallOrderBelow} pay
     * {@code smallOrderFee} instead of {@code fee}.
     */
    public record DeliveryTerms(BigDecimal fee, BigDecimal freeFrom, BigDecimal minOrder, BigDecimal smallOrderBelow,
                                BigDecimal smallOrderFee) {
        public static final DeliveryTerms UNKNOWN = new DeliveryTerms(null, null, null);

        public DeliveryTerms(BigDecimal fee, BigDecimal freeFrom, BigDecimal minOrder) {
            this(fee, freeFrom, minOrder, null, null);
        }

        /** Fee for an order of this subtotal; null when unknown. */
        public BigDecimal feeFor(BigDecimal subtotal) {
            if (fee == null) {
                return null;
            }
            if (freeFrom != null && subtotal.compareTo(freeFrom) >= 0) {
                return BigDecimal.ZERO;
            }
            if (smallOrderBelow != null && smallOrderFee != null && subtotal.compareTo(smallOrderBelow) < 0) {
                return smallOrderFee;
            }
            return fee;
        }
    }

    public record Line(long itemId, long storeId, BigDecimal unitPrice, BigDecimal quantity, BigDecimal total, Long storeProductId) {
    }

    /**
     * @param subtotal    sum of the lines
     * @param deliveryFee applied fee; null when the store's fee is unknown or delivery is not included
     * @param total       subtotal + applied fee
     */
    public record StoreBasket(long storeId, BigDecimal total, List<Line> lines, List<Long> missingItemIds, boolean complete,
                              BigDecimal subtotal, BigDecimal deliveryFee, boolean deliveryFeeKnown, boolean belowMinimumOrder) {
    }

    public record MultiStoreBasket(BigDecimal total, List<StoreBasket> stores, List<Long> missingItemIds, boolean complete) {
        public int storeCount() {
            return stores.size();
        }
    }

    public record Result(
            StoreBasket cheapestSingleStore,
            List<StoreBasket> singleStoreRanking,
            MultiStoreBasket cheapestMultiStore,
            List<Long> unavailableItemIds,
            BigDecimal savings
    ) {
    }

    public static Result optimize(List<Item> items, Map<Long, List<Offer>> offers, Integer maxStores) {
        return optimize(items, offers, maxStores, Map.of(), false);
    }

    /**
     * @param offers          itemId -> offers (at most one per store, the caller picks the store's product)
     * @param maxStores       limit for the multi-store basket, null or <= 0 = no limit
     * @param delivery        storeId -> known delivery terms
     * @param includeDelivery add known delivery fees and respect minimum orders
     */
    public static Result optimize(List<Item> items, Map<Long, List<Offer>> offers, Integer maxStores,
                                  Map<Long, DeliveryTerms> delivery, boolean includeDelivery) {
        Set<Long> storeIds = new TreeSet<>();
        offers.values().forEach(list -> list.forEach(o -> storeIds.add(o.storeId())));
        List<Long> stores = new ArrayList<>(storeIds);

        List<Long> unavailable = new ArrayList<>();
        List<Item> coverable = new ArrayList<>();
        for (Item it : items) {
            List<Offer> o = offers.get(it.id());
            if (o == null || o.isEmpty()) {
                unavailable.add(it.id());
            } else {
                coverable.add(it);
            }
        }
        Pricing pricing = new Pricing(delivery, includeDelivery);

        List<StoreBasket> ranking = new ArrayList<>();
        for (long s : stores) {
            ranking.add(basketFor(Set.of(s), coverable, offers, pricing).stores().get(0));
        }
        ranking.sort(Comparator.comparing((StoreBasket b) -> !(b.complete() && !blocked(b, pricing)))
                .thenComparing((StoreBasket b) -> -b.lines().size())
                .thenComparing(StoreBasket::total)
                .thenComparingLong(StoreBasket::storeId));
        StoreBasket single = ranking.stream().filter(b -> b.complete() && !blocked(b, pricing)).findFirst().orElse(null);

        MultiStoreBasket bestMulti = null;
        int limit = maxStores == null || maxStores <= 0 ? stores.size() : Math.min(maxStores, stores.size());
        if (!coverable.isEmpty()) {
            if (stores.size() <= MAX_EXHAUSTIVE_STORES) {
                int n = stores.size();
                for (int mask = 1; mask < (1 << n); mask++) {
                    if (Integer.bitCount(mask) > limit) {
                        continue;
                    }
                    Set<Long> subset = new LinkedHashSet<>();
                    for (int i = 0; i < n; i++) {
                        if ((mask & (1 << i)) != 0) {
                            subset.add(stores.get(i));
                        }
                    }
                    MultiStoreBasket candidate = basketFor(subset, coverable, offers, pricing);
                    if (candidate.stores().stream().anyMatch(b -> blocked(b, pricing))) {
                        continue;
                    }
                    if (better(candidate, bestMulti)) {
                        bestMulti = candidate;
                    }
                }
            } else {
                bestMulti = basketFor(new LinkedHashSet<>(stores), coverable, offers, pricing);
            }
        }
        if (bestMulti == null) {
            bestMulti = new MultiStoreBasket(BigDecimal.ZERO, List.of(), coverable.stream().map(Item::id).toList(), coverable.isEmpty());
        }

        BigDecimal savings = single != null && bestMulti.complete()
                ? single.total().subtract(bestMulti.total()).max(BigDecimal.ZERO) : null;
        return new Result(single, ranking, bestMulti, unavailable, savings);
    }

    private record Pricing(Map<Long, DeliveryTerms> delivery, boolean include) {
        DeliveryTerms terms(long storeId) {
            return delivery.getOrDefault(storeId, DeliveryTerms.UNKNOWN);
        }
    }

    private static boolean blocked(StoreBasket b, Pricing p) {
        return p.include() && b.belowMinimumOrder();
    }

    private static boolean better(MultiStoreBasket a, MultiStoreBasket b) {
        if (b == null) {
            return true;
        }
        int coveredA = a.stores().stream().mapToInt(s -> s.lines().size()).sum();
        int coveredB = b.stores().stream().mapToInt(s -> s.lines().size()).sum();
        if (coveredA != coveredB) {
            return coveredA > coveredB;
        }
        int cmp = a.total().compareTo(b.total());
        if (cmp != 0) {
            return cmp < 0;
        }
        return a.storeCount() < b.storeCount();
    }

    /** Buys each item at its cheapest store within {@code subset}; stores that end up unused are dropped. */
    private static MultiStoreBasket basketFor(Set<Long> subset, List<Item> items, Map<Long, List<Offer>> offers, Pricing pricing) {
        Map<Long, List<Line>> byStore = new LinkedHashMap<>();
        List<Long> missing = new ArrayList<>();
        for (Item it : items) {
            Offer best = null;
            for (Offer o : offers.get(it.id())) {
                if (subset.contains(o.storeId()) && (best == null || o.unitPrice().compareTo(best.unitPrice()) < 0
                        || (o.unitPrice().compareTo(best.unitPrice()) == 0 && o.storeId() < best.storeId()))) {
                    best = o;
                }
            }
            if (best == null) {
                missing.add(it.id());
                continue;
            }
            BigDecimal total = best.unitPrice().multiply(it.quantity());
            byStore.computeIfAbsent(best.storeId(), k -> new ArrayList<>())
                    .add(new Line(it.id(), best.storeId(), best.unitPrice(), it.quantity(), total, best.storeProductId()));
        }
        List<StoreBasket> baskets = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO;
        for (Long s : subset) {
            List<Line> lines = byStore.get(s);
            if (subset.size() > 1 && (lines == null || lines.isEmpty())) {
                continue;
            }
            List<Line> l = lines == null ? List.of() : lines;
            BigDecimal subtotal = l.stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            DeliveryTerms terms = pricing.terms(s);
            BigDecimal fee = terms.feeFor(subtotal);
            boolean below = terms.minOrder() != null && subtotal.compareTo(terms.minOrder()) < 0;
            BigDecimal applied = pricing.include() && fee != null ? fee : null;
            BigDecimal total = applied == null ? subtotal : subtotal.add(applied);
            grand = grand.add(total);
            baskets.add(new StoreBasket(s, total, l, missing, missing.isEmpty(), subtotal, applied, terms.fee() != null, below));
        }
        return new MultiStoreBasket(grand, baskets, missing, missing.isEmpty());
    }
}
