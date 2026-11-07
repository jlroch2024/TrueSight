package com.truesight.history;

import com.truesight.relationship.RelationshipType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The year-by-year comparison, with no database: a report is just a date and the companies it names, companies are
 * just numbers, and the evidence is a stand-in word because only the keys and changes matter here.
 */
class HistoryTimelineTest {

    private static final long COMPANY = 1;
    private static final long TSMC = 2;
    private static final long SAMSUNG = 3;

    @Test
    void noReportsReadMeansNoYears() {
        assertThat(HistoryTimeline.years(List.of())).isEmpty();
    }

    @Test
    void theYearsRunFromTheEarliestReportsYearToTheLatestAndAYearWithoutANewReportRepeatsTheNewestOne() {
        List<HistoryResponse.Year> years = HistoryTimeline.years(List.of(
                report(COMPANY, "2024-02-21", supplier(TSMC)),
                report(COMPANY, "2026-02-25", supplier(TSMC))));

        assertThat(years).extracting(HistoryResponse.Year::year).containsExactly(2024, 2025, 2026);
        assertThat(years.get(1).relationships()).singleElement()
                .satisfies(entry -> assertThat(entry.filingDate()).hasToString("2024-02-21"));
    }

    @Test
    void everyRelationshipOfTheFirstYearIsNewAsNothingCameBefore() {
        List<HistoryResponse.Year> years = HistoryTimeline.years(List.of(
                report(COMPANY, "2024-02-21", supplier(TSMC), supplier(SAMSUNG))));

        assertThat(years).hasSize(1);
        assertThat(years.get(0).relationships()).extracting(HistoryResponse.Entry::change)
                .containsOnly(HistoryResponse.Change.NEW);
        assertThat(years.get(0).gone()).isEmpty();
    }

    @Test
    void aRelationshipTheYearBeforeAlsoNamedIsKeptAndANewOneIsNewAndOneThatIsMissingIsGone() {
        List<HistoryResponse.Year> years = HistoryTimeline.years(List.of(
                report(COMPANY, "2024-02-21", supplier(TSMC), supplier(SAMSUNG)),
                report(COMPANY, "2025-02-26", supplier(TSMC), customer(SAMSUNG))));

        HistoryResponse.Year second = years.get(1);
        assertThat(second.relationships()).extracting(HistoryResponse.Entry::key, HistoryResponse.Entry::change)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("1-2-SUPPLIER", HistoryResponse.Change.KEPT),
                        org.assertj.core.groups.Tuple.tuple("1-3-CUSTOMER", HistoryResponse.Change.NEW));
        assertThat(second.gone()).containsExactly("1-3-SUPPLIER");
    }

    @Test
    void theKeyIsTheSameInEveryYearSoARelationshipCanBeFollowed() {
        List<HistoryResponse.Year> years = HistoryTimeline.years(List.of(
                report(COMPANY, "2024-02-21", supplier(TSMC)),
                report(COMPANY, "2025-02-26", supplier(TSMC)),
                report(COMPANY, "2026-02-25", supplier(TSMC))));

        assertThat(years).allSatisfy(year -> assertThat(year.relationships()).extracting(HistoryResponse.Entry::key)
                .containsExactly("1-2-SUPPLIER"));
        assertThat(years.get(2).relationships().get(0).change()).isEqualTo(HistoryResponse.Change.KEPT);
    }

    @Test
    void aRelationshipThatComesBackAfterAYearAwayIsNewAgain() {
        List<HistoryResponse.Year> years = HistoryTimeline.years(List.of(
                report(COMPANY, "2024-02-21", supplier(TSMC)),
                report(COMPANY, "2025-02-26", supplier(SAMSUNG)),
                report(COMPANY, "2026-02-25", supplier(TSMC))));

        assertThat(years.get(2).relationships().get(0).change()).isEqualTo(HistoryResponse.Change.NEW);
        assertThat(years.get(1).gone()).containsExactly("1-2-SUPPLIER");
    }

    @Test
    void aCompanyThatHadFiledNoReportYetShowsNothingOfItThatYear() {
        long other = 9;
        List<HistoryResponse.Year> years = HistoryTimeline.years(List.of(
                report(COMPANY, "2024-02-21", supplier(TSMC)),
                report(other, "2025-02-26", supplier(SAMSUNG))));

        assertThat(years.get(0).relationships()).extracting(HistoryResponse.Entry::companyId)
                .containsExactly(COMPANY);
        assertThat(years.get(1).relationships()).extracting(HistoryResponse.Entry::companyId)
                .containsExactly(COMPANY, other);
    }

    @Test
    void aRelationshipCarriesTheReportItCameFromAndItsEvidence() {
        List<HistoryResponse.Year> years = HistoryTimeline.years(List.of(
                report(COMPANY, "2024-02-21", supplier(TSMC))));

        HistoryResponse.Entry entry = years.get(0).relationships().get(0);
        assertThat(entry.reportUrl()).isEqualTo("https://example.com/2024-02-21");
        assertThat(entry.filingDate()).hasToString("2024-02-21");
        assertThat(entry.evidence()).isEqualTo("evidence");
        assertThat(entry.provides()).isEqualTo("wafers");
        assertThat(entry.companyId()).isEqualTo(COMPANY);
        assertThat(entry.counterpartyId()).isEqualTo(TSMC);
        assertThat(entry.type()).isEqualTo(RelationshipType.SUPPLIER);
    }

    @Test
    void theSameRelationshipNamedTwiceInOneReportIsOneEntry() {
        List<HistoryResponse.Year> years = HistoryTimeline.years(List.of(
                report(COMPANY, "2024-02-21", supplier(TSMC), supplier(TSMC))));

        assertThat(years.get(0).relationships()).hasSize(1);
    }

    private static HistoryTimeline.Source report(long company, String filed, HistoryTimeline.Edge... edges) {
        return new HistoryTimeline.Source(company, LocalDate.parse(filed), "https://example.com/" + filed,
                List.of(edges));
    }

    private static HistoryTimeline.Edge supplier(long counterparty) {
        return new HistoryTimeline.Edge(counterparty, RelationshipType.SUPPLIER, "wafers", "evidence");
    }

    private static HistoryTimeline.Edge customer(long counterparty) {
        return new HistoryTimeline.Edge(counterparty, RelationshipType.CUSTOMER, "wafers", "evidence");
    }
}
