// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class SwaggerConfig {
        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                                .info(new Info().title("EmergencyConnectUAE API")
                                                .version("v1.0")
                                                .description("CSC408 Distributed Information Systems"))
                                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                                .components(new Components().addSecuritySchemes("bearerAuth",
                                                new SecurityScheme().name("bearerAuth")
                                                                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                                                                .bearerFormat("JWT")));
        }

        /**
         * Adds the project-wide error responses (401/403/409/422/429 and a generic
         * 400) to every operation so the Swagger page documents the full status-code
         * surface required by the SRS without repeating @ApiResponse on each handler.
         * Per-endpoint success codes (200/201) stay on the individual @Operation.
         */
        @Bean
        public OpenApiCustomizer globalErrorResponses() {
                Map<String, String> common = Map.of(
                                "400", "Validation error or malformed request body",
                                "401", "Missing, invalid, expired, or revoked JWT",
                                "403", "Authenticated but not authorized (RBAC denial or MFA required)",
                                "409", "Conflict — resource is locked or already assigned",
                                "422", "Unprocessable entity — illegal state transition",
                                "429", "Rate limit exceeded — retry after the Retry-After header");
                return openApi -> openApi.getPaths().values().forEach(pathItem ->
                                pathItem.readOperations().forEach(op -> {
                                        ApiResponses responses = op.getResponses();
                                        common.forEach((code, desc) -> {
                                                if (responses.get(code) == null) {
                                                        responses.addApiResponse(code,
                                                                        new ApiResponse().description(desc));
                                                }
                                        });
                                }));
        }
}
