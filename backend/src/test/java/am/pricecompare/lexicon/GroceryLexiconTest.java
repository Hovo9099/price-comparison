package am.pricecompare.lexicon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class GroceryLexiconTest {

    static final GroceryLexicon LEXICON = new GroceryLexicon();

    static String code(String input) {
        GroceryLexicon.Resolution r = LEXICON.resolve(input);
        return r.recognised() ? r.concepts().get(0).code() : null;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "kat, milk", "կաթ, milk", "milk, milk", "молоко, milk", "Moloko, milk",
            "dzu, eggs", "ձու, eggs", "eggs, eggs", "яйца, eggs", "havkit, eggs",
            "panir, cheese", "պանիր, cheese", "сыр, cheese",
            "karag, butter", "կարագ, butter", "butter, butter",
            "hac, bread", "հաց, bread", "хлеб, bread",
            "shakar, sugar", "շաքարավազ, sugar", "сахар, sugar",
            "dzit, oil", "ձեթ, oil",
            "mis, meat", "hav, chicken",
            "yogurt, yogurt", "йогурт, yogurt",
            "coca cola, coca-cola", "Coca-Cola, coca-cola", "կոկա կոլա, coca-cola", "кока кола, coca-cola",
            "coca cola zero, coca-cola-zero",
            "kartofil, potatoes", "картофель, potatoes",
            "pomidor, tomatoes", "լոլիկ, tomatoes", "помидоры, tomatoes",
            "margarin, margarine"
    })
    void resolvesAcrossScripts(String input, String expected) {
        assertThat(code(input)).isEqualTo(expected);
    }

    @Test
    void butterIsNotMargarine() {
        assertThat(code("karag")).isEqualTo("butter");
        assertThat(ConceptMatcher.matches(LEXICON.get("butter").orElseThrow(), "Մարգարին «Ռամա» 250գ", "Margarine \"Rama\" 250g")).isFalse();
        assertThat(ConceptMatcher.matches(LEXICON.get("butter").orElseThrow(), "Կարագ «Պրեզիդենտ» 82% 200գ", "Butter \"President\" 82% 200g")).isTrue();
        assertThat(ConceptMatcher.matches(LEXICON.get("butter").orElseThrow(), "Սպրեդ կարագի համով 180գ", null)).isFalse();
    }

    @Test
    void typosAreTolerated() {
        assertThat(code("kartofl")).isEqualTo("potatoes");
        assertThat(code("shaqarr")).isEqualTo("sugar");
    }

    @Test
    void ambiguousWordsNeedAChoice() {
        GroceryLexicon.Resolution maslo = LEXICON.resolve("масло");
        assertThat(maslo.concepts().get(0).requiresChoice()).isTrue();
        assertThat(maslo.concepts().get(0).children()).contains("butter", "oil");
        assertThat(LEXICON.get("cheese").orElseThrow().requiresChoice()).isTrue();
        assertThat(LEXICON.get("chicken").orElseThrow().children()).contains("chicken-breast", "chicken-whole");
    }

    @Test
    void extraWordsBecomeABrandHint() {
        GroceryLexicon.Resolution r = LEXICON.resolve("kat marianna");
        assertThat(r.concepts().get(0).code()).isEqualTo("milk");
        assertThat(r.hint()).isEqualTo("marianna");
    }

    @Test
    void conceptRulesSeparateVariants() {
        GroceryConcept cola = LEXICON.get("coca-cola").orElseThrow();
        assertThat(ConceptMatcher.matches(cola, "Զովացուցիչ ըմպելիք «Coca-Cola» 1.5լ", null)).isTrue();
        assertThat(ConceptMatcher.matches(cola, "Գազավորված ըմպելիք «Կոկա Կոլա» զերո 1.5լ", null)).isFalse();
        assertThat(ConceptMatcher.matches(cola, "Pepsi Cola 1.5l", null)).isFalse();
        GroceryConcept breast = LEXICON.get("chicken-breast").orElseThrow();
        GroceryConcept whole = LEXICON.get("chicken-whole").orElseThrow();
        assertThat(ConceptMatcher.matches(breast, "Հավի կրծքամիս սառեցված", "Chicken breast frozen")).isTrue();
        assertThat(ConceptMatcher.matches(whole, "Հավի կրծքամիս սառեցված", "Chicken breast frozen")).isFalse();
        GroceryConcept milk = LEXICON.get("milk").orElseThrow();
        assertThat(ConceptMatcher.matches(milk, "Կաթ կոկոսի «Lien Ying» 400մլ", "Coconut milk")).isFalse();
        assertThat(ConceptMatcher.matches(milk, "Կաթնային շոկոլադ", "Milk chocolate")).isFalse();
    }

    @Test
    void suggestionsWhileTyping() {
        assertThat(LEXICON.suggest("kar", 5)).extracting(GroceryConcept::code).contains("butter", "potatoes");
    }
}
