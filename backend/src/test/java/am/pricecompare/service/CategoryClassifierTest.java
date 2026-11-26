package am.pricecompare.service;

import am.pricecompare.matching.CategoryClassifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryClassifierTest {

    @Test
    void usesStoreTaxonomyFirst() {
        assertThat(CategoryClassifier.classify("Dairy, ice cream and ice > Milk and milk cocktail > Milk", null, "x")).isEqualTo("dairy");
        assertThat(CategoryClassifier.classify("Dairy, ice cream and ice > Ice cream and ice", null, "x")).isEqualTo("frozen");
        assertThat(CategoryClassifier.classify("Սննդամթերք > Կաթնամթերք, ձու | Products > Dairy, eggs", null, "x")).isEqualTo("dairy");
        assertThat(CategoryClassifier.classify("Non-alcoholic beverages > Juice", null, "x")).isEqualTo("drinks");
        assertThat(CategoryClassifier.classify("Alcoholic beverages > Armenian Cognac and Brandy", null, "x")).isEqualTo("alcohol");
        assertThat(CategoryClassifier.classify("Poultry and Eggs > Eggs", null, "x")).isEqualTo("eggs");
    }

    @Test
    void fallsBackToProductNames() {
        assertThat(CategoryClassifier.classify(null, "Rice Basmati 1kg", null)).isEqualTo("grains");
        assertThat(CategoryClassifier.classify(null, null, "Ձուկ սաղմոն")).isEqualTo("fish");
        assertThat(CategoryClassifier.classify(null, null, "Ձու հավի C1 10 հատ")).isEqualTo("eggs");
        assertThat(CategoryClassifier.classify(null, null, "zzz")).isEqualTo("other");
    }
}
