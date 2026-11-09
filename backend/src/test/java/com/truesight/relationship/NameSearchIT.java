package com.truesight.relationship;

import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
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
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.truesight.support.FakeAi.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Find Who Names My Holding, criteria 1 to 6 and 8, through a real Analyse; and, from the review, that a filing the AI
 * fails on, or the day's limit on name searches, leaves the search unrecorded so a later Analyse carries on.
 *
 * <p>The holding is Cirrus Logic. The fake SEC answers its name search with the SEC's real, unchanged answer for
 * "Cirrus Logic" ({@code docs/examples/sec-search-cirrus-logic.json}), and serves CEVA's real 10-K
 * ({@code ceva-10k.txt}) for CEVA's hit. The fake Gemini gives its real answer for CEVA's passages
 * ({@code gemini-name-search-ceva.json}): Cirrus Logic is CEVA's customer. {@code docs/examples} holds no other report
 * the search found, so every other company's hit is served AMD's real 10-K ({@code amd-10k.txt}), which never names
 * Cirrus Logic: a filing with no passage naming the holding, as most of the real ones are. Criterion 6 uses NVIDIA's
 * real 10-K and Gemini's real answer for it. Nothing here calls the SEC or Gemini, and the calls are counted.
 *
 * <p>The database is shared by every integration test, so each test starts and ends by removing the companies the
 * search found, and the reports and name searches of Cirrus Logic and NVIDIA.
 */
@IntegrationTest
class NameSearchIT {

    private static final String CIRRUS_CIK = "0000772406";
    private static final String CEVA_CIK = "0001173489";
    private static final String NVIDIA_CIK = "0001045810";
    private static final String CEVA_URL =
            "https://www.sec.gov/Archives/edgar/data/1173489/000143774926006091/ceva20251231_10k.htm";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory directory;
    @Autowired CompanyRepository companies;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired NameSearchRepository searches;
    @Autowired GeminiAnswer answers;
    @Autowired AiCallLog aiCallLog;

    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;
    /** The real limit, which tests never reach ({@code application-test.yml}), unless a test makes it refuse. */
    @MockitoSpyBean ReadingLimit readingLimit;

    private List<SecClient.SecSearchHit> hits;
    private Company cirrus;

    @BeforeEach
    void fakeTheSecAndGemini() throws Exception {
        hits = SecClient.searchHitsIn(JsonMapper.builder().build().readTree(Examples.secSearchCirrusLogic()));
        purge();
        cirrus = directory.findOrCreate("CIRRUS LOGIC, INC.", CIRRUS_CIK, "CRUS", List.of());
        when(sec.searchAnnualReports(anyString(), any(), any())).thenReturn(hits);
        when(sec.download(anyString())).thenReturn(html(Examples.amd10K()));
        when(sec.download(CEVA_URL)).thenReturn(html(Examples.ceva10K()));
        when(sec.taggedFigures(any())).thenReturn(Optional.empty());
        when(gemini.generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.NAME_SEARCH), anyMap()))
                .thenReturn(answer(Examples.geminiNameSearchCeva()));
        when(gemini.generateSecondCheck(any()))
                .thenThrow(new GeminiClient.GeminiException("The second check is not asked in this test.", null));
    }

    @AfterEach
    void removeWhatTheTestSaved() {
        purge();
    }

    // ---- criterion 1 ---------------------------------------------------------------------------------------------

    @Test
    void whenAHoldingBecomesDoneItsNameIsSearchedInTheLastFifteenMonthsAndTheSearchIsRecordedWithItsFilingCount()
            throws Exception {
        storeAReadReportFor(cirrus);
        when(sec.companyForTicker("CRUS")).thenReturn(new SecClient.SecCompany("CIRRUS LOGIC, INC.", CIRRUS_CIK, "CRUS"));
        String email = "name-search-done@example.com";
        Portfolio portfolio = portfolioHolding(email, HoldingStatus.WAITING);

        analyse(email, portfolio);

        await(() -> searches.findById(cirrus.getId()).isPresent());
        assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        verify(sec).searchAnnualReports("Cirrus Logic", today.minusMonths(15), today);
        // 12 filings: every hit that is another company's report, not an exhibit (Kulicke & Soffa's) or Cirrus Logic's
        // own. ZRCN has two, of which only the first is read.
        assertThat(searches.findById(cirrus.getId()).orElseThrow().getFilingsFound()).isEqualTo(12);
        verify(sec, times(11)).download(anyString());
        verify(sec, never()).download(argThat((String url) -> url.contains("/772406/") || url.contains("ex10")));
    }

    // ---- criterion 2 ---------------------------------------------------------------------------------------------

    @Test
    void aFilingWhosePassagesNameTheHoldingGivesAnUnreviewedSecondCheckedNameSearchRelationshipOfThatFilingsCompany()
            throws Exception {
        searchForCirrus("name-search-relationship@example.com");

        Company ceva = companies.findByCik(CEVA_CIK).orElseThrow();
        Report cevaReport = reports.findFirstByCompanyIdOrderByFilingDateDesc(ceva.getId()).orElseThrow();
        List<Relationship> found = relationships.findByReportIdOrderByIdAsc(cevaReport.getId());
        assertThat(found).hasSize(1);
        Relationship relationship = found.getFirst();
        assertThat(relationship.getCompanyId()).isEqualTo(ceva.getId());
        assertThat(relationship.getCounterpartyId()).isEqualTo(cirrus.getId());
        assertThat(relationship.getType()).isEqualTo(RelationshipType.CUSTOMER);
        assertThat(relationship.getProvides()).isEqualTo("IP licenses");
        assertThat(relationship.getFoundBy()).isEqualTo(FoundBy.NAME_SEARCH);
        assertThat(relationship.getReviewStatus()).isEqualTo(ReviewStatus.UNREVIEWED);
        // The sentence is the report's own, found by checking Gemini's quote against it.
        String quote = answers.readNameSearch(Examples.geminiNameSearchCeva()).getFirst().quote();
        assertThat(relationship.getEvidence())
                .isEqualTo(new QuoteChecker(Examples.ceva10K()).evidenceFor(quote).orElseThrow());
        // The second AI was asked about it (this fake cannot answer, so it stays saved with no second check).
        verify(gemini, timeout(5_000)).generateSecondCheck(argThat(request -> cevaReport.getId().equals(request.reportId())));
    }

    // ---- criterion 3 ---------------------------------------------------------------------------------------------

    @Test
    void aFilingWithNoPassageNamingTheHoldingBesideASupplierOrCustomerWordIsNotSentToTheAi() throws Exception {
        searchForCirrus("name-search-no-passage@example.com");

        Long cevaReportId = reports.findFirstByCompanyIdOrderByFilingDateDesc(
                companies.findByCik(CEVA_CIK).orElseThrow().getId()).orElseThrow().getId();
        // 11 filings were read; only CEVA's names Cirrus Logic beside a customer word.
        verify(gemini, times(1)).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.NAME_SEARCH), anyMap());
        verify(gemini).generate(argThat((AiRequest request) -> cevaReportId.equals(request.reportId())), anyMap());
        Long skyworks = companies.findByCik("0000004127").orElseThrow().getId();
        assertThat(reports.findFirstByCompanyIdOrderByFilingDateDesc(skyworks)).isPresent();
        assertThat(relationships.findByCompanyIdIn(List.of(skyworks))).isEmpty();
    }

    // ---- criterion 4 ---------------------------------------------------------------------------------------------

    @Test
    void aCompanySearchedInTheLast90DaysIsNotSearchedAgainAndAFilingReadForTheHoldingIsNotSentToTheAiAgain()
            throws Exception {
        searchForCirrus("name-search-recent@example.com");

        // Another portfolio holds Cirrus Logic too: searched already, so not again.
        String other = "name-search-recent-other@example.com";
        Portfolio second = portfolioHolding(other, HoldingStatus.DONE);
        analyse(other, second);
        Thread.sleep(500);
        verify(sec, times(1)).searchAnnualReports(anyString(), any(), any());

        // 91 days on, it is searched again, but CEVA's report names Cirrus Logic already: no new AI call.
        searches.save(new NameSearch(cirrus.getId(), Instant.now().minus(Duration.ofDays(91)), 12));
        Instant before = Instant.now();
        analyse(other, second);
        await(() -> searches.findById(cirrus.getId()).orElseThrow().getSearchedAt().isAfter(before));
        verify(sec, times(2)).searchAnnualReports(anyString(), any(), any());
        verify(gemini, times(1)).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.NAME_SEARCH), anyMap());
        verify(sec, times(11)).download(anyString()); // every report was stored the first time
    }

    // ---- criterion 5 ---------------------------------------------------------------------------------------------

    @Test
    void aCompanyWhoseReportIsStoredIsReadFromItNotDownloadedAgain() throws Exception {
        Company ceva = directory.findOrCreate("CEVA INC", CEVA_CIK, "CEVA", List.of());
        Report stored = reports.save(new Report(ceva.getId(), "10-K", "ts226-ceva-stored", LocalDate.parse("2026-02-27"),
                CEVA_URL, Examples.ceva10K()));

        searchForCirrus("name-search-stored@example.com");

        verify(sec, never()).download(CEVA_URL);
        assertThat(relationships.findByReportIdOrderByIdAsc(stored.getId()))
                .extracting(Relationship::getCounterpartyId).containsExactly(cirrus.getId());
    }

    // ---- criterion 6 ---------------------------------------------------------------------------------------------

    @Test
    void readingThatCompanysWholeReportLaterStillHappensAndDoesNotSaveTheSameRelationshipTwice() throws Exception {
        // What a name search for TSMC would have saved from NVIDIA's report: NVIDIA's real sentence naming TSMC.
        Company nvidia = directory.findOrCreate("NVIDIA CORP", NVIDIA_CIK, "NVDA", List.of());
        Report report = reports.save(new Report(nvidia.getId(), "10-K", "ts226-nvda", LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/edgar/data/1045810/ts226-nvda.htm", Examples.nvidia10K()));
        GeminiAnswer.Found tsmcItem = answers.read(Examples.geminiNvidia()).getFirst();
        Company tsmc = directory.findOrCreate(tsmcItem.company(), null, null, List.of());
        Relationship byName = new Relationship(report.getId(), nvidia.getId(), tsmc.getId(), RelationshipType.SUPPLIER,
                tsmcItem.provides(), new QuoteChecker(Examples.nvidia10K()).evidenceFor(tsmcItem.quote()).orElseThrow());
        byName.setFoundBy(FoundBy.NAME_SEARCH);
        Long byNameId = relationships.save(byName).getId();
        when(sec.companyForTicker("NVDA")).thenReturn(new SecClient.SecCompany("NVIDIA CORP", NVIDIA_CIK, "NVDA"));
        when(sec.searchAnnualReports(anyString(), any(), any())).thenReturn(List.of());
        when(gemini.generate(argThat((AiRequest request) -> request != null
                && request.purpose() == AiPurpose.FIND_RELATIONSHIPS))).thenReturn(answer(Examples.geminiNvidia()));
        String email = "name-search-whole-report@example.com";
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Names " + System.nanoTime()));
        holdings.save(new Holding(portfolio.getId(), "NVDA", new BigDecimal("10")));

        analyse(email, portfolio);
        await(() -> holdingOf(portfolio).getStatus() == HoldingStatus.DONE);

        verify(gemini, times(1)).generate(argThat((AiRequest request) -> request != null
                && request.purpose() == AiPurpose.FIND_RELATIONSHIPS));
        List<Relationship> saved = relationships.findByReportIdOrderByIdAsc(report.getId());
        assertThat(saved.stream().filter(r -> r.getCounterpartyId().equals(tsmc.getId())
                && r.getType() == RelationshipType.SUPPLIER)).extracting(Relationship::getId).containsExactly(byNameId);
        assertThat(saved).hasSizeGreaterThan(1)
                .filteredOn(r -> r.getFoundBy() == FoundBy.REPORT).isNotEmpty();
    }

    // ---- criterion 8 ---------------------------------------------------------------------------------------------

    @Test
    void whenTheSearchFailsTheHoldingStaysDoneAndTheNextAnalyseSearchesAgain() throws Exception {
        when(sec.searchAnnualReports(anyString(), any(), any()))
                .thenThrow(new SecClient.SecAccessException("The SEC returned HTTP 403 for a request.", null))
                .thenReturn(hits);
        String email = "name-search-fails@example.com";
        Portfolio portfolio = portfolioHolding(email, HoldingStatus.DONE);

        analyse(email, portfolio);
        verify(sec, timeout(5_000)).searchAnnualReports(anyString(), any(), any());
        Thread.sleep(300);
        assertThat(searches.findById(cirrus.getId())).isEmpty();
        assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);

        analyse(email, portfolio);
        await(() -> searches.findById(cirrus.getId()).isPresent());
        verify(sec, times(2)).searchAnnualReports(anyString(), any(), any());
        assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);
    }

    // ---- an AI failure, and the day's limit, from the review --------------------------------------------------------

    @Test
    void aFilingTheAiFailsOnLeavesTheSearchUnrecordedAndTheNextAnalyseSendsItAgainWithoutDownloadingAnything()
            throws Exception {
        doThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null))
                .doReturn(answer(Examples.geminiNameSearchCeva()))
                .when(gemini).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.NAME_SEARCH), anyMap());
        String email = "name-search-ai-fails@example.com";
        Portfolio portfolio = portfolioHolding(email, HoldingStatus.DONE);

        analyse(email, portfolio);
        verify(gemini, timeout(5_000)).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.NAME_SEARCH), anyMap());
        verify(sec, timeout(5_000).times(11)).download(anyString()); // the other filings are still read
        Thread.sleep(300);
        assertThat(searches.findById(cirrus.getId())).isEmpty();
        assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);

        analyse(email, portfolio);
        await(() -> searches.findById(cirrus.getId()).isPresent());
        verify(gemini, times(2)).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.NAME_SEARCH), anyMap());
        verify(sec, times(11)).download(anyString()); // every report was stored the first time, CEVA's included
        Long ceva = companies.findByCik(CEVA_CIK).orElseThrow().getId();
        assertThat(relationships.findByCompanyIdIn(List.of(ceva)))
                .extracting(Relationship::getCounterpartyId).containsExactly(cirrus.getId());
    }

    @Test
    void pastTheDaysReadingLimitNoFilingIsSentToTheAiAndTheSearchIsNotRecordedUntilThereIsRoom() throws Exception {
        doThrow(new ReadingLimit.ReachedException()).when(readingLimit).requireRoomForNameSearch();
        String email = "name-search-limit@example.com";
        Portfolio portfolio = portfolioHolding(email, HoldingStatus.DONE);

        analyse(email, portfolio);
        verify(readingLimit, timeout(5_000)).requireRoomForNameSearch();
        Thread.sleep(300);
        verify(gemini, never()).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.NAME_SEARCH), anyMap());
        assertThat(searches.findById(cirrus.getId())).isEmpty();
        assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);

        doCallRealMethod().when(readingLimit).requireRoomForNameSearch(); // the next day
        analyse(email, portfolio);
        await(() -> searches.findById(cirrus.getId()).isPresent());
        verify(gemini, times(1)).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.NAME_SEARCH), anyMap());
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** A portfolio holding Done Cirrus Logic, Analysed, and its name search finished. */
    private void searchForCirrus(String email) throws Exception {
        Portfolio portfolio = portfolioHolding(email, HoldingStatus.DONE);
        analyse(email, portfolio);
        await(() -> searches.findById(cirrus.getId()).isPresent());
    }

    private Portfolio portfolioHolding(String email, HoldingStatus status) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Names " + System.nanoTime()));
        Holding holding = new Holding(portfolio.getId(), "CRUS", new BigDecimal("10"));
        if (status == HoldingStatus.DONE) {
            holding.setCompanyId(cirrus.getId());
        }
        holding.setStatus(status);
        holdings.save(holding);
        return portfolio;
    }

    private void analyse(String email, Portfolio portfolio) throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
    }

    private Holding holdingOf(Portfolio portfolio) {
        return holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst();
    }

    /** A stored report of the company, already read whole: an answered relationship call with nothing kept. */
    private void storeAReadReportFor(Company company) {
        Report report = reports.save(new Report(company.getId(), "10-K", "ts226-crus", LocalDate.parse("2026-05-21"),
                "https://www.sec.gov/Archives/edgar/data/772406/ts226-crus.htm", "Test report text."));
        AiRequest read = new AiRequest(AiPurpose.FIND_RELATIONSHIPS, "Test prompt.", report.getId(),
                AiRequest.reportMarker(report.getId(), report.getText()));
        Long callId = aiCallLog.record(read, "test-model", "[]", AiOutcome.ANSWERED, null, Instant.now(), 1);
        aiCallLog.attachReport(callId, report.getId());
        aiCallLog.finish(callId, 0, 0);
    }

    private static void await(Supplier<Boolean> done) throws Exception {
        for (int attempt = 0; attempt < 600; attempt++) {
            if (done.get()) return;
            Thread.sleep(50);
        }
        assertThat(done.get()).as("finished in time").isTrue();
    }

    /** The report's text as the SEC's HTML would give it: each paragraph in its own {@code <p>}. */
    private static String html(String text) {
        Document document = Document.createShell("");
        text.lines().forEach(line -> document.body().appendElement("p").text(line));
        return document.outerHtml();
    }

    /**
     * Removes what an earlier test saved: the companies the search found (none is a holding), Cirrus Logic's and
     * NVIDIA's reports, with their relationships, and their name searches.
     */
    private void purge() {
        hits.stream().map(SecClient.SecSearchHit::cik).distinct().filter(cik -> !cik.equals(CIRRUS_CIK))
                .forEach(cik -> companies.findByCik(cik).ifPresent(companies::delete));
        for (String cik : List.of(CIRRUS_CIK, NVIDIA_CIK)) {
            companies.findByCik(cik).ifPresent(company -> {
                reports.deleteByCompanyId(company.getId());
                searches.findById(company.getId()).ifPresent(searches::delete);
            });
        }
    }
}
