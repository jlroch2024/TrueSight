package com.truesight.history;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
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
import com.truesight.relationship.GeminiAnswer;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.PastRelationshipRepository;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.ReviewStatus;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.ReportTextExtractor;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The SEC Filing Timeline's reading, against a real database with the SEC and Gemini replaced by fakes: nothing here
 * calls either.
 *
 * <p>The reports are NVIDIA's real 10-K from {@code docs/examples/nvidia-10k.txt}, and Gemini's real answer for it from
 * {@code docs/examples/gemini-nvidia.json}. docs/examples has no genuinely older report yet, so that one report stands
 * in for each year of a company's history: the dates, accession numbers and the SEC's list are the test's own, and the
 * same real answer for every year makes every year name the same suppliers.
 *
 * <p>The background job is made to run at once, so each test can look at the result straight after asking.
 */
@IntegrationTest
class HistoryIT {

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory directory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired PastRelationshipRepository pastRelationships;
    @Autowired AiCallLog aiCalls;
    @Autowired GeminiAnswer answers;
    @Autowired HistoryReader reader;
    @Autowired HistoryProgress progress;
    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;
    @MockitoBean HistoryJob job;

    private String email;
    private Portfolio portfolio;

    @BeforeEach
    void runTheJobAtOnceAndAnswerWithGeminisRealAnswer() {
        email = "history-" + UUID.randomUUID() + "@example.com";
        portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "History"));
        doAnswer(invocation -> {
            reader.read(invocation.getArgument(0));
            return null;
        }).when(job).run(any());
        stubGemini(Examples.geminiNvidia());
    }

    // ---- reading ---------------------------------------------------------------------------------------------------

    @Test
    void loadHistoryReadsTheTwoPreviousReportsOfEachHoldingAndKeepsThemOutOfTheGraph() throws Exception {
        Filed filed = company("A");
        holding("AAA", filed.company());

        load().andExpect(status().isAccepted());

        assertThat(pastRelationships.findByReportIdOrderByIdAsc(filed.older1().getId())).isNotEmpty();
        assertThat(pastRelationships.findByReportIdOrderByIdAsc(filed.older2().getId())).isNotEmpty();
        // The graph's relationships are untouched: the older ones live in their own table.
        assertThat(relationships.findByReportIdOrderByIdAsc(filed.latest().getId())).hasSize(1);
        assertThat(relationships.findByCompanyIdIn(List.of(filed.company().getId()))).hasSize(1);
        // Each was read with the timeline's own purpose, and the latest report was not read again.
        ArgumentCaptor<AiRequest> asked = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, times(2)).generate(asked.capture());
        assertThat(asked.getAllValues()).extracting(AiRequest::purpose).containsOnly(AiPurpose.TIMELINE);
        assertThat(asked.getAllValues()).extracting(AiRequest::reportId)
                .containsExactlyInAnyOrder(filed.older1().getId(), filed.older2().getId());
    }

    @Test
    void theTimelineHasOneYearPerYearFromTheEarliestReportWithNewKeptAndGone() throws Exception {
        Filed filed = company("A");
        holding("AAA", filed.company());
        load();
        int named = distinctKeys(filed.older2());

        history().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.years", hasSize(3)))
                .andExpect(jsonPath("$.years[0].year").value(2024))
                .andExpect(jsonPath("$.years[1].year").value(2025))
                .andExpect(jsonPath("$.years[2].year").value(2026))
                // The earliest year has nothing before it, so it is all New.
                .andExpect(jsonPath("$.years[0].relationships", hasSize(named)))
                .andExpect(jsonPath("$.years[0].relationships[*].change", everyItem(is("NEW"))))
                // The same answer a year later: everything Kept, nothing gone.
                .andExpect(jsonPath("$.years[1].relationships[*].change", everyItem(is("KEPT"))))
                .andExpect(jsonPath("$.years[1].gone", hasSize(0)))
                // The latest report has one relationship in the graph: that one is Kept, the rest are gone.
                .andExpect(jsonPath("$.years[2].relationships", hasSize(1)))
                .andExpect(jsonPath("$.years[2].relationships[0].change").value("KEPT"))
                .andExpect(jsonPath("$.years[2].gone", hasSize(named - 1)))
                .andExpect(jsonPath("$.years[0].relationships[0].reportUrl").value(filed.older2().getUrl()))
                .andExpect(jsonPath("$.years[0].relationships[0].filingDate").value("2024-02-21"));
    }

    @Test
    void aRejectedRelationshipOfTheLatestReportIsLeftOutOfTheLatestYear() throws Exception {
        Filed filed = company("A");
        holding("AAA", filed.company());
        Relationship rejected = relationships.findByReportIdOrderByIdAsc(filed.latest().getId()).get(0);
        rejected.setReviewStatus(ReviewStatus.REJECTED);
        relationships.save(rejected);
        load();

        history().andExpect(jsonPath("$.years[2].relationships", hasSize(0)));
    }

    @Test
    void aSecondLoadReadsNothingItReadBefore() throws Exception {
        Filed filed = company("A");
        holding("AAA", filed.company());
        load();

        load().andExpect(status().isAccepted());

        verify(gemini, times(2)).generate(any(AiRequest.class));
        history().andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void aHoldingAddedLaterIsOfferedAndReadOnItsOwn() throws Exception {
        Filed first = company("A");
        holding("AAA", first.company());
        load();
        Filed later = company("B");
        holding("BBB", later.company());

        history().andExpect(jsonPath("$.unread").value(2));
        load();

        verify(gemini, times(4)).generate(any(AiRequest.class));
        history().andExpect(jsonPath("$.unread").value(0));
        assertThat(pastRelationships.findByReportIdOrderByIdAsc(later.older1().getId())).isNotEmpty();
    }

    @Test
    void unreadIsTwoPerHoldingBeforeAnythingIsRead() throws Exception {
        holding("AAA", company("A").company());
        holding("BBB", company("B").company());

        history().andExpect(jsonPath("$.unread").value(4))
                .andExpect(jsonPath("$.years", hasSize(1)))
                .andExpect(jsonPath("$.years[0].year").value(2026));
    }

    @Test
    void aHoldingNotYetAnalysedIsLeftOut() throws Exception {
        Filed filed = company("A");
        Holding waiting = holding("AAA", filed.company());
        waiting.setStatus(HoldingStatus.WAITING);
        holdings.save(waiting);

        history().andExpect(jsonPath("$.unread").value(0)).andExpect(jsonPath("$.years", hasSize(0)));
        load();
        verify(gemini, never()).generate(any(AiRequest.class));
    }

    @Test
    void anOlderReportNotSavedYetIsDownloadedSavedAndRead() throws Exception {
        Company company = directory.findOrCreate("History Download " + UUID.randomUUID(), cik(), null, List.of());
        Report latest = reports.save(new Report(company.getId(), "10-K", accession(),
                LocalDate.parse("2026-02-25"), "https://www.sec.gov/latest", Examples.nvidia10K()));
        holding("AAA", company);
        String accession = "0000000000-25-" + ThreadLocalRandom.current().nextInt(100000, 999999);
        String html = "<html><body><p>Not a real report: only for checking it is saved and read.</p></body></html>";
        when(sec.annualFilings(eq(company.getCik()), anyInt())).thenReturn(List.of(
                new SecClient.SecFiling("10-K", "ignored-latest", latest.getFilingDate(), latest.getUrl()),
                new SecClient.SecFiling("10-K", accession, LocalDate.parse("2025-02-26"), "https://www.sec.gov/older")));
        when(sec.download("https://www.sec.gov/older")).thenReturn(html);

        load();

        Report saved = reports.findByAccessionNumber(accession).orElseThrow();
        assertThat(saved.getCompanyId()).isEqualTo(company.getId());
        assertThat(saved.getText()).isEqualTo(ReportTextExtractor.extract(html));
        assertThat(saved.getFilingDate()).hasToString("2025-02-26");
        verify(gemini, times(1)).generate(any(AiRequest.class));
        // The latest report stays the company's latest everywhere else.
        assertThat(reports.findFirstByCompanyIdOrderByFilingDateDesc(company.getId()).orElseThrow().getId())
                .isEqualTo(latest.getId());
    }

    // ---- failing and refusing ----------------------------------------------------------------------------------------

    @Test
    void aFailureShowsItsReasonKeepsWhatWasReadAndRetryCarriesOn() throws Exception {
        Filed filed = company("A");
        holding("AAA", filed.company());
        doAnswer(invocation -> answerFor(invocation.getArgument(0), Examples.geminiNvidia()))
                .doThrow(new GeminiClient.GeminiException("The AI could not be reached.", null))
                .when(gemini).generate(any(AiRequest.class));

        load();

        history().andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.reason").value("The AI could not be reached."))
                .andExpect(jsonPath("$.progress.done").value(1))
                .andExpect(jsonPath("$.progress.total").value(2))
                .andExpect(jsonPath("$.unread").value(1));
        long readSoFar = Set.of(filed.older1(), filed.older2()).stream()
                .filter(report -> !pastRelationships.findByReportIdOrderByIdAsc(report.getId()).isEmpty()).count();
        assertThat(readSoFar).isEqualTo(1);

        stubGemini(Examples.geminiNvidia());
        load().andExpect(status().isAccepted());

        history().andExpect(jsonPath("$.status").value("IDLE")).andExpect(jsonPath("$.unread").value(0));
        // Only the one report that was missing was read the second time.
        verify(gemini, times(3)).generate(any(AiRequest.class));
    }

    @Test
    void aSecFailureShowsItsReason() throws Exception {
        Filed filed = company("A");
        holding("AAA", filed.company());
        when(sec.annualFilings(anyString(), anyInt()))
                .thenThrow(new SecClient.SecAccessException("The SEC could not be reached.", null));

        load();

        history().andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.reason").value("The SEC could not be reached."));
        verify(gemini, never()).generate(any(AiRequest.class));
    }

    @Test
    void loadingWhileReadingIsRefused() throws Exception {
        Filed filed = company("A");
        holding("AAA", filed.company());
        progress.begin(portfolio.getId(), 2);

        load().andExpect(status().isConflict());

        progress.finish(portfolio.getId());
    }

    @Test
    void readingShowsItsProgress() throws Exception {
        Filed filed = company("A");
        holding("AAA", filed.company());
        progress.begin(portfolio.getId(), 2);
        progress.advance(portfolio.getId());

        history().andExpect(jsonPath("$.status").value("READING"))
                .andExpect(jsonPath("$.progress.done").value(1))
                .andExpect(jsonPath("$.progress.total").value(2));

        progress.finish(portfolio.getId());
    }

    @Test
    void anotherUsersPortfolioAnswersNotFound() throws Exception {
        String other = "history-other-" + UUID.randomUUID() + "@example.com";
        logins.user(other);

        mockMvc.perform(get("/api/portfolios/{id}/history", portfolio.getId()).header("Authorization", logins.bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/portfolios/{id}/history", portfolio.getId()).header("Authorization", logins.bearer(other)))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private ResultActions load() throws Exception {
        return mockMvc.perform(post("/api/portfolios/{id}/history", portfolio.getId())
                .header("Authorization", logins.bearer(email)));
    }

    private ResultActions history() throws Exception {
        return mockMvc.perform(get("/api/portfolios/{id}/history", portfolio.getId())
                .header("Authorization", logins.bearer(email)));
    }

    /** A company with a latest report that has one relationship, and two older reports the fake SEC lists. */
    private record Filed(Company company, Report latest, Report older1, Report older2) {
    }

    private Filed company(String label) {
        Company company = directory.findOrCreate("History " + label + " " + UUID.randomUUID(), cik(), null, List.of());
        String text = Examples.nvidia10K();
        Report latest = reports.save(new Report(company.getId(), "10-K", accession(),
                LocalDate.parse("2026-02-25"), "https://www.sec.gov/latest/" + label, text));
        Report older1 = reports.save(new Report(company.getId(), "10-K", accession(),
                LocalDate.parse("2025-02-26"), "https://www.sec.gov/older1/" + label, text));
        Report older2 = reports.save(new Report(company.getId(), "10-K", accession(),
                LocalDate.parse("2024-02-21"), "https://www.sec.gov/older2/" + label, text));
        // The latest report's one relationship in the graph: the first supplier Gemini's real answer names, saved the
        // way the finder saves it.
        GeminiAnswer.Found first = answers.read(Examples.geminiNvidia()).get(0);
        Company supplier = directory.findOrCreate(
                first.commonName().isEmpty() ? first.company() : first.commonName(), null,
                CompanyDirectory.cleanTicker(first.ticker()), List.of(first.company(), first.alsoKnownAs()));
        relationships.save(new Relationship(latest.getId(), company.getId(), supplier.getId(), first.relationship(),
                first.provides(), first.quote()));
        when(sec.annualFilings(eq(company.getCik()), anyInt())).thenReturn(List.of(
                filing(latest), filing(older1), filing(older2)));
        return new Filed(company, latest, older1, older2);
    }

    private static SecClient.SecFiling filing(Report report) {
        return new SecClient.SecFiling(report.getForm(), report.getAccessionNumber(), report.getFilingDate(),
                report.getUrl());
    }

    private Holding holding(String ticker, Company company) {
        Holding holding = new Holding(portfolio.getId(), ticker, null);
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        return holdings.save(holding);
    }

    /** A unique accession number that fits the column (25 characters). */
    private static String accession() {
        return "H-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private static String cik() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    /** How many different relationships (by company, counterparty and type) a read report named. */
    private int distinctKeys(Report report) {
        return pastRelationships.findByReportIdOrderByIdAsc(report.getId()).stream()
                .map(past -> past.getCounterpartyId() + "-" + past.getType())
                .collect(Collectors.toSet()).size();
    }

    // doAnswer, not when: a second stubbing with when() would run the first answer with no request.
    private void stubGemini(String response) {
        doAnswer(invocation -> answerFor(invocation.getArgument(0), response))
                .when(gemini).generate(any(AiRequest.class));
    }

    private AiAnswer answerFor(AiRequest request, String response) {
        Long callId = aiCalls.record(request, "test-model", response, AiOutcome.ANSWERED, null, Instant.now(), 1);
        aiCalls.attachReport(callId, request.reportId());
        return new AiAnswer(response, callId);
    }
}
