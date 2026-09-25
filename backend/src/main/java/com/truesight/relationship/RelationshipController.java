package com.truesight.relationship;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The suppliers and customers found in a portfolio's annual reports. The work is done in {@link RelationshipService}. */
@RestController
@Tag(name = "Relationships")
public class RelationshipController {

    private final RelationshipService service;

    public RelationshipController(RelationshipService service) {
        this.service = service;
    }

    @GetMapping("/api/portfolios/{portfolioId}/relationships")
    @Operation(summary = "List the suppliers and customers found in a portfolio's annual reports")
    public List<RelationshipResponse> list(@PathVariable Long portfolioId) {
        return service.forPortfolio(portfolioId);
    }
}
