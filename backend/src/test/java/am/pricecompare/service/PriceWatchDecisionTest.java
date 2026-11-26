package am.pricecompare.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceWatchDecisionTest {

    private static BigDecimal p(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static PriceWatchService.Decision eval(String target, String reference, String lastNotified, String current) {
        return PriceWatchService.evaluate(p(target), p(reference), p(lastNotified), p(current));
    }

    @Test
    void withoutTargetOnlySignificantDropsNotify() {
        assertThat(eval(null, "1000", null, "990").send()).isFalse();          // 1 % – noise
        assertThat(eval(null, "1000", null, "990").reference()).isEqualByComparingTo("1000"); // reference kept
        PriceWatchService.Decision d = eval(null, "1000", null, "980");         // 2 %
        assertThat(d.send()).isTrue();
        assertThat(d.reference()).isEqualByComparingTo("980");
    }

    @Test
    void slowSuccessiveSmallDropsEventuallyNotify() {
        PriceWatchService.Decision d1 = eval(null, "1000", null, "990");
        PriceWatchService.Decision d2 = eval(null, d1.reference().toPlainString(), null, "979");
        assertThat(d2.send()).isTrue(); // 2.1 % measured against 1000, not against 990
    }

    @Test
    void referenceFollowsPriceUpSoTheNextDropNotifiesAgain() {
        PriceWatchService.Decision up = eval(null, "900", "900", "1000");
        assertThat(up.send()).isFalse();
        assertThat(up.reference()).isEqualByComparingTo("1000");
        assertThat(eval(null, "1000", "900", "950").send()).isTrue();
    }

    @Test
    void targetNotifiesOncePerNewLowAndRearmsAboveTarget() {
        assertThat(eval("500", "600", null, "520").send()).isFalse();
        PriceWatchService.Decision hit = eval("500", "520", null, "499");
        assertThat(hit.send()).isTrue();
        assertThat(eval("500", "499", "499", "499").send()).isFalse();   // same price, already told
        assertThat(eval("500", "499", "499", "450").send()).isTrue();    // new low
        PriceWatchService.Decision above = eval("500", "450", "450", "510");
        assertThat(above.send()).isFalse();
        assertThat(above.lastNotified()).isNull();                       // re-armed
        assertThat(eval("500", "510", null, "500").send()).isTrue();
    }

    @Test
    void noCurrentPriceChangesNothing() {
        PriceWatchService.Decision d = eval(null, "1000", "900", null);
        assertThat(d.send()).isFalse();
        assertThat(d.reference()).isEqualByComparingTo("1000");
        assertThat(d.lastNotified()).isEqualByComparingTo("900");
    }

    @Test
    void formatsArmenianDram() {
        assertThat(PriceWatchService.amd(new BigDecimal("1290.00"))).isEqualTo("1,290 ֏");
    }
}
