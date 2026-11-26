package am.pricecompare.service;

import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.matching.CategoryClassifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageRulesTest {

    @Test
    void kitchenwareNamedAfterFoodIsNotFood() {
        assertThat(CategoryClassifier.classify("Tableware and kitchen tools > Bread boxes and baskets", "Bread box", "Հացաման"))
                .isEqualTo("household");
        assertThat(CategoryClassifier.classify("SAS Home > Սպասք, խոհանոցային պարագա > Սեղանի պարագա > Ափսե, սկուտեղ",
                "Bread bin \"Danny Home\"", "Հացաման «Danny Home»")).isEqualTo("household");
        assertThat(CategoryClassifier.classify(null, null, "Հացաման")).isEqualTo("household");
        // real bread and non-food that is not in a food category keep their category
        assertThat(CategoryClassifier.classify("Products > Lavash, bread > Bread > White bread", "Bread 400g", "Հաց 400գ"))
                .isEqualTo("bread");
        assertThat(CategoryClassifier.classify("SAS Home > Hygiene", "Shampoo 400ml", "Շամպուն 400մլ")).isEqualTo("personal-care");
    }

    @Test
    void multiWordPhrasesBecomeOneSearchWord() {
        assertThat(SearchSynonyms.rewritePhrases("cooking oil")).isEqualTo("ձեթ");
        assertThat(SearchSynonyms.rewritePhrases("sour cream 20%")).startsWith("թթվասեր");
        assertThat(SearchSynonyms.rewritePhrases("milk")).isEqualTo("milk");
    }

    @Test
    void itemsWithoutSizeAreComparedAtTheMostCommonSize() {
        List<CanonicalProduct> breads = List.of(bread(85), bread(400), bread(400), bread(410), bread(500), bread(85.5));
        SmartListService.CommonSize common = SmartListService.commonSize(breads);
        assertThat(common.unit()).isEqualTo("g");
        assertThat(common.value().intValue()).isIn(400, 410);
        assertThat(common.includes(new BigDecimal("85"), "g")).isFalse();
        assertThat(common.includes(new BigDecimal("420"), "g")).isTrue();
        assertThat(SmartListService.commonSize(List.of(new CanonicalProduct()))).isNull();
    }

    @Test
    void smallOrdersPayTheSmallOrderDeliveryFee() {
        // SAS: < 5000 AMD -> 1500, otherwise 700 (within 3 km)
        var sas = new BasketOptimizer.DeliveryTerms(BigDecimal.valueOf(700), null, null, BigDecimal.valueOf(5000), BigDecimal.valueOf(1500));
        assertThat(sas.feeFor(BigDecimal.valueOf(4999))).isEqualByComparingTo("1500");
        assertThat(sas.feeFor(BigDecimal.valueOf(5000))).isEqualByComparingTo("700");
        assertThat(BasketOptimizer.DeliveryTerms.UNKNOWN.feeFor(BigDecimal.TEN)).isNull();
        var free = new BasketOptimizer.DeliveryTerms(BigDecimal.valueOf(500), BigDecimal.valueOf(10000), null);
        assertThat(free.feeFor(BigDecimal.valueOf(10000))).isEqualByComparingTo("0");
    }

    private static CanonicalProduct bread(double grams) {
        CanonicalProduct p = new CanonicalProduct();
        p.setSizeValue(BigDecimal.valueOf(grams));
        p.setSizeUnit("g");
        return p;
    }
}
