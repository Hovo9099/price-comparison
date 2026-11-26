package am.pricecompare.lexicon;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingListParserTest {

    static final GroceryLexicon LEXICON = new GroceryLexicon();

    static ShoppingListParser.Request adapt(String raw) {
        ShoppingListParser.Request r = ShoppingListParser.parseItem(raw);
        GroceryConcept c = LEXICON.resolve(r.phrase()).concepts().stream().findFirst().orElse(null);
        return ShoppingListParser.adaptToConcept(r, c);
    }

    @Test
    void splitsMultipleItemsInOneString() {
        assertThat(ShoppingListParser.split("kat, dzu, panir, karag, hac, shakar"))
                .containsExactly("kat", "dzu", "panir", "karag", "hac", "shakar");
        assertThat(ShoppingListParser.split("կաթ; ձու\nհաց և պանիր")).containsExactly("կաթ", "ձու", "հաց", "պանիր");
    }

    @Test
    void extractsQuantityAndSize() {
        ShoppingListParser.Request milk = adapt("2 kat 1l");
        assertThat(milk.phrase()).isEqualTo("kat");
        assertThat(milk.quantity()).isEqualByComparingTo("2");
        assertThat(milk.sizeValue()).isEqualByComparingTo("1000");
        assertThat(milk.sizeUnit()).isEqualTo("ml");

        ShoppingListParser.Request cola = adapt("coca cola 0.5l x3");
        assertThat(cola.quantity()).isEqualByComparingTo("3");
        assertThat(cola.sizeValue()).isEqualByComparingTo("500");
    }

    @Test
    void defaultSizesAndPercent() {
        ShoppingListParser.Request milk = adapt("kat 3.2%");
        assertThat(milk.sizeValue()).isEqualByComparingTo("1000");
        assertThat(milk.percent()).isEqualByComparingTo("3.2");
        assertThat(adapt("dzu").sizeValue()).isEqualByComparingTo("10");
        assertThat(adapt("dzu 6").sizeValue()).isEqualByComparingTo("6");
        assertThat(adapt("dzu 6 hat").sizeValue()).isEqualByComparingTo("6");
    }

    @Test
    void weightsOfLooseProductsAreQuantities() {
        ShoppingListParser.Request potatoes = adapt("2 kg kartofil");
        assertThat(potatoes.quantity()).isEqualByComparingTo("2");
        assertThat(potatoes.sizeValue()).isNull();
        ShoppingListParser.Request tomatoes = adapt("pomidor 500g");
        assertThat(tomatoes.quantity()).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    void countOfLoavesIsAQuantityNotAPackSize() {
        ShoppingListParser.Request bread = adapt("hac 2 hat");
        assertThat(bread.quantity()).isEqualByComparingTo("2");
        assertThat(bread.sizeValue()).isNull();
        assertThat(adapt("hac 2").quantity()).isEqualByComparingTo("2");
    }

    @Test
    void parsesTheExampleList() {
        List<String> codes = ShoppingListParser.split("kat, dzu, panir, karag, hac, shakar").stream()
                .map(s -> LEXICON.resolve(ShoppingListParser.parseItem(s).phrase()).concepts().get(0).code()).toList();
        assertThat(codes).containsExactly("milk", "eggs", "cheese", "butter", "bread", "sugar");
    }
}
