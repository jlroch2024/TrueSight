package com.truesight.news;

import java.time.Instant;

public record RecommendationResponse(Long id, RecommendationStatus status, String text, String editedText,
                                     String note, Instant createdAt, Event event) {
    public record Event(String title, String source, Instant publishedAt, String url, EventType eventType,
                        Severity severity, String summary, String reason) {}
}
