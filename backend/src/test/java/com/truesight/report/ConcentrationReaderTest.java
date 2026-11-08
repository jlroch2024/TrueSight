package com.truesight.report;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reads the real tagged figures saved in docs/examples: Cirrus Logic's 10-K for the year to 28 March 2026 and
 * NVIDIA's 10-K filed 2026-02-25. The "skipped" cases change one line of the real Cirrus file, because no real
 * filing in the examples has a figure without a party or of another type.
 */
class ConcentrationReaderTest {

    private static List<ConcentrationReader.Figure> cirrus() {
        return ConcentrationReader.read(Examples.cirrusFigures(), Examples.cirrusLabels());
    }

    private static List<ConcentrationReader.Figure> of(List<ConcentrationReader.Figure> figures, String label) {
        return figures.stream().filter(figure -> figure.label().equals(label)).toList();
    }

    @Test
    void appleHasThreeRevenueFiguresOneForEachYearTheReportTags() {
        List<ConcentrationReader.Figure> apple = of(cirrus(), "Apple, Inc.");

        assertThat(apple).hasSize(3).allSatisfy(figure -> {
            assertThat(figure.named()).isTrue();
            assertThat(figure.kind()).isEqualTo(ConcentrationKind.CUSTOMER);
            assertThat(figure.measure()).isEqualTo(ConcentrationMeasure.REVENUE);
            assertThat(figure.periodStart()).isBefore(figure.periodEnd());
        });
        assertThat(apple).extracting(ConcentrationReader.Figure::share)
                .containsExactlyInAnyOrder(new BigDecimal("0.9100"), new BigDecimal("0.8900"), new BigDecimal("0.8700"));
        assertThat(apple).extracting(ConcentrationReader.Figure::periodEnd).contains(LocalDate.parse("2026-03-28"));
    }

    @Test
    void foxconnAndLuxshareAreReceivablesFigures() {
        List<ConcentrationReader.Figure> figures = cirrus();

        assertThat(of(figures, "Foxconn")).hasSize(2).extracting(ConcentrationReader.Figure::measure)
                .containsOnly(ConcentrationMeasure.RECEIVABLES);
        assertThat(of(figures, "Foxconn")).extracting(ConcentrationReader.Figure::share)
                .containsExactlyInAnyOrder(new BigDecimal("0.3900"), new BigDecimal("0.4100"));
        assertThat(of(figures, "Luxshare")).hasSize(2).extracting(ConcentrationReader.Figure::measure)
                .containsOnly(ConcentrationMeasure.RECEIVABLES);
        assertThat(of(figures, "Luxshare")).extracting(ConcentrationReader.Figure::share)
                .containsExactlyInAnyOrder(new BigDecimal("0.2200"), new BigDecimal("0.2700"));
    }

    @Test
    void theTenLargestCustomersAreKeptButUnnamed() {
        List<ConcentrationReader.Figure> largest = of(cirrus(), "Ten Largest Customers");

        assertThat(largest).hasSize(3).allSatisfy(figure -> {
            assertThat(figure.named()).isFalse();
            assertThat(figure.measure()).isEqualTo(ConcentrationMeasure.REVENUE);
        });
        assertThat(cirrus()).hasSize(10);
    }

    @Test
    void nvidiasCustomersAreUnnamed() {
        List<ConcentrationReader.Figure> figures =
                ConcentrationReader.read(Examples.nvidiaFigures(), Examples.nvidiaLabels());

        assertThat(figures).hasSize(5).allSatisfy(figure -> {
            assertThat(figure.named()).isFalse();
            assertThat(figure.kind()).isEqualTo(ConcentrationKind.CUSTOMER);
            assertThat(figure.measure()).isEqualTo(ConcentrationMeasure.RECEIVABLES);
        });
        assertThat(figures).extracting(ConcentrationReader.Figure::label)
                .contains("Customer one", "Customer two", "Customer three");
    }

    @Test
    void aFigureWithNoPartyIsSkipped() {
        String noApple = Examples.cirrusFigures().replaceAll(
                "\\s*<xbrldi:explicitMember dimension=\"srt:MajorCustomersAxis\">crus:AppleIncMember</xbrldi:explicitMember>",
                "");

        List<ConcentrationReader.Figure> figures = ConcentrationReader.read(noApple, Examples.cirrusLabels());

        assertThat(figures).hasSize(7);
        assertThat(of(figures, "Apple, Inc.")).isEmpty();
    }

    @Test
    void aFigureOfAnotherTypeThanCustomerOrSupplierIsSkipped() {
        // Foxconn's two figures are the only ones whose context lists the member, the benchmark, then the type.
        String text = Examples.cirrusFigures();
        String foxconn = "crus:FoxconnMember</xbrldi:explicitMember>";
        StringBuilder changed = new StringBuilder(text);
        int from = 0;
        while ((from = changed.indexOf(foxconn, from)) >= 0) {
            int type = changed.indexOf("us-gaap:CustomerConcentrationRiskMember", from);
            changed.replace(type, type + "us-gaap:CustomerConcentrationRiskMember".length(),
                    "us-gaap:ProductConcentrationRiskMember");
            from = type;
        }

        List<ConcentrationReader.Figure> figures = ConcentrationReader.read(changed.toString(), Examples.cirrusLabels());

        assertThat(of(figures, "Foxconn")).isEmpty();
        assertThat(figures).hasSize(8);
    }

    @Test
    void aSupplierFigureHasTheSupplierKind() {
        String supplier = Examples.cirrusFigures().replace("us-gaap:CustomerConcentrationRiskMember",
                "us-gaap:SupplierConcentrationRiskMember");

        assertThat(ConcentrationReader.read(supplier, Examples.cirrusLabels()))
                .hasSize(10).extracting(ConcentrationReader.Figure::kind).containsOnly(ConcentrationKind.SUPPLIER);
    }

    @Test
    void aMemberWithNoLabelIsNamedFromItsOwnNameSplitAtCapitals() {
        List<ConcentrationReader.Figure> figures = ConcentrationReader.read(Examples.cirrusFigures(),
                Examples.cirrusLabels().replace("xsd#crus_AppleIncMember", "xsd#crus_ElsewhereMember"));

        assertThat(of(figures, "Apple Inc")).hasSize(3);
    }

    @Test
    void textThatIsNotXmlIsRefused() {
        assertThatThrownBy(() -> ConcentrationReader.read("not xml", Examples.cirrusLabels()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
