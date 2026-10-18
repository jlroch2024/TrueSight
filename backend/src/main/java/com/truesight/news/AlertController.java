package com.truesight.news;

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

/** Reads and changes the logged-in user's alerts for one portfolio. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/alerts")
@Tag(name = "Alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List alerts affecting a portfolio")
    public AlertsResponse list(@PathVariable Long portfolioId,
                               @RequestParam(defaultValue = "false") boolean includeDismissed) {
        return service.list(portfolioId, includeDismissed);
    }

    @PutMapping("/{alertId}")
    @Operation(summary = "Change an alert's status")
    public AlertResponse setStatus(@PathVariable Long portfolioId, @PathVariable Long alertId,
                                   @Valid @RequestBody AlertStatusRequest request) {
        return service.setStatus(portfolioId, alertId, request.status());
    }
}
