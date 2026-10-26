package com.truesight.news;

import com.truesight.user.CurrentUser;
import com.truesight.common.ApiException;
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

@RestController
@Tag(name = "Recommendations")
public class RecommendationController {
    private final RecommendationService service;
    private final CurrentUser currentUser;

    public RecommendationController(RecommendationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/recommendations")
    @Operation(summary = "List recommendations for analyst review")
    public List<RecommendationResponse> list(@RequestParam(defaultValue = "PENDING") String status) {
        currentUser.requireAnalyst();
        if (!"PENDING".equals(status.trim().toUpperCase(Locale.ROOT))) {
            throw ApiException.badRequest("The status must be PENDING.");
        }
        return service.pending();
    }

    @PutMapping("/api/recommendations/{id}")
    @Operation(summary = "Approve, edit or withhold a recommendation")
    public RecommendationResponse decide(@PathVariable Long id,
                                         @Valid @RequestBody RecommendationDecisionRequest request) {
        currentUser.requireAnalyst();
        return service.decide(id, request);
    }
}
