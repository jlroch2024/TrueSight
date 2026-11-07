package com.truesight.briefing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A downloadable PDF briefing of a portfolio's supply-chain risks. */
@RestController
@Tag(name = "Briefing")
public class BriefingController {

    private final BriefingService service;

    public BriefingController(BriefingService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/portfolios/{portfolioId}/briefing", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a PDF briefing of the portfolio's supply-chain risks")
    public ResponseEntity<byte[]> download(@PathVariable Long portfolioId,
                                           @Valid @RequestBody BriefingRequest request) {
        Briefing briefing = service.build(portfolioId, request.graphPng(), request.timeZone());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + briefing.filename() + "\"")
                .body(briefing.pdf());
    }
}
