package com.truesight.relationship;

import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Starts the analyst-only passes that give relationships a second check. */
@RestController
@Tag(name = "Second Check")
public class SecondCheckController {

    private final CurrentUser currentUser;
    private final SecondCheckPassService pass;
    private final SecondCheckSaver saver;
    private final SecondCheckJob job;

    public SecondCheckController(CurrentUser currentUser, SecondCheckPassService pass, SecondCheckSaver saver,
                                 SecondCheckJob job) {
        this.currentUser = currentUser;
        this.pass = pass;
        this.saver = saver;
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

    @PostMapping("/api/relationships/second-check/again")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Have a second AI judge again every relationship no analyst has decided, which costs AI calls")
    public void startAgain() {
        currentUser.requireAnalyst();
        saver.forgetUnreviewed();
        start();
    }
}
