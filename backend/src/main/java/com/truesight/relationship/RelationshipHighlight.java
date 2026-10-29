package com.truesight.relationship;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A phrase the AI picked out in a relationship's evidence, checked word for word against it and kept as where it
 * starts and ends in the evidence. One row of the {@code relationship_highlights} table.
 *
 * <p>{@code startAt} is where the phrase begins (counting from 0) and {@code endAt} is where it ends, so
 * {@code endAt} is always greater than {@code startAt}.
 */
@Entity
@Table(name = "relationship_highlights")
public class RelationshipHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "relationship_id", nullable = false)
    private Long relationshipId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HighlightKind kind;

    @Column(name = "start_at", nullable = false)
    private int startAt;

    @Column(name = "end_at", nullable = false)
    private int endAt;

    /** For JPA only. */
    protected RelationshipHighlight() {
    }

    public RelationshipHighlight(Long relationshipId, HighlightKind kind, int startAt, int endAt) {
        this.relationshipId = relationshipId;
        this.kind = kind;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public Long getId() { return id; }
    public Long getRelationshipId() { return relationshipId; }
    public HighlightKind getKind() { return kind; }
    public int getStartAt() { return startAt; }
    public int getEndAt() { return endAt; }
}
