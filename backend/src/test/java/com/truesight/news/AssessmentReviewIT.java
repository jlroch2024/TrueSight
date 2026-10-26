package com.truesight.news;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirm or Overrule an Event, against the real tables. Two portfolios, owned by two portfolio managers, both hold
 * NVIDIA, whose 10-K names TSMC as a supplier, so an event about TSMC reaches both: every decision must change both.
 */
@IntegrationTest
class AssessmentReviewIT {

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
    @Autowired AlertBuilder alertBuilder;

    private List<NewsItem> sample;
    private Company tsmc;
    private String firstOwner;
    private String secondOwner;
    private Portfolio first;
    private Portfolio second;
    private String analyst;

    @BeforeEach
    void makeTwoPortfoliosSharingTsmcAsASupplier() {
        sample = new GoogleNewsParser().parse(Examples.googleNewsTsmc());
        Company nvidia = companyDirectory.findOrCreate("NVIDIA Corporation", NVIDIA_CIK, "NVDA", List.of("NVIDIA"));
        tsmc = companyDirectory.findOrCreate(
                "Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM", List.of("TSMC"));
        articles.deleteByCompanyId(tsmc.getId());
        articles.deleteByCompanyId(nvidia.getId());
        reports.deleteByCompanyId(nvidia.getId());
        Report report = reports.saveAndFlush(new Report(nvidia.getId(), "10-K", "0001045810-26-000021",
                LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm",
                Examples.nvidia10K()));
        relationships.save(new Relationship(report.getId(), nvidia.getId(), tsmc.getId(), RelationshipType.SUPPLIER,
                "semiconductor wafers", EVIDENCE));

        String run = UUID.randomUUID().toString();
        firstOwner = "review-first-" + run + "@example.com";
        secondOwner = "review-second-" + run + "@example.com";
        analyst = "review-analyst-" + run + "@example.com";
        first = holdingNvidia(firstOwner, nvidia);
        second = holdingNvidia(secondOwner, nvidia);
    }

    @Test
    void theEventsTabListsTheLastSevenDaysJudgedArticlesByFilter() throws Exception {
        NewsAssessment event = judged(0, Duration.ofHours(1), true, Severity.HIGH);
        NewsAssessment noise = judged(1, Duration.ofHours(2), false, null);
        NewsAssessment old = judged(2, Duration.ofDays(8), true, Severity.HIGH);

        list("EVENTS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(event.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(noise.getId().intValue()))))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(old.getId().intValue()))))
                .andExpect(jsonPath(item(event) + ".article.title").value(contains(sample.get(0).title())))
                .andExpect(jsonPath(item(event) + ".article.source").value(contains(sample.get(0).source())))
                .andExpect(jsonPath(item(event) + ".company.ticker").value(contains("TSM")))
                .andExpect(jsonPath(item(event) + ".company.displayName").isNotEmpty())
                .andExpect(jsonPath(item(event) + ".judgement.isEvent").value(contains(true)))
                .andExpect(jsonPath(item(event) + ".judgement.eventType").value(contains("PRODUCTION_STOP")))
                .andExpect(jsonPath(item(event) + ".judgement.severity").value(contains("HIGH")))
                .andExpect(jsonPath(item(event) + ".confidence").value(contains("HIGH")))
                .andExpect(jsonPath(item(event) + ".reason").value(contains(event.getReason())));
        list("NOISE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(noise.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(event.getId().intValue()))))
                .andExpect(jsonPath(item(noise) + ".judgement.isEvent").value(contains(false)));
        list("DECIDED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(event.getId().intValue()))))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(noise.getId().intValue()))));
        list("SOMETHING")
                .andExpect(status().isBadRequest());
    }

    @Test
    void overrulingAnEventToNoiseRemovesItsAlertsFromEveryPortfolio() throws Exception {
        NewsAssessment event = judged(0, Duration.ofHours(1), true, Severity.HIGH);
        alertBuilder.run(first.getId());
        alertBuilder.run(second.getId());
        assertThat(alertsFor(first, event)).isPresent();
        assertThat(alertsFor(second, event)).isPresent();

        decide(event, """
                {"decision":"OVERRULED","isEvent":false,"note":"An earnings preview, not a disruption."}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgement.isEvent").value(false))
                .andExpect(jsonPath("$.aiSaid.isEvent").value(true))
                .andExpect(jsonPath("$.aiSaid.severity").value("HIGH"))
                .andExpect(jsonPath("$.decision.decision").value("OVERRULED"))
                .andExpect(jsonPath("$.decision.note").value("An earnings preview, not a disruption."));

        assertThat(alertsFor(first, event)).isEmpty();
        assertThat(alertsFor(second, event)).isEmpty();
        NewsAssessment saved = assessments.findById(event.getId()).orElseThrow();
        assertThat(saved.isEvent()).isFalse();
        assertThat(saved.getEventType()).isNull();
        assertThat(saved.getAiEvent()).isTrue();
        assertThat(saved.getAiEventType()).isEqualTo(EventType.PRODUCTION_STOP);
        assertThat(saved.getAiSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void overrulingNoiseToAnEventCreatesAlertsInEveryPortfolioItReaches() throws Exception {
        NewsAssessment noise = judged(0, Duration.ofHours(1), false, null);
        alertBuilder.run(first.getId());
        alertBuilder.run(second.getId());
        assertThat(alertsFor(first, noise)).isEmpty();

        decide(noise, """
                {"decision":"OVERRULED","isEvent":true,"eventType":"LABOUR_ACTION","severity":"MEDIUM",
                 "note":"The headline reports a strike at the plant."}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgement.isEvent").value(true))
                .andExpect(jsonPath("$.judgement.eventType").value("LABOUR_ACTION"))
                .andExpect(jsonPath("$.judgement.severity").value("MEDIUM"))
                .andExpect(jsonPath("$.aiSaid.isEvent").value(false));

        assertThat(alertsFor(first, noise)).isPresent();
        assertThat(alertsFor(second, noise)).isPresent();
        alerts(firstOwner, first)
                .andExpect(jsonPath(alertAbout(noise) + ".headline.assessment.eventType")
                        .value(contains("LABOUR_ACTION")));
    }

    @Test
    void changingAnEventsSeverityChangesItOnEveryAlertForThatEvent() throws Exception {
        NewsAssessment event = judged(0, Duration.ofHours(1), true, Severity.LOW);
        alertBuilder.run(first.getId());
        alertBuilder.run(second.getId());

        decide(event, """
                {"decision":"OVERRULED","isEvent":true,"eventType":"PRODUCTION_STOP","severity":"HIGH",
                 "note":"The whole plant stopped, not one line."}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSaid.severity").value("LOW"));

        alerts(firstOwner, first)
                .andExpect(jsonPath(alertAbout(event) + ".headline.assessment.severity").value(contains("HIGH")));
        alerts(secondOwner, second)
                .andExpect(jsonPath(alertAbout(event) + ".headline.assessment.severity").value(contains("HIGH")));
    }

    @Test
    void confirmingRecordsTheAnalystAndTimeAndChangesNothingElse() throws Exception {
        NewsAssessment event = judged(0, Duration.ofHours(1), true, Severity.HIGH);
        alertBuilder.run(first.getId());
        Long alertId = alertsFor(first, event).orElseThrow().getId();
        Instant before = Instant.now();

        decide(event, "{\"decision\":\"CONFIRMED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.decision").value("CONFIRMED"))
                .andExpect(jsonPath("$.decision.decidedBy").isNotEmpty())
                .andExpect(jsonPath("$.decision.decidedAt").isNotEmpty())
                .andExpect(jsonPath("$.judgement.severity").value("HIGH"))
                .andExpect(jsonPath("$.aiSaid.severity").value("HIGH"));

        NewsAssessment saved = assessments.findById(event.getId()).orElseThrow();
        assertThat(saved.getDecision()).isEqualTo(AssessmentDecision.CONFIRMED);
        assertThat(saved.getDecidedBy()).isEqualTo(logins.user(analyst).getId());
        assertThat(saved.getDecidedAt()).isAfterOrEqualTo(before.truncatedTo(ChronoUnit.MICROS));
        assertThat(saved.isEvent()).isTrue();
        assertThat(saved.getEventType()).isEqualTo(EventType.PRODUCTION_STOP);
        assertThat(saved.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(saved.getAiEvent()).isNull();
        assertThat(alertsFor(first, event).orElseThrow().getId()).isEqualTo(alertId);
        list("DECIDED").andExpect(jsonPath("$[*].id").value(hasItem(event.getId().intValue())));
        list("EVENTS").andExpect(jsonPath("$[*].id").value(not(hasItem(event.getId().intValue()))));
    }

    @Test
    void aDecidedArticleShowsTheAisAnswerBesideTheDecision() throws Exception {
        NewsAssessment noise = judged(0, Duration.ofHours(1), false, null);
        decide(noise, """
                {"decision":"OVERRULED","isEvent":true,"eventType":"SUPPLY_SHORTAGE","severity":"LOW",
                 "note":"Chip supply is short."}
                """).andExpect(status().isOk());

        list("DECIDED")
                .andExpect(jsonPath(item(noise) + ".aiSaid.isEvent").value(contains(false)))
                .andExpect(jsonPath(item(noise) + ".judgement.isEvent").value(contains(true)))
                .andExpect(jsonPath(item(noise) + ".judgement.eventType").value(contains("SUPPLY_SHORTAGE")))
                .andExpect(jsonPath(item(noise) + ".decision.decision").value(contains("OVERRULED")))
                .andExpect(jsonPath(item(noise) + ".decision.note").value(contains("Chip supply is short.")));
    }

    @Test
    void overrulingWithoutANoteIsRefusedAndChangesNothing() throws Exception {
        NewsAssessment event = judged(0, Duration.ofHours(1), true, Severity.HIGH);
        alertBuilder.run(first.getId());

        decide(event, "{\"decision\":\"OVERRULED\",\"isEvent\":false,\"note\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please say why the AI's judgement is overruled."));
        decide(event, """
                {"decision":"OVERRULED","isEvent":true,"eventType":"PRODUCTION_STOP","severity":"HIGH",
                 "note":"Same as before."}
                """)
                .andExpect(status().isBadRequest());
        decide(event, "{\"decision\":\"OVERRULED\",\"isEvent\":true,\"note\":\"No type given.\"}")
                .andExpect(status().isBadRequest());

        NewsAssessment saved = assessments.findById(event.getId()).orElseThrow();
        assertThat(saved.isEvent()).isTrue();
        assertThat(saved.getDecision()).isNull();
        assertThat(saved.getAiEvent()).isNull();
        assertThat(alertsFor(first, event)).isPresent();
    }

    @Test
    void portfolioManagersCannotListOrDecide() throws Exception {
        NewsAssessment event = judged(0, Duration.ofHours(1), true, Severity.HIGH);

        mockMvc.perform(get("/api/news/assessments").param("filter", "EVENTS")
                        .header("Authorization", logins.bearer(firstOwner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/news/assessments/{id}", event.getId())
                        .header("Authorization", logins.bearer(firstOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
        assertThat(assessments.findById(event.getId()).orElseThrow().getDecision()).isNull();
    }

    @Test
    void anUnknownAssessmentIsNotFound() throws Exception {
        mockMvc.perform(put("/api/news/assessments/{id}", Long.MAX_VALUE)
                        .header("Authorization", logins.analystBearer(analyst))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isNotFound());
    }

    private Portfolio holdingNvidia(String owner, Company nvidia) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(owner).getId(), "Review Test"));
        Holding holding = new Holding(portfolio.getId(), "NVDA", new BigDecimal("40"));
        holding.setCompanyId(nvidia.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);
        return portfolio;
    }

    /** A saved TSMC headline from the sample, published {@code ago}, with a judgement set by the test. */
    private NewsAssessment judged(int sampleIndex, Duration ago, boolean event, Severity severity) {
        NewsItem item = sample.get(sampleIndex);
        NewsArticle article = articles.save(new NewsArticle(tsmc.getId(), item.url() + "#" + UUID.randomUUID(),
                item.title(), item.source(), Instant.now().minus(ago).truncatedTo(ChronoUnit.MICROS)));
        return assessments.save(new NewsAssessment(article.getId(), event,
                event ? EventType.PRODUCTION_STOP : null, severity, Confidence.HIGH,
                event ? "Set by the test: production stopped." : null,
                event ? "Set by the test: the headline reports a stoppage."
                        : "Set by the test: the headline is not a disruption."));
    }

    private static String item(NewsAssessment assessment) {
        return "$[?(@.id == " + assessment.getId() + ")]";
    }

    /** The portfolio's alert about this assessment's article, found by the article's address, which is unique. */
    private String alertAbout(NewsAssessment assessment) {
        String url = articles.findById(assessment.getArticleId()).orElseThrow().getUrl();
        return "$.alerts[?(@.headline.article.url == '" + url + "')]";
    }

    private java.util.Optional<Alert> alertsFor(Portfolio portfolio, NewsAssessment assessment) {
        return alerts.findByPortfolioIdAndArticleId(portfolio.getId(), assessment.getArticleId());
    }

    private ResultActions list(String filter) throws Exception {
        return mockMvc.perform(get("/api/news/assessments").param("filter", filter)
                .header("Authorization", logins.analystBearer(analyst)));
    }

    private ResultActions decide(NewsAssessment assessment, String body) throws Exception {
        return mockMvc.perform(put("/api/news/assessments/{id}", assessment.getId())
                .header("Authorization", logins.analystBearer(analyst))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions alerts(String owner, Portfolio portfolio) throws Exception {
        return mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(owner)))
                .andExpect(status().isOk());
    }
}
