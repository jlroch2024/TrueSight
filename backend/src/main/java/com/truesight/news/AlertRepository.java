package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** True when this article already has an alert in this portfolio (the table allows only one). */
    boolean existsByPortfolioIdAndArticleId(Long portfolioId, Long articleId);

    Optional<Alert> findByPortfolioIdAndArticleId(Long portfolioId, Long articleId);

    List<Alert> findByPortfolioId(Long portfolioId);
}
