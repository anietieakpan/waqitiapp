package com.waqiti.customer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI Configuration for Customer Service.
 * Configures SpringDoc OpenAPI documentation with security schemes,
 * API grouping, and comprehensive metadata.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@Slf4j
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port:8086}")
    private String serverPort;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080/realms/waqiti}")
    private String issuerUri;

    private static final String BEARER_SCHEME = "bearer";
    private static final String JWT_BEARER_FORMAT = "JWT";
    private static final String OAUTH2_SCHEME = "oauth2";

    /**
     * Configures the main OpenAPI documentation.
     * Includes API metadata, servers, and security schemes.
     *
     * @return Configured OpenAPI instance
     */
    @Bean
    public OpenAPI customerServiceOpenAPI() {
        OpenAPI openAPI = new OpenAPI()
            .info(apiInfo())
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort + contextPath)
                    .description("Local Development Server"),
                new Server()
                    .url("https://api-dev.example.com/customer")
                    .description("Development Server"),
                new Server()
                    .url("https://api.example.com/customer")
                    .description("Production Server")
            ))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt", bearerSecurityScheme())
                .addSecuritySchemes("oauth2", oauth2SecurityScheme())
            )
            .addSecurityItem(new SecurityRequirement()
                .addList("bearer-jwt")
                .addList("oauth2")
            );

        log.info("OpenAPI configuration initialized for {}", applicationName);
        return openAPI;
    }

    /**
     * Configures API information metadata.
     *
     * @return API Info
     */
    private Info apiInfo() {
        return new Info()
            .title("Waqiti Customer Service API")
            .description("""
                Customer Relationship Management and Support Service API.

                This service manages:
                - Customer profiles and lifecycle management
                - Account closure requests and processing
                - Customer complaints and CFPB submissions
                - Customer analytics and churn prediction
                - Customer support interactions
                - Retention campaigns and winback strategies

                **Authentication:** OAuth2 Bearer JWT tokens from Keycloak.

                **Rate Limiting:** 100 requests per minute per user.
                """)
            .version("1.0.0")
            .contact(new Contact()
                .name("Waqiti Engineering Team")
                .email("engineering@example.com")
                .url("https://example.com")
            )
            .license(new License()
                .name("Proprietary")
                .url("https://example.com/license")
            );
    }

    /**
     * Configures Bearer JWT security scheme.
     *
     * @return SecurityScheme for Bearer JWT
     */
    private SecurityScheme bearerSecurityScheme() {
        return new SecurityScheme()
            .name("bearer-jwt")
            .type(SecurityScheme.Type.HTTP)
            .scheme(BEARER_SCHEME)
            .bearerFormat(JWT_BEARER_FORMAT)
            .in(SecurityScheme.In.HEADER)
            .description("JWT Bearer token authentication via Keycloak");
    }

    /**
     * Configures OAuth2 security scheme.
     *
     * @return SecurityScheme for OAuth2
     */
    private SecurityScheme oauth2SecurityScheme() {
        return new SecurityScheme()
            .name(OAUTH2_SCHEME)
            .type(SecurityScheme.Type.OAUTH2)
            .flows(new OAuthFlows()
                .authorizationCode(new OAuthFlow()
                    .authorizationUrl(issuerUri + "/protocol/openid-connect/auth")
                    .tokenUrl(issuerUri + "/protocol/openid-connect/token")
                    .refreshUrl(issuerUri + "/protocol/openid-connect/token")
                    .scopes(new Scopes()
                        .addString("openid", "OpenID Connect scope")
                        .addString("profile", "User profile information")
                        .addString("email", "User email address")
                        .addString("customer:read", "Read customer information")
                        .addString("customer:write", "Create and update customers")
                        .addString("complaint:read", "Read complaints")
                        .addString("complaint:write", "Create and update complaints")
                        .addString("analytics:read", "Read customer analytics")
                    )
                )
            );
    }

    /**
     * Groups public customer APIs.
     *
     * @return GroupedOpenApi for public APIs
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .displayName("Public Customer APIs")
            .pathsToMatch(
                "/v1/customers/**",
                "/v1/complaints/**",
                "/v1/support/**"
            )
            .pathsToExclude("/v1/**/internal/**", "/v1/**/admin/**")
            .build();
    }

    /**
     * Groups internal service-to-service APIs.
     *
     * @return GroupedOpenApi for internal APIs
     */
    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
            .group("internal")
            .displayName("Internal APIs")
            .pathsToMatch("/v1/**/internal/**")
            .build();
    }

    /**
     * Groups administrative APIs.
     *
     * @return GroupedOpenApi for admin APIs
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .displayName("Admin APIs")
            .pathsToMatch(
                "/v1/**/admin/**",
                "/v1/analytics/**",
                "/v1/reports/**"
            )
            .build();
    }

    /**
     * Groups lifecycle management APIs.
     *
     * @return GroupedOpenApi for lifecycle APIs
     */
    @Bean
    public GroupedOpenApi lifecycleApi() {
        return GroupedOpenApi.builder()
            .group("lifecycle")
            .displayName("Customer Lifecycle APIs")
            .pathsToMatch(
                "/v1/lifecycle/**",
                "/v1/churn/**",
                "/v1/retention/**"
            )
            .build();
    }

    /**
     * Groups account closure APIs.
     *
     * @return GroupedOpenApi for account closure APIs
     */
    @Bean
    public GroupedOpenApi accountClosureApi() {
        return GroupedOpenApi.builder()
            .group("account-closure")
            .displayName("Account Closure APIs")
            .pathsToMatch("/v1/account-closure/**")
            .build();
    }
}
