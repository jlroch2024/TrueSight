package com.truesight.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The title and description at the top of the Swagger page (/swagger-ui.html). The endpoints themselves are listed
 * automatically: every @RestController appears there with its @Operation summaries.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI trueSightApi() {
        return new OpenAPI().info(new Info()
                .title("TrueSight API")
                .version("0.1.0")
                .description("Supply-chain risk for portfolio managers, from companies' own annual reports."));
    }
}
