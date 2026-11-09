package com.truesight.common;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.news.Alert;
import com.truesight.news.AlertRepository;
import com.truesight.news.Confidence;
import com.truesight.news.EventType;
import com.truesight.news.NewsArticle;
import com.truesight.news.NewsArticleRepository;
import com.truesight.news.NewsAssessment;
import com.truesight.news.NewsAssessmentRepository;
import com.truesight.news.NewsAssessor;
import com.truesight.news.NewsCheckStepRunner;
import com.truesight.news.NewsClient;
import com.truesight.news.RecommendationRepository;
import com.truesight.news.RecommendationWriter;
import com.truesight.news.Severity;
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
import com.truesight.relationship.SecondCheckPassService;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.support.Examples;
import com.truesight.support.FakeAi;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keep to the Limits on Every Server, criteria 1 to 5. Two threads, each with its own transaction and connection,
 * stand in for two servers. The fake clients count calls and hold each one open long enough for the threads to
 * overlap. Nothing here calls the SEC, Google News or Gemini. Every company is new to each test, so it shares nothing
 * with the other tests' data.
 */
@IntegrationTest
class DatabaseLockIT {

    private static final int HELD_OPEN_MILLIS = 300;

    @Autowired DatabaseLock lock;
    @Autowired AnalysisService analysis;
    @Autowired NewsAssessor assessor;
    @Autowired SecondCheckPassService secondChecks;
    @Autowired RecommendationWriter writer;
    @Autowired CompanyDirectory directory;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired NewsArticleRepository articles;
    @Autowired NewsAssessmentRepository assessments;
    @Autowired AlertRepository alerts;
    @Autowired RecommendationRepository recommendations;
    @Autowired AiCallLog callLog;
    @Autowired TestLogins logins;
    @Autowired MockMvc mockMvc;

    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;
    @MockitoBean NewsClient news;
    @MockitoBean NewsCheckStepRunner steps;

    @Test
    void aLockCanOnlyBeTakenInsideATransaction() {
        assertThatThrownBy(() -> lock.lock("anything")).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void twoAnalysesOfOneCompanyAtOnceGiveOneReportOneSetOfRelationshipsAndOneCall() throws Exception {
        String ticker = fakeFiler();
        AtomicInteger asked = new AtomicInteger();
        when(gemini.generate(any(AiRequest.class))).thenAnswer(call -> {
            asked.incrementAndGet();
            Thread.sleep(HELD_OPEN_MILLIS);
            return recorded(call.getArgument(0));
        });
        Holding first = analysing(ticker);
        Holding second = analysing(ticker);

        together(() -> analysis.analyze(first.getId()), () -> analysis.analyze(second.getId()));

        assertThat(asked).hasValue(1);
        assertThat(statusOf(first)).isEqualTo(HoldingStatus.DONE);
        assertThat(statusOf(second)).isEqualTo(HoldingStatus.DONE);
        Long companyId = holdings.findById(first.getId()).orElseThrow().getCompanyId();
        Long reportId = reports.findFirstByCompanyIdOrderByFilingDateDesc(companyId).orElseThrow().getId();
        assertThat(reports.findInfoByCompanyIdIn(List.of(companyId))).hasSize(1);
        List<Relationship> saved = relationships.findByReportIdOrderByIdAsc(reportId);
        assertThat(saved).isNotEmpty();
        assertThat(saved.stream().map(r -> r.getCounterpartyId() + " " + r.getType())).doesNotHaveDuplicates();
    }

    @Test
    void analysesOfTwoDifferentCompaniesRunAtTheSameTimeWithoutWaitingForEachOther() throws Exception {
        String firstTicker = fakeFiler();
        String secondTicker = fakeFiler();
        CountDownLatch bothInFlight = new CountDownLatch(2);
        when(gemini.generate(any(AiRequest.class))).thenAnswer(call -> {
            bothInFlight.countDown();
            // Only reached by both when neither analysis is waiting for the other.
            assertThat(bothInFlight.await(20, TimeUnit.SECONDS)).as("both analyses in flight at once").isTrue();
            return recorded(call.getArgument(0));
        });
        Holding first = analysing(firstTicker);
        Holding second = analysing(secondTicker);

        together(() -> analysis.analyze(first.getId()), () -> analysis.analyze(second.getId()));

        assertThat(bothInFlight.getCount()).isZero();
        assertThat(statusOf(first)).isEqualTo(HoldingStatus.DONE);
        assertThat(statusOf(second)).isEqualTo(HoldingStatus.DONE);
    }

    @Test
    void twoNewsChecksSharingACompanyAskGoogleNewsAboutItOnce() throws Exception {
        Company shared = company();
        AtomicInteger searched = new AtomicInteger();
        when(news.search(anyString())).thenAnswer(call -> {
            searched.incrementAndGet();
            Thread.sleep(HELD_OPEN_MILLIS);
            return List.of();
        });
        String firstUser = email();
        String secondUser = email();
        Portfolio first = holdingDone(firstUser, shared);
        Portfolio second = holdingDone(secondUser, shared);

        checkNews(first, firstUser);
        checkNews(second, secondUser);
        awaitIdle(first);
        awaitIdle(second);

        assertThat(searched).hasValue(1);
    }

    @Test
    void anArticleSharedByTwoNewsChecksIsJudgedOnce() throws Exception {
        Company shared = company();
        NewsArticle article = articles.save(new NewsArticle(shared.getId(), "https://example.com/" + UUID.randomUUID(),
                "Set by the test: a headline", null, Instant.now()));
        AtomicInteger asked = new AtomicInteger();
        when(gemini.generate(any(AiRequest.class), any())).thenAnswer(call -> {
            asked.incrementAndGet();
            Thread.sleep(HELD_OPEN_MILLIS);
            return FakeAi.answer(Examples.geminiNewsAssessment());
        });
        Portfolio first = holdingDone(email(), shared);
        Portfolio second = holdingDone(email(), shared);

        together(() -> assessor.run(first.getId()), () -> assessor.run(second.getId()));

        assertThat(asked).hasValue(1);
        assertThat(assessments.existsByArticleId(article.getId())).isTrue();
    }

    @Test
    void twoNewsChecksReachingOneEventWriteOneSuggestion() throws Exception {
        Company company = company();
        NewsArticle article = articles.save(new NewsArticle(company.getId(), "https://example.com/" + UUID.randomUUID(),
                "Set by the test: an event", null, Instant.now()));
        assessments.save(new NewsAssessment(article.getId(), true, EventType.OTHER_DISRUPTION, Severity.MEDIUM,
                Confidence.HIGH, "Set by the test.", "Set by the test."));
        Portfolio first = holdingDone(email(), company);
        Portfolio second = holdingDone(email(), company);
        alerts.save(new Alert(first.getId(), article.getId(), BigDecimal.TEN));
        alerts.save(new Alert(second.getId(), article.getId(), BigDecimal.TEN));
        AtomicInteger asked = new AtomicInteger();
        when(gemini.generate(any(AiRequest.class), any())).thenAnswer(call -> {
            asked.incrementAndGet();
            Thread.sleep(HELD_OPEN_MILLIS);
            return FakeAi.answer(Examples.geminiRecommendation());
        });

        together(() -> writer.run(first.getId()), () -> writer.run(second.getId()));

        assertThat(asked).hasValue(1);
        assertThat(recommendations.findByArticleIdIn(List.of(article.getId()))).hasSize(1);
    }

    @Test
    void twoJobsCheckingOneReportAtOnceJudgeItOnce() throws Exception {
        String ticker = fakeFiler();
        when(gemini.generate(any(AiRequest.class))).thenAnswer(call -> recorded(call.getArgument(0)));
        Holding holding = analysing(ticker);
        analysis.analyze(holding.getId());
        Long companyId = holdings.findById(holding.getId()).orElseThrow().getCompanyId();
        Long reportId = reports.findFirstByCompanyIdOrderByFilingDateDesc(companyId).orElseThrow().getId();
        AtomicInteger asked = new AtomicInteger();
        when(gemini.generateSecondCheck(any(AiRequest.class))).thenAnswer(call -> {
            asked.incrementAndGet();
            Thread.sleep(50);
            throw new GeminiClient.GeminiException("Not answered in this test.", null);
        });

        // Every call fails, so the verdicts stay empty and the same questions can be asked again: first by one job alone.
        secondChecks.checkOnce(reportId);
        int alone = asked.getAndSet(0);

        together(() -> secondChecks.checkOnce(reportId), () -> secondChecks.checkOnce(reportId));

        assertThat(alone).isPositive();
        assertThat(asked).hasValue(alone);
    }

    /** A filer the fake SEC knows, under a ticker no other test uses, whose 10-K is NVIDIA's real one. */
    private String fakeFiler() {
        String id = UUID.randomUUID().toString().replace("-", "");
        String ticker = "L" + id.substring(0, 7).toUpperCase();
        String cik = String.format("%010d", Math.abs(id.hashCode()));
        String url = "https://www.sec.gov/Archives/edgar/data/lock/" + id + ".htm";
        when(sec.companyForTicker(ticker)).thenReturn(new SecClient.SecCompany("Lock Test " + id, cik, ticker));
        when(sec.latestAnnualFiling(cik)).thenReturn(new SecClient.SecFiling("10-K", "lock-" + id.substring(0, 18),
                LocalDate.parse("2026-02-25"), url));
        when(sec.download(url)).thenAnswer(call -> {
            Thread.sleep(50);
            return html(Examples.nvidia10K());
        });
        return ticker;
    }

    /** Records the call as the real client does, which is how the second analysis knows the report was read. */
    private AiAnswer recorded(AiRequest request) {
        Long callId = callLog.record(request, "test-model", Examples.geminiNvidia(), AiOutcome.ANSWERED, null,
                Instant.now(), 5);
        return new AiAnswer(Examples.geminiNvidia(), callId);
    }

    private Holding analysing(String ticker) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email()).getId(), "Lock Test"));
        Holding holding = new Holding(portfolio.getId(), ticker, new BigDecimal("10"));
        holding.setStatus(HoldingStatus.ANALYSING);
        return holdings.save(holding);
    }

    private Portfolio holdingDone(String email, Company company) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Lock Test"));
        Holding holding = new Holding(portfolio.getId(), "LOCK", new BigDecimal("10"));
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);
        return portfolio;
    }

    /** A company with no SEC number, so a news check asks only the fake Google News about it. */
    private Company company() {
        return directory.findOrCreate("Lock Event " + UUID.randomUUID().toString().replace("-", ""), null, null, List.of());
    }

    private HoldingStatus statusOf(Holding holding) {
        return holdings.findById(holding.getId()).orElseThrow().getStatus();
    }

    private void checkNews(Portfolio portfolio, String email) throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/news-check", portfolio.getId())
                .header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
    }

    private void awaitIdle(Portfolio portfolio) throws Exception {
        for (int attempt = 0; attempt < 400; attempt++) {
            if (portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatus() == NewsStatus.IDLE) return;
            Thread.sleep(50);
        }
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatus()).isEqualTo(NewsStatus.IDLE);
    }

    private static String email() {
        return "lock-" + UUID.randomUUID() + "@example.com";
    }

    private static void together(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(2);
        CompletableFuture<Void> one = CompletableFuture.runAsync(() -> afterBoth(start, first));
        CompletableFuture<Void> two = CompletableFuture.runAsync(() -> afterBoth(start, second));
        CompletableFuture.allOf(one, two).get(60, TimeUnit.SECONDS);
    }

    private static void afterBoth(CountDownLatch start, Runnable work) {
        start.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            throw new IllegalStateException(interrupted);
        }
        work.run();
    }

    private static String html(String text) {
        Document document = Document.createShell("");
        text.lines().forEach(line -> document.body().appendElement("p").text(line));
        return document.outerHtml();
    }
}
