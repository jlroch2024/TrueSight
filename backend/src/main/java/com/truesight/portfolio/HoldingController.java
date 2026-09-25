package com.truesight.portfolio;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** A portfolio's holdings: list them, or replace them all from a CSV. The work is done in {@link HoldingService}. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/holdings")
@Tag(name = "Holdings")
public class HoldingController {

    private final HoldingService service;

    public HoldingController(HoldingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List a portfolio's holdings")
    public List<HoldingResponse> list(@PathVariable Long portfolioId) {
        return service.list(portfolioId);
    }

    /** Answers 200, not 201: the holdings are replaced, and the answer is the portfolio's new list. */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a CSV, replacing the portfolio's holdings")
    public List<HoldingResponse> upload(@PathVariable Long portfolioId, @RequestParam(name = "file", required = false) MultipartFile file) {
        return service.replace(portfolioId, file);
    }
}
