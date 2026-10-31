package com.truesight.portfolio;

import com.truesight.company.CompanyDirectory;
import static org.mockito.ArgumentMatchers.any;
import static com.truesight.support.FakeAi.answer;
import com.truesight.ai.AiRequest;
import com.truesight.company.CompanyRepository;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AnnualReportAnalysisIT {

    private static final String TSMC_SENTENCE = "We utilize foundries, such as Taiwan Semiconductor Manufacturing "
            + "Company Limited, or TSMC, and Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor "
            + "wafers.";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyRepository companies;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired AiCallLog aiCallLog;

    @MockitoBean SecClient sec;
    // Tests never call Gemini; the default fake uses the saved real NVIDIA answer.
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
    void pressingAnalyseOnAnAnalysedPortfolioWithRelationshipsAsksTheAiNothing() throws Exception {
        String email = "annual-report-reuse@example.com";
        Portfolio first = portfolioWith(email, "NVDA");
        String reportUrl = "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm";
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021",
                "2026-02-25", reportUrl, TSMC_SENTENCE);
        stubRecordedNvidiaAnswer();
        mockMvc.perform(post("/api/portfolios/{id}/analysis", first.getId()).header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
        awaitStatuses(first.getId(), 1, HoldingStatus.DONE);
        Long nvidiaId = companies.findByCik("0001045810").orElseThrow().getId();
        Long reportId = reports.findFirstByCompanyIdOrderByFilingDateDesc(nvidiaId).orElseThrow().getId();
        assertThat(relationshipsFor(reportId, nvidiaId)).hasSize(2);

        Portfolio second = portfolioWith(email, "NVDA");
        mockMvc.perform(post("/api/portfolios/{id}/analysis", second.getId()).header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
        awaitStatuses(second.getId(), 1, HoldingStatus.DONE);

        verify(sec, times(1)).download(reportUrl);
        verify(gemini, times(1)).generate(any(AiRequest.class));
        assertThat(reports.findAll().stream().filter(report -> report.getCompanyId().equals(nvidiaId))).hasSize(1);
    }

    @Test
    void anAnsweredReportWithNoRelationshipsIsNotSentToTheAiAgain() throws Exception {
        String email = "annual-report-empty-answer@example.com";
        Portfolio portfolio = portfolioWith(email, "NVDA");
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021",
                "2026-02-25", "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm");
        stubRecordedNvidiaAnswer();

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.DONE);
        Long nvidiaId = companies.findByCik("0001045810").orElseThrow().getId();
        Long reportId = reports.findFirstByCompanyIdOrderByFilingDateDesc(nvidiaId).orElseThrow().getId();
        assertThat(relationshipsFor(reportId, nvidiaId)).isEmpty();

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.DONE);

        // A different portfolio must also reuse the completed empty result for this shared report.
        Portfolio second = portfolioWith(email, "NVDA");
        mockMvc.perform(post("/api/portfolios/{id}/analysis", second.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        awaitStatuses(second.getId(), 1, HoldingStatus.DONE);

        verify(gemini, times(1)).generate(any(AiRequest.class));
    }

    @Test
    void aReportWithNoCompletedAnswerIsRetriedOnceUsingTheRealNvidiaAnswer() throws Exception {
        String email = "annual-report-incomplete-answer@example.com";
        Portfolio portfolio = portfolioWith(email, "NVDA");
        var company = companyDirectory.findOrCreate("NVIDIA Corporation", "0001045810", "NVDA", List.of());
        String reportUrl = "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm";
        Report report = reports.saveAndFlush(new Report(company.getId(), "10-K", "legacy-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                LocalDate.parse("2026-02-25"), reportUrl, TSMC_SENTENCE));
        Holding holding = holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst();
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.saveAndFlush(holding);
        AiRequest unreadable = new AiRequest(AiPurpose.FIND_RELATIONSHIPS, "Earlier answer could not be read.",
                report.getId(), AiRequest.reportMarker(report.getId(), TSMC_SENTENCE));
        Long callId = aiCallLog.record(unreadable, "test-model", "{}", AiOutcome.UNREADABLE,
                "The earlier answer could not be read.", Instant.now(), 1);
        aiCallLog.attachReport(callId, report.getId());
        stubRecordedNvidiaAnswer();
        when(sec.companyForTicker("NVDA"))
                .thenReturn(new SecClient.SecCompany("NVIDIA Corporation", "0001045810", "NVDA"));

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.DONE);
        assertThat(relationshipsFor(report.getId(), company.getId())).hasSize(2);

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.DONE);
        verify(gemini, times(1)).generate(any(AiRequest.class));
    }

    // ---- TS-179 follow-up: a holding is Done when its relationships are saved; the second check comes after ----

    @Test
    void aHoldingIsDoneAsSoonAsItsRelationshipsAreSavedBeforeTheSecondCheckAnswers() throws Exception {
        String email = "annual-report-done-first@example.com";
        Portfolio portfolio = portfolioWith(email, "NVDA");
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021",
                "2026-02-25", "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm",
                TSMC_SENTENCE);
        stubRecordedNvidiaAnswer();
        CountDownLatch secondCheckAsked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(gemini.generateSecondCheck(any(AiRequest.class))).thenAnswer(invocation -> {
            secondCheckAsked.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("The second check was never released.");
            return answer(secondCheckAgreeingWithTwoClaims());
        });

        try {
            mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId())
                    .header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
            assertThat(secondCheckAsked.await(10, TimeUnit.SECONDS)).isTrue();

            // The second check is still waiting for its answer, and the holding is already Done.
            assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus())
                    .isEqualTo(HoldingStatus.DONE);
            Long nvidiaId = companies.findByCik("0001045810").orElseThrow().getId();
            Long reportId = reports.findFirstByCompanyIdOrderByFilingDateDesc(nvidiaId).orElseThrow().getId();
            assertThat(relationshipsFor(reportId, nvidiaId)).hasSize(2)
                    .allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNull());

            release.countDown();
            for (int attempt = 0; attempt < 400 && !relationshipsFor(reportId, nvidiaId).stream()
                    .allMatch(relationship -> relationship.getSecondCheck() != null); attempt++) {
                Thread.sleep(25);
            }
            assertThat(relationshipsFor(reportId, nvidiaId))
                    .allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNotNull());
        } finally {
            release.countDown();
        }
    }

    @Test
    void aFailedSecondCheckNeverChangesTheDoneHolding() throws Exception {
        String email = "annual-report-second-check-fails@example.com";
        Portfolio portfolio = portfolioWith(email, "NVDA");
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021",
                "2026-02-25", "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm",
                TSMC_SENTENCE);
        stubRecordedNvidiaAnswer();
        when(gemini.generateSecondCheck(any(AiRequest.class)))
                .thenThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null));

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId())
                .header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
        verify(gemini, timeout(10_000)).generateSecondCheck(any(AiRequest.class));
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.DONE);
        Thread.sleep(200); // the failure has been handled by now

        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[0].status").value("DONE"))
                .andExpect(jsonPath("$[0].statusReason").isEmpty());
        Long nvidiaId = companies.findByCik("0001045810").orElseThrow().getId();
        Long reportId = reports.findFirstByCompanyIdOrderByFilingDateDesc(nvidiaId).orElseThrow().getId();
        assertThat(relationshipsFor(reportId, nvidiaId)).hasSize(2)
                .allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNull());
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

    /** Gemini's response agreeing with the first two claims it is sent. */
    private static String secondCheckAgreeingWithTwoClaims() {
        String verdicts = "[{\"index\":1,\"verdict\":\"AGREES\",\"reason\":\"Current supplier.\"},"
                + "{\"index\":2,\"verdict\":\"AGREES\",\"reason\":\"Current supplier.\"}]";
        return JsonMapper.builder().build().writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", verdicts)))))));
    }

    private Portfolio portfolioWith(String email, String... tickers) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Report Test " + System.nanoTime()));
        for (String ticker : tickers) holdings.save(new Holding(portfolio.getId(), ticker, new BigDecimal("10")));
        return portfolio;
    }

    private List<com.truesight.relationship.Relationship> relationshipsFor(Long reportId, Long companyId) {
        return relationships.findByCompanyIdIn(List.of(companyId)).stream()
                .filter(relationship -> relationship.getReportId().equals(reportId))
                .toList();
    }

    private void mockCompanyAndReport(String ticker, String name, String cik, String form, String accession, String filingDate, String url) {
        mockCompanyAndReport(ticker, name, cik, form, accession, filingDate, url, "Report text.");
    }

    private void mockCompanyAndReport(String ticker, String name, String cik, String form, String accession,
                                     String filingDate, String url, String reportText) {
        when(sec.companyForTicker(ticker)).thenReturn(new SecClient.SecCompany(name, cik, ticker));
        when(sec.latestAnnualFiling(cik)).thenReturn(new SecClient.SecFiling(form, accession, LocalDate.parse(filingDate), url));
        when(sec.download(url)).thenReturn("<html><body><h1>Business</h1><p>" + reportText + "</p></body></html>");
    }

    private void stubRecordedNvidiaAnswer() {
        when(gemini.generate(any(AiRequest.class))).thenAnswer(invocation -> {
            AiRequest request = invocation.getArgument(0);
            String response = Examples.geminiNvidia();
            Long callId = aiCallLog.record(request, "test-model", response, AiOutcome.ANSWERED, null,
                    Instant.now(), 1);
            aiCallLog.attachReport(callId, request.reportId());
            return new AiAnswer(response, callId);
        });
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
}
