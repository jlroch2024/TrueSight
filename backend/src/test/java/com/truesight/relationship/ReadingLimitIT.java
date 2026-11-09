package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyNameRepository;
import com.truesight.company.CompanyRepository;
import com.truesight.company.ReportStatus;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.report.SupplierReader;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keep Gemini Within Budget, criteria 3 to 7: at most {@code AI_DAILY_REPORT_LIMIT} reports are read in a day, across
 * the whole app. The limit is 3 here, for this test only: every other test runs with one it can never reach
 * ({@code application-test.yml}).
 *
 * <p>Nothing here calls the SEC or Gemini. The fake SEC serves AMD's real 10-K ({@code docs/examples/amd-10k.txt}) and
 * the fake Gemini its real answer ({@code gemini-amd.json}), as {@code SupplierReaderIT} does; the three suppliers of
 * AMD the fake SEC knows are served NVIDIA's real 10-K and answer as their own. Like the real {@code GeminiClient}, the
 * fake records each call it answers in the audit trail ({@code ai_calls}), which is what the limit counts. Calls from
 * other days or for other purposes are put in the audit trail through {@link AiCallLog}.
 *
 * <p>The database is shared by every integration test, so the audit trail is emptied before and after each test (the
 * tests that ran before never leave a count behind that this test would see), and so is what the test saved.
 */
@IntegrationTest
@TestPropertySource(properties = {"truesight.ai.daily-report-limit=3", "truesight.ai.daily-name-search-limit=3"})
class ReadingLimitIT {

    private static final String REASON = "TrueSight has read as many new reports as it can today. Press Analyse again tomorrow.";
    private static final String AMD_CIK = "0000002488";
    private static final String AMD_URL = "https://www.sec.gov/Archives/edgar/data/2488/ts239-amd-10k.htm";
    /** The three suppliers of AMD the fake SEC knows: ticker, the SEC's name and its SEC number. */
    private static final List<String[]> FILERS = List.of(
            new String[] {"GFS", "GLOBALFOUNDRIES Inc.", "0001709048"},
            new String[] {"UMC", "UNITED MICROELECTRONICS CORP", "0001033767"},
            new String[] {"SANM", "SANMINA CORP", "0000897723"});
    private static final String NVIDIA_CIK = "0001045810";
    private static final String OLDER_ACCESSION = "ts239-nvda-older";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory directory;
    @Autowired CompanyRepository companies;
    @Autowired CompanyNameRepository nameRows;
    @Autowired ReportRepository reports;
    @Autowired AiCallRepository aiCalls;
    @Autowired AiCallLog callLog;
    @Autowired GeminiAnswer answers;
    @Autowired ReadingLimit readingLimit;
    @Autowired SupplierReader supplierReader;
    @Autowired RelationshipFinder finder;
    @Autowired PastRelationshipRepository pastRelationships;

    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    @BeforeEach
    void fakeTheSecAndGemini() {
        purge();
        when(sec.companyForTicker("AMD")).thenReturn(new SecClient.SecCompany("ADVANCED MICRO DEVICES INC", AMD_CIK, "AMD"));
        when(sec.latestAnnualFiling(AMD_CIK)).thenReturn(
                new SecClient.SecFiling("10-K", "ts239-amd", LocalDate.parse("2026-02-04"), AMD_URL));
        when(sec.download(AMD_URL)).thenReturn(html(Examples.amd10K()));
        for (String[] filer : FILERS) {
            when(sec.companyForTicker(filer[0])).thenReturn(new SecClient.SecCompany(filer[1], filer[2], filer[0]));
            when(sec.latestAnnualFiling(filer[2])).thenReturn(new SecClient.SecFiling("10-K",
                    "ts239-" + filer[0].toLowerCase(Locale.ROOT), LocalDate.parse("2026-02-25"), urlOf(filer[0])));
            when(sec.download(urlOf(filer[0]))).thenReturn(html(Examples.nvidia10K()));
        }
        // AMD's report gets AMD's answer; every other report, a stand-in supplier's, NVIDIA's.
        answerWith(request -> request.prompt().toLowerCase(Locale.ROOT).startsWith("below is part of advanced micro devices")
                ? Examples.geminiAmd() : Examples.geminiNvidia());
    }

    @AfterEach
    void removeWhatTheTestSaved() {
        purge();
    }

    // ---- criterion 3: past the limit, a holding that needs its report read says Failed ---------------------------

    @Test
    void withTheDaysLimitReachedAHoldingThatNeedsItsReportReadSaysFailedWithTheReasonAndNoCallGoesToGemini() throws Exception {
        recordCalls(3, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, Instant.now());
        String email = "limit-failed@example.com";
        Portfolio portfolio = portfolioWith(email);

        analyse(email, portfolio);

        await(() -> holdingOf(portfolio).getStatus() == HoldingStatus.FAILED);
        assertThat(holdingOf(portfolio).getStatusReason()).isEqualTo(REASON);
        verify(gemini, never()).generate(any(AiRequest.class));
        // A report that could not be read is not downloaded for nothing either.
        verify(sec, never()).download(AMD_URL);
    }

    // ---- criterion 4: a report already read is free, whatever the count -------------------------------------------

    @Test
    void aHoldingWhoseReportIsAlreadyReadIsDoneWhateverTheCount() throws Exception {
        suppliersUnknownToTheSec();
        String email = "limit-already-read@example.com";
        Portfolio first = portfolioWith(email);
        analyse(email, first);
        await(() -> holdingOf(first).getStatus() == HoldingStatus.DONE);
        awaitSuppliersSettled(first, 0);
        verify(gemini, times(1)).generate(any(AiRequest.class));

        // Now the limit is reached (AMD's own call, and three more); a second portfolio holding AMD costs nothing.
        recordCalls(3, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, Instant.now());
        assertThat(readingLimit.reached()).isTrue();
        Portfolio second = portfolioWith(email);
        analyse(email, second);

        await(() -> holdingOf(second).getStatus() == HoldingStatus.DONE);
        assertThat(holdingOf(second).getStatusReason()).isNull();
        verify(gemini, times(1)).generate(any(AiRequest.class));
    }

    // ---- criterion 5: Analyse the next day reads it ---------------------------------------------------------------

    @Test
    void analyseTheNextDayReadsAHoldingTheLimitFailed() throws Exception {
        suppliersUnknownToTheSec();
        recordCalls(3, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, Instant.now());
        String email = "limit-next-day@example.com";
        Portfolio portfolio = portfolioWith(email);
        analyse(email, portfolio);
        await(() -> holdingOf(portfolio).getStatus() == HoldingStatus.FAILED);

        // The next day: the same three calls are yesterday's now.
        aiCalls.deleteAll();
        recordCalls(3, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, Instant.now().minus(1, ChronoUnit.DAYS));
        analyse(email, portfolio);

        await(() -> holdingOf(portfolio).getStatus() == HoldingStatus.DONE);
        assertThat(holdingOf(portfolio).getStatusReason()).isNull();
        verify(gemini, times(1)).generate(any(AiRequest.class));
        awaitSuppliersSettled(portfolio, 0);
    }

    // ---- criterion 6: what counts --------------------------------------------------------------------------------

    @Test
    void aFailedCallCountsTowardsTheLimitSinceItMayHaveCostMoney() {
        recordCalls(2, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, Instant.now());
        assertThat(readingLimit.reached()).isFalse();

        recordCalls(1, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.FAILED, Instant.now());

        assertThat(readingLimit.reached()).isTrue();
    }

    @Test
    void aCallFromYesterdayDoesNotCount() {
        recordCalls(5, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(readingLimit.reached()).isFalse();
    }

    @Test
    void anEarlierReportsCallCountsButNewsAndSecondChecksDoNot() {
        for (AiPurpose purpose : List.of(AiPurpose.JUDGE_NEWS, AiPurpose.SECOND_CHECK, AiPurpose.RECOMMEND,
                AiPurpose.CRITICALITY, AiPurpose.COMMON_NAMES, AiPurpose.ASK, AiPurpose.HIGHLIGHT)) {
            recordCalls(5, purpose, AiOutcome.ANSWERED, Instant.now());
        }
        assertThat(readingLimit.reached()).isFalse();

        recordCalls(3, AiPurpose.TIMELINE, AiOutcome.ANSWERED, Instant.now());

        assertThat(readingLimit.reached()).isTrue();
    }

    @Test
    void aNameSearchCallDoesNotUseUpTheDaysReadingOfReports() {
        recordCalls(5, AiPurpose.NAME_SEARCH, AiOutcome.ANSWERED, Instant.now());

        assertThat(readingLimit.reached()).isFalse();
    }

    @Test
    void nameSearchesHaveADailyLimitOfTheirOwn() {
        recordCalls(2, AiPurpose.NAME_SEARCH, AiOutcome.ANSWERED, Instant.now());
        assertThat(readingLimit.nameSearchesReached()).isFalse();

        recordCalls(1, AiPurpose.NAME_SEARCH, AiOutcome.ANSWERED, Instant.now());

        assertThat(readingLimit.nameSearchesReached()).isTrue();
        assertThatThrownBy(() -> readingLimit.requireRoomForNameSearch()).isInstanceOf(ReadingLimit.ReachedException.class);
    }

    // ---- criterion 7: a supplier's or an earlier report past the limit is left unread ----------------------------

    @Test
    void aSuppliersReportPastTheLimitIsLeftUnreadNeverFailsTheHoldingAndAnalyseReadsItLater() throws Exception {
        // Two calls are made already today: AMD's own report is the third, and reaches the limit.
        recordCalls(2, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, Instant.now());
        String email = "limit-suppliers@example.com";
        Portfolio portfolio = portfolioWith(email);

        analyse(email, portfolio);

        await(() -> holdingOf(portfolio).getStatus() == HoldingStatus.DONE);
        awaitSuppliersSettled(portfolio, FILERS.size());
        for (String[] filer : FILERS) {
            Company supplier = companies.findByCik(filer[2]).orElseThrow();
            // Back to no status, not Failed: nothing was sent to Gemini or downloaded for it, and Analyse queues it again.
            assertThat(supplier.getReportStatus()).isNull();
            assertThat(supplier.getReportStatusReason()).isNull();
            verify(sec, never()).download(urlOf(filer[0]));
        }
        assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);
        assertThat(holdingOf(portfolio).getStatusReason()).isNull();
        verify(gemini, times(1)).generate(any(AiRequest.class));

        // The next day, Analyse queues them again and reads them.
        aiCalls.deleteAll();
        recordCalls(3, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, Instant.now().minus(1, ChronoUnit.DAYS));
        analyse(email, portfolio);

        await(() -> FILERS.stream().allMatch(filer -> companies.findByCik(filer[2])
                .map(company -> company.getReportStatus() == ReportStatus.DONE).orElse(false)));
        verify(gemini, times(1 + FILERS.size())).generate(any(AiRequest.class));
        assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);
    }

    @Test
    void anEarlierReportPastTheLimitIsLeftUnreadAndReadWhenThereIsRoom() {
        Company nvidia = directory.findOrCreate("NVIDIA CORP", NVIDIA_CIK, "NVDA", List.of());
        Report older = reports.save(new Report(nvidia.getId(), "10-K", OLDER_ACCESSION, LocalDate.parse("2025-02-26"),
                "https://www.sec.gov/Archives/edgar/data/1045810/ts239-nvda-older.htm", Examples.nvidia10KOlder()));
        answerWith(request -> Examples.geminiNvidiaOlder());
        recordCalls(3, AiPurpose.TIMELINE, AiOutcome.ANSWERED, Instant.now());

        assertThatThrownBy(() -> finder.findPast(older, nvidia))
                .isInstanceOf(GeminiClient.GeminiException.class)
                .hasMessage(REASON);

        assertThat(pastRelationships.existsByReportId(older.getId())).isFalse();
        verify(gemini, never()).generate(any(AiRequest.class));

        aiCalls.deleteAll();
        finder.findPast(older, nvidia);

        assertThat(pastRelationships.existsByReportId(older.getId())).isTrue();
        verify(gemini, times(1)).generate(any(AiRequest.class));
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** The fake Gemini answers each call with the text this gives, and records the call as the real client does. */
    private void answerWith(java.util.function.Function<AiRequest, String> text) {
        when(gemini.generate(any(AiRequest.class))).thenAnswer(call -> {
            AiRequest request = call.getArgument(0);
            String answer = text.apply(request);
            Long callId = callLog.record(request, "test-model", answer, AiOutcome.ANSWERED, null, Instant.now(), 5);
            return new AiAnswer(answer, callId);
        });
    }

    /** The SEC knows none of AMD's suppliers: they are marked No Report Found, and spend no call to Gemini. */
    private void suppliersUnknownToTheSec() {
        for (String[] filer : FILERS) {
            when(sec.companyForTicker(filer[0])).thenReturn(null);
        }
    }

    /** Puts calls in the audit trail, as if Gemini had been asked for {@code purpose} at {@code startedAt}. */
    private void recordCalls(int count, AiPurpose purpose, AiOutcome outcome, Instant startedAt) {
        for (int i = 0; i < count; i++) {
            callLog.record(AiRequest.of(purpose, "A call put there by ReadingLimitIT."), "test-model", null, outcome,
                    outcome == AiOutcome.FAILED ? "The AI could not be reached. Try again later." : null, startedAt, 5);
        }
    }

    private void analyse(String email, Portfolio portfolio) throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
    }

    private Portfolio portfolioWith(String email) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Limit " + System.nanoTime()));
        holdings.save(new Holding(portfolio.getId(), "AMD", new BigDecimal("10")));
        return portfolio;
    }

    private Holding holdingOf(Portfolio portfolio) {
        return holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst();
    }

    /**
     * Waits until the holding is Done and every supplier asked for has finished (No Report Found, with no call to
     * Gemini), and until {@code visited} of the three suppliers the fake SEC knows have been looked at: one that was
     * refused for the limit is back to no status, so it is told from one not yet reached by its SEC number, which
     * is saved when it is looked at.
     */
    private void awaitSuppliersSettled(Portfolio portfolio, int visited) throws Exception {
        await(() -> {
            Long amd = holdingOf(portfolio).getCompanyId();
            if (amd == null || holdingOf(portfolio).getStatus() != HoldingStatus.DONE) return false;
            SupplierReader.Progress progress = supplierReader.progressOf(List.of(amd));
            long looked = FILERS.stream().filter(filer -> companies.findByCik(filer[2]).isPresent()).count();
            return progress.toRead() > 0 && progress.read() == progress.toRead() && looked == visited;
        });
    }

    private static void await(Supplier<Boolean> done) throws Exception {
        for (int attempt = 0; attempt < 600; attempt++) {
            if (done.get()) return;
            Thread.sleep(50);
        }
        assertThat(done.get()).as("finished in time").isTrue();
    }

    private static String urlOf(String ticker) {
        return "https://www.sec.gov/Archives/edgar/data/ts239/" + ticker.toLowerCase(Locale.ROOT) + "-10k.htm";
    }

    /** The report's text as the SEC's HTML would give it: each paragraph in its own {@code <p>}. */
    private static String html(String text) {
        Document document = Document.createShell("");
        text.lines().forEach(line -> document.body().appendElement("p").text(line));
        return document.outerHtml();
    }

    /**
     * Empties the audit trail and removes what an earlier test saved: AMD's report, the three suppliers the fake SEC
     * knows, the earlier NVIDIA report this test saved, and every company the answers name. One with an SEC number
     * from elsewhere (another test's holding) is kept, with no report status.
     */
    private void purge() {
        aiCalls.deleteAll();
        companies.findByCik(AMD_CIK).ifPresent(amd -> reports.deleteByCompanyId(amd.getId()));
        reports.findByAccessionNumber(OLDER_ACCESSION).ifPresent(reports::delete);
        for (String[] filer : FILERS) {
            companies.findByCik(filer[2]).ifPresent(companies::delete);
        }
        Set<String> names = new LinkedHashSet<>();
        for (String answer : List.of(Examples.geminiAmd(), Examples.geminiNvidia(), Examples.geminiNvidiaOlder())) {
            for (GeminiAnswer.Found item : answers.read(answer)) {
                names.add(item.company());
                if (!item.commonName().isBlank()) names.add(item.commonName());
                if (!item.alsoKnownAs().isBlank()) names.add(item.alsoKnownAs());
            }
        }
        for (String name : names) {
            nameRows.findByNameKey(CompanyDirectory.nameKey(name)).ifPresent(row ->
                    companies.findById(row.getCompanyId()).ifPresent(company -> {
                        if (company.getCik() == null) {
                            companies.delete(company);
                        } else {
                            company.setReportStatus(null);
                            company.setReportStatusReason(null);
                            companies.save(company);
                        }
                    }));
        }
    }
}
