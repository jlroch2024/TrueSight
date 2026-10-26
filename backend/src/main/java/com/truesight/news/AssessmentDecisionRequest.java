package com.truesight.news;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * An analyst's decision on one assessment. CONFIRMED keeps the judgement and needs nothing else. OVERRULED gives the
 * new judgement: {@code isEvent}, and for an event its {@code eventType} and {@code severity}, with a {@code note}
 * saying why.
 */
public record AssessmentDecisionRequest(
        AssessmentDecision decision,
        @JsonProperty("isEvent") Boolean isEvent,
        EventType eventType,
        Severity severity,
        @Size(max = 500, message = "The note can be at most 500 characters.") String note) {
}
