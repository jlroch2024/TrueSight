package com.truesight.ai;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** The Analyst's searchable record of every call to Gemini. */
@RestController
@Tag(name = "AI Audit Trail")
public class AiCallController {

    private final AiCallService service;
    private final CurrentUser currentUser;

    public AiCallController(AiCallService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/ai-calls")
    @Operation(summary = "List Gemini calls newest first")
    public AiCallPageResponse list(
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page) {
        currentUser.requireAnalyst();
        return service.list(query(purpose, outcome, from, to), page);
    }

    @GetMapping("/api/ai-calls/{id}")
    @Operation(summary = "Open one Gemini call's prompt and answer")
    public AiCallDetailResponse detail(@PathVariable Long id) {
        currentUser.requireAnalyst();
        return service.detail(id);
    }

    @GetMapping(value = "/api/ai-calls/export", produces = "text/csv")
    @Operation(summary = "Export the filtered Gemini calls as CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        currentUser.requireAnalyst();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ai-calls.csv\"")
                .body(service.csv(query(purpose, outcome, from, to)));
    }

    private static AiCallQuery query(String purpose, String outcome, String from, String to) {
        return new AiCallQuery(
                parsePurpose(purpose), parseOutcome(outcome), parseDate(from, "From"), parseDate(to, "To"));
    }

    private static AiPurpose parsePurpose(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AiPurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(
                    "The purpose must be FIND_RELATIONSHIPS, JUDGE_NEWS, SECOND_CHECK, RECOMMEND, CRITICALITY or COMMON_NAMES.");
        }
    }

    private static AiOutcome parseOutcome(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AiOutcome.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("The outcome must be ANSWERED, UNREADABLE or FAILED.");
        }
    }

    private static LocalDate parseDate(String value, String name) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw ApiException.badRequest("The " + name + " date must be in YYYY-MM-DD format.");
        }
    }
}
