package com.waqiti.expense.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 (Swagger) configuration for API documentation
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8055}")
    private String serverPort;

    @Bean
    public OpenAPI expenseServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Expense Service API")
                        .description("Comprehensive expense tracking and budget management API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Waqiti Platform Team")
                                .email("support@example.com")
                                .url("https://example.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://example.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server"),
                        new Server()
                                .url("https://api.example.com")
                                .description("Production server"),
                        new Server()
                                .url("https://api-staging.example.com")
                                .description("Staging server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT authentication via Keycloak")));
    }
}
