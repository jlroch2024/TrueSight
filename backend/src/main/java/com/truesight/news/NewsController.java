package com.truesight.news;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Starts a background news check for one of the logged-in user's portfolios. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/news-check")
@Tag(name = "News")
public class NewsController {

    private final NewsCheckService service;
    private final NewsCheckJob job;

    public NewsController(NewsCheckService service, NewsCheckJob job) {
        this.service = service;
        this.job = job;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Check recent supply-chain news")
    public void start(@PathVariable Long portfolioId) {
        service.start(portfolioId);
        job.run(portfolioId);
    }
}
