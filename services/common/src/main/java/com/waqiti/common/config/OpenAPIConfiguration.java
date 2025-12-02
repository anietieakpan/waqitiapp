package com.waqiti.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * PRODUCTION-GRADE OPENAPI/SWAGGER CONFIGURATION
 *
 * Provides comprehensive API documentation for all Waqiti services.
 *
 * FEATURES:
 * - OpenAPI 3.0 specification
 * - OAuth2/JWT security schemes
 * - Multiple server environments
 * - Comprehensive metadata
 * - API versioning support
 *
 * ENDPOINTS:
 * - API Docs JSON: /v3/api-docs
 * - Swagger UI: /swagger-ui.html (disabled in production)
 *
 * @author Waqiti Platform Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@Configuration
public class OpenAPIConfiguration {

    @Value("${waqiti.api.docs.title:Waqiti Platform API}")
    private String apiTitle;

    @Value("${waqiti.api.docs.description:Production-Ready Fintech Platform}")
    private String apiDescription;

    @Value("${waqiti.api.docs.version:3.0.0}")
    private String apiVersion;

    @Value("${waqiti.api.docs.contact.name:Waqiti Platform Team}")
    private String contactName;

    @Value("${waqiti.api.docs.contact.email:platform@example.com}")
    private String contactEmail;

    @Value("${waqiti.api.docs.contact.url:https://api.example.com}")
    private String contactUrl;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String authServerUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .servers(apiServers())
            .components(securityComponents())
            .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }

    /**
     * API Metadata
     */
    private Info apiInfo() {
        return new Info()
            .title(apiTitle)
            .description(apiDescription)
            .version(apiVersion)
            .contact(new Contact()
                .name(contactName)
                .email(contactEmail)
                .url(contactUrl))
            .license(new License()
                .name("Proprietary")
                .url("https://api.example.com/license"))
            .termsOfService("https://api.example.com/terms");
    }

    /**
     * Server Configuration
     */
    private List<Server> apiServers() {
        return List.of(
            new Server()
                .url("https://api.example.com")
                .description("Production Server"),
            new Server()
                .url("https://api.example.com")
                .description("Staging Server"),
            new Server()
                .url("http://localhost:8080")
                .description("Local Development Server")
        );
    }

    /**
     * Security Schemes
     */
    private Components securityComponents() {
        return new Components()
            .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                .name("bearer-jwt")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .description("JWT Bearer Token Authentication"));
    }
}
