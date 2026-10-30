package com.truesight.history;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The SEC Filing Timeline of one of the logged-in user's portfolios. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/history")
@Tag(name = "SEC Filing Timeline")
public class HistoryController {

    private final HistoryService service;

    public HistoryController(HistoryService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Read each holding's two previous annual reports")
    public void load(@PathVariable Long portfolioId) {
        service.start(portfolioId);
    }

    @GetMapping
    @Operation(summary = "Show how suppliers and customers changed year by year, and how reading is going")
    public HistoryResponse get(@PathVariable Long portfolioId) {
        return service.get(portfolioId);
    }
}
