package com.truesight.relationship;

import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Starts the analyst-only pass that assesses criticality in reports already stored by TrueSight. */
@RestController
@Tag(name = "Criticality")
public class CriticalityController {

    private final CurrentUser currentUser;
    private final CriticalityPassService pass;
    private final CriticalityJob job;

    public CriticalityController(CurrentUser currentUser, CriticalityPassService pass, CriticalityJob job) {
        this.currentUser = currentUser;
        this.pass = pass;
        this.job = job;
    }

    @PostMapping("/api/reports/criticality")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Read stored annual reports again for supplier criticality")
    public void start() {
        currentUser.requireAnalyst();
        var pending = pass.pendingReportIds();
        if (!pending.isEmpty()) job.run(pending);
    }
}
