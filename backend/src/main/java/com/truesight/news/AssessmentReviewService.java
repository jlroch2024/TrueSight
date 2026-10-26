package com.truesight.news;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.company.CompanyRepository;
import com.truesight.relationship.RelationshipResponse.CompanySummary;
import com.truesight.user.CurrentUser;
import com.truesight.user.User;
import com.truesight.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Confirm or Overrule an Event: an analyst's decision on the AI's judgement of an article. Assessments are shared, so
 * a decision applies to every portfolio at once: overruling changes the judgement in force, and {@link AlertBuilder}
 * brings every portfolio's alerts for the article in line with it. Callers have already checked the user is an
 * Analyst.
 *
 * <p>The AI's own answer is kept: the first time an analyst changes an assessment, its judgement is copied into the
 * ai_* columns before the real ones change. Everything else that reads assessments follows the real ones unchanged.
 */
@Service
public class AssessmentReviewService {

    private final NewsAssessmentRepository assessments;
    private final NewsArticleRepository articles;
    private final CompanyRepository companies;
    private final CompanyDisplayNames displayNames;
    private final UserRepository users;
    private final AlertBuilder alertBuilder;
    private final CurrentUser currentUser;

    public AssessmentReviewService(NewsAssessmentRepository assessments, NewsArticleRepository articles,
                                   CompanyRepository companies, CompanyDisplayNames displayNames, UserRepository users,
                                   AlertBuilder alertBuilder, CurrentUser currentUser) {
        this.assessments = assessments;
        this.articles = articles;
        this.companies = companies;
        this.displayNames = displayNames;
        this.users = users;
        this.alertBuilder = alertBuilder;
        this.currentUser = currentUser;
    }

    /** The last 7 days' judged articles, newest first: undecided events, undecided noise, or those decided. */
    public List<AssessmentReviewResponse> list(AssessmentFilter filter) {
        Predicate<NewsAssessment> wanted = switch (filter) {
            case EVENTS -> assessment -> assessment.getDecision() == null && assessment.isEvent();
            case NOISE -> assessment -> assessment.getDecision() == null && !assessment.isEvent();
            case DECIDED -> assessment -> assessment.getDecision() != null;
        };
        List<AssessmentRow> rows = assessments.findJudgedSince(Instant.now().minus(NewsListService.RECENT)).stream()
                .filter(row -> wanted.test(row.assessment()))
                .toList();
        return responses(rows);
    }

    /**
     * Confirming records who decided and when, and changes nothing else. Overruling needs a note and a different
     * judgement; it changes the judgement in force and every portfolio's alerts for the article.
     */
    public AssessmentReviewResponse decide(Long id, AssessmentDecisionRequest request) {
        NewsAssessment assessment = assessments.findById(id)
                .orElseThrow(() -> ApiException.notFound("Assessment not found."));
        if (request.decision() == null) {
            throw ApiException.badRequest("Please confirm or overrule the AI's judgement.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();

        if (request.decision() == AssessmentDecision.OVERRULED) {
            overrule(assessment, request, note);
        }
        assessment.setDecision(request.decision());
        assessment.setDecidedBy(currentUser.id());
        assessment.setDecidedAt(Instant.now());
        assessment.setDecisionNote(note);
        NewsAssessment saved = assessments.save(assessment);

        if (request.decision() == AssessmentDecision.OVERRULED) {
            alertBuilder.rebuildFor(saved.getArticleId());
        }
        NewsArticle article = articles.findById(saved.getArticleId())
                .orElseThrow(() -> ApiException.notFound("Assessment not found."));
        return responses(List.of(new AssessmentRow(saved, article))).getFirst();
    }

    private void overrule(NewsAssessment assessment, AssessmentDecisionRequest request, String note) {
        if (note == null) throw ApiException.badRequest("Please say why the AI's judgement is overruled.");
        if (request.isEvent() == null) throw ApiException.badRequest("Please say whether the article is an event.");
        boolean event = request.isEvent();
        if (event && (request.eventType() == null || request.severity() == null)) {
            throw ApiException.badRequest("Please choose the event type and severity.");
        }
        EventType eventType = event ? request.eventType() : null;
        Severity severity = event ? request.severity() : null;
        if (event == assessment.isEvent() && eventType == assessment.getEventType()
                && severity == assessment.getSeverity()) {
            throw ApiException.badRequest("An overrule must change the judgement. To agree, confirm it.");
        }
        if (assessment.getAiEvent() == null) {
            assessment.setAiEvent(assessment.isEvent());
            assessment.setAiEventType(assessment.getEventType());
            assessment.setAiSeverity(assessment.getSeverity());
        }
        assessment.setEvent(event);
        assessment.setEventType(eventType);
        assessment.setSeverity(severity);
    }

    private List<AssessmentReviewResponse> responses(List<AssessmentRow> rows) {
        Map<Long, Company> companyById = companies.findAllById(
                        rows.stream().map(row -> row.article().getCompanyId()).distinct().toList())
                .stream().collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<Long, String> analystName = users.findAllById(
                        rows.stream().map(row -> row.assessment().getDecidedBy()).filter(Objects::nonNull)
                                .distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::nameShown));
        return rows.stream().map(row -> {
            NewsAssessment assessment = row.assessment();
            NewsArticle article = row.article();
            Company company = companyById.get(article.getCompanyId());
            AssessmentReviewResponse.Judgement judgement = new AssessmentReviewResponse.Judgement(
                    assessment.isEvent(), assessment.getEventType(), assessment.getSeverity());
            AssessmentReviewResponse.Judgement aiSaid = assessment.getAiEvent() == null ? judgement
                    : new AssessmentReviewResponse.Judgement(
                            assessment.getAiEvent(), assessment.getAiEventType(), assessment.getAiSeverity());
            AssessmentReviewResponse.Decision decision = assessment.getDecision() == null ? null
                    : new AssessmentReviewResponse.Decision(assessment.getDecision(),
                            analystName.get(assessment.getDecidedBy()), assessment.getDecidedAt(),
                            assessment.getDecisionNote());
            return new AssessmentReviewResponse(
                    assessment.getId(),
                    new AssessmentReviewResponse.Article(article.getTitle(), article.getSource(),
                            article.getPublishedAt(), article.getUrl()),
                    company == null ? null : new CompanySummary(company.getId(), company.getName(),
                            displayNames.of(company), company.getTicker()),
                    judgement, aiSaid, assessment.getConfidence(), assessment.getSummary(), assessment.getReason(),
                    decision);
        }).toList();
    }
}
