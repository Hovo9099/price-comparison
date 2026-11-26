package am.pricecompare.crawler;

import am.pricecompare.crawler.yerevancity.YerevanCityParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YerevanCityParserTest {

    @Test
    void parsesTopCategories() {
        List<YerevanCityParser.TopCategory> cats = YerevanCityParser.parseTopCategories(Fixtures.load("yc-top-categories.json"));
        assertThat(cats).hasSize(3);
        assertThat(cats.get(1).adult()).isTrue();
        assertThat(cats.get(2).hidden()).isTrue();
    }

    @Test
    void flattensCategoryTreeToPaths() {
        Map<String, String> paths = YerevanCityParser.parseCategoryPaths(Fixtures.load("yc-category-tree.json"));
        assertThat(paths.get("Milk")).isEqualTo("Dairy, ice cream and ice > Milk and milk cocktail > Milk");
    }

    @Test
    void parsesProductListWithDiscountsAndWeightedGoods() {
        YerevanCityParser.ListPage page = YerevanCityParser.parseProductList(Fixtures.load("yc-products.json"));
        assertThat(page.pageCount()).isEqualTo(3);
        assertThat(page.itemCount()).isEqualTo(250);
        YerevanCityParser.ListItem milk = page.items().get(0);
        assertThat(milk.currentPrice()).isEqualByComparingTo("1090");
        assertThat(milk.originalPrice()).isEqualByComparingTo("1390");
        YerevanCityParser.ListItem cheese = page.items().get(1);
        assertThat(cheese.kilogram()).isTrue();
        assertThat(cheese.currentPrice()).isEqualByComparingTo("28990");
        assertThat(cheese.originalPrice()).isNull();
        assertThat(cheese.photo()).isNull();
    }

    @Test
    void parsesDetailsWithBarcodeAndBrand() {
        YerevanCityParser.Details d = YerevanCityParser.parseDetails(Fixtures.load("yc-product-details.json"));
        assertThat(d.barcode()).isEqualTo("0000000000017");
        assertThat(d.brand()).isEqualTo("Test");
        assertThat(d.name()).isEqualTo("TEST Milk \"Test\" 3.5% 1l");
        assertThat(d.online()).isTrue();
    }

    @Test
    void parsesStoreBranches() {
        var stores = YerevanCityParser.parseStores(Fixtures.load("yc-stores.json"));
        assertThat(stores).hasSize(2);
        assertThat(stores.get(0).latitude()).isEqualByComparingTo("40.178935");
        assertThat(stores.get(1).address()).isNull();
    }

    @Test
    void failedApiResponsesAreErrors() {
        assertThatThrownBy(() -> YerevanCityParser.parseProductList("{\"success\":false}")).hasMessageContaining("success=false");
        assertThatThrownBy(() -> YerevanCityParser.parseProductList("<html>")).isInstanceOf(IllegalStateException.class);
    }
}
