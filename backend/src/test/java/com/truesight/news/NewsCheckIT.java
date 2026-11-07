package com.truesight.news;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
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
import com.truesight.relationship.ReviewStatus;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.report.SecClient;
import com.truesight.support.FakeAi;
import tools.jackson.databind.json.JsonMapper;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@IntegrationTest
class NewsCheckIT {

    private static final String NVIDIA_CIK = "0001045810";
    private static final String TSMC_CIK = "0001046179";
    private static final String REPORT_ACCESSION = "0001045810-26-000021";
    // Clorox's real 8-Ks, from docs/examples/sec-submissions-clorox.json.
    private static final String CLOROX = "The Clorox Company";
    private static final String CLOROX_CIK = "0000021076";
    // 14 August 2023, Item 8.01: unauthorized activity on its IT systems.
    private static final String CLOROX_CYBER_ATTACK = "0001206774-23-000969";
    private static final String CLOROX_CYBER_ATTACK_URL =
            "https://www.sec.gov/Archives/edgar/data/21076/000120677423000969/clx4231381-8k.htm";
    // 29 October 2026, Items 5.02, 7.01 and 9.01: none of the nine.
    private static final String CLOROX_NONE_OF_THE_NINE = "0001206774-26-000586";
    private static final String EVIDENCE = "We utilize foundries, such as Taiwan Semiconductor Manufacturing "
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
    @Autowired NewsArticleRepository articles;
    @Autowired NewsAssessmentRepository assessments;
    @Autowired List<NewsCheckStep> realSteps;
    @Autowired NewsCheckService newsCheck;

    @MockitoBean NewsClient news;
    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;
    @MockitoBean NewsCheckStepRunner steps;

    private List<NewsItem> sample;

    @BeforeEach
    void returnTheSavedGoogleNewsSample() {
        reset(news, sec, gemini, steps);
        sample = movedToNow(new GoogleNewsParser().parse(Examples.googleNewsTsmc()));
        when(news.search(anyString())).thenReturn(sample);
    }

    /**
     * The saved sample, with every article moved forward by the same amount so the newest was published an hour ago.
     * The headlines, links, publishers and the gaps between them stay as Google News sent them. Without this, the
     * "last 7 days" rule would drop the whole sample a week after it was saved, and these tests would fail.
     */
    private static List<NewsItem> movedToNow(List<NewsItem> items) {
        Instant newest = items.stream().map(NewsItem::publishedAt).max(Instant::compareTo).orElseThrow();
        Duration shift = Duration.between(newest, Instant.now().minus(Duration.ofHours(1)));
        return items.stream()
                .map(item -> new NewsItem(item.title(), item.url(), item.source(), item.publishedAt().plus(shift)))
                .toList();
    }

    @Test
    void checkingNewsSavesRecentArticlesForAnalysedHoldingsAndTheirNonRejectedRelationships() throws Exception {
        String email = "news-supply-chain@example.com";
        Company nvidia = cleanCompany("NVIDIA Corporation", NVIDIA_CIK, "NVDA");
        Company tsmc = cleanCompany("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM");
        Company samsung = cleanCompany("Samsung Electronics Co., Ltd.", null, null);
        reports.deleteByCompanyId(nvidia.getId());
        Report report = reports.saveAndFlush(new Report(nvidia.getId(), "10-K", REPORT_ACCESSION,
                LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm",
                Examples.nvidia10K()));
        relationships.save(new Relationship(report.getId(), nvidia.getId(), tsmc.getId(),
                RelationshipType.SUPPLIER, "semiconductor wafers", EVIDENCE));
        Relationship rejected = new Relationship(report.getId(), nvidia.getId(), samsung.getId(),
                RelationshipType.SUPPLIER, "semiconductor wafers", EVIDENCE);
        rejected.setReviewStatus(ReviewStatus.REJECTED);
        relationships.save(rejected);
        Portfolio portfolio = analysedPortfolio(email, nvidia);

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        verify(news).search("NVIDIA");
        verify(news).search("Taiwan Semiconductor Manufacturing");
        verify(news, never()).search("Samsung Electronics");
        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(nvidia.getId())).isNotEmpty();
        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(tsmc.getId())).isNotEmpty();
        // The steps run after each round that fetched something: the holding's and its supplier's, at least.
        verify(steps, atLeast(2)).run(portfolio.getId());
        mockMvc.perform(get("/api/portfolios").header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newsStatus").value("IDLE"))
                .andExpect(jsonPath("$[0].newsStatusReason").isEmpty())
                .andExpect(jsonPath("$[0].newsCheckedAt").isNotEmpty());
    }

    @Test
    void oneFailedCompanyFinishesTheCheckAndNamesTheCompanyThatCanBeRetried() throws Exception {
        String email = "news-partly-failed@example.com";
        Company nvidia = cleanCompany("NVIDIA Corporation", NVIDIA_CIK, "NVDA");
        Company tsmc = cleanCompany("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM");
        Portfolio portfolio = analysedPortfolio(email, nvidia);
        Holding second = new Holding(portfolio.getId(), "TSM", new BigDecimal("50"));
        second.setCompanyId(tsmc.getId());
        second.setStatus(HoldingStatus.DONE);
        holdings.save(second);
        when(news.search("Taiwan Semiconductor Manufacturing"))
                .thenThrow(new NewsClient.NewsAccessException(NewsClient.UNREACHABLE));

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        Portfolio finished = portfolios.findById(portfolio.getId()).orElseThrow();
        assertThat(finished.getNewsCheckedAt()).isNotNull();
        assertThat(finished.getNewsStatusReason()).isEqualTo(
                "News for 1 of 2 companies could not be fetched: Taiwan Semiconductor Manufacturing. "
                        + "Check News again to retry it.");
        assertThat(companies.findById(tsmc.getId()).orElseThrow().getNewsCheckedAt()).isNull();
        verify(steps).run(portfolio.getId());
    }

    @Test
    void checkingAgainWithinAnHourDoesNotAskGoogleNewsAgain() throws Exception {
        String email = "news-one-hour@example.com";
        Company tsmc = cleanCompany("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM");
        Portfolio portfolio = analysedPortfolio(email, tsmc);

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);
        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        verify(news, times(1)).search("Taiwan Semiconductor Manufacturing");
    }

    @Test
    void theSameArticleIsNeverSavedTwiceForTheSameCompany() throws Exception {
        String email = "news-no-duplicates@example.com";
        Company tsmc = cleanCompany("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM");
        Portfolio portfolio = analysedPortfolio(email, tsmc);
        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);
        int firstCount = articles.findByCompanyIdOrderByPublishedAtDesc(tsmc.getId()).size();
        tsmc.setNewsCheckedAt(null);
        companies.saveAndFlush(tsmc);

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(tsmc.getId())).hasSize(firstCount);
    }

    @Test
    void onlyTheTenNewestArticlesFromTheLastSevenDaysAreSaved() throws Exception {
        String email = "news-recent-limit@example.com";
        Company tsmc = cleanCompany("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM");
        Portfolio portfolio = analysedPortfolio(email, tsmc);

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        List<NewsArticle> saved = articles.findByCompanyIdOrderByPublishedAtDesc(tsmc.getId());
        assertThat(saved).hasSize(10);
        assertThat(saved).allSatisfy(article ->
                assertThat(article.getPublishedAt()).isAfterOrEqualTo(Instant.now().minus(Duration.ofDays(7))));
        assertThat(saved).isSortedAccordingTo(
                (left, right) -> right.getPublishedAt().compareTo(left.getPublishedAt()));
    }

    @Test
    void checkingWhileNewsIsAlreadyBeingCheckedIsRefused() throws Exception {
        String email = "news-running@example.com";
        Company tsmc = cleanCompany("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM");
        Portfolio portfolio = analysedPortfolio(email, tsmc);
        portfolio.setNewsStatus(NewsStatus.CHECKING);
        portfolios.saveAndFlush(portfolio);

        mockMvc.perform(post("/api/portfolios/{id}/news-check", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("News is already being checked for this portfolio."));
        verify(news, never()).search(anyString());
    }

    @Test
    void aPortfolioWithoutAnalysedHoldingsIsRefused() throws Exception {
        String email = "news-not-analysed@example.com";
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "News Not Analysed"));
        holdings.save(new Holding(portfolio.getId(), "TSM", new BigDecimal("100")));

        mockMvc.perform(post("/api/portfolios/{id}/news-check", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Analyse the portfolio before checking the news."));
        verify(news, never()).search(anyString());
    }

    @Test
    void aGoogleNewsFailureIsShownAndTheCheckCanBeRetried() throws Exception {
        String email = "news-retry@example.com";
        Company tsmc = cleanCompany("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM");
        Portfolio portfolio = analysedPortfolio(email, tsmc);
        when(news.search(anyString())).thenThrow(new NewsClient.NewsAccessException(NewsClient.UNREACHABLE));

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.FAILED);
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatusReason())
                .isEqualTo(NewsClient.UNREACHABLE);

        reset(news);
        when(news.search(anyString())).thenReturn(sample);
        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);
        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(tsmc.getId())).isNotEmpty();
    }

    @Test
    void anotherUsersPortfolioCannotBeChecked() throws Exception {
        Company tsmc = cleanCompany("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM");
        Portfolio portfolio = analysedPortfolio("news-owner@example.com", tsmc);

        mockMvc.perform(post("/api/portfolios/{id}/news-check", portfolio.getId())
                        .header("Authorization", logins.bearer("news-stranger@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
        verify(news, never()).search(anyString());
    }

    // ---- TS-223: three rounds, most important first ----------------------------------------------------------------

    @Test
    void theCompaniesComeInThreeRoundsHoldingsThenSuppliersThenTheirSuppliers() {
        Chain chain = chain();
        Portfolio portfolio = analysedPortfolio("news-rounds-list@example.com", chain.holding());

        NewsCheckService.NewsRounds rounds = newsCheck.roundsFor(portfolio.getId());

        assertThat(rounds.holdings()).extracting(NewsCheckService.NewsCompany::id).containsExactly(chain.holding().getId());
        assertThat(rounds.suppliers()).extracting(NewsCheckService.NewsCompany::id).containsExactly(chain.supplier().getId());
        assertThat(rounds.suppliersSuppliers()).extracting(NewsCheckService.NewsCompany::id)
                .containsExactly(chain.far().getId());
        // Together they are the companies there always were.
        assertThat(newsCheck.companiesFor(portfolio.getId())).extracting(NewsCheckService.NewsCompany::id)
                .containsExactlyInAnyOrder(chain.holding().getId(), chain.supplier().getId(), chain.far().getId());
    }

    @Test
    void eachRoundIsJudgedAndTurnedIntoAlertsBeforeTheNextRoundIsFetched() throws Exception {
        String email = "news-rounds-order@example.com";
        Chain chain = chain();
        Portfolio portfolio = analysedPortfolio(email, chain.holding());

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        InOrder order = inOrder(news, steps);
        order.verify(news).search(searchName(chain.holding()));
        order.verify(steps).run(portfolio.getId());
        order.verify(news).search(searchName(chain.supplier()));
        order.verify(steps).run(portfolio.getId());
        order.verify(news).search(searchName(chain.far()));
        order.verify(steps).run(portfolio.getId());
        verify(steps, times(3)).run(portfolio.getId());
    }

    @Test
    void theHoldingsNewsIsJudgedWhileTheSuppliersNewsIsStillBeingFetched() throws Exception {
        String email = "news-rounds-blocked@example.com";
        Chain chain = chain();
        Portfolio portfolio = analysedPortfolio(email, chain.holding());
        CountDownLatch inSupplierSearch = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(news.search(searchName(chain.supplier()))).thenAnswer(invocation -> {
            inSupplierSearch.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("The test never released the search");
            return sample;
        });
        try {
            checkNews(portfolio, email);

            assertThat(inSupplierSearch.await(10, TimeUnit.SECONDS)).isTrue();
            // The holding's round is done: its news is saved and the steps (judging, alerts) have run, while the
            // supplier's news has not been fetched yet and the check is still going.
            verify(steps, times(1)).run(portfolio.getId());
            assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(chain.holding().getId())).isNotEmpty();
            assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(chain.supplier().getId())).isEmpty();
            assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatus())
                    .isEqualTo(NewsStatus.CHECKING);
        } finally {
            release.countDown();
        }
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);
        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(chain.supplier().getId())).isNotEmpty();
    }

    @Test
    void aRoundWhoseFetchesAllFailDoesNotStopTheNextRound() throws Exception {
        String email = "news-rounds-failed-round@example.com";
        Chain chain = chain();
        Portfolio portfolio = analysedPortfolio(email, chain.holding());
        when(news.search(searchName(chain.holding()))).thenThrow(new NewsClient.NewsAccessException("Down.", null));

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        verify(news).search(searchName(chain.supplier()));
        verify(news).search(searchName(chain.far()));
        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(chain.supplier().getId())).isNotEmpty();
        // Nothing was fetched in the first round, so nothing was judged for it; the later two were.
        verify(steps, times(2)).run(portfolio.getId());
    }

    // ---- TS-223: how long each round's news is kept ---------------------------------------------------------------

    @Test
    void aSuppliersSupplierIsNotFetchedAgainWithinSixHoursButAHoldingAndItsSupplierAreAfterAnHour() throws Exception {
        String email = "news-rounds-windows@example.com";
        Chain chain = chain();
        Portfolio portfolio = analysedPortfolio(email, chain.holding());
        checkedAgo(chain.holding(), Duration.ofHours(2));
        checkedAgo(chain.supplier(), Duration.ofHours(2));
        checkedAgo(chain.far(), Duration.ofHours(2));

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        verify(news).search(searchName(chain.holding()));
        verify(news).search(searchName(chain.supplier()));
        verify(news, never()).search(searchName(chain.far()));
    }

    @Test
    void aSuppliersSupplierIsFetchedAgainOnceSixHoursHavePassed() throws Exception {
        String email = "news-rounds-windows-six@example.com";
        Chain chain = chain();
        Portfolio portfolio = analysedPortfolio(email, chain.holding());
        checkedAgo(chain.holding(), Duration.ofMinutes(10));
        checkedAgo(chain.supplier(), Duration.ofMinutes(10));
        checkedAgo(chain.far(), Duration.ofHours(7));

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        verify(news, never()).search(searchName(chain.holding()));
        verify(news, never()).search(searchName(chain.supplier()));
        verify(news).search(searchName(chain.far()));
    }

    // ---- TS-223: the AI failing between rounds ---------------------------------------------------------------------

    @Test
    void anAiFailureAfterTheHoldingsRoundFailsTheCheckKeepsThatRoundAndFetchesNoMore() throws Exception {
        String email = "news-rounds-ai-fails-first@example.com";
        Chain chain = chain();
        Portfolio portfolio = analysedPortfolio(email, chain.holding());
        doThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null))
                .when(steps).run(portfolio.getId());

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.FAILED);

        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatusReason())
                .isEqualTo(NewsAssessor.AI_FAILED);
        // The holdings' news was fetched and saved; the AI is not answering, so the check stops there.
        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(chain.holding().getId())).isNotEmpty();
        verify(news, never()).search(searchName(chain.supplier()));
        verify(news, never()).search(searchName(chain.far()));
        verify(steps, times(1)).run(portfolio.getId());
    }

    @Test
    void anAiFailureAfterTheSuppliersRoundKeepsBothRoundsAndSkipsTheLast() throws Exception {
        String email = "news-rounds-ai-fails-second@example.com";
        Chain chain = chain();
        Portfolio portfolio = analysedPortfolio(email, chain.holding());
        doNothing().doThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null))
                .when(steps).run(portfolio.getId());

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.FAILED);

        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(chain.holding().getId())).isNotEmpty();
        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(chain.supplier().getId())).isNotEmpty();
        verify(news, never()).search(searchName(chain.far()));
    }

    @Test
    void theNextCheckAfterAnAiFailureFetchesTheRoundsItDidNotReachAndNotTheOnesItDid() throws Exception {
        String email = "news-rounds-ai-fails-then-retry@example.com";
        Chain chain = chain();
        Portfolio portfolio = analysedPortfolio(email, chain.holding());
        doThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null))
                .doNothing()
                .when(steps).run(portfolio.getId());
        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.FAILED);

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        // The holding's news was fetched once, in the first check; the second check fetched the rest.
        verify(news, times(1)).search(searchName(chain.holding()));
        verify(news, times(1)).search(searchName(chain.supplier()));
        verify(news, times(1)).search(searchName(chain.far()));
    }

    /** A holding company, its direct supplier and that supplier's supplier, each made new for the test. */
    private record Chain(Company holding, Company supplier, Company far) {
    }

    private Chain chain() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Company holding = cleanCompany("Rounds Alpha " + unique, null, null);
        Company supplier = cleanCompany("Rounds Beta " + unique, null, null);
        Company far = cleanCompany("Rounds Gamma " + unique, null, null);
        supplies(holding, supplier);
        supplies(supplier, far);
        return new Chain(holding, supplier, far);
    }

    /** The report of {@code customer} says {@code supplier} supplies it. */
    private void supplies(Company customer, Company supplier) {
        Report report = reports.save(new Report(customer.getId(), "10-K",
                "R-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/", "Set by the test: the report's text is not used."));
        relationships.save(new Relationship(report.getId(), customer.getId(), supplier.getId(),
                RelationshipType.SUPPLIER, "semiconductor wafers", EVIDENCE));
    }

    private void checkedAgo(Company company, Duration ago) {
        company.setNewsCheckedAt(Instant.now().minus(ago));
        companies.saveAndFlush(company);
    }

    private static String searchName(Company company) {
        return NewsSearchName.of(company.getName());
    }

    @Test
    void aNewsCheckSavesARecentEightKReportingOneOfTheNineItemsWithItsOwnWordsAsTheHeadline() throws Exception {
        String email = "news-8k-saved@example.com";
        Company clorox = cleanCompany(CLOROX, CLOROX_CIK, "CLX");
        Portfolio portfolio = analysedPortfolio(email, clorox);
        List<SecClient.SecCurrentReport> filings = cloroxFilingsWithTheNewest(CLOROX_CYBER_ATTACK);
        when(sec.recentCurrentReports(CLOROX_CIK)).thenReturn(filings);
        when(sec.download(CLOROX_CYBER_ATTACK_URL)).thenReturn(Examples.sec8kClorox());

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        List<NewsArticle> eightKs = eightKsOf(clorox);
        assertThat(eightKs).singleElement().satisfies(article -> {
            assertThat(article.getUrl()).isEqualTo(CLOROX_CYBER_ATTACK_URL);
            assertThat(article.getSource()).isEqualTo("SEC 8-K");
            assertThat(article.getPublishedAt()).isEqualTo(acceptedAt(filings, CLOROX_CYBER_ATTACK));
            assertThat(article.getTitle()).startsWith("The Clorox Company, Item 8.01: The Clorox Company (the "
                    + "“Company” or “Clorox”) has identified unauthorized activity on some of its Information "
                    + "Technology (IT) systems.");
            String opening = article.getTitle().substring("The Clorox Company, Item 8.01: ".length());
            assertThat(opening).hasSizeLessThanOrEqualTo(300);
        });
        // Its Google News is saved beside it.
        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(clorox.getId())).hasSizeGreaterThan(1);
        verify(sec, times(1)).download(anyString());
    }

    @Test
    void anEightKThatCannotBeDownloadedIsSavedWithTheItemsNameAsItsHeadline() throws Exception {
        String email = "news-8k-download-failed@example.com";
        Company clorox = cleanCompany(CLOROX, CLOROX_CIK, "CLX");
        Portfolio portfolio = analysedPortfolio(email, clorox);
        when(sec.recentCurrentReports(CLOROX_CIK)).thenReturn(cloroxFilingsWithTheNewest(CLOROX_CYBER_ATTACK));
        when(sec.download(CLOROX_CYBER_ATTACK_URL)).thenThrow(new SecClient.SecAccessException(
                "The SEC returned HTTP 503 for a request.", null));

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        assertThat(eightKsOf(clorox)).singleElement()
                .satisfies(article -> assertThat(article.getTitle())
                        .isEqualTo("The Clorox Company Files an 8-K: Other Events"));
    }

    @Test
    void anEightKReportingNoneOfTheNineItemsIsNeverDownloadedSavedOrJudged() throws Exception {
        String email = "news-8k-not-read@example.com";
        Company clorox = cleanCompany(CLOROX, CLOROX_CIK, "CLX");
        Portfolio portfolio = analysedPortfolio(email, clorox);
        // Its newest 8-K reports Items 5.02, 7.01 and 9.01 only; the one before it is months older.
        when(sec.recentCurrentReports(CLOROX_CIK)).thenReturn(cloroxFilingsWithTheNewest(CLOROX_NONE_OF_THE_NINE));

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        verify(sec, never()).download(anyString());
        assertThat(eightKsOf(clorox)).isEmpty();
    }

    @Test
    void aSavedEightKIsNotDownloadedAgainAndACompanyCheckedWithinAnHourIsNotAskedAgain() throws Exception {
        String email = "news-8k-once@example.com";
        Company clorox = cleanCompany(CLOROX, CLOROX_CIK, "CLX");
        Portfolio portfolio = analysedPortfolio(email, clorox);
        when(sec.recentCurrentReports(CLOROX_CIK)).thenReturn(cloroxFilingsWithTheNewest(CLOROX_CYBER_ATTACK));
        when(sec.download(CLOROX_CYBER_ATTACK_URL)).thenReturn(Examples.sec8kClorox());

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);
        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);
        verify(sec, times(1)).recentCurrentReports(CLOROX_CIK);

        clorox = companies.findById(clorox.getId()).orElseThrow();
        clorox.setNewsCheckedAt(null);
        companies.saveAndFlush(clorox);
        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        verify(sec, times(2)).recentCurrentReports(CLOROX_CIK);
        verify(sec, times(1)).download(CLOROX_CYBER_ATTACK_URL);
        assertThat(eightKsOf(clorox)).hasSize(1);
    }

    @Test
    void anEightKOlderThanTenNewsHeadlinesIsStillSavedAndDownloadedOnlyOnce() throws Exception {
        String email = "news-8k-not-crowded@example.com";
        Company clorox = cleanCompany(CLOROX, CLOROX_CIK, "CLX");
        Portfolio portfolio = analysedPortfolio(email, clorox);
        // The 8-K three days old, the saved Google News within the last hour or so: a busy company's week.
        List<SecClient.SecCurrentReport> filings = cloroxFilingsWithTheNewest(CLOROX_CYBER_ATTACK).stream()
                .map(filing -> new SecClient.SecCurrentReport(filing.accessionNumber(), filing.items(),
                        filing.acceptedAt().minus(Duration.ofDays(3)), filing.url()))
                .toList();
        Instant eightKAccepted = acceptedAt(filings, CLOROX_CYBER_ATTACK);
        assertThat(sample).filteredOn(item -> item.publishedAt().isAfter(eightKAccepted)).hasSizeGreaterThanOrEqualTo(10);
        when(sec.recentCurrentReports(CLOROX_CIK)).thenReturn(filings);
        when(sec.download(CLOROX_CYBER_ATTACK_URL)).thenReturn(Examples.sec8kClorox());

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);
        clorox = companies.findById(clorox.getId()).orElseThrow();
        clorox.setNewsCheckedAt(null);
        companies.saveAndFlush(clorox);
        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        assertThat(eightKsOf(clorox)).singleElement()
                .satisfies(article -> assertThat(article.getUrl()).isEqualTo(CLOROX_CYBER_ATTACK_URL));
        verify(sec, times(1)).download(CLOROX_CYBER_ATTACK_URL);
    }

    @Test
    void aSavedEightKIsJudgedByFilterOutNoiseAndBecomesAnAlertThatOpensTheFiling() throws Exception {
        String email = "news-8k-alert@example.com";
        Company clorox = cleanCompany(CLOROX, CLOROX_CIK, "CLX");
        Portfolio portfolio = analysedPortfolio(email, clorox);
        when(news.search(anyString())).thenReturn(List.of());
        when(sec.recentCurrentReports(CLOROX_CIK)).thenReturn(cloroxFilingsWithTheNewest(CLOROX_CYBER_ATTACK));
        when(sec.download(CLOROX_CYBER_ATTACK_URL)).thenReturn(Examples.sec8kClorox());
        // Filter Out Noise gets Gemini's real answer for this headline (number 1 in it: an event). A recommendation
        // is not part of this story: it fails, which never fails the news check.
        when(gemini.generate(any(AiRequest.class), any())).thenAnswer(call -> {
            if (call.<AiRequest>getArgument(0).purpose() == AiPurpose.JUDGE_NEWS) {
                return FakeAi.answer(Examples.gemini8kAssessment());
            }
            throw new GeminiClient.GeminiException("Not part of this test.", null);
        });
        doAnswer(call -> {
            new NewsCheckStepRunner(realSteps).run(call.getArgument(0));
            return null;
        }).when(steps).run(anyLong());

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        NewsArticle eightK = eightKsOf(clorox).getFirst();
        assertThat(assessments.findByArticleIdIn(List.of(eightK.getId()))).singleElement().satisfies(assessment -> {
            assertThat(assessment.isEvent()).isTrue();
            assertThat(assessment.getEventType()).isEqualTo(EventType.CYBER_ATTACK);
        });
        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].headline.article.title").value(eightK.getTitle()))
                .andExpect(jsonPath("$.alerts[0].headline.article.source").value("SEC 8-K"))
                .andExpect(jsonPath("$.alerts[0].headline.article.url").value(CLOROX_CYBER_ATTACK_URL));
    }

    @Test
    void aCompanyWithoutAnSecNumberIsCheckedOnGoogleNewsOnly() throws Exception {
        String email = "news-8k-no-cik@example.com";
        Company samsung = cleanCompany("Samsung Electronics Co., Ltd.", null, null);
        Portfolio portfolio = analysedPortfolio(email, samsung);

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        verify(news).search("Samsung Electronics");
        verify(sec, never()).recentCurrentReports(anyString());
        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(samsung.getId())).isNotEmpty();
    }

    @Test
    void whenTheSecCannotBeReachedTheCompanysGoogleNewsIsStillSavedAndTheCheckFinishes() throws Exception {
        String email = "news-8k-sec-down@example.com";
        Company clorox = cleanCompany(CLOROX, CLOROX_CIK, "CLX");
        Portfolio portfolio = analysedPortfolio(email, clorox);
        when(sec.recentCurrentReports(CLOROX_CIK)).thenThrow(new SecClient.SecAccessException(
                "The SEC could not be reached. Check the network connection and try again later.", null));

        checkNews(portfolio, email);
        awaitFinished(portfolio.getId(), NewsStatus.IDLE);

        assertThat(articles.findByCompanyIdOrderByPublishedAtDesc(clorox.getId())).isNotEmpty();
        assertThat(eightKsOf(clorox)).isEmpty();
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatusReason()).isNull();
        verify(steps).run(portfolio.getId());
    }

    /**
     * Clorox's 8-Ks from the saved submissions list, every acceptance time moved forward by the same amount so the
     * one named was accepted 30 minutes ago (newer than any saved Google News headline). The items, addresses and the
     * gaps between filings stay as the SEC sent them; filings after it are now in the future, so none is read.
     */
    private static List<SecClient.SecCurrentReport> cloroxFilingsWithTheNewest(String accession) {
        return cloroxFilingsWithTheNewest(accession, Duration.ofMinutes(30));
    }

    /** The same, with the one named accepted {@code age} ago. */
    private static List<SecClient.SecCurrentReport> cloroxFilingsWithTheNewest(String accession, Duration age) {
        List<SecClient.SecCurrentReport> saved = SecClient.currentReportsIn(CLOROX_CIK,
                JsonMapper.builder().build().readTree(Examples.secSubmissionsClorox()));
        Instant anchor = saved.stream().filter(filing -> filing.accessionNumber().equals(accession))
                .findFirst().orElseThrow().acceptedAt();
        // In whole seconds, as the SEC gives them (and as the database keeps them).
        Duration shift = Duration.between(anchor,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(age));
        return saved.stream()
                .map(filing -> new SecClient.SecCurrentReport(filing.accessionNumber(), filing.items(),
                        filing.acceptedAt().plus(shift), filing.url()))
                .toList();
    }

    private static Instant acceptedAt(List<SecClient.SecCurrentReport> filings, String accession) {
        return filings.stream().filter(filing -> filing.accessionNumber().equals(accession))
                .findFirst().orElseThrow().acceptedAt();
    }

    private List<NewsArticle> eightKsOf(Company company) {
        return articles.findByCompanyIdOrderByPublishedAtDesc(company.getId()).stream()
                .filter(article -> "SEC 8-K".equals(article.getSource()))
                .toList();
    }

    private Company cleanCompany(String name, String cik, String ticker) {
        Company company = companyDirectory.findOrCreate(name, cik, ticker, List.of());
        articles.deleteByCompanyId(company.getId());
        company.setNewsCheckedAt(null);
        return companies.saveAndFlush(company);
    }

    private Portfolio analysedPortfolio(String email, Company company) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "News Test"));
        Holding holding = new Holding(portfolio.getId(), company.getTicker() == null ? "SUPPLIER" : company.getTicker(),
                new BigDecimal("100"));
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);
        return portfolio;
    }

    private void checkNews(Portfolio portfolio, String email) throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/news-check", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
    }

    private void awaitFinished(Long portfolioId, NewsStatus expected) throws Exception {
        for (int attempt = 0; attempt < 400; attempt++) {
            if (portfolios.findById(portfolioId).orElseThrow().getNewsStatus() == expected) return;
            Thread.sleep(25);
        }
        assertThat(portfolios.findById(portfolioId).orElseThrow().getNewsStatus()).isEqualTo(expected);
    }
}
