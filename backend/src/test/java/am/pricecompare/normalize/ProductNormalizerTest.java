package am.pricecompare.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Coca-Cola 1.5L            | coca cola 1500ml",
            "Coca Cola 1500ml          | coca cola 1500ml",
            "Coca-Cola 1,5 լ           | coca cola 1500ml",
            "COCA-COLA  1.5 Л.         | coca cola 1500ml",
            "Կաթ «Պարմալատ» 3.5% 1լ    | կաթ պարմալատ 3.5% 1000ml",
            "Rice Basmati 1 kg         | rice basmati 1000g",
            "Պանիր 500 գր              | պանիր 500g",
            "Eggs C1 10 pcs            | eggs c1 10pcs",
    })
    void normalizesCaseSpacesPunctuationAndUnits(String input, String expected) {
        assertThat(ProductNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    void parsesVolumeSizeInMillilitres() {
        ParsedName p = ProductNormalizer.parse("Coca-Cola 1.5L");
        assertThat(p.sizeValue()).isEqualByComparingTo("1500");
        assertThat(p.sizeUnit()).isEqualTo("ml");
        assertThat(p.tokens()).containsExactly("coca", "cola");
    }

    @Test
    void parsesArmenianUnitsAndFatPercent() {
        ParsedName p = ProductNormalizer.parse("Կաթ «Պարմալատ» ուլտրապաստերիզացված 3.5% 1լ");
        assertThat(p.sizeValue()).isEqualByComparingTo("1000");
        assertThat(p.sizeUnit()).isEqualTo("ml");
        assertThat(p.percent()).isEqualByComparingTo("3.5");
        assertThat(p.quoted()).isEqualTo("Պարմալատ");
        assertThat(p.tokens()).contains("կաթ", "պարմալատ");
    }

    @Test
    void parsesMassInGrams() {
        ParsedName p = ProductNormalizer.parse("Հումուս «Esti Original Hummus» 150գ");
        assertThat(p.sizeValue()).isEqualByComparingTo("150");
        assertThat(p.sizeUnit()).isEqualTo("g");
        ParsedName kg = ProductNormalizer.parse("Rice Basmati 0.9kg");
        assertThat(kg.sizeValue()).isEqualByComparingTo("900");
        assertThat(kg.sizeUnit()).isEqualTo("g");
    }

    @Test
    void parsesMultipacks() {
        ParsedName p = ProductNormalizer.parse("Water Bjni 6x0.5l");
        assertThat(p.packCount()).isEqualTo(6);
        assertThat(p.sizeValue()).isEqualByComparingTo("500");
        ParsedName p2 = ProductNormalizer.parse("Beer 0.5l x 4");
        assertThat(p2.packCount()).isEqualTo(4);
        assertThat(p2.sizeValue()).isEqualByComparingTo("500");
    }

    @Test
    void armenianDefiniteArticleAfterUnit() {
        ParsedName p = ProductNormalizer.parse("Կապսուլային սուրճ «Իզզո» 50հատը");
        assertThat(p.sizeUnit()).isEqualTo("pcs");
        assertThat(p.sizeValue()).isEqualByComparingTo("50");
    }

    @Test
    void countOnlyProductsUsePieces() {
        ParsedName p = ProductNormalizer.parse("Ձու հավի C1 10 հատ");
        assertThat(p.sizeUnit()).isEqualTo("pcs");
        assertThat(p.sizeValue()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void transliterationMakesScriptsComparable() {
        assertThat(ProductNormalizer.brandKey("Կոկա-Կոլա")).isEqualTo(ProductNormalizer.brandKey("Coca-Cola"));
        assertThat(ProductNormalizer.brandKey("Пармалат")).isEqualTo(ProductNormalizer.brandKey("Parmalat"));
        assertThat(ProductNormalizer.brandKey("Kühne ապրանքանիշ")).isEqualTo("kuhne");
        assertThat(Transliterator.toLatin("կաթ")).isEqualTo("kat");
        assertThat(Transliterator.toLatin("բուրգեր")).isEqualTo("burger");
    }

    @Test
    void searchTextContainsLatinAndPhoneticVariants() {
        String s = ProductNormalizer.searchText("Կոկա-Կոլա 1.5լ");
        assertThat(s).contains("կոկա կոլա").contains("koka kola");
        assertThat(ProductNormalizer.phoneticPhrase("coca cola")).isEqualTo("koka kola");
    }

    @Test
    void formatsSizesForDisplay() {
        assertThat(ProductNormalizer.formatSize(new BigDecimal("1500"), "ml")).isEqualTo("1.5 լ");
        assertThat(ProductNormalizer.formatSize(new BigDecimal("500"), "g")).isEqualTo("500 գ");
    }

    @Test
    void cleansBrandNoise() {
        assertThat(ProductNormalizer.cleanBrand("Kühne ապրանքանիշ")).isEqualTo("Kühne");
    }
}
