package com.truesight.config;

import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.company.ReportStatus;
import com.truesight.news.NewsCheckService;
import com.truesight.news.NewsCheckStepRunner;
import com.truesight.news.NewsClient;
import com.truesight.portfolio.AnalysisService;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.NewsStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.truesight.support.FakeAi.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recovery of work a server restart interrupted, criteria 2 to 8. The schedule is off (the test profile), so each test
 * writes its own {@code workers} rows, calls {@code recoverAll()} and the buttons' endpoints directly. A server that has
 * gone is a random id with no row, or a row older than 2 minutes. No SEC or Gemini: both are faked.
 */
@IntegrationTest
class WorkRecoveryIT {

    private static final String ANALYSIS_INTERRUPTED = "The analysis was interrupted. Press Analyse to try again.";
    private static final String NEWS_INTERRUPTED = "The News Check Was Interrupted. Check News to Try Again.";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired WorkerHeartbeat heartbeat;
    @Autowired WorkRecovery recovery;
    @Autowired WorkerRepository workers;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyRepository companies;
    @Autowired CompanyDirectory directory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired SupplierReader supplierReader;
    @Autowired AnalysisService analysisService;
    @Autowired NewsCheckService newsCheck;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;
    @MockitoBean NewsClient news;
    @MockitoBean NewsCheckStepRunner steps;

    private final List<Portfolio> portfolioList = new ArrayList<>();
    private final List<Company> companyList = new ArrayList<>();
    private final List<UUID> workerRows = new ArrayList<>();

    @BeforeEach
    void fakeTheOutsideServices() {
        reset(sec, gemini, news, steps);
    }

    @AfterEach
    void removeWhatTheTestSaved() {
        for (Portfolio portfolio : portfolioList) {
            holdings.deleteAll(holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()));
            portfolios.deleteById(portfolio.getId());
        }
        for (Company company : companyList) {
            companies.findById(company.getId()).ifPresent(companies::delete);
        }
        workerRows.forEach(workers::deleteById);
    }

    // ---- criterion 2: running work records its server ------------------------------------------------------------

    @Test
    void aHoldingBeingAnalysedRecordsItsServer() throws Exception {
        String email = "recovery-records-holding@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = holding(portfolio, "RCV1", HoldingStatus.WAITING, null, null);

        analyse(portfolio, email).andExpect(status().isAccepted());

        await(() -> holdingNow(holding).getStatus() != HoldingStatus.ANALYSING);
        assertThat(holdingNow(holding).getWorkerId()).isEqualTo(heartbeat.id());
    }

    @Test
    void aPortfolioBeingCheckedRecordsItsServer() throws Exception {
        String email = "recovery-records-news@example.com";
        Portfolio portfolio = portfolio(email);
        holding(portfolio, "RCV2", HoldingStatus.DONE, company("Records News"), null);

        mockMvc.perform(post("/api/portfolios/{id}/news-check", portfolio.getId())
                .header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());

        await(() -> portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatus() == NewsStatus.IDLE);
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsWorkerId()).isEqualTo(heartbeat.id());
    }

    @Test
    void aDailyCheckRecordsItsServer() {
        Portfolio portfolio = portfolio("recovery-records-daily@example.com");
        holding(portfolio, "RCV3", HoldingStatus.DONE, company("Records Daily"), null);

        assertThat(newsCheck.startForDaily(portfolio.getId())).isTrue();

        Portfolio saved = portfolios.findById(portfolio.getId()).orElseThrow();
        assertThat(saved.getNewsStatus()).isEqualTo(NewsStatus.CHECKING);
        assertThat(saved.getNewsWorkerId()).isEqualTo(heartbeat.id());
    }

    @Test
    void aSupplierQueuedForReadingRecordsItsServer() {
        Portfolio portfolio = portfolio("recovery-records-supplier@example.com");
        Company held = company("Records Held");
        Company supplier = supplier(held, "RCVS", null, null);
        holding(portfolio, "RCV4", HoldingStatus.DONE, held, null);

        List<Long> queued = supplierReader.queue(held.getId());

        assertThat(queued).containsExactly(supplier.getId());
        Company saved = companies.findById(supplier.getId()).orElseThrow();
        assertThat(saved.getReportStatus()).isEqualTo(ReportStatus.WAITING);
        assertThat(saved.getReportWorkerId()).isEqualTo(heartbeat.id());
    }

    // ---- criterion 3: a holding whose server has gone is Failed, and Analyse tries it again -----------------------

    @Test
    void theEveryMinuteLookFailsInterruptedWorkWhenItRunsOnItsOwnSchedule() {
        // Built by hand with the schedule on, and called as the scheduler calls it: from outside any transaction.
        WorkRecovery onSchedule = new WorkRecovery(holdings, portfolios, companies, supplierReader, heartbeat, true,
                transactionManager);
        Portfolio portfolio = portfolio("recovery-on-schedule@example.com");
        Holding gone = holding(portfolio, "RCV0", HoldingStatus.ANALYSING, null, UUID.randomUUID());

        onSchedule.scheduledRecovery();

        assertThat(holdingNow(gone).getStatus()).isEqualTo(HoldingStatus.FAILED);
        assertThat(holdingNow(gone).getStatusReason()).isEqualTo(ANALYSIS_INTERRUPTED);
    }

    @Test
    void aHoldingAnalysingOnAServerThatHasGoneBecomesFailedWithTheReason() {
        Portfolio portfolio = portfolio("recovery-holding-gone@example.com");
        Holding gone = holding(portfolio, "RCV5", HoldingStatus.ANALYSING, null, UUID.randomUUID());
        Holding stale = holding(portfolio, "RCV6", HoldingStatus.ANALYSING, null, workerSeen(Duration.ofMinutes(10)));

        recovery.recoverAll();

        assertThat(holdingNow(gone).getStatus()).isEqualTo(HoldingStatus.FAILED);
        assertThat(holdingNow(gone).getStatusReason()).isEqualTo(ANALYSIS_INTERRUPTED);
        assertThat(holdingNow(stale).getStatus()).isEqualTo(HoldingStatus.FAILED);
        assertThat(holdingNow(stale).getStatusReason()).isEqualTo(ANALYSIS_INTERRUPTED);
    }

    @Test
    void pressingAnalyseTriesAnInterruptedHoldingAgain() throws Exception {
        String email = "recovery-holding-retry@example.com";
        Portfolio portfolio = portfolio(email);
        Holding gone = holding(portfolio, "RCV7", HoldingStatus.ANALYSING, null, UUID.randomUUID());
        recovery.recoverAll();
        assertThat(holdingNow(gone).getStatus()).isEqualTo(HoldingStatus.FAILED);

        analyse(portfolio, email).andExpect(status().isAccepted());

        // The fake SEC does not know the ticker, so the holding ends with No Report Found: it was tried again.
        await(() -> holdingNow(gone).getStatus() == HoldingStatus.NO_REPORT_FOUND);
        verify(sec).companyForTicker("RCV7");
    }

    @Test
    void analyseLooksForInterruptedWorkBeforeRefusingWithA409() throws Exception {
        String email = "recovery-holding-button@example.com";
        Portfolio portfolio = portfolio(email);
        Holding gone = holding(portfolio, "RCV8", HoldingStatus.ANALYSING, null, UUID.randomUUID());

        analyse(portfolio, email).andExpect(status().isAccepted());

        await(() -> holdingNow(gone).getStatus() == HoldingStatus.NO_REPORT_FOUND);
    }

    // ---- criterion 4: a holding whose server is alive is left alone -----------------------------------------------

    @Test
    void aHoldingAnalysingOnAServerThatIsAliveIsLeftAloneAndAnalyseStillAnswers409() throws Exception {
        String email = "recovery-holding-alive@example.com";
        Portfolio portfolio = portfolio(email);
        Holding alive = holding(portfolio, "RCV9", HoldingStatus.ANALYSING, null, workerSeen(Duration.ofSeconds(20)));

        recovery.recoverAll();

        assertThat(holdingNow(alive).getStatus()).isEqualTo(HoldingStatus.ANALYSING);
        analyse(portfolio, email).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Analysis is already running for this portfolio."));
        assertThat(holdingNow(alive).getStatus()).isEqualTo(HoldingStatus.ANALYSING);
    }

    // ---- criterion 5: a news check whose server has gone is Failed; Check News and the daily check start again ----

    @Test
    void aPortfolioCheckingOnAServerThatHasGoneBecomesFailedWithTheReason() {
        Portfolio portfolio = portfolio("recovery-news-gone@example.com");
        checking(portfolio, UUID.randomUUID());

        recovery.recoverAll();

        Portfolio saved = portfolios.findById(portfolio.getId()).orElseThrow();
        assertThat(saved.getNewsStatus()).isEqualTo(NewsStatus.FAILED);
        assertThat(saved.getNewsStatusReason()).isEqualTo(NEWS_INTERRUPTED);
    }

    @Test
    void checkNewsStartsANewCheckAfterAnInterruptedOne() throws Exception {
        String email = "recovery-news-button@example.com";
        Portfolio portfolio = portfolio(email);
        holding(portfolio, "RCV10", HoldingStatus.DONE, company("News Button"), null);
        checking(portfolio, UUID.randomUUID());

        // Pressed with no recoverAll: it looks for this portfolio's interrupted work first, before refusing.
        mockMvc.perform(post("/api/portfolios/{id}/news-check", portfolio.getId())
                .header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());

        await(() -> portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatus() == NewsStatus.IDLE);
    }

    @Test
    void theDailyCheckStartsANewCheckAfterAnInterruptedOneWithoutAnyoneAsking() {
        Portfolio portfolio = portfolio("recovery-news-daily@example.com");
        holding(portfolio, "RCV11", HoldingStatus.DONE, company("News Daily"), null);
        checking(portfolio, UUID.randomUUID());

        assertThat(newsCheck.startForDaily(portfolio.getId())).isTrue();

        Portfolio saved = portfolios.findById(portfolio.getId()).orElseThrow();
        assertThat(saved.getNewsStatus()).isEqualTo(NewsStatus.CHECKING);
        assertThat(saved.getNewsWorkerId()).isEqualTo(heartbeat.id());
    }

    @Test
    void aCheckOnAServerThatIsAliveIsNotTakenOver() throws Exception {
        String email = "recovery-news-alive@example.com";
        Portfolio portfolio = portfolio(email);
        holding(portfolio, "RCV12", HoldingStatus.DONE, company("News Alive"), null);
        checking(portfolio, workerSeen(Duration.ofSeconds(20)));

        mockMvc.perform(post("/api/portfolios/{id}/news-check", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("News is already being checked for this portfolio."));
        assertThat(newsCheck.startForDaily(portfolio.getId())).isFalse();
    }

    // ---- criterion 6: a supplier whose server has gone is read at the next Analyse ----------------------------------

    @Test
    void aSupplierWaitingOnAServerThatHasGoneIsReadAtTheNextAnalyseEvenWhenEveryHoldingIsDone() throws Exception {
        assertASupplierIsReadAgain("recovery-supplier-waiting@example.com", ReportStatus.WAITING, "RCVW");
    }

    @Test
    void aSupplierAnalysingOnAServerThatHasGoneIsReadAtTheNextAnalyse() throws Exception {
        assertASupplierIsReadAgain("recovery-supplier-analysing@example.com", ReportStatus.ANALYSING, "RCVA");
    }

    private void assertASupplierIsReadAgain(String email, ReportStatus stuck, String ticker) throws Exception {
        Portfolio portfolio = portfolio(email);
        Company held = company("Supplier Held " + ticker);
        Report report = reports.save(new Report(held.getId(), "10-K", "wr-" + System.nanoTime(),
                LocalDate.parse("2026-02-25"), "https://www.sec.gov/example", "Report text."));
        // Everything the holding needs is read, so only the supplier is missing.
        report.setFiguresReadAt(Instant.now());
        reports.save(report);
        Company supplier = supplier(held, ticker, stuck, UUID.randomUUID());
        Holding holding = holding(portfolio, "HELD" + ticker, HoldingStatus.DONE, held, null);

        analyse(portfolio, email).andExpect(status().isAccepted());

        // The fake SEC does not know the supplier's ticker: it is read (asked about) and ends with No Report Found.
        await(() -> companies.findById(supplier.getId()).orElseThrow().getReportStatus() == ReportStatus.NO_REPORT_FOUND);
        verify(sec).companyForTicker(ticker);
        assertThat(holdingNow(holding).getStatus()).isEqualTo(HoldingStatus.DONE);
        // The holding's own report is not sent to the AI again.
        verify(gemini, never()).generate(any(AiRequest.class));
    }

    // ---- criterion 7: a holding marked Failed whose work then finishes ends as Done -------------------------------

    @Test
    void aHoldingMarkedFailedWhoseWorkThenFinishesEndsAsDone() throws Exception {
        String email = "recovery-finishes@example.com";
        String cik = String.format("%010d", 900_000_000L + System.nanoTime() % 99_000_000L);
        String url = "https://www.sec.gov/Archives/edgar/data/ts232/finish.htm";
        Portfolio portfolio = portfolio(email);
        Holding holding = holding(portfolio, "RCVF", HoldingStatus.WAITING, null, null);
        CountDownLatch working = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(sec.companyForTicker("RCVF")).thenAnswer(call -> {
            working.countDown();
            release.await(10, TimeUnit.SECONDS);
            return new SecClient.SecCompany("RECOVERY FINISHES INC", cik, "RCVF");
        });
        when(sec.latestAnnualFiling(cik)).thenReturn(
                new SecClient.SecFiling("10-K", "ts232-" + System.nanoTime() % 1_000_000L, LocalDate.parse("2026-02-25"), url));
        when(sec.download(url)).thenReturn(html(Examples.nvidia10K()));
        when(gemini.generate(any(AiRequest.class))).thenAnswer(call -> answer(Examples.geminiNvidia()));

        analyse(portfolio, email).andExpect(status().isAccepted());
        assertThat(working.await(10, TimeUnit.SECONDS)).as("the analysis started").isTrue();
        // The server is wrongly thought gone while it works: the same conditional update recovery runs, as another server.
        Integer changed = new TransactionTemplate(transactionManager).execute(status ->
                holdings.failInterruptedIn(portfolio.getId(), Holding.INTERRUPTED_REASON, UUID.randomUUID(),
                        Instant.now().minus(Duration.ofMinutes(2))));
        assertThat(changed).isEqualTo(1);
        assertThat(holdingNow(holding).getStatus()).isEqualTo(HoldingStatus.FAILED);

        release.countDown();

        await(() -> holdingNow(holding).getStatus() == HoldingStatus.DONE);
        assertThat(holdingNow(holding).getStatusReason()).isNull();
        companies.findByCik(cik).ifPresent(companyList::add);
        await(() -> {
            SupplierReader.Progress progress = supplierReader.progressOf(List.of(holdingNow(holding).getCompanyId()));
            return progress.read() == progress.toRead();
        });
    }

    @Test
    void aHoldingNotStartedYetWhenItIsMarkedFailedIsSkipped() {
        Portfolio portfolio = portfolio("recovery-skipped@example.com");
        Holding holding = holding(portfolio, "RCVG", HoldingStatus.FAILED, null, UUID.randomUUID());

        analysisService.analyze(holding.getId());

        assertThat(holdingNow(holding).getStatus()).isEqualTo(HoldingStatus.FAILED);
        verify(sec, never()).companyForTicker(any());
    }

    // ---- criterion 8: work saved before V5, with no server recorded, counts as interrupted -------------------------

    @Test
    void workSavedWithNoServerRecordedCountsAsInterrupted() {
        Portfolio portfolio = portfolio("recovery-no-server@example.com");
        Holding holding = holding(portfolio, "RCVN", HoldingStatus.ANALYSING, null, null);
        checking(portfolio, null);
        Company held = company("No Server Held");
        Company supplier = supplier(held, "RCVNS", ReportStatus.WAITING, null);

        recovery.recoverAll();

        assertThat(holdingNow(holding).getStatus()).isEqualTo(HoldingStatus.FAILED);
        assertThat(holdingNow(holding).getStatusReason()).isEqualTo(ANALYSIS_INTERRUPTED);
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatus()).isEqualTo(NewsStatus.FAILED);
        assertThat(companies.findById(supplier.getId()).orElseThrow().getReportStatus()).isNull();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions analyse(Portfolio portfolio, String email) throws Exception {
        return mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId())
                .header("Authorization", logins.bearer(email)));
    }

    private Portfolio portfolio(String email) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Recovery " + System.nanoTime()));
        portfolioList.add(portfolio);
        return portfolio;
    }

    private Holding holding(Portfolio portfolio, String ticker, HoldingStatus status, Company company, UUID worker) {
        Holding holding = new Holding(portfolio.getId(), ticker, new BigDecimal("10"));
        holding.setStatus(status);
        holding.setWorkerId(worker);
        if (company != null) holding.setCompanyId(company.getId());
        return holdings.save(holding);
    }

    private void checking(Portfolio portfolio, UUID worker) {
        Portfolio saved = portfolios.findById(portfolio.getId()).orElseThrow();
        saved.setNewsStatus(NewsStatus.CHECKING);
        saved.setNewsWorkerId(worker);
        portfolios.save(saved);
    }

    private Company company(String name) {
        Company company = directory.findOrCreate(name + " " + UUID.randomUUID(), null, null, List.of());
        companyList.add(company);
        return company;
    }

    /** A supplier of {@code held}, in the given report status and owned by the given server, with a ticker. */
    private Company supplier(Company held, String ticker, ReportStatus reportStatus, UUID worker) {
        Company supplier = directory.findOrCreate("Supplier " + ticker + " " + UUID.randomUUID(), null, ticker, List.of());
        supplier.setReportStatus(reportStatus);
        supplier.setReportWorkerId(worker);
        companies.save(supplier);
        companyList.add(supplier);
        Report report = reports.findFirstByCompanyIdOrderByFilingDateDesc(held.getId()).orElseGet(() ->
                reports.save(new Report(held.getId(), "10-K", "wr-" + System.nanoTime(), LocalDate.parse("2026-02-25"),
                        "https://www.sec.gov/example", "Report text.")));
        if (relationships.findByReportIdOrderByIdAsc(report.getId()).stream()
                .noneMatch(relationship -> supplier.getId().equals(relationship.getCounterpartyId()))) {
            relationships.save(new Relationship(report.getId(), held.getId(), supplier.getId(),
                    RelationshipType.SUPPLIER, "chips", "We buy chips."));
        }
        return supplier;
    }

    /** A server last seen this long ago. */
    private UUID workerSeen(Duration ago) {
        UUID id = UUID.randomUUID();
        workers.save(new Worker(id, Instant.now().minus(ago)));
        workerRows.add(id);
        return id;
    }

    private Holding holdingNow(Holding holding) {
        return holdings.findById(holding.getId()).orElseThrow();
    }

    private static void await(Supplier<Boolean> done) throws Exception {
        for (int attempt = 0; attempt < 400; attempt++) {
            if (done.get()) return;
            Thread.sleep(25);
        }
        assertThat(done.get()).as("finished in time").isTrue();
    }

    /** The report's text as the SEC's HTML would give it: each paragraph in its own {@code <p>}. */
    private static String html(String text) {
        Document document = Document.createShell("");
        text.lines().forEach(line -> document.body().appendElement("p").text(line));
        return document.outerHtml();
    }
}
