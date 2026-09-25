package com.truesight.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/health answers "is the backend running?". The website calls it on the home page, and Google Cloud can
 * call it to check the live site is up.
 *
 * <p>This is the example every other endpoint copies:
 * <ul>
 *   <li>a class ending in {@code Controller}, in the package of the feature it belongs to;</li>
 *   <li>its address under {@code /api/};</li>
 *   <li>{@code @Operation} with a one-line summary, which is what the Swagger page shows;</li>
 *   <li>a small record as the answer, never a database entity;</li>
 *   <li>a test next to it: {@code HealthControllerTest} for the endpoint alone, {@code HealthIT} for the whole app.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health")
public class HealthController {

    /** What GET /api/health answers. */
    public record HealthResponse(String status) {
    }

    @GetMapping
    @Operation(summary = "Check the backend is running")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}
