package com.truesight.news;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "news_articles")
public class NewsArticle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(nullable = false, length = 2000)
    private String url;
    @Column(nullable = false, length = 1000)
    private String title;
    @Column(length = 200)
    private String source;
    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    protected NewsArticle() {}
    public NewsArticle(Long companyId, String url, String title, String source, Instant publishedAt) {
        this.companyId = companyId;
        this.url = url;
        this.title = title;
        this.source = source;
        this.publishedAt = publishedAt;
    }
    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getSource() { return source; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public void setUrl(String url) { this.url = url; }
    public void setTitle(String title) { this.title = title; }
    public void setSource(String source) { this.source = source; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
