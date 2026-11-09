package com.truesight.briefing;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BriefingServiceTest {

    @Test
    void theTimeZoneTheWebsiteSendsIsTheOneTheBriefingUses() {
        assertThat(BriefingService.zoneOf("Asia/Singapore")).isEqualTo(ZoneId.of("Asia/Singapore"));
        assertThat(BriefingService.zoneOf(" America/New_York ")).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    void noTimeZoneOrOneThatIsNotRealMeansUtc() {
        assertThat(BriefingService.zoneOf(null)).isEqualTo(ZoneOffset.UTC);
        assertThat(BriefingService.zoneOf("  ")).isEqualTo(ZoneOffset.UTC);
        assertThat(BriefingService.zoneOf("Not/AZone")).isEqualTo(ZoneOffset.UTC);
        assertThat(BriefingService.zoneOf("<script>")).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void theBiggestDependencyIsTheLargestNamedRevenueOrPurchasesFigureOfTheLatestYear() {
        var apple = new com.truesight.relationship.RelationshipResponse.CompanySummary(7L, "Apple Inc.", "Apple", "AAPL");
        var report = new com.truesight.relationship.RelationshipResponse.ReportSummary("10-K",
                java.time.LocalDate.parse("2026-05-21"), "https://www.sec.gov/crus-10k.htm");
        var latest = java.time.LocalDate.parse("2026-03-28");
        var older = java.time.LocalDate.parse("2025-03-29");
        var figures = java.util.List.of(
                concentration("Customer A", null, com.truesight.report.ConcentrationMeasure.REVENUE, "0.9500", latest, report),
                concentration("Apple", apple, com.truesight.report.ConcentrationMeasure.RECEIVABLES, "0.9900", latest, report),
                concentration("Apple", apple, com.truesight.report.ConcentrationMeasure.REVENUE, "0.9100", latest, report),
                concentration("Apple", apple, com.truesight.report.ConcentrationMeasure.REVENUE, "0.9300", older, report));

        assertThat(BriefingService.biggestOf("CRUS", figures)).contains(new BriefingData.BiggestDependency(
                "CRUS", "Apple", 91, "Revenue", 2026, "10-K", java.time.LocalDate.parse("2026-05-21")));
    }

    @Test
    void aHoldingWithNoNamedFigureHasNoBiggestDependency() {
        assertThat(BriefingService.biggestOf("CRUS", java.util.List.of())).isEmpty();
    }

    private static com.truesight.report.ConcentrationResponse concentration(
            String label, com.truesight.relationship.RelationshipResponse.CompanySummary party,
            com.truesight.report.ConcentrationMeasure measure, String share, java.time.LocalDate end,
            com.truesight.relationship.RelationshipResponse.ReportSummary report) {
        return new com.truesight.report.ConcentrationResponse(label, party, com.truesight.report.ConcentrationKind.CUSTOMER,
                measure, new java.math.BigDecimal(share), null, end, report);
    }
}
