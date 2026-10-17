package com.truesight.news;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.news.NewsAssessmentReader.Judgement;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * See the Collected News: GET /api/portfolios/{id}/news. The headlines are the real Google News sample
 * ({@link Examples#googleNewsTsmc()}) and the judgement is Gemini's real answer about them
 * ({@link Examples#geminiNewsAssessment()}). Only the publication times are moved to just before the test runs, so
 * the "last 7 days" rule does not make the test fail as the sample ages.
 */
@IntegrationTest
class NewsListIT {

    private static final String NVIDIA_CIK = "0001045810";
    private static final String TSMC_CIK = "0001046179";
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

    private List<NewsItem> sample;
    private Company nvidia;
    private Company tsmc;
    private Company outsider;

    @BeforeEach
    void nvidiaHeldWithTsmcAsItsSupplier() {
        sample = new GoogleNewsParser().parse(Examples.googleNewsTsmc());
        nvidia = cleanCompany("NVIDIA Corporation", NVIDIA_CIK, "NVDA");
        tsmc = cleanCompany("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM");
        // A company with news saved, but outside this portfolio's supply chain.
        outsider = cleanCompany("Panasonic", null, null);
        reports.deleteByCompanyId(nvidia.getId());
        Report report = reports.saveAndFlush(new Report(nvidia.getId(), "10-K", "0001045810-26-000021",
                LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm",
                Examples.nvidia10K()));
        relationships.save(new Relationship(report.getId(), nvidia.getId(), tsmc.getId(),
                RelationshipType.SUPPLIER, "semiconductor wafers", EVIDENCE));
    }

    @Test
    void listsTheRecentArticlesAboutTheHoldingsAndTheirSuppliersNewestFirst() throws Exception {
        String email = "news-list@example.com";
        Portfolio portfolio = portfolioHolding(email, nvidia);
        save(tsmc, sample.get(0), Duration.ofHours(1));
        save(nvidia, sample.get(1), Duration.ofHours(3));
        save(tsmc, sample.get(2), Duration.ofDays(2));

        mockMvc.perform(get("/api/portfolios/{id}/news", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value(sample.get(0).title()))
                .andExpect(jsonPath("$[0].source").value(sample.get(0).source()))
                .andExpect(jsonPath("$[0].url").value(sample.get(0).url()))
                .andExpect(jsonPath("$[0].company.name").value("Taiwan Semiconductor Manufacturing Company Limited"))
                .andExpect(jsonPath("$[0].company.ticker").value("TSM"))
                .andExpect(jsonPath("$[0].isHolding").value(false))
                .andExpect(jsonPath("$[0].assessment").isEmpty())
                .andExpect(jsonPath("$[1].title").value(sample.get(1).title()))
                .andExpect(jsonPath("$[1].company.name").value("NVIDIA Corporation"))
                .andExpect(jsonPath("$[1].isHolding").value(true))
                .andExpect(jsonPath("$[2].title").value(sample.get(2).title()));
    }

    @Test
    void leavesOutArticlesOlderThanSevenDaysAndCompaniesOutsideTheSupplyChain() throws Exception {
        String email = "news-list-filtered@example.com";
        Portfolio portfolio = portfolioHolding(email, nvidia);
        save(tsmc, sample.get(0), Duration.ofHours(1));
        save(tsmc, sample.get(1), Duration.ofDays(8));
        save(outsider, sample.get(2), Duration.ofHours(1));

        mockMvc.perform(get("/api/portfolios/{id}/news", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value(sample.get(0).title()));
    }

    @Test
    void anArticleTheAiHasJudgedCarriesItsAssessment() throws Exception {
        String email = "news-list-judged@example.com";
        Portfolio portfolio = portfolioHolding(email, nvidia);
        NewsArticle article = save(tsmc, sample.get(0), Duration.ofHours(1));
        // Gemini's real judgement of the first headline in the sample.
        Judgement judgement = new NewsAssessmentReader().read(Examples.geminiNewsAssessment(), 20).get(1);
        assessments.save(new NewsAssessment(article.getId(), judgement.event(), judgement.eventType(),
                judgement.severity(), judgement.confidence(), judgement.summary(), judgement.reason()));

        mockMvc.perform(get("/api/portfolios/{id}/news", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assessment.isEvent").value(false))
                .andExpect(jsonPath("$[0].assessment.confidence").value(judgement.confidence().name()))
                .andExpect(jsonPath("$[0].assessment.reason").value(judgement.reason()))
                .andExpect(jsonPath("$[0].assessment.eventType").isEmpty())
                .andExpect(jsonPath("$[0].assessment.severity").isEmpty());
    }

    @Test
    void anotherUsersPortfolioNewsCannotBeRead() throws Exception {
        Portfolio portfolio = portfolioHolding("news-list-owner@example.com", nvidia);
        save(tsmc, sample.get(0), Duration.ofHours(1));

        mockMvc.perform(get("/api/portfolios/{id}/news", portfolio.getId())
                        .header("Authorization", logins.bearer("news-list-stranger@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
    }

    private NewsArticle save(Company company, NewsItem item, Duration ago) {
        return articles.save(new NewsArticle(company.getId(), item.url(), item.title(), item.source(),
                Instant.now().minus(ago)));
    }

    private Company cleanCompany(String name, String cik, String ticker) {
        Company company = companyDirectory.findOrCreate(name, cik, ticker, List.of());
        articles.deleteByCompanyId(company.getId());
        return companies.saveAndFlush(company);
    }

    private Portfolio portfolioHolding(String email, Company company) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "News List Test"));
        Holding holding = new Holding(portfolio.getId(), company.getTicker(), new BigDecimal("100"));
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);
        return portfolio;
    }
}
