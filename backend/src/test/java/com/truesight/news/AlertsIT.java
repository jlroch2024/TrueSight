package com.truesight.news;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final String GLOBALFOUNDRIES_REUTERS =
            "GlobalFoundries to make key AI chip component for TSMC in $2 billion deal";
    private static final String GLOBALFOUNDRIES_YAHOO =
            "GlobalFoundries Signs $2 Billion Silicon Supply Deal With Taiwan Semiconductor Manufacturing";
    private static final String GLOBALFOUNDRIES_TRADINGVIEW =
            "Weekly Recap: Record Q3 revenue surge and $2B GlobalFoundries CoWoS deal";
    private static final String QUALCOMM_VS_TSMC =
            "Qualcomm vs. Taiwan Semiconductor Manufacturing: Which Technology Stock Is a Better Buy in 2026?";
    private static final String TSMC_CHEAP_SMARTPHONES_TRADINGVIEW =
            "The Death of Cheap Smartphones? Taiwan Semiconductor Could Profit From Major Market Shift";
    private static final String TSMC_RECORD_PROFIT_REUTERS =
            "TSMC expected to report sixth straight quarter of record profit thanks to AI boom";
    private static final String TSMC_BOMBSHELL_YAHOO =
            "Taiwan Semiconductor Manufacturing Just Dropped a Major Bombshell Investors Must Be Aware Of";
    private static final String TSMC_EARNINGS_WATCH_MOTLEY =
            "3 Things to Watch When Taiwan Semiconductor (TSMC) Reports Earnings on Oct. 15";
    private static final String REUTERS = "Reuters";
    private static final String YAHOO_FINANCE = "Yahoo Finance";
    private static final String TRADINGVIEW = "TradingView";
    private static final String MOTLEY_FOOL = "The Motley Fool";
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
    @Autowired RecommendationRepository recommendations;

    private Map<HeadlineKey, NewsItem> sampleByHeadline;
    private Company nvidia;
    private Company tsmc;
    private Relationship tsmcSuppliesNvidia;

    @BeforeEach
    void makeNvidiaAndTsmcSupplyChain() {
        // Every headline, publisher, URL and publication gap comes from docs/examples/google-news-tsmc.xml, the Google
        // News sample captured 14 October 2026. The product owner agreed to use this saved sample instead of the
        // 20 October GameStop headlines named in the story. Shift every timestamp by the same offset, as NewsCheckIT
        // does, so the sample stays recent without changing the gaps between articles.
        List<NewsItem> savedSample = new GoogleNewsParser().parse(Examples.googleNewsTsmc());
        Instant newestPublishedAt = savedSample.stream().map(NewsItem::publishedAt).max(Instant::compareTo).orElseThrow();
        Duration sampleShift = Duration.between(newestPublishedAt, Instant.now().minus(Duration.ofHours(1)));
        sampleByHeadline = savedSample.stream()
                .map(item -> new NewsItem(item.title(), item.url(), item.source(), item.publishedAt().plus(sampleShift)))
                .collect(Collectors.toMap(item -> new HeadlineKey(item.title(), item.source()), item -> item));
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
        NewsArticle article = article(tsmc, TSMC_BOMBSHELL_YAHOO, YAHOO_FINANCE);
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
                .andExpect(jsonPath("$.alerts[0].alertIds[0]").value(alert.getId()))
                .andExpect(jsonPath("$.alerts[0].status").value("NEW"))
                .andExpect(jsonPath("$.alerts[0].exposure").value(40.0))
                .andExpect(jsonPath("$.alerts[0].headline.article.title").value(article.getTitle()))
                .andExpect(jsonPath("$.alerts[0].headline.article.source").value(article.getSource()))
                .andExpect(jsonPath("$.alerts[0].headline.article.publishedAt")
                        .value(article.getPublishedAt().truncatedTo(ChronoUnit.MICROS).toString()))
                .andExpect(jsonPath("$.alerts[0].headline.article.url").value(article.getUrl()))
                // The stored name of the company the article is about, which is how the graph keys its companies.
                .andExpect(jsonPath("$.alerts[0].headline.article.companyName").value(tsmc.getName()))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.eventType").value("PRODUCTION_STOP"))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.severity").value("HIGH"))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.confidence").value("MEDIUM"))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.summary").value(assessment.getSummary()))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.reason").value(assessment.getReason()))
                .andExpect(jsonPath("$.alerts[0].sources[0].title").value(article.getTitle()))
                .andExpect(jsonPath("$.alerts[0].holdings", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].holdings[0].holdingId").value(holding.getId()))
                .andExpect(jsonPath("$.alerts[0].holdings[0].ticker").value("NVDA"))
                .andExpect(jsonPath("$.alerts[0].holdings[0].weight").value(40.0))
                .andExpect(jsonPath("$.alerts[0].holdings[0].indirect").value(false))
                // Display names, as every page shows them (Spell Each Company as Its Own Report Does).
                .andExpect(jsonPath("$.alerts[0].holdings[0].supplierName").value("Taiwan Semiconductor Manufacturing"))
                .andExpect(jsonPath("$.alerts[0].holdings[0].middleName").value(nullValue()))
                .andExpect(jsonPath("$.alerts[0].holdings[0].customerName").value("NVIDIA"))
                .andExpect(jsonPath("$.alerts[0].holdings[0].relationshipId").value(tsmcSuppliesNvidia.getId()))
                .andExpect(jsonPath("$.alerts[0].holdings[0].reviewStatus").value("UNREVIEWED"));
    }

    @Test
    void managersSeeOnlyAnApprovedRecommendationAndItsEditedText() throws Exception {
        String email = "alerts-recommendation@example.com";
        Portfolio portfolio = portfolio(email);
        hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, TSMC_BOMBSHELL_YAHOO, YAHOO_FINANCE);
        event(article, Severity.HIGH, Confidence.HIGH);
        alert(portfolio, article, "40", AlertStatus.NEW);

        Recommendation recommendation = new Recommendation(article.getId(), "Original suggestion.");
        recommendation.setEditedText("Check the supplier delivery updates.");
        recommendation.setStatus(RecommendationStatus.APPROVED);
        recommendations.save(recommendation);

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].recommendation").value("Check the supplier delivery updates."));

        recommendation.setStatus(RecommendationStatus.PENDING);
        recommendations.save(recommendation);
        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].recommendation").value(nullValue()));

        recommendation.setStatus(RecommendationStatus.WITHHELD);
        recommendations.save(recommendation);
        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].recommendation").value(nullValue()));
    }

    @Test
    void theArticlesCompanyNameIsItsStoredNameNotItsDisplayNameAndItsHoldingsStayDisplayNames() throws Exception {
        String email = "alerts-company-name@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, TSMC_BOMBSHELL_YAHOO, YAHOO_FINANCE);
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].headline.article.companyName").value(tsmc.getName()))
                .andExpect(jsonPath("$.alerts[0].headline.article.companyName")
                        .value(org.hamcrest.Matchers.not("Taiwan Semiconductor Manufacturing")))
                .andExpect(jsonPath("$.alerts[0].holdings[0].supplierName").value("Taiwan Semiconductor Manufacturing"));
    }

    @Test
    void anAlertAboutTheHoldingItselfNamesTheHoldingsCompany() throws Exception {
        String email = "alerts-company-name-direct@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(nvidia, TSMC_BOMBSHELL_YAHOO, YAHOO_FINANCE);
        event(article, Severity.MEDIUM, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), null));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].headline.article.companyName").value(nvidia.getName()))
                .andExpect(jsonPath("$.alerts[0].holdings[0].supplierName").value(nullValue()));
    }

    @Test
    void anIndirectHoldingCarriesTheMiddleCompanyAndTheTwoStepFlag() throws Exception {
        String email = "alerts-indirect-fields@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "40");
        Company zeiss = companyDirectory.findOrCreate(
                "Carl Zeiss SMT Alerts IT " + UUID.randomUUID(), null, null, List.of());
        Report report = reports.save(new Report(tsmc.getId(), "20-F",
                "A-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                LocalDate.parse("2026-04-10"), "https://www.sec.gov/", Examples.nvidia10K()));
        Relationship zeissSuppliesTsmc = relationships.save(new Relationship(
                report.getId(), tsmc.getId(), zeiss.getId(), RelationshipType.SUPPLIER,
                "lithography systems", EVIDENCE));
        NewsArticle article = article(zeiss, TSMC_BOMBSHELL_YAHOO, YAHOO_FINANCE);
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);
        AlertHolding reached = new AlertHolding(alert.getId(), holding.getId(), zeissSuppliesTsmc.getId());
        reached.setSecondRelationshipId(tsmcSuppliesNvidia.getId());
        alertHoldings.save(reached);

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].holdings[0].indirect").value(true))
                // Display names, as every page shows them. Zeiss's test name has no legal ending, so it reads as stored.
                .andExpect(jsonPath("$.alerts[0].holdings[0].supplierName").value(zeiss.getName()))
                .andExpect(jsonPath("$.alerts[0].holdings[0].middleName").value("Taiwan Semiconductor Manufacturing"))
                .andExpect(jsonPath("$.alerts[0].holdings[0].customerName").value("NVIDIA"));
    }

    @Test
    void groupedEventUsesItsMostSevereAlertAsHeadline() throws Exception {
        String email = "alerts-order@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "100");
        NewsArticle lowArticle = article(tsmc, GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE);
        event(lowArticle, Severity.LOW, Confidence.HIGH);
        Alert low = alert(portfolio, lowArticle, "100", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(low.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));
        NewsArticle highArticle = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(highArticle, Severity.HIGH, Confidence.HIGH);
        Alert high = alert(portfolio, highArticle, "5", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(high.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].alertIds", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].alertIds[0]").value(high.getId()))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.severity").value("HIGH"))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.confidence").value("HIGH"))
                .andExpect(jsonPath("$.alerts[0].headline.article.title").value(highArticle.getTitle()));
    }

    @Test
    void equallySevereAlertsUseTheNewestArticleAsHeadline() throws Exception {
        String email = "alerts-tie-order@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "100");
        NewsArticle older = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(older, Severity.HIGH, Confidence.LOW);
        Alert olderAlert = alert(portfolio, older, "15", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(olderAlert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));
        NewsArticle newer = article(tsmc, GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE);
        event(newer, Severity.HIGH, Confidence.HIGH);
        Alert newerAlert = alert(portfolio, newer, "10", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(newerAlert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].headline.article.title").value(newer.getTitle()))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.confidence").value("HIGH"));
    }

    @Test
    void sameDayReutersAndYahooReportsGroupAndLaterTradingViewReportStaysSeparate() throws Exception {
        String email = "alerts-grouped@example.com";
        Portfolio portfolio = portfolio(email);
        Holding nvidiaHolding = hold(portfolio, nvidia, "40");
        Holding tsmcHolding = hold(portfolio, tsmc, "20");
        // Reuters and Yahoo Finance reported the GlobalFoundries deal on 8 October 2026. TradingView reported it on
        // 11 October 2026 at 23:00 GMT, about 3 days 10 hours after Reuters, so it falls outside the three-day window.
        List<NewsItem> eventReports = List.of(
                headline(GLOBALFOUNDRIES_REUTERS, REUTERS),
                headline(GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE),
                headline(GLOBALFOUNDRIES_TRADINGVIEW, TRADINGVIEW));
        for (int i = 0; i < eventReports.size(); i++) {
            NewsArticle article = article(tsmc, eventReports.get(i));
            Severity severity = i == 0 ? Severity.HIGH : (i == 1 ? Severity.MEDIUM : Severity.LOW);
            event(article, EventType.SUPPLY_SHORTAGE, severity, Confidence.HIGH);
            Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);
            alertHoldings.save(new AlertHolding(alert.getId(), nvidiaHolding.getId(), tsmcSuppliesNvidia.getId()));
            alertHoldings.save(new AlertHolding(alert.getId(), tsmcHolding.getId(), null));
        }

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].alertIds", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].sources", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].sources[0].title").value(GLOBALFOUNDRIES_YAHOO))
                .andExpect(jsonPath("$.alerts[0].sources[0].source").value("Yahoo Finance"))
                .andExpect(jsonPath("$.alerts[0].sources[0].publishedAt")
                        .value(headline(GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE).publishedAt()
                                .truncatedTo(ChronoUnit.MICROS).toString()))
                .andExpect(jsonPath("$.alerts[0].sources[1].title").value(GLOBALFOUNDRIES_REUTERS))
                .andExpect(jsonPath("$.alerts[0].sources[1].source").value("Reuters"))
                .andExpect(jsonPath("$.alerts[0].sources[1].publishedAt")
                        .value(headline(GLOBALFOUNDRIES_REUTERS, REUTERS).publishedAt()
                                .truncatedTo(ChronoUnit.MICROS).toString()))
                .andExpect(jsonPath("$.alerts[0].headline.article.title").value(GLOBALFOUNDRIES_REUTERS))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.severity").value("HIGH"))
                .andExpect(jsonPath("$.alerts[0].headline.assessment.confidence").value("HIGH"))
                .andExpect(jsonPath("$.alerts[0].exposure").value(60.0))
                .andExpect(jsonPath("$.alerts[0].holdings", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].holdings[0].relationshipId").value(tsmcSuppliesNvidia.getId()))
                .andExpect(jsonPath("$.alerts[0].holdings[1].relationshipId").value(nullValue()))
                .andExpect(jsonPath("$.alerts[1].sources", hasSize(1)))
                .andExpect(jsonPath("$.alerts[1].sources[0].title").value(GLOBALFOUNDRIES_TRADINGVIEW))
                .andExpect(jsonPath("$.alerts[1].sources[0].source").value(TRADINGVIEW))
                .andExpect(jsonPath("$.alerts[1].sources[0].publishedAt")
                        .value(headline(GLOBALFOUNDRIES_TRADINGVIEW, TRADINGVIEW).publishedAt()
                                .truncatedTo(ChronoUnit.MICROS).toString()));
    }

    @Test
    void twoAlertsAboutOneCompanyAndTypeFiveDaysApartStayTwoCards() throws Exception {
        String email = "alerts-five-days@example.com";
        Portfolio portfolio = portfolio(email);
        // The Motley Fool headline is 7 October 2026 and the TradingView headline is 12 October 2026, about five days
        // apart in docs/examples/google-news-tsmc.xml.
        NewsArticle oldArticle = article(tsmc, QUALCOMM_VS_TSMC, MOTLEY_FOOL);
        event(oldArticle, EventType.SUPPLY_SHORTAGE, Severity.HIGH, Confidence.HIGH);
        alert(portfolio, oldArticle, "40", AlertStatus.NEW);
        NewsArticle recentArticle = article(tsmc, TSMC_CHEAP_SMARTPHONES_TRADINGVIEW, TRADINGVIEW);
        event(recentArticle, EventType.SUPPLY_SHORTAGE, Severity.MEDIUM, Confidence.MEDIUM);
        alert(portfolio, recentArticle, "40", AlertStatus.NEW);
        assertThat(Duration.between(oldArticle.getPublishedAt(), recentArticle.getPublishedAt()))
                .isGreaterThan(Duration.ofDays(5));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].sources", hasSize(1)))
                .andExpect(jsonPath("$.alerts[1].sources", hasSize(1)));
    }

    @Test
    void twoAlertsAboutOneCompanyWithDifferentEventTypesStayTwoCards() throws Exception {
        String email = "alerts-different-types@example.com";
        Portfolio portfolio = portfolio(email);
        // Both are real 12 October 2026 headlines from docs/examples/google-news-tsmc.xml, so the date gap cannot be
        // what separates them.
        NewsArticle first = article(tsmc, TSMC_RECORD_PROFIT_REUTERS, REUTERS);
        event(first, EventType.SUPPLY_SHORTAGE, Severity.HIGH, Confidence.HIGH);
        alert(portfolio, first, "40", AlertStatus.NEW);
        NewsArticle second = article(tsmc, TSMC_BOMBSHELL_YAHOO, YAHOO_FINANCE);
        event(second, EventType.LEGAL_OR_REGULATORY, Severity.MEDIUM, Confidence.MEDIUM);
        alert(portfolio, second, "40", AlertStatus.NEW);
        assertThat(Duration.between(first.getPublishedAt(), second.getPublishedAt()))
                .isLessThan(Duration.ofDays(3));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].sources", hasSize(1)))
                .andExpect(jsonPath("$.alerts[1].sources", hasSize(1)));
    }

    @Test
    void anAlertJustInsideThreeDaysOfTheFirstJoinsItsEvent() throws Exception {
        String email = "alerts-three-day-boundary@example.com";
        Portfolio portfolio = portfolio(email);
        // Reuters is 8 October 2026 13:01 GMT and The Motley Fool is 11 October 2026 11:05 GMT in
        // docs/examples/google-news-tsmc.xml: 2 days 22 hours apart, inside the three-day window. The sample has no
        // pair exactly 72 hours apart.
        NewsArticle first = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(first, EventType.SUPPLY_SHORTAGE, Severity.HIGH, Confidence.HIGH);
        alert(portfolio, first, "40", AlertStatus.NEW);
        NewsArticle later = article(tsmc, TSMC_EARNINGS_WATCH_MOTLEY, MOTLEY_FOOL);
        event(later, EventType.SUPPLY_SHORTAGE, Severity.MEDIUM, Confidence.MEDIUM);
        alert(portfolio, later, "40", AlertStatus.NEW);
        assertThat(Duration.between(first.getPublishedAt(), later.getPublishedAt()))
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofDays(3));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].sources", hasSize(2)));
    }

    @Test
    void anEventKeepsOneReachedHoldingEntryForEachRelationship() throws Exception {
        String email = "alerts-two-relationships@example.com";
        Portfolio portfolio = portfolio(email);
        Holding nvda = hold(portfolio, nvidia, "40");
        Report secondReport = reports.saveAndFlush(new Report(nvidia.getId(), "10-K", "0001045810-26-000022",
                LocalDate.parse("2026-02-25"), "https://example.com/nvda-second-report", Examples.nvidia10K()));
        Relationship secondLink = relationships.save(new Relationship(secondReport.getId(), nvidia.getId(),
                tsmc.getId(), RelationshipType.SUPPLIER, "semiconductor wafers", EVIDENCE));
        NewsArticle firstArticle = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(firstArticle, EventType.SUPPLY_SHORTAGE, Severity.HIGH, Confidence.HIGH);
        Alert first = alert(portfolio, firstArticle, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(first.getId(), nvda.getId(), tsmcSuppliesNvidia.getId()));
        NewsArticle secondArticle = article(tsmc, GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE);
        event(secondArticle, EventType.SUPPLY_SHORTAGE, Severity.MEDIUM, Confidence.MEDIUM);
        Alert second = alert(portfolio, secondArticle, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(second.getId(), nvda.getId(), secondLink.getId()));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].holdings", hasSize(2)))
                .andExpect(jsonPath("$.alerts[0].holdings[0].relationshipId").value(tsmcSuppliesNvidia.getId()))
                .andExpect(jsonPath("$.alerts[0].holdings[1].relationshipId").value(secondLink.getId()))
                .andExpect(jsonPath("$.alerts[0].exposure").value(40.0));
    }

    @Test
    void anEventsWeightedExposureSumsEveryDistinctHoldingItReaches() throws Exception {
        String email = "alerts-weighted-exposure@example.com";
        Portfolio portfolio = portfolio(email);
        Holding nvda = hold(portfolio, nvidia, "40");
        Holding tsmcHolding = hold(portfolio, tsmc, "20");
        NewsArticle firstArticle = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(firstArticle, EventType.SUPPLY_SHORTAGE, Severity.HIGH, Confidence.HIGH);
        Alert first = alert(portfolio, firstArticle, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(first.getId(), nvda.getId(), tsmcSuppliesNvidia.getId()));
        NewsArticle secondArticle = article(tsmc, GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE);
        event(secondArticle, EventType.SUPPLY_SHORTAGE, Severity.MEDIUM, Confidence.MEDIUM);
        Alert second = alert(portfolio, secondArticle, "20", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(second.getId(), tsmcHolding.getId(), null));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weighted").value(true))
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].exposure").value(60.0))
                .andExpect(jsonPath("$.alerts[0].holdings", hasSize(2)));
    }

    @Test
    void anUnweightedEventsExposureCountsEveryDistinctHoldingItReaches() throws Exception {
        String email = "alerts-unweighted-exposure@example.com";
        Portfolio portfolio = portfolio(email);
        Holding nvda = hold(portfolio, nvidia, null);
        Holding tsmcHolding = hold(portfolio, tsmc, null);
        NewsArticle firstArticle = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(firstArticle, EventType.SUPPLY_SHORTAGE, Severity.HIGH, Confidence.HIGH);
        Alert first = alert(portfolio, firstArticle, "1", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(first.getId(), nvda.getId(), tsmcSuppliesNvidia.getId()));
        NewsArticle secondArticle = article(tsmc, GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE);
        event(secondArticle, EventType.SUPPLY_SHORTAGE, Severity.MEDIUM, Confidence.MEDIUM);
        Alert second = alert(portfolio, secondArticle, "1", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(second.getId(), tsmcHolding.getId(), null));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weighted").value(false))
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].exposure").value(2.0))
                .andExpect(jsonPath("$.alerts[0].holdings", hasSize(2)));
    }

    @Test
    void eventStatusComesFromTheLeadAlertWhenGroupRowsHaveMixedStatuses() throws Exception {
        String email = "alerts-mixed-status@example.com";
        Portfolio portfolio = portfolio(email);
        NewsArticle olderSevere = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(olderSevere, EventType.SUPPLY_SHORTAGE, Severity.HIGH, Confidence.HIGH);
        alert(portfolio, olderSevere, "40", AlertStatus.DISMISSED);
        NewsArticle newerMild = article(tsmc, GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE);
        event(newerMild, EventType.SUPPLY_SHORTAGE, Severity.LOW, Confidence.LOW);
        alert(portfolio, newerMild, "40", AlertStatus.NEW);

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(0)));
        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .param("includeDismissed", "true")
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].status").value("DISMISSED"))
                .andExpect(jsonPath("$.alerts[0].sources", hasSize(2)));
    }

    @Test
    void theSingleAlertStatusEndpointStillWorksAndRejectsAcknowledged() throws Exception {
        String email = "alerts-single-status@example.com";
        Portfolio portfolio = portfolio(email);
        NewsArticle article = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);

        mockMvc.perform(put("/api/portfolios/{id}/alerts/{alertId}", portfolio.getId(), alert.getId())
                        .header("Authorization", logins.bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An alert is either open or dismissed."));
        singleStatus(email, portfolio, alert, "DISMISSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.article.companyName").value(tsmc.getName()));
        singleStatus(email, portfolio, alert, "NEW")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void dismissingAnEventUpdatesEveryAlertAndHidesItFromOpen() throws Exception {
        String email = "alerts-status@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));
        NewsArticle secondArticle = article(tsmc, GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE);
        event(secondArticle, EventType.SUPPLY_SHORTAGE, Severity.MEDIUM, Confidence.MEDIUM);
        Alert secondAlert = alert(portfolio, secondArticle, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(secondAlert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        changeStatus(email, portfolio, List.of(alert.getId(), secondAlert.getId()), "DISMISSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$.alerts", hasSize(0)));
        assertThat(alerts.findById(alert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.DISMISSED);
        assertThat(alerts.findById(secondAlert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.DISMISSED);
    }

    @Test
    void showDismissedReturnsDismissedAlertsAndRestoreMakesOneNewAgain() throws Exception {
        String email = "alerts-restore@example.com";
        Portfolio portfolio = portfolio(email);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.DISMISSED);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));
        NewsArticle secondArticle = article(tsmc, GLOBALFOUNDRIES_YAHOO, YAHOO_FINANCE);
        event(secondArticle, Severity.MEDIUM, Confidence.MEDIUM);
        Alert secondAlert = alert(portfolio, secondArticle, "40", AlertStatus.DISMISSED);
        alertHoldings.save(new AlertHolding(secondAlert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .param("includeDismissed", "true")
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].status").value("DISMISSED"))
                .andExpect(jsonPath("$.alerts[0].sources", hasSize(2)));

        changeStatus(email, portfolio, List.of(alert.getId(), secondAlert.getId()), "NEW")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));
        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$.alerts[0].status").value("NEW"));
        assertThat(alerts.findById(alert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.NEW);
        assertThat(alerts.findById(secondAlert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.NEW);
    }

    @Test
    void filteredNewsListsOnlyNoiseWithTheAisReasonsAndCountsAssessedArticles() throws Exception {
        String email = "alerts-noise@example.com";
        Portfolio portfolio = portfolio(email);
        hold(portfolio, nvidia, "40");
        NewsAssessmentReader reader = new NewsAssessmentReader();
        var judgements = reader.read(Examples.geminiNewsAssessment(), 20);
        List<NewsItem> noiseHeadlines = List.of(
                headline(TSMC_BOMBSHELL_YAHOO, YAHOO_FINANCE), headline(QUALCOMM_VS_TSMC, MOTLEY_FOOL));
        for (int index = 0; index < noiseHeadlines.size(); index++) {
            NewsArticle article = article(tsmc, noiseHeadlines.get(index));
            NewsAssessmentReader.Judgement judgement = judgements.get(index + 1);
            assessments.save(new NewsAssessment(article.getId(), false, null, null, judgement.confidence(), null,
                    judgement.reason()));
        }
        NewsArticle eventArticle = article(tsmc, TSMC_RECORD_PROFIT_REUTERS, REUTERS);
        event(eventArticle, Severity.HIGH, Confidence.HIGH);

        mockMvc.perform(get("/api/portfolios/{id}/news", portfolio.getId())
                        .param("noise", "true")
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].assessment.isEvent").value(false))
                .andExpect(jsonPath("$[0].assessment.reason").value(judgements.get(1).reason()))
                .andExpect(jsonPath("$[1].assessment.reason").value(judgements.get(2).reason()));
        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$.weighted").value(true))
                .andExpect(jsonPath("$.holdingCount").value(1))
                .andExpect(jsonPath("$.articlesChecked").doesNotExist())
                .andExpect(jsonPath("$.noiseCount").doesNotExist());
    }

    @Test
    void anotherUsersAlertsCannotBeReadOrChangedAndUnknownAlertsAreNotFound() throws Exception {
        String owner = "alerts-owner@example.com";
        Portfolio portfolio = portfolio(owner);
        Holding holding = hold(portfolio, nvidia, "40");
        NewsArticle article = article(tsmc, GLOBALFOUNDRIES_REUTERS, REUTERS);
        event(article, Severity.HIGH, Confidence.HIGH);
        Alert alert = alert(portfolio, article, "40", AlertStatus.NEW);
        alertHoldings.save(new AlertHolding(alert.getId(), holding.getId(), tsmcSuppliesNvidia.getId()));
        String strangerToken = logins.bearer("alerts-stranger@example.com");

        mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", strangerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
        mockMvc.perform(put("/api/portfolios/{id}/alerts/status", portfolio.getId())
                        .header("Authorization", strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertIds\":[" + alert.getId() + "],\"status\":\"DISMISSED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
        mockMvc.perform(put("/api/portfolios/{id}/alerts/status", portfolio.getId())
                        .header("Authorization", logins.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertIds\":[" + Long.MAX_VALUE + "],\"status\":\"DISMISSED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Alert not found."));
        Portfolio otherPortfolio = portfolio("alerts-other-owner@example.com");
        Alert foreignAlert = alert(otherPortfolio, article, "20", AlertStatus.NEW);
        mockMvc.perform(put("/api/portfolios/{id}/alerts/status", portfolio.getId())
                        .header("Authorization", logins.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertIds\":[" + alert.getId() + "," + foreignAlert.getId()
                                + "],\"status\":\"DISMISSED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Alert not found."));
        assertThat(alerts.findById(alert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.NEW);
        mockMvc.perform(put("/api/portfolios/{id}/alerts/status", portfolio.getId())
                        .header("Authorization", logins.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertIds\":[" + alert.getId() + "],\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An alert is either open or dismissed."));
        mockMvc.perform(put("/api/portfolios/{id}/alerts/{alertId}", portfolio.getId(), alert.getId())
                        .header("Authorization", logins.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An alert is either open or dismissed."));
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

    private NewsItem headline(String title, String source) {
        NewsItem item = sampleByHeadline.get(new HeadlineKey(title, source));
        if (item == null) throw new IllegalArgumentException("Headline is not in the saved Google News sample.");
        return item;
    }

    private NewsArticle article(Company company, String title, String source) {
        return article(company, headline(title, source));
    }

    private NewsArticle article(Company company, NewsItem item) {
        return articles.save(new NewsArticle(company.getId(), item.url(), item.title(), item.source(),
                item.publishedAt().truncatedTo(ChronoUnit.MICROS)));
    }

    private NewsAssessment event(NewsArticle article, Severity severity, Confidence confidence) {
        return event(article, EventType.PRODUCTION_STOP, severity, confidence);
    }

    private NewsAssessment event(NewsArticle article, EventType type, Severity severity, Confidence confidence) {
        return assessments.save(new NewsAssessment(
                article.getId(), true, type, severity, confidence,
                "Set by the test: production stopped.", "Set by the test: the headline reports a stoppage."));
    }

    private Alert alert(Portfolio portfolio, NewsArticle article, String exposure, AlertStatus status) {
        Alert alert = new Alert(portfolio.getId(), article.getId(), new BigDecimal(exposure));
        alert.setStatus(status);
        return alerts.saveAndFlush(alert);
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(
            String email, Portfolio portfolio, List<Long> alertIds, String status) throws Exception {
        String ids = alertIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return mockMvc.perform(put("/api/portfolios/{id}/alerts/status", portfolio.getId())
                .header("Authorization", logins.bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alertIds\":[" + ids + "],\"status\":\"" + status + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions singleStatus(
            String email, Portfolio portfolio, Alert alert, String status) throws Exception {
        return mockMvc.perform(put("/api/portfolios/{id}/alerts/{alertId}", portfolio.getId(), alert.getId())
                .header("Authorization", logins.bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private record HeadlineKey(String title, String source) {
    }
}
