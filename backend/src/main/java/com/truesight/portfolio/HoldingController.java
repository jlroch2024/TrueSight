package com.truesight.portfolio;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.truesight.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * A portfolio's holdings: list them, replace them all from a CSV, or add, re-weight or remove one. The work is done in
 * {@link HoldingService}.
 */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/holdings")
@Tag(name = "Holdings")
public class HoldingController {

    private final HoldingService service;
    private final AnalysisService analysis;
    private final AnalysisJob job;

    public HoldingController(HoldingService service, AnalysisService analysis, AnalysisJob job) {
        this.service = service;
        this.analysis = analysis;
        this.job = job;
    }

    @GetMapping
    @Operation(summary = "List a portfolio's holdings")
    public List<HoldingResponse> list(@PathVariable Long portfolioId) {
        return service.list(portfolioId);
    }

    /** Answers 200, not 201: the holdings are replaced, and the answer is the portfolio's new list. */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a CSV, replacing the portfolio's holdings")
    public List<HoldingResponse> upload(@PathVariable Long portfolioId, @RequestParam(name = "file", required = false) MultipartFile file) {
        return service.replace(portfolioId, file);
    }

    /** The holding comes back Waiting; its analysis starts at once, in the background. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add one holding to a portfolio, and start analysing it")
    public HoldingResponse add(@PathVariable Long portfolioId, @Valid @RequestBody AddHoldingRequest request) {
        HoldingResponse added = service.add(portfolioId, request);
        startAnalysis(portfolioId);
        return added;
    }

    @PutMapping("/{holdingId}")
    @Operation(summary = "Change one holding's weight")
    public HoldingResponse changeWeight(@PathVariable Long portfolioId, @PathVariable Long holdingId,
                                        @Valid @RequestBody ChangeWeightRequest request) {
        return service.changeWeight(portfolioId, holdingId, request);
    }

    @DeleteMapping("/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove one holding from a portfolio, and from its alerts")
    public void remove(@PathVariable Long portfolioId, @PathVariable Long holdingId) {
        service.remove(portfolioId, holdingId);
    }

    /**
     * Starts the portfolio's analysis, as Analyse does. Called after the holding is saved, so the background job can
     * find it. An analysis that is already running is not an error here: the new holding waits for the next Analyse.
     */
    private void startAnalysis(Long portfolioId) {
        try {
            List<Long> queued = analysis.start(portfolioId);
            if (!queued.isEmpty()) {
                job.run(queued);
            }
        } catch (ApiException exception) {
            if (exception.status() != HttpStatus.CONFLICT) {
                throw exception;
            }
        }
    }
}
