package am.pricecompare.crawler;

import am.pricecompare.crawler.supermarketam.SupermarketAmParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupermarketAmParserTest {

    static final String BASE = "https://supermarket.am";

    @Test
    void parsesMenuWithLevel2CrawlUnits() {
        List<DiscoveredCategory> cats = SupermarketAmParser.parseMenu(Fixtures.load("supermarketam-menu.html"), BASE);
        assertThat(cats).extracting(DiscoveredCategory::externalId).contains("bakaleya_1046", "makaronnye-izdeliya_1082",
                "makarony_1089", "pshenichnaya_2765", "napitki_1050", "voda_1200");
        assertThat(cats.stream().filter(DiscoveredCategory::leaf).map(DiscoveredCategory::externalId).toList())
                .containsExactlyInAnyOrder("makaronnye-izdeliya_1082", "pshenichnaya_2765", "voda_1200");
        DiscoveredCategory makarony = cats.stream().filter(c -> c.externalId().equals("makarony_1089")).findFirst().orElseThrow();
        assertThat(makarony.parentExternalId()).isEqualTo("makaronnye-izdeliya_1082");
        assertThat(makarony.level()).isEqualTo(3);
    }

    @Test
    void parsesListingCards() {
        SupermarketAmParser.ListingPage page = SupermarketAmParser.parseListing(Fixtures.load("supermarketam-listing.html"), BASE);
        assertThat(page.total()).isEqualTo(1157);
        assertThat(page.cards()).hasSize(2);
        SupermarketAmParser.Card pasta = page.cards().get(0);
        assertThat(pasta.id()).isEqualTo("900301");
        assertThat(pasta.url()).isEqualTo("https://supermarket.am/products/_900301/");
        assertThat(pasta.price()).isEqualByComparingTo("590");
        assertThat(pasta.oldPrice()).isEqualByComparingTo("760");
        assertThat(pasta.soldByWeight()).isFalse();
        assertThat(pasta.imageUrl()).isEqualTo("https://supermarket.am/images/cache/test1.png");
        SupermarketAmParser.Card sugar = page.cards().get(1);
        assertThat(sugar.id()).isEqualTo("900302");
        assertThat(sugar.price()).isEqualByComparingTo("1375");
        assertThat(sugar.oldPrice()).isNull();
        assertThat(sugar.soldByWeight()).isTrue();
    }
}
