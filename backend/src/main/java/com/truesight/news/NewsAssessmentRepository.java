package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NewsAssessmentRepository extends JpaRepository<NewsAssessment, Long> {

    boolean existsByArticleId(Long articleId);

    List<NewsAssessment> findByArticleIdIn(Collection<Long> articleIds);

    Optional<NewsAssessment> findByArticleId(Long articleId);

    /** Every judged article published since then, with its judgement, newest first: the Events tab. */
    @Query("""
            select new com.truesight.news.AssessmentRow(assessment, article)
            from NewsAssessment assessment
              join NewsArticle article on article.id = assessment.articleId
            where article.publishedAt > :since
            order by article.publishedAt desc, article.id desc
            """)
    List<AssessmentRow> findJudgedSince(@Param("since") Instant since);

    /** The assessments an analyst confirmed or overruled in {@code [from, to)}, for the AI Quality Dashboard. */
    @Query("select a from NewsAssessment a where a.decidedAt >= :from and a.decidedAt < :to")
    List<NewsAssessment> findDecidedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
