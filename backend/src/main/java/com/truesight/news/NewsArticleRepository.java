package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByCompanyIdAndUrl(Long companyId, String url);

    List<NewsArticle> findByCompanyIdOrderByPublishedAtDesc(Long companyId);

    @Transactional
    void deleteByCompanyId(Long companyId);
}
