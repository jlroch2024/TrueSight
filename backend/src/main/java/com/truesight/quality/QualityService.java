package com.truesight.quality;

import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiCallTiming;
import com.truesight.ai.AiOutcome;
import com.truesight.news.NewsAssessment;
import com.truesight.news.NewsAssessmentRepository;
import com.truesight.news.Recommendation;
import com.truesight.news.RecommendationRepository;
import com.truesight.news.RecommendationStatus;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.ReviewStatus;
import com.truesight.relationship.SecondCheckVerdict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * The AI Quality Dashboard: how often analysts agreed with each AI step over the last 8 weeks. Worked out on request
 * from the decisions already stored; nothing is saved.
 *
 * <p>A week is 7 whole UTC days. The newest ends today, so it holds today and the 6 days before it.
 */
@Service
public class QualityService {

    static final int WEEKS = 8;

    private final RelationshipRepository relationships;
    private final NewsAssessmentRepository assessments;
    private final RecommendationRepository recommendations;
    private final AiCallRepository calls;

    public QualityService(RelationshipRepository relationships, NewsAssessmentRepository assessments,
                          RecommendationRepository recommendations, AiCallRepository calls) {
        this.relationships = relationships;
        this.assessments = assessments;
        this.recommendations = recommendations;
        this.calls = calls;
    }

    /** The last 8 weeks up to {@code now}, oldest first. Tests pass their own moment; the endpoint passes the time. */
    @Transactional(readOnly = true)
    public QualityResponse report(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<Week> weeks = IntStream.range(0, WEEKS)
                .mapToObj(index -> Week.ending(today.minusWeeks(WEEKS - 1 - index)))
                .toList();
        Instant from = weeks.getFirst().from();
        Instant to = weeks.getLast().to();

        List<Relationship> reviewed = relationships.findAiReviewedBetween(from, to);
        List<NewsAssessment> decided = assessments.findDecidedBetween(from, to);
        List<Recommendation> recommended = recommendations.findDecidedBetween(from, to);
        List<AiCallTiming> timings = calls.findTimingsBetween(from, to);

        return new QualityResponse(
                weeks.stream().map(week -> relationshipWeek(week,
                        week.of(reviewed, Relationship::getReviewedAt))).toList(),
                weeks.stream().map(week -> newsWeek(week,
                        week.of(decided, NewsAssessment::getDecidedAt))).toList(),
                weeks.stream().map(week -> recommendationWeek(week,
                        week.of(recommended, QualityService::decidedAt))).toList(),
                weeks.stream().map(week -> callWeek(week,
                        week.of(timings, AiCallTiming::startedAt))).toList());
    }

    private static QualityResponse.RelationshipWeek relationshipWeek(Week week, List<Relationship> reviewed) {
        Predicate<Relationship> rejected = relationship -> relationship.getReviewStatus() == ReviewStatus.REJECTED;
        List<Relationship> doubted = reviewed.stream().filter(QualityService::doubtedBeforeReview).toList();
        return new QualityResponse.RelationshipWeek(week.start(), week.end(),
                reviewed.size(),
                count(reviewed, relationship -> relationship.getReviewStatus() == ReviewStatus.APPROVED),
                count(reviewed, rejected),
                doubted.size(),
                count(doubted, rejected));
    }

    /** "Later rejected" means the second check doubted it first, so a doubt recorded after the review is not one. */
    private static boolean doubtedBeforeReview(Relationship relationship) {
        return relationship.getSecondCheck() == SecondCheckVerdict.DOUBTS
                && relationship.getSecondCheckedAt() != null
                && !relationship.getSecondCheckedAt().isAfter(relationship.getReviewedAt());
    }

    private static QualityResponse.NewsWeek newsWeek(Week week, List<NewsAssessment> decided) {
        return new QualityResponse.NewsWeek(week.start(), week.end(),
                decided.size(),
                count(decided, QualityService::overruled),
                count(decided, assessment -> Boolean.FALSE.equals(assessment.getAiEvent()) && assessment.isEvent()),
                count(decided, assessment -> Boolean.TRUE.equals(assessment.getAiEvent()) && !assessment.isEvent()));
    }

    /**
     * The AI was overruled when the judgement in force differs from its own. Its own is copied into ai_* the first time
     * an analyst changes it, so an article confirmed after an overrule still counts, and one changed back does not.
     */
    private static boolean overruled(NewsAssessment assessment) {
        return assessment.getAiEvent() != null
                && (assessment.getAiEvent() != assessment.isEvent()
                    || assessment.getAiEventType() != assessment.getEventType()
                    || assessment.getAiSeverity() != assessment.getSeverity());
    }

    private static QualityResponse.RecommendationWeek recommendationWeek(Week week, List<Recommendation> decided) {
        return new QualityResponse.RecommendationWeek(week.start(), week.end(),
                decided.size(),
                count(decided, recommendation -> recommendation.getStatus() == RecommendationStatus.APPROVED
                        && !edited(recommendation)),
                count(decided, recommendation -> recommendation.getStatus() == RecommendationStatus.APPROVED
                        && edited(recommendation)),
                count(decided, recommendation -> recommendation.getStatus() == RecommendationStatus.WITHHELD));
    }

    /** The Recommendations tab sends the wording with every approval, so unchanged wording is not an edit. */
    private static boolean edited(Recommendation recommendation) {
        return recommendation.getEditedText() != null
                && !recommendation.getEditedText().equals(recommendation.getText());
    }

    /** One withheld automatically as investment advice was never decided by an analyst: it counts from when written. */
    private static Instant decidedAt(Recommendation recommendation) {
        return recommendation.getDecidedAt() != null ? recommendation.getDecidedAt() : recommendation.getCreatedAt();
    }

    private static QualityResponse.CallWeek callWeek(Week week, List<AiCallTiming> started) {
        return new QualityResponse.CallWeek(week.start(), week.end(),
                count(started, call -> call.outcome() == AiOutcome.ANSWERED),
                count(started, call -> call.outcome() == AiOutcome.UNREADABLE),
                count(started, call -> call.outcome() == AiOutcome.FAILED),
                median(started.stream().mapToInt(AiCallTiming::durationMs).sorted().toArray()));
    }

    /** The middle duration, or halfway between the middle two when there is an even number; null for none. */
    static Integer median(int[] sorted) {
        if (sorted.length == 0) return null;
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }

    private static <T> int count(List<T> items, Predicate<T> wanted) {
        return (int) items.stream().filter(wanted).count();
    }

    /** Seven whole UTC days, {@code start} to {@code end} inclusive. */
    private record Week(LocalDate start, LocalDate end) {

        static Week ending(LocalDate end) {
            return new Week(end.minusDays(6), end);
        }

        Instant from() {
            return start.atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        Instant to() {
            return end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        /** The items whose time falls in this week. */
        <T> List<T> of(List<T> items, Function<T, Instant> time) {
            return items.stream().filter(item -> {
                Instant at = time.apply(item);
                return !at.isBefore(from()) && at.isBefore(to());
            }).toList();
        }
    }
}
