package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** True when this article already has an alert in this portfolio (the table allows only one). */
    boolean existsByPortfolioIdAndArticleId(Long portfolioId, Long articleId);

    Optional<Alert> findByPortfolioIdAndArticleId(Long portfolioId, Long articleId);

    List<Alert> findByPortfolioId(Long portfolioId);

    Optional<Alert> findByIdAndPortfolioId(Long id, Long portfolioId);

    /** Alerts in the order the page shows them: severity, exposure, then newest article. */
    @Query("""
            select new com.truesight.news.AlertRow(a, article, assessment)
            from Alert a
              join NewsArticle article on article.id = a.articleId
              join NewsAssessment assessment on assessment.articleId = article.id
            where a.portfolioId = :portfolioId
              and (:includeDismissed = true or a.status <> com.truesight.news.AlertStatus.DISMISSED)
            order by case assessment.severity
                       when com.truesight.news.Severity.HIGH then 0
                       when com.truesight.news.Severity.MEDIUM then 1
                       else 2 end,
                     a.exposure desc,
                     article.publishedAt desc
            """)
    List<AlertRow> findOrdered(@Param("portfolioId") Long portfolioId,
                               @Param("includeDismissed") boolean includeDismissed);
}
