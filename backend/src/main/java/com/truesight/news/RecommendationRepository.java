package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    Optional<Recommendation> findByArticleId(Long articleId);

    List<Recommendation> findByStatusOrderByCreatedAtAsc(RecommendationStatus status);

    List<Recommendation> findByArticleIdIn(Collection<Long> articleIds);

    /**
     * The recommendations approved or withheld in {@code [from, to)}, for the AI Quality Dashboard. One withheld
     * automatically as investment advice has no decision time, so it counts from when it was written.
     */
    @Query("""
            select r from Recommendation r
            where r.status <> com.truesight.news.RecommendationStatus.PENDING
              and coalesce(r.decidedAt, r.createdAt) >= :from and coalesce(r.decidedAt, r.createdAt) < :to
            """)
    List<Recommendation> findDecidedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
