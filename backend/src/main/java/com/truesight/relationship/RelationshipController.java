package com.truesight.relationship;

import com.truesight.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The suppliers and customers found in a portfolio's annual reports. The work is done in {@link RelationshipService}. */
@RestController
@Tag(name = "Relationships")
public class RelationshipController {

    private final RelationshipService service;
    private final RelationshipEditService edits;
    private final CurrentUser currentUser;

    public RelationshipController(RelationshipService service, RelationshipEditService edits, CurrentUser currentUser) {
        this.service = service;
        this.edits = edits;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/portfolios/{portfolioId}/relationships")
    @Operation(summary = "List the suppliers and customers found in a portfolio's annual reports")
    public List<RelationshipResponse> list(@PathVariable Long portfolioId) {
        return service.forPortfolio(portfolioId);
    }

    @PutMapping("/api/relationships/{id}")
    @Operation(summary = "Correct what a relationship provides")
    public ReviewItemResponse edit(@PathVariable Long id, @Valid @RequestBody EditRelationshipRequest request) {
        currentUser.requireAnalyst();
        return edits.edit(id, request);
    }

    @PostMapping("/api/relationships")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a relationship found in an annual report")
    public ReviewItemResponse add(@Valid @RequestBody AddRelationshipRequest request) {
        currentUser.requireAnalyst();
        return edits.add(request);
    }

    @GetMapping(value = "/api/companies", params = "withReport=true")
    @Operation(summary = "List companies with a stored annual report")
    public List<CompanyOption> companiesWithReport() {
        currentUser.requireAnalyst();
        return edits.companiesWithReport();
    }
}
