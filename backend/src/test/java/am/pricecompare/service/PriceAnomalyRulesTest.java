package am.pricecompare.service;

import am.pricecompare.domain.PriceAnomaly.Kind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceAnomalyRulesTest {

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void ownHistory() {
        assertThat(PriceAnomalyService.classifyChange(d("1000"), d("500"))).isNull();          // a big sale is fine
        assertThat(PriceAnomalyService.classifyChange(d("1000"), d("400"))).isEqualTo(Kind.SUDDEN_DROP);
        assertThat(PriceAnomalyService.classifyChange(d("1000"), d("100"))).isEqualTo(Kind.SUDDEN_DROP); // per 100 g
        assertThat(PriceAnomalyService.classifyChange(d("1000"), d("2400"))).isNull();
        assertThat(PriceAnomalyService.classifyChange(d("1000"), d("10000"))).isEqualTo(Kind.SUDDEN_RISE); // extra 0
        assertThat(PriceAnomalyService.classifyChange(null, d("1"))).isNull();
    }

    @Test
    void market() {
        assertThat(PriceAnomalyService.classifyMarket(d("800"), d("250"))).isNull();
        assertThat(PriceAnomalyService.classifyMarket(d("800"), d("199"))).isEqualTo(Kind.BELOW_MARKET);
        assertThat(PriceAnomalyService.classifyMarket(d("800"), d("3200"))).isEqualTo(Kind.ABOVE_MARKET);
    }

    @Test
    void medianOfOddAndEven() {
        assertThat(PriceAnomalyService.median(List.of(d("3"), d("1"), d("2")))).isEqualByComparingTo("2");
        assertThat(PriceAnomalyService.median(List.of(d("4"), d("1"), d("2"), d("3")))).isEqualByComparingTo("2.5");
    }
}
