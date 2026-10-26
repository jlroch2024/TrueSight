package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** True when this article already has an alert in this portfolio (the table allows only one). */
    boolean existsByPortfolioIdAndArticleId(Long portfolioId, Long articleId);

    Optional<Alert> findByPortfolioIdAndArticleId(Long portfolioId, Long articleId);

    List<Alert> findByPortfolioId(Long portfolioId);

    Optional<Alert> findByIdAndPortfolioId(Long id, Long portfolioId);

    /** Removes this article's alerts from every portfolio; the table removes their holdings with them. */
    @Modifying
    @Query("delete from Alert a where a.articleId = :articleId")
    int deleteByArticleId(@Param("articleId") Long articleId);

    /** Alerts ordered for the page; event grouping and dismissed filtering happen after this query. */
    @Query("""
            select new com.truesight.news.AlertRow(a, article, assessment)
            from Alert a
              join NewsArticle article on article.id = a.articleId
              join NewsAssessment assessment on assessment.articleId = article.id
            where a.portfolioId = :portfolioId
            order by case assessment.severity
                       when com.truesight.news.Severity.HIGH then 0
                       when com.truesight.news.Severity.MEDIUM then 1
                       else 2 end,
                     a.exposure desc,
                     article.publishedAt desc
            """)
    List<AlertRow> findOrdered(@Param("portfolioId") Long portfolioId);

    List<Alert> findByIdInAndPortfolioId(Collection<Long> ids, Long portfolioId);
}
