package com.truesight.news;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RecommendationService {
    private final RecommendationRepository recommendations;
    private final NewsArticleRepository articles;
    private final NewsAssessmentRepository assessments;
    private final CurrentUser currentUser;

    public RecommendationService(RecommendationRepository recommendations, NewsArticleRepository articles,
                                 NewsAssessmentRepository assessments, CurrentUser currentUser) {
        this.recommendations = recommendations;
        this.articles = articles;
        this.assessments = assessments;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> pending() {
        return recommendations.findByStatusOrderByCreatedAtAsc(RecommendationStatus.PENDING).stream()
                .map(this::response).toList();
    }

    @Transactional
    public RecommendationResponse decide(Long id, RecommendationDecisionRequest request) {
        if (request.decision() == null) throw ApiException.badRequest("Please approve or withhold the recommendation.");
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        if (request.decision() == RecommendationDecision.WITHHELD && note == null) {
            throw ApiException.badRequest("Please say why this recommendation is withheld.");
        }
        Recommendation recommendation = recommendations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Recommendation not found."));
        if (request.text() != null && !request.text().isBlank()) {
            if (request.decision() == RecommendationDecision.APPROVED) recommendation.setEditedText(request.text().strip());
        }
        recommendation.setStatus(request.decision() == RecommendationDecision.APPROVED
                ? RecommendationStatus.APPROVED : RecommendationStatus.WITHHELD);
        recommendation.setNote(note);
        recommendation.setDecidedBy(currentUser.id());
        recommendation.setDecidedAt(Instant.now());
        recommendations.save(recommendation);
        return response(recommendation);
    }

    private RecommendationResponse response(Recommendation recommendation) {
        NewsArticle article = articles.findById(recommendation.getArticleId()).orElse(null);
        NewsAssessment assessment = assessments.findByArticleIdIn(List.of(recommendation.getArticleId())).stream()
                .findFirst().orElse(null);
        RecommendationResponse.Event event = article == null || assessment == null ? null
                : new RecommendationResponse.Event(article.getTitle(), article.getSource(), article.getPublishedAt(),
                article.getUrl(), assessment.getEventType(), assessment.getSeverity(), assessment.getSummary(),
                assessment.getReason());
        return new RecommendationResponse(recommendation.getId(), recommendation.getStatus(), recommendation.getText(),
                recommendation.getEditedText(), recommendation.getNote(), recommendation.getCreatedAt(), event);
    }
}
