package com.truesight.portfolio;

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
 * A company in a portfolio: its ticker, and optionally its weight. One row of the {@code holdings} table.
 *
 * <p>{@code companyId} is empty until the analysis matches the ticker to a company. {@code status} is where the
 * analysis has got; {@code statusReason} says why, when it failed.
 */
@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(nullable = false)
    private String ticker;

    /** The holding's share of the portfolio, as a percentage. Empty if the CSV did not give one. */
    @Column(precision = 9, scale = 4)
    private BigDecimal weight;

    @Column(name = "company_id")
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldingStatus status = HoldingStatus.WAITING;

    @Column(name = "status_reason")
    private String statusReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Holding() {
    }

    public Holding(Long portfolioId, String ticker, BigDecimal weight) {
        this.portfolioId = portfolioId;
        this.ticker = ticker;
        this.weight = weight;
    }

    public Long getId() {
        return id;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public HoldingStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    /** Sets the status, clearing the reason. Use {@link #fail} for a failure. */
    public void setStatus(HoldingStatus status) {
        this.status = status;
        this.statusReason = null;
    }

    /** Marks the holding Failed, keeping the reason so the website can show it. */
    public void fail(String reason) {
        this.status = HoldingStatus.FAILED;
        this.statusReason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
