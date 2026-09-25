package com.truesight.portfolio;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The logged-in user's portfolios: list, create, rename and delete. The work is done in {@link PortfolioService}. */
@RestController
@RequestMapping("/api/portfolios")
@Tag(name = "Portfolios")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List my portfolios")
    public List<PortfolioResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a portfolio")
    public PortfolioResponse create(@Valid @RequestBody PortfolioRequest request) {
        return service.create(request.name());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a portfolio")
    public PortfolioResponse rename(@PathVariable Long id, @Valid @RequestBody PortfolioRequest request) {
        return service.rename(id, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a portfolio and its holdings")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
