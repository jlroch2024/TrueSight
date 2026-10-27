package com.truesight.news;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.truesight.relationship.RelationshipResponse.CompanySummary;

import java.time.Instant;

/**
 * One judged article on the Analyst Desk's Events tab. {@code judgement} is the one in force, which every alert
 * follows. {@code aiSaid} is the AI's own answer: the same as {@code judgement} until an analyst overrules it.
 * {@code decision} is null until an analyst decides.
 */
public record AssessmentReviewResponse(
        Long id,
        Article article,
        CompanySummary company,
        Judgement judgement,
        Judgement aiSaid,
        Confidence confidence,
        String summary,
        String reason,
        Decision decision) {

    public record Article(String title, String source, Instant publishedAt, String url) {
    }

    /** eventType and severity are null for noise. */
    public record Judgement(@JsonProperty("isEvent") boolean isEvent, EventType eventType, Severity severity) {
    }

    /** decidedBy is the analyst's name as TrueSight shows it, or null if their account has gone. */
    public record Decision(AssessmentDecision decision, String decidedBy, Instant decidedAt, String note) {
    }
}
