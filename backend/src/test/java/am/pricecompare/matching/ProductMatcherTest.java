package am.pricecompare.matching;

import am.pricecompare.matching.ProductMatcher.Decision;
import am.pricecompare.matching.ProductMatcher.MatchResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Test fixtures below are fictional product names used only for testing. */
class ProductMatcherTest {

    @Test
    void sameProductWithDifferentSpellingsMatches() {
        assertThat(ProductMatcher.score("Coca-Cola 1.5L", "Coca Cola 1500ml").decision()).isEqualTo(Decision.AUTO_MATCH);
        assertThat(ProductMatcher.score("Coca-Cola 1.5L", "Coca-Cola 1.5 լ").decision()).isEqualTo(Decision.AUTO_MATCH);
    }

    @Test
    void differentSizesNeverMatch() {
        MatchResult r = ProductMatcher.score("Coca-Cola 500ml", "Coca-Cola 1.5L");
        assertThat(r.decision()).isEqualTo(Decision.NO_MATCH);
        assertThat(r.reason()).contains("size");
    }

    @Test
    void differentFatContentNeverMatches() {
        MatchResult r = ProductMatcher.score("Milk 2.5% 1L", "Milk 3.2% 1L");
        assertThat(r.decision()).isEqualTo(Decision.NO_MATCH);
        assertThat(r.reason()).contains("percentage");
    }

    @Test
    void barcodeIsDecisive() {
        ProductFeatures a = ProductFeatures.of("Կաթ «Պարմալատ» 3.5% 1լ", null, null, "8001234567890", null, null, false);
        ProductFeatures b = ProductFeatures.of("Молоко Пармалат 1л", null, null, "8001234567890", null, null, false);
        MatchResult r = ProductMatcher.score(a, b);
        assertThat(r.score()).isEqualTo(1.0);
        assertThat(r.decision()).isEqualTo(Decision.AUTO_MATCH);
    }

    @Test
    void differentBarcodesDoNotMatchEvenWithSameName() {
        ProductFeatures a = ProductFeatures.of("Coca-Cola 1.5L", null, null, "1111111111111", null, null, false);
        ProductFeatures b = ProductFeatures.of("Coca-Cola 1.5L", null, null, "2222222222222", null, null, false);
        assertThat(ProductMatcher.score(a, b).decision()).isEqualTo(Decision.NO_MATCH);
    }

    @Test
    void differentBrandsDoNotMatch() {
        ProductFeatures a = ProductFeatures.of("Milk 3.2% 1L", "Milk \"Parmalat\" 3.2% 1l", "Parmalat", null, null, null, false);
        ProductFeatures b = ProductFeatures.of("Milk 3.2% 1L", "Milk \"Prostokvashino\" 3.2% 1l", "Prostokvashino", null, null, null, false);
        assertThat(ProductMatcher.score(a, b).decision()).isEqualTo(Decision.NO_MATCH);
    }

    @Test
    void crossScriptBrandMatchesViaTransliteration() {
        ProductFeatures a = ProductFeatures.of("Կաթ «Պարմալատ» 3.5% 1լ", "Milk \"Parmalat\" ultra-pasteurized 3.5% 1l",
                "Parmalat", null, null, null, false);
        ProductFeatures b = ProductFeatures.of("Կաթ «Parmalat» ուլտրապաստերիզացված 3.5% 1լ", "Milk \"Parmalat\" UHT 3.5% 1L",
                "Պարմալատ", null, null, null, false);
        MatchResult r = ProductMatcher.score(a, b);
        assertThat(r.decision()).isNotEqualTo(Decision.NO_MATCH);
        assertThat(r.reason()).contains("brand");
    }

    @Test
    void uncertainMatchGoesToReview() {
        ProductFeatures a = ProductFeatures.of("Sauce Kuhne Black Garlic 235ml", null, "Kühne", null, null, null, false);
        ProductFeatures b = ProductFeatures.of("Sauce Kuhne Garlic 235ml", null, "Kuhne", null, null, null, false);
        MatchResult r = ProductMatcher.score(a, b);
        assertThat(r.decision()).isEqualTo(Decision.REVIEW);
        assertThat(r.score()).isBetween(ProductMatcher.REVIEW_THRESHOLD, ProductMatcher.AUTO_MATCH_THRESHOLD);
    }

    @Test
    void multipackDoesNotMatchSingleBottle() {
        assertThat(ProductMatcher.score("Water Bjni 6x0.5l", "Water Bjni 0.5l").decision()).isEqualTo(Decision.NO_MATCH);
    }

    @Test
    void unrelatedProductsDoNotMatch() {
        assertThat(ProductMatcher.score("Rice Basmati 1kg", "Buckwheat 1kg").decision()).isEqualTo(Decision.NO_MATCH);
    }

    @Test
    void tokenSimilarityHandlesAbbreviations() {
        assertThat(ProductMatcher.tokenScore("ultrapast", "ultrapasteurized")).isGreaterThan(0.8);
        assertThat(ProductMatcher.levenshtein("parmalat", "parmalatt", 1)).isEqualTo(1);
    }

    @Test
    void differentQuotedProductLinesDoNotMatch() {
        ProductFeatures a = ProductFeatures.of("Wine \"Cape Spring\" Chenin Blanc, white dry 0.75l", null, null, null, null, null, false);
        ProductFeatures b = ProductFeatures.of("White wine \"Akoom Chenin Blanc\" 750ml", null, null, null, null, null, false);
        MatchResult r = ProductMatcher.score(a, b);
        assertThat(r.decision()).isEqualTo(Decision.NO_MATCH);
        assertThat(r.reason()).contains("quoted");
    }

    @Test
    void sameQuotedLineInDifferentWordOrderMatches() {
        ProductFeatures a = ProductFeatures.of("Red wine \"Matsu El Recio\" 750ml", null, null, null, null, null, false);
        ProductFeatures b = ProductFeatures.of("Wine \"Matsu\" El Recio, red dry 0.75l", null, null, null, null, null, false);
        assertThat(ProductMatcher.score(a, b).decision()).isNotEqualTo(Decision.NO_MATCH);
    }

    @Test
    void rareTokensWeighMoreThanGenericOnes() {
        ProductMatcher.TokenWeights idf = k -> k.equals("vin") || k.equals("vine") || k.equals("drai") ? 0.2 : 3.0;
        java.util.List<String> a = java.util.List.of("vine", "drai", "zorah");
        java.util.List<String> b = java.util.List.of("vine", "drai", "karas");
        assertThat(ProductMatcher.tokenSimilarity(a, b, idf)).isLessThan(ProductMatcher.tokenSimilarity(a, b));
    }

    // Real name variants observed on 2026-10-08 (Yerevan City, Supermarket.am, Carrefour, SAS)
    @Test
    void sameMilkAcrossStoresWithDescriptiveWordsAndTypos() {
        ProductFeatures yc = ProductFeatures.of("Կաթ «Մարիաննա» ուլտրապաստերիզացված 3.2% 1լ", null, "Marianna", null, null, null, false);
        ProductFeatures smam = ProductFeatures.of("Կաթ «Մարիաննա» 3.2%, 1լ", null, null, null, null, null, false);
        ProductFeatures cf = ProductFeatures.of("Մարիաննա Ուլտրապաստերացված կաթ 3.2% 1լ", null, "Marianna", null, null, null, false);
        ProductFeatures sas = ProductFeatures.of("Կաթ ուլտրապաստերիզացված «Մարիաննա» 1լ, յուղայնությունը` 3.2%", null, "Մարիաննա", null, null, null, false);
        assertThat(ProductMatcher.score(yc, smam).decision()).isEqualTo(Decision.AUTO_MATCH);
        assertThat(ProductMatcher.score(yc, cf).decision()).isEqualTo(Decision.AUTO_MATCH);
        assertThat(ProductMatcher.score(yc, sas).decision()).isEqualTo(Decision.AUTO_MATCH);
    }

    @Test
    void colaMatchesAcrossStoresButNeverColaZero() {
        ProductFeatures sas = ProductFeatures.of("Զովացուցիչ գազավորված ըմպելիք «Coca-Cola» 1.5լ", null, "Coca Cola", null, null, null, false);
        ProductFeatures smam = ProductFeatures.of("Գազավորված ըմպելիք «Կոկա կոլա» 1.5լ", null, null, null, null, null, false);
        ProductFeatures cf = ProductFeatures.of("Կոկա Կոլա 1.5լ", null, "Coca Cola", null, null, null, false);
        ProductFeatures zero = ProductFeatures.of("Զովացուցիչ գազավորված ըմպելիք «Coca Cola Zero» 1.5լ", null, "Coca Cola", null, null, null, false);
        assertThat(ProductMatcher.score(sas, smam).decision()).isEqualTo(Decision.AUTO_MATCH);
        assertThat(ProductMatcher.score(sas, cf).decision()).isEqualTo(Decision.AUTO_MATCH);
        assertThat(ProductMatcher.score(zero, cf).decision()).isNotEqualTo(Decision.AUTO_MATCH);
        assertThat(ProductMatcher.score(zero, sas).decision()).isNotEqualTo(Decision.AUTO_MATCH);
    }

    @Test
    void unbrandedGenericNameDoesNotJoinABrandedProduct() {
        ProductFeatures generic = ProductFeatures.of("Կաթ 3.2% 1լ", null, null, null, null, null, false);
        ProductFeatures branded = ProductFeatures.of("Կաթ «Մարիաննա» 3.2% 1լ", null, "Marianna", null, null, null, false);
        assertThat(ProductMatcher.score(generic, branded).decision()).isNotEqualTo(Decision.AUTO_MATCH);
    }

    @Test
    void regularAndSugarFreeDrinksNeverMerge() {
        MatchResult r = ProductMatcher.score(
                ProductFeatures.of("Coca-Cola 1.5L", null, "Coca-Cola", null, null, null, false),
                ProductFeatures.of("Coca-Cola Zero 1.5L", null, "Coca-Cola", null, null, null, false));
        assertThat(r.decision()).isEqualTo(Decision.NO_MATCH);
        assertThat(ProductMatcher.score("Pepsi Light 0.5l", "Pepsi 0.5l").decision()).isEqualTo(Decision.NO_MATCH);
    }

    @Test
    void butterNeverMergesWithMargarineOrSpread() {
        MatchResult r = ProductMatcher.score(
                ProductFeatures.of("Կարագ «Ռամա» 200գ", "Butter \"Rama\" 200g", "Rama", null, null, null, false),
                ProductFeatures.of("Մարգարին «Ռամա» 200գ", "Margarine \"Rama\" 200g", "Rama", null, null, null, false));
        assertThat(r.decision()).isEqualTo(Decision.NO_MATCH);
    }

    @Test
    void lactoseFreeIsADifferentProduct() {
        assertThat(ProductMatcher.score("Milk Parmalat 3.5% 1L", "Milk Parmalat lactose free 3.5% 1L").decision()).isEqualTo(Decision.NO_MATCH);
    }

    @Test
    void confidenceBandsFollowThe95And80Rule() {
        assertThat(ProductMatcher.AUTO_MATCH_THRESHOLD).isEqualTo(0.95);
        assertThat(ProductMatcher.REVIEW_THRESHOLD).isEqualTo(0.80);
    }
}
