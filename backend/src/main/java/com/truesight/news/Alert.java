package com.truesight.news;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;
    @Column(name = "article_id", nullable = false)
    private Long articleId;
    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal exposure;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private AlertStatus status = AlertStatus.NEW;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    protected Alert() {}
    public Alert(Long portfolioId, Long articleId, BigDecimal exposure) {
        this.portfolioId = portfolioId;
        this.articleId = articleId;
        this.exposure = exposure;
    }
    public Long getId() { return id; }
    public Long getPortfolioId() { return portfolioId; }
    public Long getArticleId() { return articleId; }
    public BigDecimal getExposure() { return exposure; }
    public AlertStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(AlertStatus status) { this.status = status; }
}
