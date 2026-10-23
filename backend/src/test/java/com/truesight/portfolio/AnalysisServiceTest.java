package com.truesight.portfolio;

import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.relationship.RelationshipFinder;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.user.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
        AnalysisService service = new AnalysisService(portfolios, holdings, currentUser, companies, companyRepository,
                reports, relationships, aiCalls, sec, relationshipFinder);

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
        AnalysisService service = new AnalysisService(portfolios, holdings, currentUser, companies, companyRepository,
                reports, relationships, aiCalls, sec, relationshipFinder);

        when(currentUser.id()).thenReturn(41L);
        when(portfolios.findByIdAndUserId(7L, 41L)).thenReturn(Optional.of(new Portfolio(41L, "Test")));
        Holding retryable = new Holding(7L, "NVDA", BigDecimal.TEN);
        retryable.setCompanyId(101L);
        retryable.setStatus(HoldingStatus.DONE);
        ReflectionTestUtils.setField(retryable, "id", 1L);
        Holding alreadyAnswered = new Holding(7L, "ASML", BigDecimal.TEN);
        alreadyAnswered.setCompanyId(102L);
        alreadyAnswered.setStatus(HoldingStatus.DONE);
        ReflectionTestUtils.setField(alreadyAnswered, "id", 2L);
        Holding legacyText = new Holding(7L, "ASML", BigDecimal.TEN);
        legacyText.setCompanyId(103L);
        legacyText.setStatus(HoldingStatus.DONE);
        ReflectionTestUtils.setField(legacyText, "id", 3L);
        when(holdings.findByPortfolioIdOrderByTickerAsc(7L)).thenReturn(List.of(retryable, alreadyAnswered, legacyText));

        Report incompleteReport = report(101L, 501L);
        Report completedReport = report(102L, 502L);
        Report legacyReport = report(103L, 503L);
        legacyReport.setText(legacyAsmlText());
        when(reports.findFirstByCompanyIdOrderByFilingDateDesc(101L)).thenReturn(Optional.of(incompleteReport));
        when(reports.findFirstByCompanyIdOrderByFilingDateDesc(102L)).thenReturn(Optional.of(completedReport));
        when(reports.findFirstByCompanyIdOrderByFilingDateDesc(103L)).thenReturn(Optional.of(legacyReport));
        when(relationships.existsByReportId(501L)).thenReturn(false);
        when(relationships.existsByReportId(502L)).thenReturn(false);
        when(relationships.existsByReportId(503L)).thenReturn(false);
        when(aiCalls.existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(501L, AiPurpose.FIND_RELATIONSHIPS,
                AiOutcome.ANSWERED)).thenReturn(false);
        when(aiCalls.existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(502L, AiPurpose.FIND_RELATIONSHIPS,
                AiOutcome.ANSWERED)).thenReturn(true);
        when(aiCalls.existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(503L, AiPurpose.FIND_RELATIONSHIPS,
                AiOutcome.ANSWERED)).thenReturn(true);

        assertThat(service.start(7L)).containsExactly(1L, 3L);
        assertThat(retryable.getStatus()).isEqualTo(HoldingStatus.ANALYSING);
        assertThat(alreadyAnswered.getStatus()).isEqualTo(HoldingStatus.DONE);
        assertThat(legacyText.getStatus()).isEqualTo(HoldingStatus.ANALYSING);
        verify(holdings).saveAll(List.of(retryable, legacyText));
        verify(aiCalls).existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(501L, AiPurpose.FIND_RELATIONSHIPS,
                AiOutcome.ANSWERED);
        verify(aiCalls).existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(502L, AiPurpose.FIND_RELATIONSHIPS,
                AiOutcome.ANSWERED);
        verify(aiCalls, never()).existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(503L, AiPurpose.FIND_RELATIONSHIPS,
                AiOutcome.ANSWERED);
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
