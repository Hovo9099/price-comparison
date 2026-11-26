package am.pricecompare.crawler;

import am.pricecompare.crawler.parma.ParmaParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParmaParserTest {

    static final String BASE = "https://parma.am";

    @Test
    void parsesMenuHierarchyAndLeaves() {
        List<DiscoveredCategory> cats = ParmaParser.parseMenu(Fixtures.load("parma-menu.html"), BASE);
        assertThat(cats).extracting(DiscoveredCategory::externalId)
                .containsExactly("kathnamtherq-yev-dzu", "test-kath", "test-panir", "test-panir-hayakakan", "test-mrger");
        assertThat(cats.stream().filter(DiscoveredCategory::leaf).map(DiscoveredCategory::externalId).toList())
                .containsExactlyInAnyOrder("test-kath", "test-panir-hayakakan", "test-mrger");
        DiscoveredCategory armenianCheese = cats.stream().filter(c -> c.externalId().equals("test-panir-hayakakan")).findFirst().orElseThrow();
        assertThat(armenianCheese.parentExternalId()).isEqualTo("test-panir");
        assertThat(armenianCheese.level()).isEqualTo(3);
        assertThat(armenianCheese.name()).isEqualTo("Հայկական պանիր");
        assertThat(cats.get(1).parentExternalId()).isEqualTo("kathnamtherq-yev-dzu");
    }

    @Test
    void parsesCardsPricesDiscountsWeightAndPagination() {
        ParmaParser.ListingPage page = ParmaParser.parseListing(Fixtures.load("parma-listing.html"), BASE);
        assertThat(page.lastPage()).isEqualTo(3);
        assertThat(page.cards()).hasSize(3);

        ParmaParser.Card milk = page.cards().get(0);
        assertThat(milk.id()).isEqualTo("90001");
        assertThat(milk.url()).isEqualTo("https://parma.am/hy/product/product?slug=kath-testkath-3.2-1l_90001");
        assertThat(milk.name()).isEqualTo("Կաթ «Թեստկաթ» 3.2% 1լ");
        assertThat(milk.price()).isEqualByComparingTo("610");
        assertThat(milk.oldPrice()).isNull();
        assertThat(milk.soldByWeight()).isFalse();
        assertThat(milk.imageUrl()).isEqualTo("https://static.parma.am/cache/product/90001/thumb_90001.jpg");

        ParmaParser.Card cheese = page.cards().get(1);
        assertThat(cheese.price()).isEqualByComparingTo("1990");
        assertThat(cheese.oldPrice()).isEqualByComparingTo("2400");
        assertThat(cheese.discountText()).contains("2026-10-15");

        // sold by weight: the card shows the price of one 0.5 kg step; the price per kg is stored
        ParmaParser.Card apple = page.cards().get(2);
        assertThat(apple.soldByWeight()).isTrue();
        assertThat(apple.price()).isEqualByComparingTo("480");
    }
}
