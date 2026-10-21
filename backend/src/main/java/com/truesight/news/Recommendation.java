package com.truesight.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One suggested next step for an event, made by the AI and checked by an analyst before a portfolio manager sees it.
 * Shared, like the event itself: one per article. Research support, never investment advice.
 */
@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "article_id", nullable = false, unique = true)
    private Long articleId;
    /** The AI's suggestion, as it wrote it. */
    @Column(nullable = false, length = 1000)
    private String text;
    /** The analyst's version, when they changed the wording. */
    @Column(name = "edited_text", length = 1000)
    private String editedText;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private RecommendationStatus status = RecommendationStatus.PENDING;
    @Column(length = 500)
    private String note;
    @Column(name = "decided_by")
    private Long decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Recommendation() {}

    public Recommendation(Long articleId, String text) {
        this.articleId = articleId;
        this.text = text;
    }

    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public String getText() { return text; }
    public String getEditedText() { return editedText; }
    public RecommendationStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Long getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setEditedText(String editedText) { this.editedText = editedText; }
    public void setStatus(RecommendationStatus status) { this.status = status; }
    public void setNote(String note) { this.note = note; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
