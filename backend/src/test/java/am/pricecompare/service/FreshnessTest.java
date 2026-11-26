package am.pricecompare.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessTest {

    final Instant now = Instant.parse("2026-10-08T12:00:00Z");

    @Test
    void classifiesByAge() {
        assertThat(Freshness.of(now.minus(Duration.ofHours(2)), now)).isEqualTo(Freshness.FRESH);
        assertThat(Freshness.of(now.minus(Duration.ofHours(23)), now)).isEqualTo(Freshness.FRESH);
        assertThat(Freshness.of(now.minus(Duration.ofHours(24)), now)).isEqualTo(Freshness.RECENT);
        assertThat(Freshness.of(now.minus(Duration.ofHours(71)), now)).isEqualTo(Freshness.RECENT);
        assertThat(Freshness.of(now.minus(Duration.ofDays(3)), now)).isEqualTo(Freshness.STALE);
        assertThat(Freshness.of(now.minus(Duration.ofDays(7)), now)).isEqualTo(Freshness.OUTDATED);
        assertThat(Freshness.of(null, now)).isEqualTo(Freshness.OUTDATED);
        assertThat(Freshness.OUTDATED.usableForComparison()).isFalse();
        assertThat(Freshness.STALE.usableForComparison()).isTrue();
    }
}
