package com.truesight.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/settings gives the website the settings it needs in the browser, so none of them is written into the
 * website's code.
 *
 * <p>Today that is only the Logo.dev token, which the supply chain graph adds to each logo's address. It is Logo.dev's
 * "publishable" kind of key, made to be seen by browsers, but it still comes from {@code LOGO_DEV_TOKEN} rather than
 * the code, and only a logged-in user can ask for it. When it is not set, the answer is an empty token, and the graph
 * shows each company's first letter instead of its logo.
 */
@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings")
public class SettingsController {

    /** What GET /api/settings answers. {@code logoToken} is empty when no Logo.dev token is set. */
    public record SettingsResponse(String logoToken) {
    }

    private final String logoToken;

    public SettingsController(@Value("${truesight.logo-dev.token:}") String logoToken) {
        this.logoToken = logoToken == null ? "" : logoToken.trim();
    }

    @GetMapping
    @Operation(summary = "Get the settings the website needs, such as the Logo.dev token")
    public SettingsResponse settings() {
        return new SettingsResponse(logoToken);
    }
}
