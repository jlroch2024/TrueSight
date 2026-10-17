package com.truesight.news;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.truesight.company.Company;
import com.truesight.relationship.RelationshipResponse.CompanySummary;

import java.time.Instant;

/**
 * One article about a company in a portfolio's supply chain, as the website sees it. {@code isHolding} says whether
 * the company is one of the portfolio's holdings. {@code assessment} is null until the AI has judged the article.
 */
public record NewsArticleResponse(
        Long id,
        String title,
        String source,
        Instant publishedAt,
        String url,
        CompanySummary company,
        @JsonProperty("isHolding") boolean isHolding,
        Assessment assessment) {

    /** The AI's judgement of the article: whether it describes a disruption event, and how serious. */
    public record Assessment(
            @JsonProperty("isEvent") boolean isEvent,
            EventType eventType,
            Severity severity,
            Confidence confidence,
            String summary,
            String reason) {

        static Assessment from(NewsAssessment assessment) {
            return new Assessment(assessment.isEvent(), assessment.getEventType(), assessment.getSeverity(),
                    assessment.getConfidence(), assessment.getSummary(), assessment.getReason());
        }
    }

    static NewsArticleResponse from(NewsArticle article, Company company, boolean isHolding, NewsAssessment assessment) {
        return new NewsArticleResponse(article.getId(), article.getTitle(), article.getSource(),
                article.getPublishedAt(), article.getUrl(),
                new CompanySummary(company.getId(), company.getName(), company.getTicker()), isHolding,
                assessment == null ? null : Assessment.from(assessment));
    }
}
