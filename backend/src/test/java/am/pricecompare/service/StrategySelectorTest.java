package am.pricecompare.service;

import am.pricecompare.service.StrategySelector.ProductPrice;
import am.pricecompare.service.StrategySelector.Selection;
import am.pricecompare.service.StrategySelector.Strategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fictional prices per litre (test-only). */
class StrategySelectorTest {

    static List<ProductPrice> eight() {
        return List.of(p(1, 500), p(2, 560), p(3, 600), p(4, 650), p(5, 700), p(6, 760), p(7, 900), p(8, 1400));
    }

    static ProductPrice p(long id, int price) {
        return new ProductPrice(id, BigDecimal.valueOf(price));
    }

    @Test
    void cheapestMayUseEveryComparableProduct() {
        assertThat(StrategySelector.select(Strategy.CHEAPEST, eight()).eligible()).hasSize(8);
    }

    @Test
    void midRangeUsesTheMainstreamPriceBand() {
        Selection s = StrategySelector.select(Strategy.MID_RANGE, eight());
        assertThat(s.eligible()).containsExactlyInAnyOrder(3L, 4L, 5L, 6L);
        assertThat(s.medianPricePerUnit()).isEqualByComparingTo("675");
        assertThat(s.percentile().get(1L)).isZero();
        assertThat(s.percentile().get(8L)).isEqualTo(100);
    }

    @Test
    void premiumUsesTheTopQuartile() {
        Selection s = StrategySelector.select(Strategy.PREMIUM, eight());
        assertThat(s.eligible()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(s.noPremiumAlternative()).isFalse();
    }

    @Test
    void withFewProductsPremiumIsTheHighestPriced() {
        assertThat(StrategySelector.select(Strategy.PREMIUM, List.of(p(1, 500), p(2, 800))).eligible()).containsExactly(2L);
        assertThat(StrategySelector.select(Strategy.MID_RANGE, List.of(p(1, 500), p(2, 800))).eligible()).hasSize(1);
    }

    @Test
    void singleProductHasNoPremiumAlternative() {
        Selection s = StrategySelector.select(Strategy.PREMIUM, List.of(p(1, 500)));
        assertThat(s.noPremiumAlternative()).isTrue();
        assertThat(s.eligible()).containsExactly(1L);
    }

    @Test
    void noProductsMeansNothingEligibleAndNoInventedPrice() {
        Selection s = StrategySelector.select(Strategy.CHEAPEST, List.of());
        assertThat(s.eligible()).isEmpty();
        assertThat(s.medianPricePerUnit()).isNull();
    }
}
