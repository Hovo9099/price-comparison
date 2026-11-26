package am.pricecompare.service;

import am.pricecompare.service.BasketOptimizer.DeliveryTerms;
import am.pricecompare.service.BasketOptimizer.Item;
import am.pricecompare.service.BasketOptimizer.Offer;
import am.pricecompare.service.BasketOptimizer.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Fictional prices and fees (test-only). */
class DeliveryFeeOptimizerTest {

    static Offer o(long store, int price) {
        return new Offer(store, BigDecimal.valueOf(price), null);
    }

    static final List<Item> ITEMS = List.of(new Item(1, "a", BigDecimal.ONE), new Item(2, "b", BigDecimal.ONE));
    // Store 1: 1000 + 1000; store 2: 900 + 1100 -> splitting saves 100 before fees
    static final Map<Long, List<Offer>> OFFERS = Map.of(1L, List.of(o(1, 1000), o(2, 900)), 2L, List.of(o(1, 1000), o(2, 1100)));

    @Test
    void withoutFeesSplittingIsCheapest() {
        Result r = BasketOptimizer.optimize(ITEMS, OFFERS, null);
        assertThat(r.cheapestMultiStore().total()).isEqualByComparingTo("1900");
        assertThat(r.cheapestMultiStore().storeCount()).isEqualTo(2);
    }

    @Test
    void knownDeliveryFeesMakeSplittingNotWorthIt() {
        Map<Long, DeliveryTerms> fees = Map.of(1L, new DeliveryTerms(BigDecimal.valueOf(500), null, null),
                2L, new DeliveryTerms(BigDecimal.valueOf(500), null, null));
        Result r = BasketOptimizer.optimize(ITEMS, OFFERS, null, fees, true);
        // single store: 2000 + 500 = 2500; split: 1900 + 1000 = 2900 -> one store wins
        assertThat(r.cheapestMultiStore().storeCount()).isEqualTo(1);
        assertThat(r.cheapestMultiStore().total()).isEqualByComparingTo("2500");
        assertThat(r.cheapestSingleStore().deliveryFee()).isEqualByComparingTo("500");
    }

    @Test
    void freeDeliveryThresholdAndMinimumOrderAreRespected() {
        Map<Long, DeliveryTerms> fees = Map.of(1L, new DeliveryTerms(BigDecimal.valueOf(500), BigDecimal.valueOf(1500), null),
                2L, new DeliveryTerms(BigDecimal.valueOf(300), null, BigDecimal.valueOf(1500)));
        Result r = BasketOptimizer.optimize(ITEMS, OFFERS, null, fees, true);
        // store 1 alone: 2000 >= 1500 -> free delivery; any split leaves store 2 below its 1500 minimum
        assertThat(r.cheapestMultiStore().total()).isEqualByComparingTo("2000");
        assertThat(r.cheapestMultiStore().stores().get(0).storeId()).isEqualTo(1L);
        assertThat(r.cheapestMultiStore().stores().get(0).deliveryFee()).isEqualByComparingTo("0");
    }

    @Test
    void unknownFeesAreNeverAssumed() {
        Result r = BasketOptimizer.optimize(ITEMS, OFFERS, null, Map.of(), true);
        assertThat(r.cheapestMultiStore().total()).isEqualByComparingTo("1900");
        assertThat(r.cheapestMultiStore().stores()).allMatch(s -> !s.deliveryFeeKnown() && s.deliveryFee() == null);
    }
}
