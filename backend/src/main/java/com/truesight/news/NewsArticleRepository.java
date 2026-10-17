package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByCompanyIdAndUrl(Long companyId, String url);

    List<NewsArticle> findByCompanyIdOrderByPublishedAtDesc(Long companyId);

    /** The 200 newest articles about any of these companies published after {@code after}. */
    List<NewsArticle> findTop200ByCompanyIdInAndPublishedAtAfterOrderByPublishedAtDesc(Collection<Long> companyIds,
                                                                                       Instant after);

    /** These companies' articles that the AI has not judged yet, in the order they were saved. */
    @Query("""
            select a from NewsArticle a
            where a.companyId in :companyIds
              and not exists (select s.id from NewsAssessment s where s.articleId = a.id)
            order by a.id""")
    List<NewsArticle> findUnassessedByCompanyIdIn(@Param("companyIds") Collection<Long> companyIds);

    @Transactional
    void deleteByCompanyId(Long companyId);
}
