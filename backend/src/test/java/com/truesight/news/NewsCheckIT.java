package com.truesight.news;

import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
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
import com.truesight.report.SecClient;
import com.truesight.support.Examples;
import com.truesight.support.FakeAi;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        verify(steps).run(portfolio.getId());
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
        List<SecClient.SecCurrentReport> saved = SecClient.currentReportsIn(CLOROX_CIK,
                JsonMapper.builder().build().readTree(Examples.secSubmissionsClorox()));
        Instant anchor = saved.stream().filter(filing -> filing.accessionNumber().equals(accession))
                .findFirst().orElseThrow().acceptedAt();
        // In whole seconds, as the SEC gives them (and as the database keeps them).
        Duration shift = Duration.between(anchor,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(Duration.ofMinutes(30)));
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
