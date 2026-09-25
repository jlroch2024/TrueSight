package com.truesight.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves portfolios.
 *
 * <p>Every lookup includes the owner's id. Use {@link #findByIdAndUserId} rather than {@code findById}, so that
 * another user's portfolio is simply "not found", never shown by mistake.
 */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUserIdOrderByNameAsc(Long userId);

    Optional<Portfolio> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
