package com.truesight.portfolio;

import java.time.Instant;

/** One portfolio, as the website sees it. */
public record PortfolioResponse(Long id, String name, Instant createdAt) {

    static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(portfolio.getId(), portfolio.getName(), portfolio.getCreatedAt());
    }
}
