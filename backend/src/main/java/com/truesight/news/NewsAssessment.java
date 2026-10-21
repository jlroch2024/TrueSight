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

    // The AI's own judgement, copied here the first time an analyst changes it. The columns above always hold the
    // judgement in force, so the rest of TrueSight never needs to look at these.
    @Column(name = "ai_is_event")
    private Boolean aiEvent;
    @Enumerated(EnumType.STRING) @Column(name = "ai_event_type", length = 30)
    private EventType aiEventType;
    @Enumerated(EnumType.STRING) @Column(name = "ai_severity", length = 10)
    private Severity aiSeverity;

    @Enumerated(EnumType.STRING) @Column(length = 10)
    private AssessmentDecision decision;
    @Column(name = "decided_by")
    private Long decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_note", length = 500)
    private String decisionNote;
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

    public Boolean getAiEvent() { return aiEvent; }
    public EventType getAiEventType() { return aiEventType; }
    public Severity getAiSeverity() { return aiSeverity; }
    public AssessmentDecision getDecision() { return decision; }
    public Long getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setAiEvent(Boolean aiEvent) { this.aiEvent = aiEvent; }
    public void setAiEventType(EventType aiEventType) { this.aiEventType = aiEventType; }
    public void setAiSeverity(Severity aiSeverity) { this.aiSeverity = aiSeverity; }
    public void setDecision(AssessmentDecision decision) { this.decision = decision; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
}
