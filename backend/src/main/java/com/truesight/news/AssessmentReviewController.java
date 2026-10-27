package com.truesight.news;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** The Analyst Desk's Events tab: analysts confirm or overrule the AI's judgement of each article. */
@RestController
@RequestMapping("/api/news/assessments")
@Tag(name = "News Review")
public class AssessmentReviewController {

    private final AssessmentReviewService service;
    private final CurrentUser currentUser;

    public AssessmentReviewController(AssessmentReviewService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "List the last 7 days' judged articles for analyst review")
    public List<AssessmentReviewResponse> list(@RequestParam(defaultValue = "EVENTS") String filter) {
        currentUser.requireAnalyst();
        AssessmentFilter chosen;
        try {
            chosen = AssessmentFilter.valueOf(filter.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw ApiException.badRequest("The filter must be EVENTS, NOISE or DECIDED.");
        }
        return service.list(chosen);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Confirm or overrule the AI's judgement of an article")
    public AssessmentReviewResponse decide(@PathVariable Long id, @Valid @RequestBody AssessmentDecisionRequest request) {
        currentUser.requireAnalyst();
        return service.decide(id, request);
    }
}
