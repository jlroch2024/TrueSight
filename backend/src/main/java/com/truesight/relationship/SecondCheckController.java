package com.truesight.relationship;

import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Starts the analyst-only pass that gives every relationship without one a second check. */
@RestController
@Tag(name = "Second Check")
public class SecondCheckController {

    private final CurrentUser currentUser;
    private final SecondCheckPassService pass;
    private final SecondCheckJob job;

    public SecondCheckController(CurrentUser currentUser, SecondCheckPassService pass, SecondCheckJob job) {
        this.currentUser = currentUser;
        this.pass = pass;
        this.job = job;
    }

    @PostMapping("/api/relationships/second-check")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Have a second AI check every relationship that has not been checked yet")
    public void start() {
        currentUser.requireAnalyst();
        var pending = pass.pendingReportIds();
        if (!pending.isEmpty()) job.run(pending);
    }
}
