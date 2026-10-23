package com.truesight.news;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
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

/** The Alerts page API, against the real Sprint 2 tables. */
@IntegrationTest
class AlertsIT {

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
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired NewsArticleRepository articles;
    @Autowired NewsAssessmentRepository assessments;
    @Autowired AlertRepository alerts;
    @Autowired AlertHoldingRepository alertHoldings;

    private List<NewsItem> sample;
    private Company nvidia;
    private Company tsmc;
    private Relationship tsmcSuppliesNvidia;

    @BeforeEach
    void makeNvidiaAndTsmcSupplyChain() {
        sample = new GoogleNewsParser().parse(Examples.googleNewsTsmc());
        nvidia = companyDirectory.findOrCreate("NVIDIA Corporation", NVIDIA_CIK, "NVDA", List.of("NVIDIA"));
        tsmc = companyDirectory.findOrCreate(
                "Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM", List.of("TSMC"));
        articles.deleteByCompanyId(tsmc.getId());
        articles.deleteByCompanyId(nvidia.getId());
        reports.deleteByCompanyId(nvidia.getId());
        Report report = reports.saveAndFlush(new Report(nvidia.getId(), "10-K", "0001045810-26-000021",
                LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm",
                Examples.nvidia10K()));
        tsmcSuppliesNvidia = relationships.save(new Relationship(
                report.getId(), nvidia.getId(), tsmc.getId(), RelationshipType.SUPPLIER,
                "semiconductor wafers", EVIDENCE));
    }

    @Test
    void anAlertShowsTheArticleAssessmentExposureAndEveryHoldingItReaches() throws Exception {
        String email = "alerts-fields@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, 0, Duration.ofHours(1));
        NewsAssessment assessment = event(article, Severity.HIGH, Confidence.MEDIUM);
        Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weighted").value(true))
                .andExpect(jsonPath("$.holdingCount").value(1))
                .andExpect(jsonPath("$.articlesChecked").doesNotExist())
                .andExpect(jsonPath("$.noiseCount").doesNotExist())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].id").value(alert.getId()))
                .andExpect(jsonPath("$.alerts[0].status").value("NEW"))
                .andExpect(jsonPath("$.alerts[0].exposure").value(40.0))
                .andExpect(jsonPath("$.alerts[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.alerts[0].article.title").value(article.getTitle()))
                .andExpect(jsonPath("$.alerts[0].article.source").value(article.getSource()))
                .andExpect(jsonPath("$.alerts[0].article.publishedAt")
                        .value(article.getPublishedAt().truncatedTo(ChronoUnit.MICROS).toString()))
                .andExpect(jsonPath("$.alerts[0].article.url").value(article.getUrl()))
                .andExpect(jsonPath("$.alerts[0].assessment.eventType").value("PRODUCTION_STOP"))
                .andExpect(jsonPath("$.alerts[0].assessment.severity").value("HIGH"))
                .andExpect(jsonPath("$.alerts[0].assessment.confidence").value("MEDIUM"))
                .andExpect(jsonPath("$.alerts[0].assessment.summary").value(assessment.getSummary()))
                .andExpect(jsonPath("$.alerts[0].assessment.reason").value(assessment.getReason()))
                .andExpect(jsonPath("$.alerts[0].holdings", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].holdings[0].holdingId").value(holding.getId()))
                .andExpect(jsonPath("$.alerts[0].holdings[0].ticker").value("NVDA"))
                .andExpect(jsonPath("$.alerts[0].holdings[0].weight").value(40.0))
                .andExpect(jsonPath("$.alerts[0].holdings[0].supplierName").value(tsmc.getName()))
                .andExpect(jsonPath("$.alerts[0].holdings[0].customerName").value(nvidia.getName()))
                .andExpect(jsonPath("$.alerts[0].holdings[0].link").doesNotExist())
                .andExpect(jsonPath("$.alerts[0].holdings[0].relationshipId").value(tsmcSuppliesNvidia.getId()))
                .andExpect(jsonPath("$.alerts[0].holdings[0].reviewStatus").value("UNREVIEWED"));
    }

    @Test
    void anUnweightedPortfolioCountsItsHoldingsAndAnEventAboutAHoldingNamesNoCompanies() throws Exception {
        String email = "alerts-unweighted@example.com";
        Portfolio portfolio = portfolio(email);
        Holding heldNvidia = hold(portfolio, nvidia, null);
        Holding heldTsmc = hold(portfolio, tsmc, null);
        NewsArticle article = article(tsmc, 0, Duration.ofHours(1));
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "2", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(alert.getId(), heldNvidia.getId(), tsmcSuppliesNvidia.getId()));
        alertHoldings.save(new AlertHolding(alert.getId(), heldTsmc.getId(), null));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weighted").value(false))
                .andExpect(jsonPath("$.holdingCount").value(2))
                .andExpect(jsonPath("$.alerts[0].exposure").value(2.0))
                .andExpect(jsonPath("$.alerts[0].holdings", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].holdings[0].ticker").value("NVDA"))
                .andExpect(jsonPath("$.alerts[0].holdings[0].weight").doesNotExist())
                .andExpect(jsonPath("$.alerts[0].holdings[1].ticker").value("TSM"))
                .andExpect(jsonPath("$.alerts[0].holdings[1].supplierName").doesNotExist())
                .andExpect(jsonPath("$.alerts[0].holdings[1].customerName").doesNotExist())
                .andExpect(jsonPath("$.alerts[0].holdings[0].relationshipId").value(tsmcSuppliesNvidia.getId()))
                .andExpect(jsonPath("$.alerts[0].holdings[1].relationshipId").value(nullValue()))
                .andExpect(jsonPath("$.alerts[0].holdings[1].reviewStatus").doesNotExist());
    }

    @Test
    void highSeverityIsListedBeforeLowWhateverTheirDatesAndExposure() throws Exception {
        String email = "alerts-order@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "100");
        NewsArticle lowArticle = article(tsmc, 0, Duration.ofMinutes(5));
        event(lowArticle, Severity.LOW, Confidence.HIGH);
        Alert low = alert(portfolio, lowArticle, "100", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(low.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));
        NewsArticle highArticle = article(tsmc, 1, Duration.ofDays(2));
        event(highArticle, Severity.HIGH, Confidence.HIGH);
        Alert high = alert(portfolio, highArticle, "5", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(high.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].id").value(high.getId()))
                .andExpect(jsonPath("$.alerts[1].id").value(low.getId()));
    }

    @Test
    void acknowledgingIsRefusedAndDismissingPersistsAndHidesTheAlert() throws Exception {
        String email = "alerts-status@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, 0, Duration.ofHours(1));
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        changeStatus(email, portfolio, alert, "ACKNOWLEDGED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An alert is either open or dismissed."));
        assertThat(alerts.findById(alert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.NEW);

        changeStatus(email, portfolio, alert, "DISMISSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$.alerts", hasSize(0)));
        assertThat(alerts.findById(alert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.DISMISSED);
    }

    @Test
    void anAlertStoredAsAcknowledgedIsReportedAsNew() throws Exception {
        String email = "alerts-acknowledged@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, 0, Duration.ofHours(1));
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.ACKNOWLEDGED);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].status").value("NEW"));
    }

    @Test
    void showDismissedReturnsDismissedAlertsAndRestoreMakesOneNewAgain() throws Exception {
        String email = "alerts-restore@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, 0, Duration.ofHours(1));
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.DISMISSED);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .param("includeDismissed", "true")
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].status").value("DISMISSED"));

        changeStatus(email, portfolio, alert, "NEW")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));
        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$.alerts[0].status").value("NEW"));
    }

    @Test
    void filteredNewsListsOnlyNoiseWithTheAisReasons() throws Exception {
        String email = "alerts-noise@example.com";
        Portfolio portfolio = portfolio(email);
        hold(portfolio, nvidia, "40");
        NewsAssessmentReader reader = new NewsAssessmentReader();
        var judgements = reader.read(Examples.geminiNewsAssessment(), 20);
        for (int index = 0; index < 2; index++) {
            NewsArticle article = article(tsmc, index, Duration.ofHours(index + 1));
            NewsAssessmentReader.Judgement judgement = judgements.get(index + 1);
            assessments.save(new NewsAssessment(article.getId(), false, null, null, judgement.confidence(), null,
                    judgement.reason()));
        }
        NewsArticle eventArticle = article(tsmc, 2, Duration.ofHours(3));
        event(eventArticle, Severity.HIGH, Confidence.HIGH);

        mockMvc.perform(get("/api/portfolios/{id}/news", portfolio.getId())
                        .param("noise", "true")
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].assessment.isEvent").value(false))
                .andExpect(jsonPath("$[0].assessment.reason").value(judgements.get(1).reason()))
                .andExpect(jsonPath("$[1].assessment.reason").value(judgements.get(2).reason()));
    }

    @Test
    void anotherUsersAlertsCannotBeReadOrChangedAndUnknownAlertsAreNotFound() throws Exception {
        String owner = "alerts-owner@example.com";
        Portfolio portfolio = portfolio(owner);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, 0, Duration.ofHours(1));
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));
        String strangerToken = logins.bearer("alerts-stranger@example.com");

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", strangerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
        mockMvc.perform(put("/api/portfolios/{id}/alerts/{alertId}", portfolio.getId(), alert.getId())
                        .header("Authorization", strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
        mockMvc.perform(put("/api/portfolios/{id}/alerts/{alertId}", portfolio.getId(), Long.MAX_VALUE)
                        .header("Authorization", logins.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Alert not found."));
    }

    private Portfolio portfolio(String email) {
        return portfolios.save(new Portfolio(logins.user(email).getId(), "Alerts Test"));
    }

    private Holding hold(Portfolio portfolio, Company company, String weight) {
        Holding holding = new Holding(portfolio.getId(), company.getTicker(),
                weight == null ? null : new BigDecimal(weight));
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        return holdings.save(holding);
    }

    private NewsArticle article(Company company, int sampleIndex, Duration ago) {
        NewsItem item = sample.get(sampleIndex);
        return articles.save(new NewsArticle(company.getId(), item.url() + "#" + UUID.randomUUID(), item.title(),
                item.source(), Instant.now().minus(ago).truncatedTo(ChronoUnit.MICROS)));
    }

    private NewsAssessment event(NewsArticle article, Severity severity, Confidence confidence) {
        return assessments.save(new NewsAssessment(
                article.getId(), true, EventType.PRODUCTION_STOP, severity, confidence,
                "Set by the test: production stopped.", "Set by the test: the headline reports a stoppage."));
    }

    private Alert alert(Portfolio portfolio, NewsArticle article, String exposure, AlertStatus status) {
        Alert alert = new Alert(portfolio.getId(), article.getId(), new BigDecimal(exposure));
        alert.setStatus(status);
        return alerts.saveAndFlush(alert);
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(
            String email, Portfolio portfolio, Alert alert, String status) throws Exception {
        return mockMvc.perform(put("/api/portfolios/{id}/alerts/{alertId}", portfolio.getId(), alert.getId())
                .header("Authorization", logins.bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }
}
