package com.truesight.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What POST /api/portfolios and PUT /api/portfolios/{id} receive: the portfolio's name. */
public record PortfolioRequest(
        @NotBlank(message = "is needed.")
        @Size(max = 100, message = "must be 100 characters or fewer.")
        String name) {
}
