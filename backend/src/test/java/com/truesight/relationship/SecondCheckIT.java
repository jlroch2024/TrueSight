package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCall;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.truesight.support.FakeAi.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Second AI Checks the First, end to end.
 *
 * <p>NVIDIA's real 10-K is saved first, as in {@code FindSuppliersAndCustomersIT}. Gemini is replaced by a fake: its
 * first call gives its real answer from {@code docs/examples/gemini-nvidia.json}, and the second check gives its real
 * answer from {@code docs/examples/gemini-nvidia-second-check.json}, which was run by hand on those seven claims.
 * Nothing here calls the SEC or Gemini.
 *
 * <p>The database is shared by every integration test, so the pass may find relationships other tests left. The fake
 * answers any claims it is sent, so the pass can always finish, and each test looks only at its own relationships.
 */
@IntegrationTest
class SecondCheckIT {

    private static final String NVIDIA_CIK = "0001045810";
    private static final String NVIDIA_URL =
            "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm";
    private static final String NVIDIA_ACCESSION = "0001045810-26-000021";
    private static final String TSMC_SENTENCE = "We utilize foundries, such as Taiwan Semiconductor Manufacturing "
            + "Company Limited, or TSMC, and Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor "
            + "wafers.";
    /** One of the seven real sentences in gemini-nvidia.json: it marks a prompt as NVIDIA's seven claims. */
    private static final String SK_HYNIX_SENTENCE =
            "We purchase memory from SK Hynix Inc., Micron Technology, Inc., and Samsung.";
    private static final Pattern CLAIM_LINE = Pattern.compile("(?m)^\\d+\\. Claim: ");

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired AiCallLog aiCalls;
    @Autowired AiCallRepository aiCallRows;
    @Autowired GeminiAnswer answers;
    @Autowired SecondCheckPassService pass;
    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    private final JsonMapper json = JsonMapper.builder().build();
    private String nvidiaName;

    @BeforeEach
    void saveNvidiasReportAndFakeGemini() {
        Company nvidia = companyDirectory.findOrCreate("NVIDIA Corporation", NVIDIA_CIK, "NVDA", List.of());
        nvidiaName = nvidia.getName(); // another test may have saved the company first, under the SEC's spelling
        reports.deleteByCompanyId(nvidia.getId()); // and, with it, any relationships found in it before
        reports.save(new Report(nvidia.getId(), "10-K", NVIDIA_ACCESSION, LocalDate.parse("2026-02-25"),
                NVIDIA_URL, Examples.nvidia10K()));
        when(sec.companyForTicker("NVDA")).thenReturn(new SecClient.SecCompany("NVIDIA Corporation", NVIDIA_CIK, "NVDA"));
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidia()));
        when(gemini.generateSecondCheck(any(AiRequest.class)))
                .thenAnswer(invocation -> answer(fakeSecondCheck(invocation.getArgument(0))));
    }

    // ---- criterion 1 ---------------------------------------------------------------------------------------------

    @Test
    void analysingAReportSendsItsQuoteCheckedClaimsForASecondCheck() throws Exception {
        analyse("sc-sends@example.com", HoldingStatus.DONE);

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, times(1)).generateSecondCheck(sent.capture());
        AiRequest request = sent.getValue();
        assertThat(request.purpose()).isEqualTo(AiPurpose.SECOND_CHECK);
        assertThat(request.reportId()).isEqualTo(nvidiaReportId());
        assertThat(claimsIn(request.prompt())).isEqualTo(7);
        // Each claim reads like "Sony buys from AMD: semi-custom SoC products", and is followed by its sentence.
        assertThat(request.prompt())
                .contains("1. Claim: " + nvidiaName + " buys from ")
                .contains(": semiconductor wafers\n   Sentence: " + TSMC_SENTENCE)
                .contains("Sentence: " + SK_HYNIX_SENTENCE)
                .contains("plan, a past relationship, a possibility, a competitor");
        // It sees only the sentences, never the report.
        assertThat(request.prompt()).doesNotContain("Item 1. Business").doesNotContain("Item 1A. Risk Factors");
        assertThat(request.prompt().length()).isLessThan(5_000);
        assertThat(request.recordedPrompt()).isEqualTo(request.prompt());
    }

    @Test
    void theRealSecondCheckAnswerIsSavedOnEachRelationshipWithItsVerdictAndReason() throws Exception {
        analyse("sc-saves@example.com", HoldingStatus.DONE);

        List<GeminiAnswer.SecondCheckFound> real = answers.readSecondChecks(Examples.geminiNvidiaSecondCheck());
        List<Relationship> saved = nvidiaRelationships();
        assertThat(saved).hasSize(7);
        assertThat(real).extracting(GeminiAnswer.SecondCheckFound::index).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);
        for (GeminiAnswer.SecondCheckFound verdict : real) {
            Relationship relationship = saved.get(verdict.index() - 1);
            assertThat(relationship.getSecondCheck()).isEqualTo(verdict.verdict());
            assertThat(relationship.getSecondCheckReason()).isEqualTo(verdict.reason());
            assertThat(relationship.getSecondCheckedAt()).isNotNull();
        }
    }

    @Test
    void aSecondCheckNeverChangesTheReviewStatusSoTheGraphKeepsEveryRelationship() throws Exception {
        String email = "sc-graph@example.com";
        Portfolio portfolio = analyse(email, HoldingStatus.DONE);

        assertThat(nvidiaRelationships()).allSatisfy(relationship -> {
            assertThat(relationship.getSecondCheck()).isNotNull();
            assertThat(relationship.getReviewStatus()).isEqualTo(ReviewStatus.UNREVIEWED);
        });
        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    void theSecondCheckIsRecordedInTheAuditTrailAgainstTheReportWithHowManyVerdictsWereKept() throws Exception {
        // As GeminiClient would: save the call's row, and answer with its id.
        when(gemini.generateSecondCheck(any(AiRequest.class))).thenAnswer(invocation -> {
            AiRequest request = invocation.getArgument(0);
            Long callId = aiCalls.record(request, "gemini-test-model", Examples.geminiNvidiaSecondCheck(),
                    AiOutcome.ANSWERED, null, Instant.now(), 1);
            aiCalls.attachReport(callId, request.reportId());
            return new AiAnswer(Examples.geminiNvidiaSecondCheck(), callId);
        });

        analyse("sc-audit@example.com", HoldingStatus.DONE);

        List<AiCall> calls = aiCallRows.findAll().stream()
                .filter(call -> call.getPurpose() == AiPurpose.SECOND_CHECK
                        && nvidiaReportId().equals(call.getReportId()))
                .toList();
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().getOutcome()).isEqualTo(AiOutcome.ANSWERED);
        assertThat(calls.getFirst().getKept()).isEqualTo(7);
        assertThat(calls.getFirst().getDropped()).isZero();
        assertThat(calls.getFirst().getPrompt()).contains("Sentence: " + TSMC_SENTENCE)
                .doesNotContain("Item 1. Business");
    }

    @Test
    void aClaimTheAnswerLeavesOutHasNoSecondCheckUntilThePassAsksAgain() throws Exception {
        List<Map<String, Object>> withoutTheFirst = answers.readSecondChecks(Examples.geminiNvidiaSecondCheck()).stream()
                .filter(item -> item.index() != 1)
                .map(item -> Map.<String, Object>of("index", item.index(), "verdict", item.verdict().name(),
                        "reason", item.reason()))
                .toList();
        when(gemini.generateSecondCheck(any(AiRequest.class))).thenReturn(answer(response(withoutTheFirst)));
        analyse("sc-left-out@example.com", HoldingStatus.DONE);

        List<Relationship> saved = nvidiaRelationships();
        assertThat(saved.get(0).getSecondCheck()).isNull();
        assertThat(saved.subList(1, 7)).allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNotNull());

        when(gemini.generateSecondCheck(any(AiRequest.class)))
                .thenAnswer(invocation -> answer(fakeSecondCheck(invocation.getArgument(0))));
        pass.check(nvidiaReportId());

        assertThat(nvidiaRelationships()).allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNotNull());
    }

    // ---- criterion 3 ---------------------------------------------------------------------------------------------

    @Test
    void thePassChecksEveryRelationshipWithoutASecondCheckAndRunningItAgainSendsNothing() throws Exception {
        // Two reports' worth of unchecked relationships: NVIDIA's seven, whose check failed, and one of their own.
        when(gemini.generateSecondCheck(any(AiRequest.class)))
                .thenThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null));
        analyse("sc-pass-first@example.com", HoldingStatus.DONE);
        Relationship other = unchecked("Pass Other Holding", "Pass Other Supplier");
        assertThat(nvidiaRelationships()).allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNull());
        assertThat(pass.pendingReportIds()).contains(nvidiaReportId(), other.getReportId());

        when(gemini.generateSecondCheck(any(AiRequest.class)))
                .thenAnswer(invocation -> answer(fakeSecondCheck(invocation.getArgument(0))));
        startPass(logins.analystBearer("sc-pass-analyst@example.com"));
        awaitNothingPending();

        assertThat(nvidiaRelationships()).allSatisfy(relationship -> {
            assertThat(relationship.getSecondCheck()).isNotNull();
            assertThat(relationship.getSecondCheckReason()).isNotBlank();
        });
        assertThat(relationships.findById(other.getId()).orElseThrow().getSecondCheck()).isNotNull();

        clearInvocations(gemini);
        startPass(logins.analystBearer("sc-pass-analyst@example.com"));

        verify(gemini, never()).generateSecondCheck(any(AiRequest.class));
        assertThat(pass.pendingReportIds()).isEmpty();
    }

    @Test
    void thePassNeverSendsARelationshipThatAlreadyHasASecondCheck() throws Exception {
        analyse("sc-pass-skip@example.com", HoldingStatus.DONE);
        List<Relationship> before = nvidiaRelationships();
        assertThat(before).allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNotNull());
        clearInvocations(gemini);

        pass.check(nvidiaReportId());

        verify(gemini, never()).generateSecondCheck(any(AiRequest.class));
        assertThat(nvidiaRelationships()).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    void aReportsClaimsGoInCallsOfAtMostThirty() throws Exception {
        Company holding = company("Batch Holding");
        Report report = reports.save(new Report(holding.getId(), "10-K", accession(), LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/" + UUID.randomUUID(), "text"));
        for (int i = 1; i <= 31; i++) {
            relationships.save(new Relationship(report.getId(), holding.getId(), company("Batch Supplier " + i).getId(),
                    RelationshipType.SUPPLIER, "parts", "Sentence " + i + " from the report."));
        }

        pass.check(report.getId());

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, atLeastOnce()).generateSecondCheck(sent.capture());
        List<Integer> sizes = sent.getAllValues().stream()
                .filter(request -> report.getId().equals(request.reportId()))
                .map(request -> claimsIn(request.prompt())).toList();
        assertThat(sizes).containsExactly(30, 1);
        assertThat(relationships.findByReportIdOrderByIdAsc(report.getId()))
                .hasSize(31).allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNotNull());
    }

    @Test
    void aCustomerClaimReadsThatTheCompanySellsToTheOtherCompany() throws Exception {
        Company holding = company("Customer Claim Holding");
        Company customer = company("Customer Claim Buyer");
        Report report = reports.save(new Report(holding.getId(), "10-K", accession(), LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/" + UUID.randomUUID(), "text"));
        relationships.save(new Relationship(report.getId(), holding.getId(), customer.getId(), RelationshipType.CUSTOMER,
                "graphics processors", "Sentence from the report."));

        pass.check(report.getId());

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, atLeastOnce()).generateSecondCheck(sent.capture());
        assertThat(sent.getAllValues()).anySatisfy(request -> {
            assertThat(request.reportId()).isEqualTo(report.getId());
            assertThat(request.prompt()).contains("1. Claim: " + holding.getName() + " sells to " + customer.getName()
                    + ": graphics processors\n   Sentence: Sentence from the report.");
        });
    }

    // ---- criterion 4 ---------------------------------------------------------------------------------------------

    @Test
    void aFailedSecondCheckLeavesTheRelationshipsSavedWithNoSecondCheck() throws Exception {
        when(gemini.generateSecondCheck(any(AiRequest.class)))
                .thenThrow(new GeminiClient.GeminiException("The AI returned an error (HTTP 503). Try again later.", null));
        String email = "sc-fails@example.com";

        Portfolio portfolio = analyse(email, HoldingStatus.DONE);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$", hasSize(7)));
        assertThat(nvidiaRelationships()).hasSize(7).allSatisfy(relationship -> {
            assertThat(relationship.getSecondCheck()).isNull();
            assertThat(relationship.getSecondCheckReason()).isNull();
            assertThat(relationship.getSecondCheckedAt()).isNull();
        });
        assertThat(pass.pendingReportIds()).contains(nvidiaReportId());
    }

    @Test
    void anUnreadableSecondCheckLeavesTheRelationshipsSavedWithNoSecondCheckAndIsRecordedAsUnreadable() throws Exception {
        Long callId = aiCalls.record(AiRequest.of(AiPurpose.SECOND_CHECK, "Recorded by the test."), "gemini-test-model",
                null, AiOutcome.ANSWERED, null, Instant.now(), 1);
        when(gemini.generateSecondCheck(any(AiRequest.class)))
                .thenReturn(new AiAnswer("<html>Bad gateway</html>", callId));

        analyse("sc-unreadable@example.com", HoldingStatus.DONE);

        assertThat(nvidiaRelationships()).hasSize(7)
                .allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNull());
        assertThat(aiCallRows.findById(callId).orElseThrow().getOutcome()).isEqualTo(AiOutcome.UNREADABLE);
        assertThat(pass.pendingReportIds()).contains(nvidiaReportId());
    }

    @Test
    void thePassTriesAgainTheRelationshipsWhoseSecondCheckFailed() throws Exception {
        when(gemini.generateSecondCheck(any(AiRequest.class)))
                .thenThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null));
        analyse("sc-retry@example.com", HoldingStatus.DONE);
        assertThat(nvidiaRelationships()).allSatisfy(relationship -> assertThat(relationship.getSecondCheck()).isNull());

        when(gemini.generateSecondCheck(any(AiRequest.class)))
                .thenAnswer(invocation -> answer(fakeSecondCheck(invocation.getArgument(0))));
        pass.check(nvidiaReportId());

        List<GeminiAnswer.SecondCheckFound> real = answers.readSecondChecks(Examples.geminiNvidiaSecondCheck());
        List<Relationship> saved = nvidiaRelationships();
        for (GeminiAnswer.SecondCheckFound verdict : real) {
            assertThat(saved.get(verdict.index() - 1).getSecondCheck()).isEqualTo(verdict.verdict());
        }
    }

    // ---- criterion 5 ---------------------------------------------------------------------------------------------

    @Test
    void portfolioManagersGetForbiddenFromThePassAndNothingIsSent() throws Exception {
        unchecked("Forbidden Pass Holding", "Forbidden Pass Supplier");

        mockMvc.perform(post("/api/relationships/second-check")
                        .header("Authorization", logins.bearer("sc-pass-manager@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only analysts can do this."));

        verify(gemini, never()).generateSecondCheck(any(AiRequest.class));
    }

    @Test
    void thePassNeedsALogin() throws Exception {
        mockMvc.perform(post("/api/relationships/second-check")).andExpect(status().isUnauthorized());
    }

    @Test
    void analystsStartThePassAndAreAnsweredAccepted() throws Exception {
        mockMvc.perform(post("/api/relationships/second-check")
                        .header("Authorization", logins.analystBearer("sc-pass-accepted@example.com")))
                .andExpect(status().isAccepted());
        awaitNothingPending();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /**
     * What the fake second check answers. NVIDIA's seven claims get Gemini's real answer from
     * {@code docs/examples/}. Any other claims (left in the shared database by other tests) get an "Agrees" for each,
     * so the pass can always finish.
     */
    private String fakeSecondCheck(AiRequest request) throws Exception {
        int claims = claimsIn(request.prompt());
        if (claims == 7 && request.prompt().contains(SK_HYNIX_SENTENCE)) {
            return Examples.geminiNvidiaSecondCheck();
        }
        return response(IntStream.rangeClosed(1, claims)
                .mapToObj(index -> Map.<String, Object>of("index", index, "verdict", "AGREES",
                        "reason", "Test reply to claim " + index + "."))
                .toList());
    }

    /** Gemini's response around the text of an answer. */
    private String response(List<Map<String, Object>> verdicts) throws Exception {
        String content = json.writeValueAsString(verdicts);
        return json.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", content)))))));
    }

    private static int claimsIn(String prompt) {
        return (int) CLAIM_LINE.matcher(prompt).results().count();
    }

    private Long nvidiaReportId() {
        return reports.findByAccessionNumber(NVIDIA_ACCESSION).orElseThrow().getId();
    }

    /** NVIDIA's relationships, in the order Gemini listed them, which is the order of their claims. */
    private List<Relationship> nvidiaRelationships() {
        return relationships.findByReportIdOrderByIdAsc(nvidiaReportId());
    }

    private Company company(String name) {
        return companyDirectory.findOrCreate(name + " " + UUID.randomUUID(), null, null, List.of());
    }

    private static String accession() {
        return "sc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    /** A saved relationship nobody has second-checked, in a report of its own. */
    private Relationship unchecked(String holdingName, String supplierName) {
        Company holding = company(holdingName);
        Report report = reports.save(new Report(holding.getId(), "10-K", accession(), LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/" + UUID.randomUUID(), "text"));
        return relationships.save(new Relationship(report.getId(), holding.getId(), company(supplierName).getId(),
                RelationshipType.SUPPLIER, "semiconductor wafers", "We buy wafers from them."));
    }

    private void startPass(String bearer) throws Exception {
        mockMvc.perform(post("/api/relationships/second-check").header("Authorization", bearer))
                .andExpect(status().isAccepted());
    }

    /**
     * The second check runs after the holding is Done, so waits for it: until Gemini has been asked, and then until
     * the verdicts are saved (or, when it was made to fail, for a moment, to be sure nothing is saved).
     */
    private void awaitSecondCheck() throws InterruptedException {
        for (int attempt = 0; attempt < 400 && !secondCheckAsked(); attempt++) {
            Thread.sleep(25);
        }
        assertThat(secondCheckAsked()).isTrue();
        for (int attempt = 0; attempt < 40; attempt++) {
            List<Relationship> saved = nvidiaRelationships();
            if (!saved.isEmpty() && saved.stream().allMatch(relationship -> relationship.getSecondCheck() != null)) return;
            Thread.sleep(25);
        }
    }

    private boolean secondCheckAsked() {
        return org.mockito.Mockito.mockingDetails(gemini).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("generateSecondCheck"));
    }

    /** Waits for the background pass to leave nothing without a second check. */
    private void awaitNothingPending() throws InterruptedException {
        for (int attempt = 0; attempt < 800 && !pass.pendingReportIds().isEmpty(); attempt++) {
            Thread.sleep(25);
        }
        assertThat(pass.pendingReportIds()).isEmpty();
    }

    /** Makes a portfolio holding NVIDIA, presses Analyse, and waits for the holding to reach {@code expected}. */
    private Portfolio analyse(String email, HoldingStatus expected) throws Exception {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "SC Test " + System.nanoTime()));
        holdings.save(new Holding(portfolio.getId(), "NVDA", new BigDecimal("100")));
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 400; attempt++) {
            if (holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus() == expected) {
                awaitSecondCheck();
                return portfolio;
            }
            Thread.sleep(25);
        }
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus())
                .isEqualTo(expected);
        return portfolio;
    }
}
