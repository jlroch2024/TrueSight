package com.truesight.portfolio;

import com.truesight.common.ApiException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The CSV rules on their own: which column is the ticker, how weights are read, and what is refused. */
class HoldingCsvReaderTest {

    final HoldingCsvReader reader = new HoldingCsvReader();

    List<HoldingCsvReader.Row> read(String csv) {
        return reader.read(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsTheSampleFileAsTenHoldings() throws Exception {
        List<HoldingCsvReader.Row> rows = reader.read(Files.newInputStream(Path.of("../docs/examples/holdings.csv")));

        assertThat(rows).hasSize(10);
        assertThat(rows.get(0)).isEqualTo(new HoldingCsvReader.Row("NVDA", new BigDecimal("10")));
    }

    @Test
    void findsASymbolColumnInAnyCapitalisationAndWithSpaces() {
        assertThat(read("Name, S Y M B O L \nApple,aapl\n")).containsExactly(new HoldingCsvReader.Row("AAPL", null));
    }

    @Test
    void usesTheFirstTickerColumnWhenThereAreTwo() {
        assertThat(read("Symbol,Ticker\nAAPL,MSFT\n")).extracting(HoldingCsvReader.Row::ticker).containsExactly("AAPL");
    }

    @Test
    void acceptsWeightsWithAndWithoutAPercentSign() {
        assertThat(read("ticker,weight\nAAPL,12.5\nMSFT,12.5%\nNVDA,\n"))
                .extracting(HoldingCsvReader.Row::weight)
                .containsExactly(new BigDecimal("12.5"), new BigDecimal("12.5"), null);
    }

    @Test
    void keepsCommasInsideQuotes() {
        assertThat(read("name,ticker\n\"Apple, Inc.\",AAPL\n")).extracting(HoldingCsvReader.Row::ticker)
                .containsExactly("AAPL");
    }

    /** A file with a ticker column and this many numbered tickers, with an empty-ticker row after each, as a spreadsheet may leave. */
    static String rows(int count, boolean withBlankRows) {
        StringBuilder csv = new StringBuilder("ticker,weight\n");
        for (int i = 1; i <= count; i++) {
            csv.append("T").append(i).append(",1\n");
            if (withBlankRows) {
                csv.append(",\n");
            }
        }
        return csv.toString();
    }

    @Test
    void aFileOfOneHundredHoldingsIsAccepted() {
        assertThat(read(rows(HoldingCsvReader.MAX_HOLDINGS, false))).hasSize(100);
    }

    @Test
    void aFileOfMoreThanOneHundredHoldingsIsRefused() {
        assertThatThrownBy(() -> read(rows(HoldingCsvReader.MAX_HOLDINGS + 1, false)))
                .isInstanceOf(ApiException.class)
                .hasMessage("A portfolio can hold at most 100 companies.");
    }

    @Test
    void rowsWithAnEmptyTickerAreNotCountedTowardsTheLimit() {
        assertThat(read(rows(HoldingCsvReader.MAX_HOLDINGS, true))).hasSize(100);
        assertThatThrownBy(() -> read(rows(HoldingCsvReader.MAX_HOLDINGS + 1, true)))
                .hasMessage("A portfolio can hold at most 100 companies.");
    }

    @Test
    void aFileWithNoTickerColumnIsRefused() {
        assertThatThrownBy(() -> read("company,weight\nApple,10\n"))
                .isInstanceOf(ApiException.class)
                .hasMessage("The file needs a ticker column.");
    }

    @Test
    void aWeightThatIsNotANumberIsRefused() {
        assertThatThrownBy(() -> read("ticker,weight\nAAPL,lots\n"))
                .hasMessage("Row 2: the weight \"lots\" is not a number.");
    }

    @Test
    void aFileWithOnlyAHeaderIsRefused() {
        assertThatThrownBy(() -> read("ticker,weight\n")).hasMessage("The file has no holdings.");
    }
}
