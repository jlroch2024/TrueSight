package com.truesight.portfolio;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
@Tag(name = "Portfolio Analysis")
public class HoldingsController {

    private final AnalysisService analysis;
    private final AnalysisJob job;

    public HoldingsController(AnalysisService analysis, AnalysisJob job) {
        this.analysis = analysis;
        this.job = job;
    }

    @PostMapping("/analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start annual report analysis for a portfolio")
    public void analyze(@PathVariable Long portfolioId) {
        List<Long> queued = analysis.start(portfolioId);
        if (!queued.isEmpty()) job.run(queued);
    }

}
