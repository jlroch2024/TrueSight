package com.truesight.ask;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Questions about a portfolio, answered from its own data. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/questions")
@Tag(name = "Ask TrueSight")
public class AskController {

    private final AskService service;

    public AskController(AskService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Ask a question about a portfolio and get an answer with its sources")
    public AskResponse ask(@PathVariable Long portfolioId, @Valid @RequestBody AskRequest request) {
        return service.ask(portfolioId, request);
    }
}
