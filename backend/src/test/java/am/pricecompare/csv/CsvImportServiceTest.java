package am.pricecompare.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvImportServiceTest {

    private static CsvImportService.Row parse(String csv) throws IOException {
        try (CSVParser p = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(new StringReader(csv))) {
            Map<String, String> headers = new HashMap<>();
            p.getHeaderNames().forEach(h -> headers.put(h.toLowerCase().replace("_", ""), h));
            CSVRecord rec = p.iterator().next();
            return CsvImportService.parseRow(rec, headers, "test.csv");
        }
    }

    @Test
    void parsesAFullRow() throws IOException {
        var row = parse("""
                store,externalProductId,name,brand,barcode,price,currency,available,capturedAt
                nor-zovq,NZ-1,TEST Coca Cola 1.5L,Coca-Cola,5449000000000,"1,090",AMD,false,2026-10-01T10:00:00+04:00
                """);
        assertThat(row.store()).isEqualTo("nor-zovq");
        assertThat(row.product().price()).isEqualByComparingTo("1090");
        assertThat(row.product().available()).isFalse();
        assertThat(row.product().barcode()).isEqualTo("5449000000000");
        assertThat(row.product().capturedAt()).isEqualTo(Instant.parse("2026-10-01T06:00:00Z"));
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> parse("store,externalProductId,name,price,capturedAt\nx,1,n,-5,2026-10-01\n"))
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> parse("store,externalProductId,name,price,capturedAt\nx,1,n,10,yesterday\n"))
                .hasMessageContaining("ISO-8601");
        assertThatThrownBy(() -> parse("store,externalProductId,name,price,capturedAt\nx,1,,10,2026-10-01\n"))
                .hasMessageContaining("name");
    }

    @Test
    void acceptsDateOnlyAndLocalDateTime() {
        assertThat(CsvImportService.parseInstant("2026-10-01")).isEqualTo(Instant.parse("2026-09-30T20:00:00Z"));
        assertThat(CsvImportService.parseInstant("2026-10-01 12:30:00")).isEqualTo(Instant.parse("2026-10-01T08:30:00Z"));
    }
}
