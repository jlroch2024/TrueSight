package com.truesight.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Dependency figures: how much a company depends on each customer or supplier its annual report tags. */
@RestController
@Tag(name = "Dependency Figures")
public class ConcentrationController {

    private final ConcentrationService service;

    public ConcentrationController(ConcentrationService service) {
        this.service = service;
    }

    @GetMapping("/api/companies/{companyId}/concentrations")
    @Operation(summary = "List a company's dependency figures from its annual reports")
    public List<ConcentrationResponse> list(@PathVariable Long companyId) {
        return service.forCompany(companyId);
    }
}
