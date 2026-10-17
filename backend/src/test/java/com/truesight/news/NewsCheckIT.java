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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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

    @MockitoBean NewsClient news;
    @MockitoBean NewsCheckStepRunner steps;

    private List<NewsItem> sample;

    @BeforeEach
    void returnTheSavedGoogleNewsSample() {
        reset(news, steps);
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
