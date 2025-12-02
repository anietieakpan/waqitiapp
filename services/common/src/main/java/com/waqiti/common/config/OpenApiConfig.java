package com.waqiti.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import org.springdoc.core.models.GroupedOpenApi;

import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${openapi.server.url:http://localhost:8080}")
    private String serverUrl;

    @Value("${openapi.server.description:Default Server}")
    private String serverDescription;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API Documentation")
                        .description("API documentation for the " + applicationName + " service of Waqiti platform")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Waqiti Team")
                                .email("dev@example.com")
                                .url("https://api.example.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://api.example.com/license")))
                .servers(List.of(
                        new Server()
                                .url(serverUrl)
                                .description(serverDescription)
                ))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Authorization header using the Bearer scheme. Example: \"Authorization: Bearer {token}\""))
                )
                .tags(getTags());
    }

    private List<Tag> getTags() {
        return Arrays.asList(
                new Tag().name("Authentication").description("Authentication operations"),
                new Tag().name("Users").description("User management operations"),
                new Tag().name("Wallets").description("Wallet management operations"),
                new Tag().name("Payments").description("Payment operations"),
                new Tag().name("Transactions").description("Transaction operations"),
                new Tag().name("Notifications").description("Notification operations"),
                new Tag().name("Integration").description("External system integration operations")
        );
    }

    @Bean
    public OpenApiCustomizer sortSchemasAlphabetically() {
        return openApi -> {
            // Sort schemas alphabetically
            if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
                var schemas = openApi.getComponents().getSchemas();
                var sortedSchemas = schemas.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (oldValue, newValue) -> oldValue,
                                java.util.LinkedHashMap::new));
                openApi.getComponents().setSchemas(sortedSchemas);
            }

            // Sort paths alphabetically
            if (openApi.getPaths() != null) {
                var paths = openApi.getPaths();
                Map<String, io.swagger.v3.oas.models.PathItem> sortedPathMap = paths.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (oldValue, newValue) -> oldValue,
                                java.util.LinkedHashMap::new));

                // Create a new Paths object and add all sorted entries
                Paths sortedPaths = new Paths();
                sortedPathMap.forEach(sortedPaths::addPathItem);
                openApi.setPaths(sortedPaths);
            }
        };
    }
}