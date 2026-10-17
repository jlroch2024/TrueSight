package com.truesight.news;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Starts a background news check for one of the logged-in user's portfolios, and lists the news it collected. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
@Tag(name = "News")
public class NewsController {

    private final NewsCheckService service;
    private final NewsCheckJob job;
    private final NewsListService news;

    public NewsController(NewsCheckService service, NewsCheckJob job, NewsListService news) {
        this.service = service;
        this.job = job;
        this.news = news;
    }

    @PostMapping("/news-check")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Check recent supply-chain news")
    public void start(@PathVariable Long portfolioId) {
        service.start(portfolioId);
        job.run(portfolioId);
    }

    @GetMapping("/news")
    @Operation(summary = "List recent news about a portfolio's supply chain")
    public List<NewsArticleResponse> list(@PathVariable Long portfolioId) {
        return news.list(portfolioId);
    }
}
