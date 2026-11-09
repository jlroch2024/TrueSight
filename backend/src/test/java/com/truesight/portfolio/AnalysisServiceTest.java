package com.truesight.portfolio;

import com.truesight.ai.AiCall;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.relationship.FoundBy;
import com.truesight.relationship.RelationshipFinder;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.config.WorkRecovery;
import com.truesight.config.WorkerHeartbeat;
import com.truesight.relationship.ReportReading;
import com.truesight.report.Report;
import com.truesight.report.ReportReader;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.report.SupplierReader;
import com.truesight.user.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalysisServiceTest {

    @Test
    void reanalysingAReportWithLegacyWrappedTextRefreshesItBeforeFindingRelationships() throws Exception {
        PortfolioRepository portfolios = mock(PortfolioRepository.class);
        HoldingRepository holdings = mock(HoldingRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        CompanyDirectory companies = mock(CompanyDirectory.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        ReportRepository reports = mock(ReportRepository.class);
        RelationshipRepository relationships = mock(RelationshipRepository.class);
        AiCallRepository aiCalls = mock(AiCallRepository.class);
        SecClient sec = mock(SecClient.class);
        RelationshipFinder relationshipFinder = mock(RelationshipFinder.class);
        AnalysisService service = new AnalysisService(portfolios, holdings, currentUser, companyRepository, reports,
                new ReportReading(aiCalls, relationships), new ReportReader(sec, companies, reports, relationshipFinder),
                mock(SupplierReader.class), mock(WorkRecovery.class), mock(WorkerHeartbeat.class), mock(com.truesight.common.DatabaseLock.class));

        Holding holding = new Holding(7L, "ASML", BigDecimal.TEN);
        holding.setStatus(HoldingStatus.ANALYSING);
        ReflectionTestUtils.setField(holding, "id", 3L);
        when(holdings.findById(3L)).thenReturn(Optional.of(holding));
        Company asml = mock(Company.class);
        when(asml.getId()).thenReturn(102L);
        when(asml.getName()).thenReturn("ASML Holding N.V.");
        when(sec.companyForTicker("ASML")).thenReturn(new SecClient.SecCompany("ASML Holding N.V.", "0000937966", "ASML"));
        when(companies.findOrCreate("ASML Holding N.V.", "0000937966", "ASML", List.of())).thenReturn(asml);

        String html;
        try (var in = getClass().getResourceAsStream("/reports/asml-20f-zeiss-lines.html")) {
            html = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String oldText = String.join("\n", org.jsoup.Jsoup.parse(html).select("div").eachText());
        Report report = new Report(102L, "20-F", "asml-test-701", LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/example/ASML-20F.html", oldText);
        ReflectionTestUtils.setField(report, "id", 701L);
        when(reports.findFirstByCompanyIdOrderByFilingDateDesc(102L)).thenReturn(Optional.of(report));
        when(sec.download("https://www.sec.gov/example/ASML-20F.html")).thenReturn(html);

        service.analyze(3L);

        assertThat(report.getText()).contains("Carl Zeiss SMT, our sole supplier of lenses, mirrors, illuminators,");
        assertThat(report.getText()).doesNotContain("Carl Zeiss SMT, our \nsole supplier");
        verify(reports).save(report);
        verify(relationshipFinder).findIn(report, asml, true);
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.DONE);
    }

    @Test
    void completedHoldingsAreSelectedForRetryIndividually() throws Exception {
        PortfolioRepository portfolios = mock(PortfolioRepository.class);
        HoldingRepository holdings = mock(HoldingRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        CompanyDirectory companies = mock(CompanyDirectory.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        ReportRepository reports = mock(ReportRepository.class);
        RelationshipRepository relationships = mock(RelationshipRepository.class);
        AiCallRepository aiCalls = mock(AiCallRepository.class);
        SecClient sec = mock(SecClient.class);
        RelationshipFinder relationshipFinder = mock(RelationshipFinder.class);
        ReportReading reading = mock(ReportReading.class);
        AnalysisService service = new AnalysisService(portfolios, holdings, currentUser, companyRepository, reports,
                reading, new ReportReader(sec, companies, reports, relationshipFinder), mock(SupplierReader.class),
                mock(WorkRecovery.class), mock(WorkerHeartbeat.class), mock(com.truesight.common.DatabaseLock.class));

        when(currentUser.id()).thenReturn(41L);
        when(portfolios.findByIdAndUserId(7L, 41L)).thenReturn(Optional.of(new Portfolio(41L, "Test")));
        Holding retryable = done(1L, "NVDA", 101L);
        Holding alreadyRead = done(2L, "ASML", 102L);
        Holding legacyText = done(3L, "ASML", 103L);
        when(holdings.findByPortfolioIdOrderByTickerAsc(7L)).thenReturn(List.of(retryable, alreadyRead, legacyText));

        Report readFromTooLittle = report(101L, 501L);
        readFromTooLittle.setFiguresReadAt(Instant.now());
        Report readInFull = report(102L, 502L);
        readInFull.setFiguresReadAt(Instant.now());
        Report legacyReport = report(103L, 503L);
        legacyReport.setText(legacyAsmlText());
        when(reports.findFirstByCompanyIdOrderByFilingDateDesc(101L)).thenReturn(Optional.of(readFromTooLittle));
        when(reports.findFirstByCompanyIdOrderByFilingDateDesc(102L)).thenReturn(Optional.of(readInFull));
        when(reports.findFirstByCompanyIdOrderByFilingDateDesc(103L)).thenReturn(Optional.of(legacyReport));
        when(reading.readFromItsFullText(readFromTooLittle)).thenReturn(false);
        when(reading.readFromItsFullText(readInFull)).thenReturn(true);

        assertThat(service.start(7L)).containsExactly(1L, 3L);
        assertThat(retryable.getStatus()).isEqualTo(HoldingStatus.ANALYSING);
        assertThat(alreadyRead.getStatus()).isEqualTo(HoldingStatus.DONE);
        assertThat(legacyText.getStatus()).isEqualTo(HoldingStatus.ANALYSING);
        verify(holdings).saveAll(List.of(retryable, legacyText));
        verify(reading, never()).readFromItsFullText(legacyReport);
    }

    // ---- TS-229: a report read from too little text is read again, once ------------------------------------------

    @Test
    void aDoneHoldingWhoseReportHasRelationshipsButWasReadFromAContentsPageIsAnalysedAgain() {
        Fixture fixture = new Fixture();
        Report report = fixture.reportWithText(601L, "10-K", "Item 1. Business\n" + "Our supplier is Example.\n".repeat(400));
        when(fixture.relationships.existsByReportIdAndFoundBy(601L, com.truesight.relationship.FoundBy.REPORT)).thenReturn(true);
        fixture.answered(601L, "TEXT:\n" + "[report 601: 107 characters]");

        assertThat(fixture.service.start(7L)).containsExactly(1L);
    }

    @Test
    void aDoneHoldingWhoseReportWasReadFromItsFullTextIsNeverAskedAgainEvenIfItFoundNothing() {
        Fixture fixture = new Fixture();
        String text = "Our supplier is Example.\n".repeat(400);
        Report report = fixture.reportWithText(602L, "10-K", text);
        when(fixture.relationships.existsByReportIdAndFoundBy(602L, com.truesight.relationship.FoundBy.REPORT)).thenReturn(false);
        int today = com.truesight.relationship.ReportSection.forAi("10-K", text).length();
        fixture.answered(602L, "TEXT:\n" + "[report 602: " + (today / 2) + " characters]");

        assertThat(fixture.service.start(7L)).isEmpty();
    }

    @Test
    void aCallWithNoMarkerCountsAsNotReadAndAReportWithRelationshipsButNoCallAtAllIsLeftAsItIs() {
        Fixture withoutMarker = new Fixture();
        withoutMarker.reportWithText(603L, "10-K", "Our supplier is Example.");
        withoutMarker.answered(603L, "A prompt recorded before markers.");
        assertThat(withoutMarker.service.start(7L)).containsExactly(1L);

        Fixture beforeCallsWereRecorded = new Fixture();
        beforeCallsWereRecorded.reportWithText(604L, "10-K", "Our supplier is Example.");
        when(beforeCallsWereRecorded.relationships.existsByReportIdAndFoundBy(604L, com.truesight.relationship.FoundBy.REPORT)).thenReturn(true);
        assertThat(beforeCallsWereRecorded.service.start(7L)).isEmpty();
    }

    /** One Done holding (id 1, company 101) in portfolio 7, with the real ReportReading over mocked repositories. */
    private static final class Fixture {
        final HoldingRepository holdings = mock(HoldingRepository.class);
        final ReportRepository reports = mock(ReportRepository.class);
        final RelationshipRepository relationships = mock(RelationshipRepository.class);
        final AiCallRepository aiCalls = mock(AiCallRepository.class);
        final AnalysisService service;

        Fixture() {
            PortfolioRepository portfolios = mock(PortfolioRepository.class);
            CurrentUser currentUser = mock(CurrentUser.class);
            when(currentUser.id()).thenReturn(41L);
            when(portfolios.findByIdAndUserId(7L, 41L)).thenReturn(Optional.of(new Portfolio(41L, "Test")));
            when(holdings.findByPortfolioIdOrderByTickerAsc(7L)).thenReturn(List.of(done(1L, "CRUS", 101L)));
            service = new AnalysisService(portfolios, holdings, currentUser, mock(CompanyRepository.class), reports,
                    new ReportReading(aiCalls, relationships), mock(ReportReader.class), mock(SupplierReader.class),
                    mock(WorkRecovery.class), mock(WorkerHeartbeat.class), mock(com.truesight.common.DatabaseLock.class));
        }

        Report reportWithText(Long id, String form, String text) {
            Report report = new Report(101L, form, "test-" + id, LocalDate.parse("2026-02-25"),
                    "https://www.sec.gov/example", text);
            ReflectionTestUtils.setField(report, "id", id);
            // These tests are about the relationships: the figures (TS-225) are read already.
            report.setFiguresReadAt(Instant.now());
            when(reports.findFirstByCompanyIdOrderByFilingDateDesc(101L)).thenReturn(Optional.of(report));
            return report;
        }

        void answered(Long reportId, String recordedPrompt) {
            AiCall call = new AiCall(AiPurpose.FIND_RELATIONSHIPS, "gemini", recordedPrompt, reportId, "[]",
                    AiOutcome.ANSWERED, null, Instant.now(), 10);
            call.setKept(0);
            when(aiCalls.findByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(reportId, AiPurpose.FIND_RELATIONSHIPS,
                    AiOutcome.ANSWERED)).thenReturn(List.of(call));
            when(aiCalls.existsByReportIdAndPurpose(reportId, AiPurpose.FIND_RELATIONSHIPS)).thenReturn(true);
        }
    }

    private static Holding done(Long id, String ticker, Long companyId) {
        Holding holding = new Holding(7L, ticker, BigDecimal.TEN);
        holding.setCompanyId(companyId);
        holding.setStatus(HoldingStatus.DONE);
        ReflectionTestUtils.setField(holding, "id", id);
        return holding;
    }

    @Test
    void aDoneHoldingWhoseReportFiguresWereNeverReadIsSelectedAndOneWhoseFiguresWereReadIsNot() {
        PortfolioRepository portfolios = mock(PortfolioRepository.class);
        HoldingRepository holdings = mock(HoldingRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        ReportRepository reports = mock(ReportRepository.class);
        ReportReading reading = mock(ReportReading.class);
        AnalysisService service = new AnalysisService(portfolios, holdings, currentUser,
                mock(CompanyRepository.class), reports, reading,
                new ReportReader(mock(SecClient.class), mock(CompanyDirectory.class), reports,
                        mock(RelationshipFinder.class)),
                mock(SupplierReader.class), mock(WorkRecovery.class), mock(WorkerHeartbeat.class), mock(com.truesight.common.DatabaseLock.class));
        when(currentUser.id()).thenReturn(41L);
        when(portfolios.findByIdAndUserId(7L, 41L)).thenReturn(Optional.of(new Portfolio(41L, "Test")));
        Holding unread = new Holding(7L, "CRUS", BigDecimal.TEN);
        unread.setCompanyId(201L);
        unread.setStatus(HoldingStatus.DONE);
        ReflectionTestUtils.setField(unread, "id", 1L);
        Holding read = new Holding(7L, "NVDA", BigDecimal.TEN);
        read.setCompanyId(202L);
        read.setStatus(HoldingStatus.DONE);
        ReflectionTestUtils.setField(read, "id", 2L);
        when(holdings.findByPortfolioIdOrderByTickerAsc(7L)).thenReturn(List.of(unread, read));
        Report unreadReport = report(201L, 601L);
        Report readReport = report(202L, 602L);
        readReport.setFiguresReadAt(java.time.Instant.now());
        when(reports.findFirstByCompanyIdOrderByFilingDateDesc(201L)).thenReturn(Optional.of(unreadReport));
        when(reports.findFirstByCompanyIdOrderByFilingDateDesc(202L)).thenReturn(Optional.of(readReport));
        // Both reports were read for relationships from their full text already: only the figures are missing.
        when(reading.readFromItsFullText(unreadReport)).thenReturn(true);
        when(reading.readFromItsFullText(readReport)).thenReturn(true);

        assertThat(service.start(7L)).containsExactly(1L);
        assertThat(unread.getStatus()).isEqualTo(HoldingStatus.ANALYSING);
        assertThat(read.getStatus()).isEqualTo(HoldingStatus.DONE);
    }

    private static Report report(Long companyId, Long id) {
        Report report = new Report(companyId, "10-K", "test-" + id, LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/example", "Report text.");
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    private String legacyAsmlText() throws Exception {
        try (var in = getClass().getResourceAsStream("/reports/asml-20f-zeiss-lines.html")) {
            String html = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return String.join("\n", org.jsoup.Jsoup.parse(html).select("div").eachText());
        }
    }
}
