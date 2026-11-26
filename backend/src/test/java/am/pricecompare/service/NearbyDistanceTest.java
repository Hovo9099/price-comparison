package am.pricecompare.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NearbyDistanceTest {

    @Test
    void haversine() {
        // Republic Square -> Cascade, Yerevan: about 1.6 km
        assertThat(NearbyService.distanceKm(40.1777, 44.5126, 40.1916, 44.5154)).isCloseTo(1.56, within(0.1));
        assertThat(NearbyService.distanceKm(40.18, 44.51, 40.18, 44.51)).isZero();
    }
}
