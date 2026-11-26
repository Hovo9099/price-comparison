package am.pricecompare.lexicon;

import am.pricecompare.domain.CanonicalProduct;
import am.pricecompare.domain.Category;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Product-acceptance rules on product names observed in real catalogs (2026-10-09). */
class ConceptCandidateRulesTest {

    static final GroceryLexicon LEXICON = new GroceryLexicon();

    static CanonicalProduct product(String name, String categorySlug, Integer size, String unit) {
        CanonicalProduct p = new CanonicalProduct();
        p.setName(name);
        Category c = new Category();
        c.setSlug(categorySlug);
        p.setCategory(c);
        if (size != null) {
            p.setSizeValue(BigDecimal.valueOf(size));
            p.setSizeUnit(unit);
        }
        return p;
    }

    static ConceptCandidateService.Criteria criteria(String code, Integer size, String unit) {
        return new ConceptCandidateService.Criteria(LEXICON.get(code).orElseThrow(), size == null ? null : BigDecimal.valueOf(size), unit, null, null);
    }

    @Test
    void wordInsideAnotherCategoryProductDoesNotCount() {
        assertThat(ConceptCandidateService.accepts(criteria("bread", null, null),
                product("Յոգուրտ «Միլկին» սալոր-հաց. 2.5% 160գր", "dairy", 160, "g"))).isFalse();
        assertThat(ConceptCandidateService.accepts(criteria("bread", null, null),
                product("Թարմ հաց 450գ", "bread", 450, "g"))).isTrue();
    }

    @Test
    void sizeWithinTenPercentOnly() {
        assertThat(ConceptCandidateService.accepts(criteria("milk", 1000, "ml"),
                product("Կաթ «Մարիաննա» 3.2% 950մլ", "dairy", 950, "ml"))).isTrue();
        assertThat(ConceptCandidateService.accepts(criteria("milk", 1000, "ml"),
                product("Կաթ «Մարիաննա» 3.2% 0.5լ", "dairy", 500, "ml"))).isFalse();
    }
}
