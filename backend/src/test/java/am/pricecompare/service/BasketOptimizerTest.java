package am.pricecompare.service;

import am.pricecompare.service.BasketOptimizer.Item;
import am.pricecompare.service.BasketOptimizer.Offer;
import am.pricecompare.service.BasketOptimizer.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Deterministic fixtures with fictional prices (test-only). */
class BasketOptimizerTest {

    static final long A = 1, B = 2, C = 3;

    static Offer o(long store, int price) {
        return new Offer(store, BigDecimal.valueOf(price), null);
    }

    static Item item(long id, int qty) {
        return new Item(id, "item" + id, BigDecimal.valueOf(qty));
    }

    @Test
    void findsCheapestSingleStoreAndMultiStoreBasket() {
        List<Item> items = List.of(item(10, 1), item(11, 1), item(12, 1));
        Map<Long, List<Offer>> offers = Map.of(
                10L, List.of(o(A, 500), o(B, 450), o(C, 520)),
                11L, List.of(o(A, 300), o(B, 350), o(C, 280)),
                12L, List.of(o(A, 1000), o(B, 1100), o(C, 990)));
        Result r = BasketOptimizer.optimize(items, offers, null);
        // A = 1800, B = 1900, C = 1790 -> single C
        assertThat(r.cheapestSingleStore().storeId()).isEqualTo(C);
        assertThat(r.cheapestSingleStore().total()).isEqualByComparingTo("1790");
        // multi: 450 (B) + 280 (C) + 990 (C) = 1720
        assertThat(r.cheapestMultiStore().total()).isEqualByComparingTo("1720");
        assertThat(r.cheapestMultiStore().storeCount()).isEqualTo(2);
        assertThat(r.savings()).isEqualByComparingTo("70");
        assertThat(r.unavailableItemIds()).isEmpty();
    }

    @Test
    void quantitiesMultiplyPrices() {
        Result r = BasketOptimizer.optimize(List.of(item(10, 3)), Map.of(10L, List.of(o(A, 200), o(B, 210))), null);
        assertThat(r.cheapestSingleStore().total()).isEqualByComparingTo("600");
        assertThat(r.cheapestMultiStore().total()).isEqualByComparingTo("600");
        assertThat(r.savings()).isEqualByComparingTo("0");
    }

    @Test
    void itemsUnavailableEverywhereAreReportedAndIgnored() {
        Result r = BasketOptimizer.optimize(List.of(item(10, 1), item(11, 1)),
                Map.of(10L, List.of(o(A, 100)), 11L, List.of()), null);
        assertThat(r.unavailableItemIds()).containsExactly(11L);
        assertThat(r.cheapestSingleStore().storeId()).isEqualTo(A);
        assertThat(r.cheapestMultiStore().complete()).isTrue();
    }

    @Test
    void noSingleStoreWhenNoStoreHasEverything() {
        Result r = BasketOptimizer.optimize(List.of(item(10, 1), item(11, 1)),
                Map.of(10L, List.of(o(A, 100)), 11L, List.of(o(B, 200))), null);
        assertThat(r.cheapestSingleStore()).isNull();
        assertThat(r.cheapestMultiStore().total()).isEqualByComparingTo("300");
        assertThat(r.savings()).isNull();
        assertThat(r.singleStoreRanking()).hasSize(2);
        assertThat(r.singleStoreRanking().get(0).complete()).isFalse();
    }

    @Test
    void maxStoresLimitsTheCombination() {
        List<Item> items = List.of(item(10, 1), item(11, 1), item(12, 1));
        Map<Long, List<Offer>> offers = Map.of(
                10L, List.of(o(A, 100), o(B, 150), o(C, 150)),
                11L, List.of(o(A, 150), o(B, 100), o(C, 150)),
                12L, List.of(o(A, 150), o(B, 150), o(C, 100)));
        Result unlimited = BasketOptimizer.optimize(items, offers, null);
        assertThat(unlimited.cheapestMultiStore().total()).isEqualByComparingTo("300");
        assertThat(unlimited.cheapestMultiStore().storeCount()).isEqualTo(3);
        Result two = BasketOptimizer.optimize(items, offers, 2);
        assertThat(two.cheapestMultiStore().total()).isEqualByComparingTo("350");
        assertThat(two.cheapestMultiStore().storeCount()).isEqualTo(2);
    }

    @Test
    void tiesPreferFewerStoresDeterministically() {
        Result r = BasketOptimizer.optimize(List.of(item(10, 1), item(11, 1)),
                Map.of(10L, List.of(o(A, 100), o(B, 100)), 11L, List.of(o(A, 100), o(B, 100))), null);
        assertThat(r.cheapestMultiStore().storeCount()).isEqualTo(1);
        assertThat(r.cheapestMultiStore().stores().get(0).storeId()).isEqualTo(A);
    }

    @Test
    void emptyListProducesEmptyBaskets() {
        Result r = BasketOptimizer.optimize(List.of(), Map.of(), null);
        assertThat(r.cheapestSingleStore()).isNull();
        assertThat(r.cheapestMultiStore().total()).isEqualByComparingTo("0");
    }
}
