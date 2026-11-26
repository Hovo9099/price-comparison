package am.pricecompare.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsRulesTest {

    // Excerpt modelled on the real sas.am / carrefour.am robots.txt files
    static final String ROBOTS = """
            User-Agent: SemrushBot
            Disallow: /

            User-Agent: *
            Disallow: /bitrix/
            Disallow: */search/
            Disallow: /*PAGEN
            Disallow: /*index.php$
            Allow: /catalog/special/
            Disallow: /catalog/special/private
            """;

    @Test
    void appliesStarGroupWithWildcardsAndAnchors() {
        RobotsRules r = RobotsRules.parse(ROBOTS);
        assertThat(r.isAllowed("/catalog/sous/?LIMIT=60&offset=60")).isTrue();
        assertThat(r.isAllowed("/catalog/sous/?PAGEN_1=2")).isFalse();
        assertThat(r.isAllowed("/bitrix/admin")).isFalse();
        assertThat(r.isAllowed("/ru/search/?q=milk")).isFalse();
        assertThat(r.isAllowed("/index.php")).isFalse();
        assertThat(r.isAllowed("/index.php?x=1")).isTrue();
        assertThat(r.isAllowed("/catalog/special/x")).isTrue();
        assertThat(r.isAllowed("/catalog/special/private")).isFalse();
    }

    @Test
    void queryStringRuleBlocksAnyQuery() {
        RobotsRules r = RobotsRules.parse("User-agent: *\nDisallow: /*?\nDisallow: /catalog/\n");
        assertThat(r.isAllowed("/am/coca-cola-pet-1l")).isTrue();
        assertThat(r.isAllowed("/am/drinks?p=2")).isFalse();
        assertThat(r.isAllowed("/catalog/product/view/id/5")).isFalse();
    }

    @Test
    void otherAgentsGroupsAreIgnored() {
        RobotsRules r = RobotsRules.parse("User-agent: BadBot\nDisallow: /\n");
        assertThat(r.isAllowed("/anything")).isTrue();
    }
}
