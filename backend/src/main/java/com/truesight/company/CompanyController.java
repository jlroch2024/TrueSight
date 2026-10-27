package com.truesight.company;

import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Companies: the analyst-only one-off pass that names the companies found in reports the way people know them. */
@RestController
@Tag(name = "Companies")
public class CompanyController {

    private final CurrentUser currentUser;
    private final CommonNameJob job;

    public CompanyController(CurrentUser currentUser, CommonNameJob job) {
        this.currentUser = currentUser;
        this.job = job;
    }

    @PostMapping("/api/companies/common-names")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Give the companies found in reports their common names, and join duplicates")
    public void start() {
        currentUser.requireAnalyst();
        job.run();
    }
}
