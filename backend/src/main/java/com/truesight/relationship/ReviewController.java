package com.truesight.relationship;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** The Analyst's review of the AI's findings. Both endpoints are for Analysts only. */
@RestController
@Tag(name = "Review")
public class ReviewController {

    private final ReviewService service;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/reviews")
    @Operation(summary = "List the relationships to review, unreviewed ones first")
    public List<ReviewItemResponse> list(@RequestParam(required = false) String status) {
        currentUser.requireAnalyst();
        return service.list(parse(status));
    }

    @PutMapping("/api/relationships/{id}/review")
    @Operation(summary = "Approve or reject a relationship the AI found")
    public ReviewItemResponse review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        currentUser.requireAnalyst();
        return service.review(id, request);
    }

    /** No status means all of them. Anything that is not a review status is a bad request, not a crash. */
    private static ReviewStatus parse(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReviewStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("The status must be UNREVIEWED, APPROVED or REJECTED.");
        }
    }
}
