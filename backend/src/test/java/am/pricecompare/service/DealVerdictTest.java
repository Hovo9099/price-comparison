package am.pricecompare.service;

import am.pricecompare.web.dto.DealDto.Verdict;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DealVerdictTest {

    final Instant now = Instant.now();
    final Instant old = now.minus(60, ChronoUnit.DAYS);

    Verdict v(String original, String maxPaid, Instant firstSeen) {
        return DealService.verdict(new BigDecimal(original), maxPaid == null ? null : new BigDecimal(maxPaid), firstSeen, now);
    }

    @Test
    void verdicts() {
        assertThat(v("600", "600", old)).isEqualTo(Verdict.GENUINE);
        assertThat(v("600", "595", old)).isEqualTo(Verdict.GENUINE);       // within 1 %
        assertThat(v("600", "550", old)).isEqualTo(Verdict.INFLATED);
        assertThat(v("600", "550", now.minus(5, ChronoUnit.DAYS))).isEqualTo(Verdict.UNVERIFIED);
        assertThat(v("600", null, null)).isEqualTo(Verdict.UNVERIFIED);
        assertThat(v("600", "600", now)).isEqualTo(Verdict.GENUINE);        // proven even with short history
    }
}
