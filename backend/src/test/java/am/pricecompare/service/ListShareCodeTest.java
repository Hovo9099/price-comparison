package am.pricecompare.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ListShareCodeTest {

    @Test
    void normalizesTypedCodes() {
        assertThat(ListShareService.normalize("abcd-2345")).isEqualTo("ABCD2345");
        assertThat(ListShareService.normalize(" ABCD 2345 ")).isEqualTo("ABCD2345");
        assertThat(ListShareService.normalize("ABCD0OIL")).isNull();   // look-alikes are not in the alphabet
        assertThat(ListShareService.normalize("ABC")).isNull();
        assertThat(ListShareService.normalize(null)).isNull();
    }

    @Test
    void generatedCodesUseTheAlphabetAndDiffer() {
        ListShareService s = new ListShareService(null, null);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String c = s.newCode();
            assertThat(ListShareService.normalize(c)).isEqualTo(c);
            seen.add(c);
        }
        assertThat(seen).hasSize(1000);
    }
}
