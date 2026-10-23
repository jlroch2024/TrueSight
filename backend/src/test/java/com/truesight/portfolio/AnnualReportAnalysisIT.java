package com.truesight.portfolio;

import com.truesight.company.CompanyDirectory;
import static org.mockito.ArgumentMatchers.any;
import static com.truesight.support.FakeAi.answer;
import com.truesight.ai.AiRequest;
import com.truesight.company.CompanyRepository;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AnnualReportAnalysisIT {

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyRepository companies;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;

    @MockitoBean SecClient sec;
    // Analysis goes on to ask the AI for each report. Tests never call Gemini: this fake gives its real NVIDIA answer,
    // whose quotes are not in these tests' short reports, so no relationships are saved.
    @MockitoBean GeminiClient gemini;

    @BeforeEach
    void clearSavedReportsForSampleCompanies() {
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidia()));
        companies.findByCik("0001045810").ifPresent(company -> reports.deleteByCompanyId(company.getId()));
        companies.findByCik("0000937966").ifPresent(company -> reports.deleteByCompanyId(company.getId()));
    }

    @Test
    void analysisFindsNvidia10KAndAsml20FAndShowsTheirReports() throws Exception {
        String email = "annual-report-analysis@example.com";
        Portfolio portfolio = portfolioWith(email, "NVDA", "ASML");
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021", "2026-02-25",
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm");
        mockCompanyAndReport("ASML", "ASML Holding N.V.", "0000937966", "20-F", "0001628280-26-011378", "2026-02-25",
                "https://www.sec.gov/Archives/edgar/data/937966/000162828026011378/asml-20251231.htm");

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 2, HoldingStatus.DONE);

        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("ASML Holding N.V."))
                .andExpect(jsonPath("$[0].status").value("DONE"))
                .andExpect(jsonPath("$[0].reportType").value("20-F"))
                .andExpect(jsonPath("$[0].reportDate").value("2026-02-25"))
                .andExpect(jsonPath("$[0].reportLink").value("https://www.sec.gov/Archives/edgar/data/937966/000162828026011378/asml-20251231.htm"))
                .andExpect(jsonPath("$[0].lastAnalysed").isNotEmpty())
                .andExpect(jsonPath("$[1].companyName").value("NVIDIA Corporation"))
                .andExpect(jsonPath("$[1].reportType").value("10-K"))
                .andExpect(jsonPath("$[1].reportDate").value("2026-02-25"))
                .andExpect(jsonPath("$[1].lastAnalysed").isNotEmpty());

        Long nvidiaId = companies.findByCik("0001045810").orElseThrow().getId();
        Long asmlId = companies.findByCik("0000937966").orElseThrow().getId();
        Report nvidiaReport = reports.findFirstByCompanyIdOrderByFilingDateDesc(nvidiaId).orElseThrow();
        Report asmlReport = reports.findFirstByCompanyIdOrderByFilingDateDesc(asmlId).orElseThrow();
        assertThat(nvidiaReport.getForm()).isEqualTo("10-K");
        assertThat(asmlReport.getForm()).isEqualTo("20-F");
        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[0].lastAnalysed").value(asmlReport.getCreatedAt().toString()))
                .andExpect(jsonPath("$[1].lastAnalysed").value(nvidiaReport.getCreatedAt().toString()));
    }

    @Test
    void anUnknownTickerShowsNoReportFound() throws Exception {
        String email = "annual-report-no-filing@example.com";
        Portfolio portfolio = portfolioWith(email, "UNKNOWN");
        when(sec.companyForTicker("UNKNOWN")).thenReturn(null);

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.NO_REPORT_FOUND);
        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[0].status").value("NO_REPORT_FOUND"))
                .andExpect(jsonPath("$[0].lastAnalysed").value(nullValue()));
    }

    @Test
    void aHoldingWithASavedReportButNoCompletedAnalysisHasNoLastAnalysedTime() throws Exception {
        String email = "annual-report-not-done@example.com";
        Portfolio portfolio = portfolioWith(email, "TS96");
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var company = companyDirectory.findOrCreate("TS96 Example Company " + nonce, null, "TS96", List.of());
        reports.saveAndFlush(new Report(company.getId(), "10-K", "TS96-" + nonce, LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/example", "Report text."));
        Holding holding = holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst();
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.ANALYSING);
        holdings.saveAndFlush(holding);

        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportLink").value("https://www.sec.gov/example"))
                .andExpect(jsonPath("$[0].lastAnalysed").value(nullValue()));
    }

    @Test
    void pressingAnalyseAgainReusesTheSavedCompanyReport() throws Exception {
        String email = "annual-report-reuse@example.com";
        Portfolio first = portfolioWith(email, "NVDA");
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021", "2026-02-25",
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm");
        mockMvc.perform(post("/api/portfolios/{id}/analysis", first.getId()).header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
        awaitStatuses(first.getId(), 1, HoldingStatus.DONE);

        Portfolio second = portfolioWith(email, "NVDA");
        mockMvc.perform(post("/api/portfolios/{id}/analysis", second.getId()).header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
        awaitStatuses(second.getId(), 1, HoldingStatus.DONE);

        verify(sec, times(1)).download("https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm");
        Long nvidiaId = companies.findByCik("0001045810").orElseThrow().getId();
        assertThat(reports.findAll().stream().filter(report -> report.getCompanyId().equals(nvidiaId))).hasSize(1);
    }

    @Test
    void secFailureIsSavedWithTheReason() throws Exception {
        String email = "annual-report-failure@example.com";
        Portfolio portfolio = portfolioWith(email, "NVDA");
        when(sec.companyForTicker("NVDA")).thenThrow(new SecClient.SecAccessException("The SEC could not be reached.", new RuntimeException()));

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.FAILED);
        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].statusReason").value("The SEC could not be reached."));
    }

    /** TS-90: a failure is usually the SEC or the AI being briefly unreachable, so Analyse tries it again. */
    @Test
    void pressingAnalyseAgainRetriesAFailedHolding() throws Exception {
        String email = "annual-report-retry@example.com";
        Portfolio portfolio = portfolioWith(email, "NVDA");
        when(sec.companyForTicker("NVDA")).thenThrow(new SecClient.SecAccessException("The SEC could not be reached.", new RuntimeException()));
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.FAILED);

        // The SEC is reachable again.
        reset(sec);
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021", "2026-02-25",
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm");
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.DONE);

        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[0].status").value("DONE"))
                .andExpect(jsonPath("$[0].statusReason").isEmpty())
                .andExpect(jsonPath("$[0].reportType").value("10-K"));
    }

    @Test
    void pressingAnalyseAgainRetriesCompletedHoldingsWhenNoRelationshipsWereSaved() throws Exception {
        String email = "annual-report-empty-graph-retry@example.com";
        Portfolio portfolio = portfolioWith(email, "NVDA");
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021", "2026-02-25",
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm");

        // This saved real answer has no quote in the short test report, so the first completed analysis saves no links.
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.DONE);
        Long nvidiaId = companies.findByCik("0001045810").orElseThrow().getId();
        assertThat(relationships.findByCompanyIdIn(List.of(nvidiaId))).isEmpty();

        // A later explicit Analyse retries the empty result. This answer cites the report's actual sentence.
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer("""
                {"candidates":[{"content":{"parts":[{"text":"[{\\"company\\":\\"Example Supplier\\",\\"alsoKnownAs\\":\\"\\",\\"relationship\\":\\"SUPPLIER\\",\\"provides\\":\\"components\\",\\"quote\\":\\"Report text.\\"}]"}]}}]}
                """));
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());

        awaitRelationships(nvidiaId, 1);
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()))
                .singleElement().extracting(Holding::getStatus).isEqualTo(HoldingStatus.DONE);
    }

    @Test
    void analyseIsRefusedWhileAnAnalysisIsAlreadyRunning() throws Exception {
        String email = "annual-report-running@example.com";
        Portfolio portfolio = portfolioWith(email, "NVDA");
        Holding holding = holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst();
        holding.setStatus(HoldingStatus.ANALYSING);
        holdings.save(holding);

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Analysis is already running for this portfolio."));
    }

    private Portfolio portfolioWith(String email, String... tickers) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Report Test " + System.nanoTime()));
        for (String ticker : tickers) holdings.save(new Holding(portfolio.getId(), ticker, new BigDecimal("10")));
        return portfolio;
    }

    private void mockCompanyAndReport(String ticker, String name, String cik, String form, String accession, String filingDate, String url) {
        when(sec.companyForTicker(ticker)).thenReturn(new SecClient.SecCompany(name, cik, ticker));
        when(sec.latestAnnualFiling(cik)).thenReturn(new SecClient.SecFiling(form, accession, LocalDate.parse(filingDate), url));
        when(sec.download(url)).thenReturn("<html><body><h1>Business</h1><p>Report text.</p></body></html>");
    }

    private void awaitStatuses(Long portfolioId, int count, HoldingStatus status) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            List<Holding> rows = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
            if (rows.size() == count && rows.stream().allMatch(row -> row.getStatus() == status)) return;
            Thread.sleep(25);
        }
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolioId))
                .hasSize(count).allMatch(row -> row.getStatus() == status);
    }

    private void awaitRelationships(Long companyId, int count) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (relationships.findByCompanyIdIn(List.of(companyId)).size() == count) return;
            Thread.sleep(25);
        }
        assertThat(relationships.findByCompanyIdIn(List.of(companyId))).hasSize(count);
    }
}
