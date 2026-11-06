package com.truesight.ask;

import com.truesight.common.ApiError;
import com.truesight.relationship.GeminiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    // The AI being out of reach is not the user's mistake: tell them, in the AI client's own readable words.
    @ExceptionHandler(GeminiClient.GeminiException.class)
    ResponseEntity<ApiError> aiUnavailable(GeminiClient.GeminiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(AskService.UnreadableAnswer.class)
    ResponseEntity<ApiError> aiUnreadable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("The AI's answer could not be read. Try again."));
    }
}
