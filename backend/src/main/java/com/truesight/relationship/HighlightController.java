package com.truesight.relationship;

import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Starts the analyst-only pass that reads every quote not yet read for highlights. */
@RestController
@Tag(name = "Highlights")
public class HighlightController {

    private final CurrentUser currentUser;
    private final HighlightJob job;

    public HighlightController(CurrentUser currentUser, HighlightJob job) {
        this.currentUser = currentUser;
        this.job = job;
    }

    @PostMapping("/api/relationships/highlights")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Highlight what matters in every quote not yet read")
    public void start() {
        currentUser.requireAnalyst();
        job.run();
    }
}
