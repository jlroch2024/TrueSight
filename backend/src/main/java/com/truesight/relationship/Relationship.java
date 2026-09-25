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

    public String getEvidence() {
        return evidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
