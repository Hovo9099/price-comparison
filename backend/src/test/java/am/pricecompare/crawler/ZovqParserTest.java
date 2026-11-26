package am.pricecompare.crawler;

import am.pricecompare.crawler.zovq.ZovqParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ZovqParserTest {

    static final String BASE = "https://zovq.am";

    @Test
    void readsSitemapAndKeepsOnlyLeafCategories() {
        String index = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <sitemap><loc>https://zovq.am/sitemap/test-sitemap-1.xml.gz</loc></sitemap>
                </sitemapindex>""";
        assertThat(ZovqParser.locs(index)).containsExactly("https://zovq.am/sitemap/test-sitemap-1.xml.gz");
        List<String> urls = List.of(
                "https://zovq.am/",
                "https://zovq.am/Kathnamtherq/",
                "https://zovq.am/Kathnamtherq/Kath/",
                "https://zovq.am/Kathnamtherq/Kefir/",
                "https://zovq.am/Pahatsvoner/",
                "https://zovq.am/manufacturer/Test-Producer/",
                "https://zovq.am/Datark-1/Ogtagvortsel/",
                "https://zovq.am/Kath-Testkath-3.2-1l/00-00090001",
                "https://other.example/Kath/");
        assertThat(ZovqParser.leafCategories(urls, BASE, Set.of("manufacturer", "datark-1")))
                .containsExactly("https://zovq.am/Kathnamtherq/Kath/", "https://zovq.am/Kathnamtherq/Kefir/", "https://zovq.am/Pahatsvoner/");
    }

    @Test
    void parsesListingCardsDiscountsWeightAndPagination() {
        ZovqParser.ListingPage page = ZovqParser.parseListing(Fixtures.load("zovq-listing.html"));
        assertThat(page.lastPage()).isEqualTo(3);
        assertThat(page.cards()).hasSize(4);

        ZovqParser.Card milk = page.cards().get(0);
        assertThat(milk.id()).isEqualTo("00-00090001");
        assertThat(milk.url()).isEqualTo("https://zovq.am/Kath-Testkath-3.2-1l/00-00090001");
        assertThat(milk.name()).isEqualTo("Կաթ «Թեստկաթ» 3.2% 1լ");
        assertThat(milk.price()).isEqualByComparingTo("1540");
        assertThat(milk.listPrice()).isNull();
        assertThat(milk.soldByWeight()).isFalse();
        assertThat(milk.shopwareId()).isEqualTo("0123456789abcdef0123456789abcdef");

        ZovqParser.Card onion = page.cards().get(1);
        assertThat(onion.id()).isEqualTo("CB-00090002");
        assertThat(onion.price()).isEqualByComparingTo("480");
        assertThat(onion.listPrice()).isEqualByComparingTo("600");
        assertThat(onion.soldByWeight()).isTrue();          // bare "կգ": price per kg

        assertThat(page.cards().get(2).soldByWeight()).isFalse(); // "1կգ" is a pack size
        assertThat(page.cards().get(3).price()).isNull();
    }

    @Test
    void parsesProductMicrodataWithBarcode() {
        String url = "https://zovq.am/Mvouraba-Testmvouraba-300g/CB-00090009";
        ZovqParser.Detail d = ZovqParser.parseDetail(Fixtures.load("zovq-product.html"), url);
        assertThat(d.id()).isEqualTo("CB-00090009");
        assertThat(d.name()).isEqualTo("Մուրաբա «Թեստմուրաբա» 300գ");
        assertThat(d.price()).isEqualByComparingTo("840");
        assertThat(d.currency()).isEqualTo("AMD");
        assertThat(d.gtin13()).isEqualTo("0000000900092");
        assertThat(d.sku()).isEqualTo("ЦБ-00090009");
        assertThat(d.manufacturer()).isEqualTo("ԹԵՍՏ ԱՐՏԱԴՐՈՂ ՍՊԸ");
        assertThat(d.breadcrumb()).containsExactly("Պահածոներ", "Մուրաբա");
        assertThat(ZovqParser.parseDetail("<html><body><h1>no offer</h1></body></html>", url)).isNull();
    }
}
