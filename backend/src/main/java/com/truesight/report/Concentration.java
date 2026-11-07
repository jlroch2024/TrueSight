package com.truesight.report;

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
import java.time.LocalDate;

/**
 * A dependency figure a report's own XBRL tags give, such as "Apple: 91% of revenue". One row of the
 * {@code concentrations} table. It is not a relationship: it has no sentence, and it may name no company.
 *
 * <ul>
 *   <li>{@code companyId}: the company whose report it is.</li>
 *   <li>{@code counterpartyId}: the company named, or null when the report does not name it ("Customer A").</li>
 *   <li>{@code label}: the name as the filing labels it.</li>
 *   <li>{@code share}: from 0 to 1.</li>
 * </ul>
 */
@Entity
@Table(name = "concentrations")
public class Concentration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "counterparty_id")
    private Long counterpartyId;

    @Column(nullable = false, length = 300)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConcentrationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ConcentrationMeasure measure;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal share;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Concentration() {
    }

    public Concentration(Long reportId, Long companyId, Long counterpartyId, String label, ConcentrationKind kind,
                         ConcentrationMeasure measure, BigDecimal share, LocalDate periodStart, LocalDate periodEnd) {
        this.reportId = reportId;
        this.companyId = companyId;
        this.counterpartyId = counterpartyId;
        this.label = label;
        this.kind = kind;
        this.measure = measure;
        this.share = share;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public Long getCompanyId() { return companyId; }
    public Long getCounterpartyId() { return counterpartyId; }
    public String getLabel() { return label; }
    public ConcentrationKind getKind() { return kind; }
    public ConcentrationMeasure getMeasure() { return measure; }
    public BigDecimal getShare() { return share; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Instant getCreatedAt() { return createdAt; }
}
