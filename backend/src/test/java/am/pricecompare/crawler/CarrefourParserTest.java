package am.pricecompare.crawler;

import am.pricecompare.crawler.carrefour.CarrefourParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CarrefourParserTest {

    @Test
    void extractsOnlyProductUrlsFromSitemap() {
        assertThat(CarrefourParser.productUrls(Fixtures.load("carrefour-sitemap.xml"), "am"))
                .containsExactly("https://carrefour.am/am/everyday-products", "https://carrefour.am/am/test-cola-1l",
                        "https://carrefour.am/am/test-lemon");
    }

    @Test
    void parsesProductPage() {
        CarrefourParser.Product p = CarrefourParser.parseProduct(Fixtures.load("carrefour-product.html"), "https://carrefour.am/am/test-cola-1l");
        assertThat(p.name()).isEqualTo("TEST Կոլա 1լ");
        assertThat(p.sku()).isEqualTo("T5067");
        assertThat(p.entityId()).isEqualTo("9001");
        assertThat(p.price()).isEqualByComparingTo("560");
        assertThat(p.oldPrice()).isEqualByComparingTo("650");
        assertThat(p.brand()).isEqualTo("Test Cola");
        assertThat(p.available()).isTrue();
        assertThat(p.soldByWeight()).isFalse();
    }

    @Test
    void weightedProductUsesPricePerKg() {
        CarrefourParser.Product p = CarrefourParser.parseProduct(Fixtures.load("carrefour-weighted.html"), "https://carrefour.am/am/test-lemon");
        assertThat(p.soldByWeight()).isTrue();
        assertThat(p.perKgPrice()).isEqualByComparingTo("585");
        assertThat(p.price()).isEqualByComparingTo("117");
    }

    @Test
    void nonProductPageReturnsNull() {
        assertThat(CarrefourParser.parseProduct("<html><body>category</body></html>", "https://carrefour.am/am/x")).isNull();
    }
}
