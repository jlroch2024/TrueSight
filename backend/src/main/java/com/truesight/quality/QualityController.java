package com.truesight.quality;

import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** The AI Quality Dashboard: the AI's track record, for analysts. */
@RestController
@Tag(name = "AI Quality")
public class QualityController {

    private final QualityService service;
    private final CurrentUser currentUser;

    public QualityController(QualityService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/quality")
    @Operation(summary = "Show how often analysts agreed with each AI step, week by week for the last 8 weeks")
    public QualityResponse quality() {
        currentUser.requireAnalyst();
        return service.report(Instant.now());
    }
}
