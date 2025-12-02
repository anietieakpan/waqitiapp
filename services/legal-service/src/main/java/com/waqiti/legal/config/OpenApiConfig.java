package com.waqiti.legal.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration
 *
 * Provides API documentation accessible at:
 * - Swagger UI: http://localhost:8090/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8090/v3/api-docs
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-11-09
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI legalServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Legal Service API")
                        .description("Comprehensive legal document management, compliance tracking, " +
                                "bankruptcy processing, and subpoena handling service")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Waqiti Legal Team")
                                .email("legal@example.com")
                                .url("https://example.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://example.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8090")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api-staging.example.com")
                                .description("Staging Environment"),
                        new Server()
                                .url("https://api.example.com")
                                .description("Production Environment")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT authentication token")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearer-jwt"));
    }
}
