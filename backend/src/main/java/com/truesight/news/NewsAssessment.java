package com.truesight.news;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "news_assessments")
public class NewsAssessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "article_id", nullable = false, unique = true)
    private Long articleId;
    @Column(name = "is_event", nullable = false)
    private boolean event;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", length = 30)
    private EventType eventType;
    @Enumerated(EnumType.STRING) @Column(length = 10)
    private Severity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Confidence confidence;
    @Column(length = 500)
    private String summary;
    @Column(nullable = false, length = 500)
    private String reason;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    protected NewsAssessment() {}
    public NewsAssessment(Long articleId, boolean event, EventType eventType, Severity severity,
                          Confidence confidence, String summary, String reason) {
        this.articleId = articleId;
        this.event = event;
        this.eventType = eventType;
        this.severity = severity;
        this.confidence = confidence;
        this.summary = summary;
        this.reason = reason;
    }
    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public boolean isEvent() { return event; }
    public EventType getEventType() { return eventType; }
    public Severity getSeverity() { return severity; }
    public Confidence getConfidence() { return confidence; }
    public String getSummary() { return summary; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setEvent(boolean event) { this.event = event; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setReason(String reason) { this.reason = reason; }
}
