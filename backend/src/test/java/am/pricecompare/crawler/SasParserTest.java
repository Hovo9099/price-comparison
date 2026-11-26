package am.pricecompare.crawler;

import am.pricecompare.crawler.sas.SasParser;
import am.pricecompare.crawler.sas.SasProviderTestAccess;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SasParserTest {

    static final String BASE = "https://www.sas.am";

    @Test
    void parsesCategoryTreeWithParentsAndLeaves() {
        List<DiscoveredCategory> cats = SasParser.parseMenu(Fixtures.load("sas-menu.html"), BASE);
        assertThat(cats).extracting(DiscoveredCategory::externalId)
                .containsExactly("new_products", "prodovolstvennyy_sektor", "molochnye_produkty", "mm_gluten_free", "moloko", "syry", "armyanskiy_syr");
        DiscoveredCategory moloko = cats.stream().filter(c -> c.externalId().equals("moloko")).findFirst().orElseThrow();
        assertThat(moloko.parentExternalId()).isEqualTo("molochnye_produkty");
        assertThat(moloko.leaf()).isTrue();
        assertThat(moloko.level()).isEqualTo(3);
        DiscoveredCategory syry = cats.stream().filter(c -> c.externalId().equals("syry")).findFirst().orElseThrow();
        assertThat(syry.leaf()).isFalse();
        assertThat(cats.stream().filter(c -> c.externalId().equals("armyanskiy_syr")).findFirst().orElseThrow().parentExternalId()).isEqualTo("syry");
    }

    @Test
    void parsesListingCardsPricesDiscountsAndPagination() {
        SasParser.ListingPage page = SasParser.parseListing(Fixtures.load("sas-listing.html"), BASE);
        assertThat(page.maxOffset()).isEqualTo(120);
        assertThat(page.items()).hasSize(3);

        SasParser.ListingItem milk = page.items().get(0);
        assertThat(milk.id()).isEqualTo("900001");
        assertThat(milk.url()).isEqualTo("https://www.sas.am/catalog/moloko/900001/");
        assertThat(milk.name()).isEqualTo("TEST Կաթ «Թեստ» 3.2% 1լ");
        assertThat(milk.price()).isEqualByComparingTo("930");
        assertThat(milk.oldPrice()).isEqualByComparingTo("1080");
        assertThat(milk.unitPrice()).isEqualByComparingTo("930");
        assertThat(milk.unitPriceUnit()).isEqualTo("l");
        assertThat(milk.maxQuantity()).isEqualTo(56);
        assertThat(milk.imageUrl()).isEqualTo("https://www.sas.am/upload/test/b1.webp");
        assertThat(milk.soldByWeight()).isFalse();

        SasParser.ListingItem meat = page.items().get(1);
        assertThat(meat.soldByWeight()).isTrue();
        assertThat(meat.price()).isEqualByComparingTo("3725");
        assertThat(meat.saleStep()).isEqualByComparingTo("0.5");
        assertThat(meat.unitPrice()).isEqualByComparingTo("7450");

        SasParser.ListingItem oos = page.items().get(2);
        assertThat(oos.outOfStock()).isTrue();
        assertThat(oos.price()).isNull();
    }

    @Test
    void weightedGoodsAreNormalisedToPricePerKg() {
        SasParser.ListingPage page = SasParser.parseListing(Fixtures.load("sas-listing.html"), BASE);
        DiscoveredProduct meat = SasProviderTestAccess.fromListing(page.items().get(1), null, "Միս", Instant.now());
        assertThat(meat.soldByWeight()).isTrue();
        assertThat(meat.price()).isEqualByComparingTo("7450");
        assertThat(meat.packageUnit()).isEqualTo("kg");

        DiscoveredProduct oos = SasProviderTestAccess.fromListing(page.items().get(2), null, "Կաթ", Instant.now());
        assertThat(oos.available()).isFalse();
        assertThat(oos.price()).isNull();

        DiscoveredProduct milk = SasProviderTestAccess.fromListing(page.items().get(0), "TEST Milk \"Test\" 3.2% 1l", "Կաթ", Instant.now());
        assertThat(milk.originalPrice()).isEqualByComparingTo("1080");
        assertThat(milk.nameEn()).isEqualTo("TEST Milk \"Test\" 3.2% 1l");
        assertThat(milk.available()).isTrue();
    }

    @Test
    void parsesProductPageSkuBrandAndStock() {
        SasParser.Detail d = SasParser.parseDetail(Fixtures.load("sas-product.html"), BASE);
        assertThat(d.id()).isEqualTo("900001");
        assertThat(d.sku()).isEqualTo("123456");
        assertThat(d.brand()).isEqualTo("Թեստ ապրանքանիշ");
        assertThat(d.price()).isEqualByComparingTo("930");
        assertThat(d.oldPrice()).isEqualByComparingTo("1080");
        assertThat(d.stockQuantity()).isEqualByComparingTo("60");
        assertThat(d.imageUrl()).isEqualTo("https://www.sas.am/upload/test/big.jpg");
        assertThat(d.breadcrumbs()).containsExactly("Կատալոգ", "Կաթ");

        SasParser.ListingPage page = SasParser.parseListing(Fixtures.load("sas-listing.html"), BASE);
        DiscoveredProduct base = SasProviderTestAccess.fromListing(page.items().get(0), null, "Կաթ", Instant.now());
        DiscoveredProduct enriched = SasProviderTestAccess.enrich(base, d);
        assertThat(enriched.sku()).isEqualTo("123456");
        assertThat(enriched.brand()).isEqualTo("Թեստ");
        assertThat(enriched.detailsFetched()).isTrue();
    }
}
