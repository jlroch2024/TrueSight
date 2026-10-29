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

import java.time.Instant;

/**
 * A supplier or customer relationship found in one of a company's older annual reports. One row of the
 * {@code past_relationships} table. It has the same shape as a {@link Relationship}, but only the SEC Filing
 * Timeline reads it: the graph, alerts, the review queue and the news check never do.
 */
@Entity
@Table(name = "past_relationships")
public class PastRelationship {

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
    @Column(nullable = false, length = 10)
    private RelationshipType type;

    @Column(length = 500)
    private String provides;

    @Column(nullable = false)
    private String evidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected PastRelationship() {
    }

    public PastRelationship(Long reportId, Long companyId, Long counterpartyId, RelationshipType type,
                            String provides, String evidence) {
        this.reportId = reportId;
        this.companyId = companyId;
        this.counterpartyId = counterpartyId;
        this.type = type;
        this.provides = provides;
        this.evidence = evidence;
    }

    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public Long getCompanyId() { return companyId; }
    public Long getCounterpartyId() { return counterpartyId; }
    public RelationshipType getType() { return type; }
    public String getProvides() { return provides; }
    public String getEvidence() { return evidence; }
    public Instant getCreatedAt() { return createdAt; }
}
