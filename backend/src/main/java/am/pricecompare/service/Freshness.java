package am.pricecompare.service;

import java.time.Duration;
import java.time.Instant;

/**
 * How current a price observation is. Stale and outdated prices are never presented as live:
 * clients show the age, and basket optimization ignores OUTDATED prices.
 */
public enum Freshness {
    /** 0-24 hours */
    FRESH,
    /** 24-72 hours */
    RECENT,
    /** 3-7 days */
    STALE,
    /** more than 7 days */
    OUTDATED;

    public static Freshness of(Instant capturedAt, Instant now) {
        if (capturedAt == null) {
            return OUTDATED;
        }
        long hours = Duration.between(capturedAt, now).toHours();
        if (hours < 24) {
            return FRESH;
        }
        if (hours < 72) {
            return RECENT;
        }
        if (hours < 24 * 7) {
            return STALE;
        }
        return OUTDATED;
    }

    public boolean usableForComparison() {
        return this != OUTDATED;
    }
}
