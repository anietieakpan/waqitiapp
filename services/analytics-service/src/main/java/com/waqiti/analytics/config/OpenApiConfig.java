package com.waqiti.analytics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration
 *
 * Provides comprehensive API documentation for analytics service.
 * Accessible at: /swagger-ui.html
 *
 * Features:
 * - OAuth2/JWT authentication configuration
 * - Request/response examples
 * - Error response documentation
 * - API versioning
 * - Rate limiting documentation
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:analytics-service}")
    private String applicationName;

    @Value("${server.port:8087}")
    private String serverPort;

    @Bean
    public OpenAPI analyticsServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Waqiti Analytics Service API")
                .version("1.0.0")
                .description("""
                    # Waqiti Analytics Service

                    Comprehensive analytics, reporting, and machine learning service for the Waqiti fintech platform.

                    ## Features

                    - **Transaction Analytics**: Real-time and historical transaction metrics
                    - **User Analytics**: User behavior, engagement, and lifetime value analysis
                    - **Merchant Analytics**: Merchant performance and revenue metrics
                    - **Fraud Detection**: ML-powered fraud detection and risk scoring
                    - **Predictive Analytics**: Revenue forecasting, churn prediction, trend analysis
                    - **Real-time Dashboards**: Live metrics and KPIs
                    - **Custom Reports**: Ad-hoc queries and scheduled reports

                    ## Authentication

                    All endpoints require OAuth2/JWT authentication via Keycloak.
                    Include the bearer token in the Authorization header:

                    ```
                    Authorization: Bearer {your_jwt_token}
                    ```

                    ## Rate Limiting

                    - Standard endpoints: 100 requests/minute per user
                    - Report generation: 10 requests/minute per user
                    - ML predictions: 50 requests/minute per user

                    ## Error Responses

                    All error responses follow this format:

                    ```json
                    {
                      "timestamp": "2025-11-10T14:30:00Z",
                      "status": 400,
                      "error": "Bad Request",
                      "message": "Invalid date range",
                      "path": "/api/v1/analytics/transactions"
                    }
                    ```

                    ## Support

                    - Email: analytics-support@example.com
                    - Slack: #analytics-api-support
                    """)
                .contact(new Contact()
                    .name("Waqiti Platform Team")
                    .email("platform-team@example.com")
                    .url("https://api.example.com/support"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://api.example.com/license")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Local Development"),
                new Server()
                    .url("https://api.example.com")
                    .description("Development Environment"),
                new Server()
                    .url("https://api.example.com")
                    .description("Staging Environment"),
                new Server()
                    .url("https://api.example.com")
                    .description("Production Environment")
            ))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT token obtained from Keycloak authentication"))
                .addSecuritySchemes("oauth2", new SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .description("OAuth2 authentication via Keycloak")
                    .flows(new io.swagger.v3.oas.models.security.OAuthFlows()
                        .authorizationCode(new io.swagger.v3.oas.models.security.OAuthFlow()
                            .authorizationUrl("https://api.example.com/realms/waqiti-fintech/protocol/openid-connect/auth")
                            .tokenUrl("https://api.example.com/realms/waqiti-fintech/protocol/openid-connect/token")
                            .scopes(new io.swagger.v3.oas.models.security.Scopes()
                                .addString("analytics:dashboard-view", "View analytics dashboards")
                                .addString("analytics:transaction-view", "View transaction analytics")
                                .addString("analytics:user-metrics", "Access user metrics")
                                .addString("analytics:ml-predict", "Execute ML predictions")
                                .addString("analytics:report-generate", "Generate reports"))))))
            .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
            .addSecurityItem(new SecurityRequirement().addList("oauth2"));
    }
}
