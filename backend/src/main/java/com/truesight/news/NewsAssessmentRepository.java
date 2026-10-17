package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NewsAssessmentRepository extends JpaRepository<NewsAssessment, Long> {

    boolean existsByArticleId(Long articleId);

    List<NewsAssessment> findByArticleIdIn(Collection<Long> articleIds);
}
