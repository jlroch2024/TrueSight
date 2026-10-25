package com.truesight.relationship;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A supplier or customer relationship stated in an annual report. One row of the {@code relationships} table.
 *
 * <ul>
 *   <li>{@code companyId}: the company whose report it came from.</li>
 *   <li>{@code counterpartyId}: the other company.</li>
 *   <li>{@code type}: which way round, see {@link RelationshipType}.</li>
 *   <li>{@code provides}: what is supplied, e.g. "semiconductor wafers".</li>
 *   <li>{@code evidence}: the report's own sentence that says so. Never the AI's wording.</li>
 * </ul>
 *
 * <p>Relationships belong to a report, not to a portfolio. Each portfolio shows the relationships of its holdings'
 * reports.
 */
@Entity
@Table(name = "relationships")
public class Relationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipType type;

    private String provides;

    @Column(nullable = false)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 10)
    private ReviewStatus reviewStatus = ReviewStatus.UNREVIEWED;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** AI for one found in a report, ANALYST for one an analyst added. Every Sprint 1 and 2 relationship is AI. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RelationshipSource source = RelationshipSource.AI;

    @Column(name = "added_by")
    private Long addedBy;

    @Column(name = "edited_by")
    private Long editedBy;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private Criticality criticality;

    @Column(name = "criticality_evidence")
    private String criticalityEvidence;

    /** How closely the AI's quote matched the report, from 0 to 1. */
    @Column(name = "quote_match", precision = 4, scale = 3)
    private BigDecimal quoteMatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "second_check", length = 10)
    private SecondCheck secondCheck;

    @Column(name = "second_check_reason", length = 500)
    private String secondCheckReason;

    @Column(name = "second_checked_at")
    private Instant secondCheckedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Relationship() {
    }

    public Relationship(Long reportId, Long companyId, Long counterpartyId, RelationshipType type, String provides,
                        String evidence) {
        this.reportId = reportId;
        this.companyId = companyId;
        this.counterpartyId = counterpartyId;
        this.type = type;
        this.provides = provides;
        this.evidence = evidence;
    }

    public Long getId() {
        return id;
    }

    public Long getReportId() {
        return reportId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getCounterpartyId() {
        return counterpartyId;
    }

    public RelationshipType getType() {
        return type;
    }

    public String getProvides() {
        return provides;
    }

    public void setProvides(String provides) {
        this.provides = provides;
    }

    public String getEvidence() {
        return evidence;
    }

    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public Long getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }

    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public RelationshipSource getSource() { return source; }
    public Long getAddedBy() { return addedBy; }
    public Long getEditedBy() { return editedBy; }
    public Instant getEditedAt() { return editedAt; }
    public Criticality getCriticality() { return criticality; }
    public String getCriticalityEvidence() { return criticalityEvidence; }
    public BigDecimal getQuoteMatch() { return quoteMatch; }
    public SecondCheck getSecondCheck() { return secondCheck; }
    public String getSecondCheckReason() { return secondCheckReason; }
    public Instant getSecondCheckedAt() { return secondCheckedAt; }

    public void setSource(RelationshipSource source) { this.source = source; }
    public void setAddedBy(Long addedBy) { this.addedBy = addedBy; }
    public void setEditedBy(Long editedBy) { this.editedBy = editedBy; }
    public void setEditedAt(Instant editedAt) { this.editedAt = editedAt; }
    public void setCriticality(Criticality criticality) { this.criticality = criticality; }
    public void setCriticalityEvidence(String criticalityEvidence) { this.criticalityEvidence = criticalityEvidence; }
    public void setQuoteMatch(BigDecimal quoteMatch) { this.quoteMatch = quoteMatch; }
    public void setSecondCheck(SecondCheck secondCheck) { this.secondCheck = secondCheck; }
    public void setSecondCheckReason(String secondCheckReason) { this.secondCheckReason = secondCheckReason; }
    public void setSecondCheckedAt(Instant secondCheckedAt) { this.secondCheckedAt = secondCheckedAt; }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
