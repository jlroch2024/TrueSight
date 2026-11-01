package com.truesight.news;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Disruption Simulator: what happens to one of the logged-in user's portfolios if a company stops supplying. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
@Tag(name = "Disruption Simulator")
public class WhatIfController {

    private final WhatIfService service;

    public WhatIfController(WhatIfService service) {
        this.service = service;
    }

    @GetMapping("/what-if")
    @Operation(summary = "Show which holdings a company stopping would reach")
    public WhatIfResponse whatIf(@PathVariable Long portfolioId, @RequestParam Long companyId) {
        return service.whatIf(portfolioId, companyId);
    }
}
