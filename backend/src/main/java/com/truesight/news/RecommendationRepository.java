package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    Optional<Recommendation> findByArticleId(Long articleId);

    List<Recommendation> findByStatusOrderByCreatedAtAsc(RecommendationStatus status);

    List<Recommendation> findByArticleIdIn(Collection<Long> articleIds);
}
