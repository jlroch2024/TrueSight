package com.truesight.relationship;

import com.truesight.company.Company;
import java.time.Instant;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiCall;
import com.truesight.ai.AiAnswer;
import static org.mockito.ArgumentMatchers.any;
import static com.truesight.support.FakeAi.answer;
import com.truesight.ai.AiRequest;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Find Suppliers and Customers with AI, end to end: press Analyse, then read the relationships.
 *
 * <p>NVIDIA's real 10-K is saved in the database first, as the annual report story would have done, and Gemini is
 * replaced by a fake that gives its real answer from {@code docs/examples/gemini-nvidia.json}. Nothing here calls the
 * SEC or Gemini.
 */
@IntegrationTest
class FindSuppliersAndCustomersIT {

    private static final String NVIDIA_CIK = "0001045810";
    private static final String NVIDIA_URL =
            "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm";
    private static final String TSMC_SENTENCE = "We utilize foundries, such as Taiwan Semiconductor Manufacturing "
            + "Company Limited, or TSMC, and Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor "
            + "wafers.";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired CompanyRepository companies;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;

    @Autowired AiCallLog aiCalls;
    @Autowired AiCallRepository aiCallRows;
    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    @BeforeEach
    void saveNvidiasReport() {
        Company nvidia = companyDirectory.findOrCreate("NVIDIA Corporation", NVIDIA_CIK, "NVDA", List.of());
        reports.deleteByCompanyId(nvidia.getId()); // and, with it, any relationships found in it before
        reports.save(new Report(nvidia.getId(), "10-K", "0001045810-26-000021", LocalDate.parse("2026-02-25"),
                NVIDIA_URL, Examples.nvidia10K()));
        when(sec.companyForTicker("NVDA")).thenReturn(new SecClient.SecCompany("NVIDIA Corporation", NVIDIA_CIK, "NVDA"));
    }

    @Test
    void analysingAPortfolioHoldingNvidiaListsTsmcAsASupplierWithNvidiasOwnSentence() throws Exception {
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidia()));
        String email = "ai-tsmc@example.com";
        Portfolio portfolio = analyse(email, HoldingStatus.DONE);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].company.name").value("NVIDIA Corporation"))
                .andExpect(jsonPath("$[0].company.ticker").value("NVDA"))
                .andExpect(jsonPath("$[0].counterparty.name").value("Taiwan Semiconductor Manufacturing Company Limited"))
                .andExpect(jsonPath("$[0].type").value("SUPPLIER"))
                .andExpect(jsonPath("$[0].provides").value("semiconductor wafers"))
                .andExpect(jsonPath("$[0].evidence").value(TSMC_SENTENCE))
                .andExpect(jsonPath("$[0].report.form").value("10-K"))
                .andExpect(jsonPath("$[0].report.filingDate").value("2026-02-25"))
                .andExpect(jsonPath("$[0].report.url").value(NVIDIA_URL));
    }

    @Test
    void everyRelationshipShowsTheOtherCompanyWhichWayRoundWhatItProvidesAndTheEvidence() throws Exception {
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidia()));
        String email = "ai-every-part@example.com";
        Portfolio portfolio = analyse(email, HoldingStatus.DONE);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[*].counterparty.name", containsInAnyOrder(
                        "Taiwan Semiconductor Manufacturing Company Limited", "Samsung Electronics Co., Ltd.",
                        "SK Hynix Inc.", "Micron Technology, Inc.", "Hon Hai Precision Industry Co., Ltd.",
                        "Wistron Corporation", "Fabrinet")))
                .andExpect(jsonPath("$[*].counterparty.id", everyItem(notNullValue())))
                .andExpect(jsonPath("$[*].type", everyItem(is("SUPPLIER"))))
                .andExpect(jsonPath("$[*].provides", everyItem(not(emptyOrNullString()))))
                .andExpect(jsonPath("$[*].evidence", everyItem(not(emptyOrNullString()))));

        // The AI's alsoKnownAs is remembered as another name, so a report calling it "TSMC" finds the same company.
        Company tsmc = companyDirectory.findOrCreate("TSMC", null, null, List.of());
        assertThat(tsmc.getName()).isEqualTo("Taiwan Semiconductor Manufacturing Company Limited");
    }

    @Test
    void aRelationshipWhoseQuoteIsNotInTheReportIsNotSaved() throws Exception {
        // Gemini's real answer, with the quote for SK Hynix and Micron changed to one that is not in the report.
        String changed = Examples.geminiNvidia().replace(
                "We purchase memory from SK Hynix Inc., Micron Technology, Inc., and Samsung.",
                "We purchase memory from SK Hynix Inc. and Micron Technology, Inc. under ten-year agreements.");
        assertThat(changed).isNotEqualTo(Examples.geminiNvidia());
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(changed));
        String email = "ai-bad-quote@example.com";
        Portfolio portfolio = analyse(email, HoldingStatus.DONE);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].counterparty.name", not(hasItem("SK Hynix Inc."))))
                .andExpect(jsonPath("$[*].counterparty.name",
                        not(hasItem("Micron Technology, Inc."))));
    }

    @Test
    void analysingACompanyASecondTimeMakesNoNewRequestToTheAi() throws Exception {
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidia()));
        analyse("ai-first-time@example.com", HoldingStatus.DONE);

        // A different user's portfolio: reports are public, so NVIDIA's relationships are reused.
        String email = "ai-second-time@example.com";
        Portfolio second = analyse(email, HoldingStatus.DONE);

        verify(gemini, times(1)).generate(any(AiRequest.class));
        mockMvc.perform(get("/api/portfolios/{id}/relationships", second.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    void onlyTheBusinessAndRiskSectionsAreSentToTheAi() throws Exception {
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidia()));
        analyse("ai-sections@example.com", HoldingStatus.DONE);

        ArgumentCaptor<AiRequest> prompt = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini).generate(prompt.capture());
        assertThat(prompt.getValue().prompt())
                .startsWith("Below is part of NVIDIA Corporation's annual report (10-K).")
                .contains("copied exactly from the text")
                .contains("TEXT:\nItem 1. Business\nOur Company")
                .contains("Item 1A. Risk Factors")
                .doesNotContain("Item 2. Properties")
                .doesNotContain("Item 5. Market for Registrant");
    }

    @Test
    void ifTheAiFailsTheHoldingShowsFailedAndTheReason() throws Exception {
        when(gemini.generate(any(AiRequest.class))).thenThrow(
                new GeminiClient.GeminiException("The AI returned an error (HTTP 503). Try again later.", null));
        String email = "ai-fails@example.com";
        Portfolio portfolio = analyse(email, HoldingStatus.FAILED);

        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].statusReason").value("The AI returned an error (HTTP 503). Try again later."))
                .andExpect(jsonPath("$[0].reportType").value("10-K"));
        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anAnswerThatIsNotValidJsonIsAskedForOnceMore() throws Exception {
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(cutOffAnswer()), answer(Examples.geminiNvidia()));
        String email = "ai-retry@example.com";
        Portfolio portfolio = analyse(email, HoldingStatus.DONE);

        verify(gemini, times(2)).generate(any(AiRequest.class));
        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    void anAnswerThatIsNotValidJsonTwiceMarksTheHoldingFailed() throws Exception {
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(cutOffAnswer()));
        String email = "ai-retry-fails@example.com";
        Portfolio portfolio = analyse(email, HoldingStatus.FAILED);

        verify(gemini, times(2)).generate(any(AiRequest.class));
        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[0].statusReason")
                        .value("The AI's answer could not be read, even when asked a second time."));
    }

    @Test
    void theAuditTrailRecordsHowManyQuotesWereKeptAndHowManyDropped() throws Exception {
        // Gemini's real answer with the SK Hynix and Micron quote changed, as above: 5 are kept, those 2 dropped.
        String changed = Examples.geminiNvidia().replace(
                "We purchase memory from SK Hynix Inc., Micron Technology, Inc., and Samsung.",
                "We purchase memory from SK Hynix Inc. and Micron Technology, Inc. under ten-year agreements.");
        Long callId = recordedCall();
        when(gemini.generate(any(AiRequest.class))).thenReturn(new AiAnswer(changed, callId));

        analyse("ai-audit-kept@example.com", HoldingStatus.DONE);

        AiCall call = aiCallRows.findById(callId).orElseThrow();
        assertThat(call.getKept()).isEqualTo(5);
        assertThat(call.getDropped()).isEqualTo(2);
    }

    @Test
    void anAnswerThatCannotBeReadIsRecordedAsUnreadableBothTimes() throws Exception {
        Long first = recordedCall();
        Long second = recordedCall();
        when(gemini.generate(any(AiRequest.class)))
                .thenReturn(new AiAnswer(cutOffAnswer(), first), new AiAnswer(cutOffAnswer(), second));

        analyse("ai-audit-unreadable@example.com", HoldingStatus.FAILED);

        assertThat(aiCallRows.findById(first).orElseThrow().getOutcome()).isEqualTo(AiOutcome.UNREADABLE);
        assertThat(aiCallRows.findById(second).orElseThrow().getOutcome()).isEqualTo(AiOutcome.UNREADABLE);
    }

    @Test
    void geminiIsSentTheReportButTheAuditTrailKeepsOnlyAMarkerNamingIt() throws Exception {
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidia()));

        analyse("ai-audit-marker@example.com", HoldingStatus.DONE);

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini).generate(request.capture());
        Long reportId = reports.findByAccessionNumber("0001045810-26-000021").orElseThrow().getId();
        assertThat(request.getValue().purpose()).isEqualTo(AiPurpose.FIND_RELATIONSHIPS);
        assertThat(request.getValue().reportId()).isEqualTo(reportId);
        assertThat(request.getValue().prompt()).contains("Taiwan Semiconductor Manufacturing Company Limited");
        assertThat(request.getValue().recordedPrompt())
                .containsPattern("\\[report " + reportId + ": \\d+ characters]")
                .doesNotContain("Taiwan Semiconductor Manufacturing Company Limited");
    }

    /** A real row in the audit trail, as GeminiClient would save, for a fake answer to point at. */
    private Long recordedCall() {
        return aiCalls.record(AiRequest.of(AiPurpose.FIND_RELATIONSHIPS, "Recorded by the test."), "gemini-test-model",
                null, AiOutcome.ANSWERED, null, Instant.now(), 1);
    }

    @Test
    void anotherUsersPortfolioRelationshipsAreNotFound() throws Exception {
        Portfolio someoneElses = portfolioWith("ai-owner@example.com");

        mockMvc.perform(get("/api/portfolios/{id}/relationships", someoneElses.getId())
                        .header("Authorization", logins.bearer("ai-stranger@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
        verify(gemini, never()).generate(any(AiRequest.class));
    }

    /** Gemini's real answer with the end of its list cut off, as when Gemini stops early. Not valid JSON. */
    private static String cutOffAnswer() {
        return Examples.geminiNvidia().replace("final products.\\\"}]\"", "final products.\\\"}\"");
    }

    /** Makes a portfolio holding NVIDIA, presses Analyse, and waits for the holding to reach {@code expected}. */
    private Portfolio analyse(String email, HoldingStatus expected) throws Exception {
        Portfolio portfolio = portfolioWith(email);
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 400; attempt++) {
            if (holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus() == expected) {
                return portfolio;
            }
            Thread.sleep(25);
        }
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus())
                .isEqualTo(expected);
        return portfolio;
    }

    private Portfolio portfolioWith(String email) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "AI Test " + System.nanoTime()));
        holdings.save(new Holding(portfolio.getId(), "NVDA", new BigDecimal("100")));
        return portfolio;
    }
}
