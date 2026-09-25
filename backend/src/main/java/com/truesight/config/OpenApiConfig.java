package com.truesight.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The title and description at the top of the Swagger page (/swagger-ui.html). The endpoints themselves are listed
 * automatically: every @RestController appears there with its @Operation summaries.
 *
 * <p>It also adds the <b>Authorize</b> button: log in with {@code POST /api/auth/login}, paste the token it returns,
 * and every protected endpoint can then be tried from the page.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "Log-in token";

    @Bean
    OpenAPI trueSightApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TrueSight API")
                        .version("0.1.0")
                        .description("Supply-chain risk for portfolio managers, from companies' own annual reports."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
