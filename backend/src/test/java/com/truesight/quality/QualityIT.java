package com.truesight.quality;

import com.truesight.ai.AiCall;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.news.AssessmentDecision;
import com.truesight.news.Confidence;
import com.truesight.news.EventType;
import com.truesight.news.NewsArticle;
import com.truesight.news.NewsArticleRepository;
import com.truesight.news.NewsAssessment;
import com.truesight.news.NewsAssessmentRepository;
import com.truesight.news.Recommendation;
import com.truesight.news.RecommendationRepository;
import com.truesight.news.RecommendationStatus;
import com.truesight.news.Severity;
import com.truesight.quality.QualityResponse.CallWeek;
import com.truesight.quality.QualityResponse.NewsWeek;
import com.truesight.quality.QualityResponse.RecommendationWeek;
import com.truesight.quality.QualityResponse.RelationshipWeek;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipSource;
import com.truesight.relationship.RelationshipType;
import com.truesight.relationship.ReviewStatus;
import com.truesight.relationship.SecondCheckVerdict;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Acceptance Criteria of the AI Quality Dashboard, through the whole app and a real database.
 *
 * <p>The database is shared by every integration test and never emptied, and the dashboard counts everything in its 8
 * weeks. So the hand count asks {@link QualityService} for the 8 weeks up to a moment about 27 years ago, where no
 * other test saves anything. That moment is relative to now, like every date in it, so the test never expires.
 */
@IntegrationTest
class QualityIT {

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired QualityService quality;
    @Autowired CompanyDirectory companies;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired NewsArticleRepository articles;
    @Autowired NewsAssessmentRepository assessments;
    @Autowired RecommendationRepository recommendations;
    @Autowired AiCallRepository calls;
    @Autowired JdbcTemplate jdbc;

    private final Instant now = Instant.now().minus(Duration.ofDays(10_000));
    private final LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

    @Test
    void eachSectionsFiguresMatchAHandCountOfTheStoredDecisionsWeekByWeek() {
        Company company = companies.findOrCreate("Quality Test " + UUID.randomUUID(), null, null, List.of());
        Company counterparty = companies.findOrCreate("Quality Supplier " + UUID.randomUUID(), null, null, List.of());
        Report report = reports.save(new Report(company.getId(), "10-K",
                "qa-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), today,
                "https://www.sec.gov/Archives/" + UUID.randomUUID(), "text"));

        // Finding relationships. This week: 3 approved, 2 rejected. Two were doubted before their review, and one of
        // those was rejected; the third doubt came after the review, so it is not a doubt later rejected.
        reviewed(report, counterparty, ReviewStatus.APPROVED, at(0), null);
        reviewed(report, counterparty, ReviewStatus.APPROVED, at(0), null);
        reviewed(report, counterparty, ReviewStatus.APPROVED, at(0), at(0).minus(Duration.ofHours(1)));
        reviewed(report, counterparty, ReviewStatus.REJECTED, at(0), at(0).minus(Duration.ofHours(1)));
        reviewed(report, counterparty, ReviewStatus.REJECTED, at(0), at(0).plus(Duration.ofHours(1)));
        // An analyst's own relationship is approved as it is saved: it says nothing about the AI.
        Relationship added = reviewed(report, counterparty, ReviewStatus.APPROVED, at(0), null);
        added.setSource(RelationshipSource.ANALYST);
        relationships.save(added);
        // Not reviewed, reviewed before the 8 weeks, or after today: none of them count.
        Relationship unreviewed = relationship(report, counterparty);
        unreviewed.setSecondCheck(SecondCheckVerdict.DOUBTS);
        unreviewed.setSecondCheckedAt(at(0));
        relationships.save(unreviewed);
        reviewed(report, counterparty, ReviewStatus.REJECTED, now.minus(Duration.ofDays(57)), null);
        reviewed(report, counterparty, ReviewStatus.REJECTED, now.plus(Duration.ofDays(2)), null);
        // Three weeks ago: one doubt, later rejected.
        reviewed(report, counterparty, ReviewStatus.REJECTED, at(3), at(3).minus(Duration.ofDays(1)));

        // Judging news. This week: 7 decided, 4 overruled (noise made an event twice, once since confirmed; an event
        // made noise once; a severity changed once). One overruled and then changed back to the AI's answer is not.
        decided(company, AssessmentDecision.CONFIRMED, null, null, true, Severity.HIGH, at(0));
        decided(company, AssessmentDecision.CONFIRMED, null, null, false, null, at(0));
        decided(company, AssessmentDecision.OVERRULED, false, null, true, Severity.HIGH, at(0));
        decided(company, AssessmentDecision.CONFIRMED, false, null, true, Severity.MEDIUM, at(0));
        decided(company, AssessmentDecision.OVERRULED, true, Severity.HIGH, false, null, at(0));
        decided(company, AssessmentDecision.OVERRULED, true, Severity.HIGH, true, Severity.LOW, at(0));
        decided(company, AssessmentDecision.OVERRULED, true, Severity.HIGH, true, Severity.HIGH, at(0));
        decided(company, null, null, null, true, Severity.HIGH, null);
        // Six weeks ago: one confirmed.
        decided(company, AssessmentDecision.CONFIRMED, null, null, false, null, at(6));

        // Recommendations. This week: 5 decided. Approved as written twice (the Recommendations tab sends the same
        // wording back), edited once, withheld by an analyst once and automatically as investment advice once.
        recommendation(company, RecommendationStatus.APPROVED, null, at(0));
        recommendation(company, RecommendationStatus.APPROVED, "Watch the test supplier.", at(0));
        recommendation(company, RecommendationStatus.APPROVED, "Watch the test supplier's next shipment.", at(0));
        recommendation(company, RecommendationStatus.WITHHELD, null, at(0));
        Recommendation automatic = recommendation(company, RecommendationStatus.WITHHELD, null, null);
        jdbc.update("UPDATE recommendations SET created_at = ? WHERE id = ?", Timestamp.from(at(0)), automatic.getId());
        recommendation(company, RecommendationStatus.PENDING, null, null);
        // Two weeks ago: one withheld.
        recommendation(company, RecommendationStatus.WITHHELD, null, at(2));

        // Calls. This week: 2 answered, 1 unreadable, 1 failed, taking 100, 300, 200 and 1000 ms: the median is
        // halfway between 200 and 300. Last week: 2 answered and 1 failed, taking 700, 500 and 50 ms: the median is 500.
        call(AiOutcome.ANSWERED, 100, at(0));
        call(AiOutcome.ANSWERED, 300, at(0));
        call(AiOutcome.UNREADABLE, 200, at(0));
        call(AiOutcome.FAILED, 1000, at(0));
        call(AiOutcome.ANSWERED, 700, at(1));
        call(AiOutcome.ANSWERED, 500, at(1));
        call(AiOutcome.FAILED, 50, at(1));

        QualityResponse figures = quality.report(now);

        assertThat(figures.relationships()).containsExactly(
                relationshipsIn(0, 0, 0, 0, 0, 0),
                relationshipsIn(1, 0, 0, 0, 0, 0),
                relationshipsIn(2, 0, 0, 0, 0, 0),
                relationshipsIn(3, 0, 0, 0, 0, 0),
                relationshipsIn(4, 1, 0, 1, 1, 1),
                relationshipsIn(5, 0, 0, 0, 0, 0),
                relationshipsIn(6, 0, 0, 0, 0, 0),
                relationshipsIn(7, 5, 3, 2, 2, 1));
        assertThat(figures.news()).containsExactly(
                newsIn(0, 0, 0, 0, 0),
                newsIn(1, 1, 0, 0, 0),
                newsIn(2, 0, 0, 0, 0),
                newsIn(3, 0, 0, 0, 0),
                newsIn(4, 0, 0, 0, 0),
                newsIn(5, 0, 0, 0, 0),
                newsIn(6, 0, 0, 0, 0),
                newsIn(7, 7, 4, 2, 1));
        assertThat(figures.recommendations()).containsExactly(
                recommendationsIn(0, 0, 0, 0, 0),
                recommendationsIn(1, 0, 0, 0, 0),
                recommendationsIn(2, 0, 0, 0, 0),
                recommendationsIn(3, 0, 0, 0, 0),
                recommendationsIn(4, 0, 0, 0, 0),
                recommendationsIn(5, 1, 0, 0, 1),
                recommendationsIn(6, 0, 0, 0, 0),
                recommendationsIn(7, 5, 2, 1, 2));
        assertThat(figures.calls()).containsExactly(
                callsIn(0, 0, 0, 0, null),
                callsIn(1, 0, 0, 0, null),
                callsIn(2, 0, 0, 0, null),
                callsIn(3, 0, 0, 0, null),
                callsIn(4, 0, 0, 0, null),
                callsIn(5, 0, 0, 0, null),
                callsIn(6, 2, 0, 1, 500),
                callsIn(7, 2, 1, 1, 250));
    }

    @Test
    void anAnalystGetsEachSectionForEachOfTheLastEightWeeksEndingToday() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get("/api/quality").header("Authorization", logins.analystBearer("quality-analyst@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships", hasSize(8)))
                .andExpect(jsonPath("$.news", hasSize(8)))
                .andExpect(jsonPath("$.recommendations", hasSize(8)))
                .andExpect(jsonPath("$.calls", hasSize(8)))
                .andExpect(jsonPath("$.relationships[0].weekStart").value(today.minusDays(55).toString()))
                .andExpect(jsonPath("$.relationships[0].weekEnd").value(today.minusDays(49).toString()))
                .andExpect(jsonPath("$.calls[7].weekStart").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.calls[7].weekEnd").value(today.toString()))
                .andExpect(jsonPath("$.news[7].decided").isNumber())
                .andExpect(jsonPath("$.recommendations[7].approvedAsWritten").isNumber());
    }

    @Test
    void portfolioManagersGet403() throws Exception {
        mockMvc.perform(get("/api/quality").header("Authorization", logins.bearer("quality-manager@example.com")))
                .andExpect(status().isForbidden());
    }

    // ---- building data -------------------------------------------------------------------------------------------

    /** Two days into the week that ended this many weeks before {@code now}, clear of its edges. */
    private Instant at(int weeksAgo) {
        return now.minus(Duration.ofDays(7L * weeksAgo + 2));
    }

    private Relationship relationship(Report report, Company counterparty) {
        return relationships.save(new Relationship(report.getId(), report.getCompanyId(), counterparty.getId(),
                RelationshipType.SUPPLIER, "test parts", "We buy test parts from them."));
    }

    /** A relationship an analyst decided at {@code reviewedAt}, which the second check doubted at {@code doubtedAt}. */
    private Relationship reviewed(Report report, Company counterparty, ReviewStatus status, Instant reviewedAt,
                                  Instant doubtedAt) {
        Relationship relationship = relationship(report, counterparty);
        relationship.setReviewStatus(status);
        relationship.setReviewedAt(reviewedAt);
        if (doubtedAt != null) {
            relationship.setSecondCheck(SecondCheckVerdict.DOUBTS);
            relationship.setSecondCheckedAt(doubtedAt);
        }
        return relationships.save(relationship);
    }

    private NewsArticle article(Company company) {
        return articles.save(new NewsArticle(company.getId(), "https://news.example.com/" + UUID.randomUUID(),
                "Test headline", "Test Publisher", now));
    }

    /**
     * An article's assessment as Confirm or Overrule an Event leaves it. {@code aiEvent} is the AI's own answer, kept
     * only once an analyst changed it (an AI event is always a High supply shortage here); {@code event} and
     * {@code severity} are the judgement in force.
     */
    private void decided(Company company, AssessmentDecision decision, Boolean aiEvent, Severity aiSeverity,
                         boolean event, Severity severity, Instant decidedAt) {
        NewsAssessment assessment = new NewsAssessment(article(company).getId(), event,
                event ? EventType.SUPPLY_SHORTAGE : null, severity, Confidence.HIGH, null, "Test reason.");
        if (aiEvent != null) {
            assessment.setAiEvent(aiEvent);
            assessment.setAiEventType(aiEvent ? EventType.SUPPLY_SHORTAGE : null);
            assessment.setAiSeverity(aiSeverity);
        }
        assessment.setDecision(decision);
        assessment.setDecidedAt(decidedAt);
        assessments.save(assessment);
    }

    private Recommendation recommendation(Company company, RecommendationStatus status, String editedText,
                                          Instant decidedAt) {
        Recommendation recommendation = new Recommendation(article(company).getId(), "Watch the test supplier.");
        recommendation.setStatus(status);
        recommendation.setEditedText(editedText);
        recommendation.setDecidedAt(decidedAt);
        return recommendations.save(recommendation);
    }

    private void call(AiOutcome outcome, int durationMs, Instant startedAt) {
        calls.save(new AiCall(AiPurpose.JUDGE_NEWS, "test-model", "Test prompt.", null, null, outcome, null,
                startedAt, durationMs));
    }

    // ---- the hand count, week 0 the oldest and week 7 the one ending today ------------------------------------------

    private LocalDate end(int week) {
        return today.minusWeeks(7 - week);
    }

    private RelationshipWeek relationshipsIn(int week, int reviewed, int approved, int rejected, int doubtsReviewed,
                                             int doubtsRejected) {
        return new RelationshipWeek(end(week).minusDays(6), end(week), reviewed, approved, rejected, doubtsReviewed,
                doubtsRejected);
    }

    private NewsWeek newsIn(int week, int decided, int overruled, int noiseMadeEvents, int eventsMadeNoise) {
        return new NewsWeek(end(week).minusDays(6), end(week), decided, overruled, noiseMadeEvents, eventsMadeNoise);
    }

    private RecommendationWeek recommendationsIn(int week, int decided, int approvedAsWritten, int edited,
                                                 int withheld) {
        return new RecommendationWeek(end(week).minusDays(6), end(week), decided, approvedAsWritten, edited, withheld);
    }

    private CallWeek callsIn(int week, int answered, int unreadable, int failed, Integer medianMs) {
        return new CallWeek(end(week).minusDays(6), end(week), answered, unreadable, failed, medianMs);
    }
}
