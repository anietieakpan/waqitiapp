package com.waqiti.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API Versioning Filter
 * Handles version routing based on Accept header, URL path, or custom header
 */
@Slf4j
@Component("apiVersioningFilter")
public class ApiVersioningFilter extends AbstractGatewayFilterFactory<ApiVersioningFilter.Config> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("/api/v(\\d+)/");
    private static final String DEFAULT_VERSION = "1";
    private static final String API_VERSION_HEADER = "X-API-Version";
    private static final String ACCEPT_VERSION_HEADER = "Accept-Version";
    
    // Version deprecation map
    private final Map<String, VersionStatus> versionStatusMap = new ConcurrentHashMap<>();

    public ApiVersioningFilter() {
        super(Config.class);
        initializeVersionStatus();
    }

    private void initializeVersionStatus() {
        versionStatusMap.put("1", new VersionStatus(true, false, null));
        versionStatusMap.put("2", new VersionStatus(true, false, null));
        versionStatusMap.put("3", new VersionStatus(true, false, null));
        // Version 0 is deprecated
        versionStatusMap.put("0", new VersionStatus(false, true, "Version 0 is deprecated. Please upgrade to version 1 or higher."));
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // Extract API version from various sources
            String version = extractApiVersion(request);
            
            // Validate version
            VersionStatus status = versionStatusMap.get(version);
            if (status == null || !status.isActive()) {
                return handleInvalidVersion(exchange, version, status);
            }
            
            // Add deprecation warning if applicable
            if (status.isDeprecated()) {
                ServerHttpResponse response = exchange.getResponse();
                response.getHeaders().add("X-API-Deprecation-Warning", status.getDeprecationMessage());
                response.getHeaders().add("Sunset", status.getSunsetDate());
            }
            
            // Modify the request path if needed
            ServerHttpRequest modifiedRequest = modifyRequestForVersion(request, version);
            
            // Add version headers for downstream services
            modifiedRequest = modifiedRequest.mutate()
                .header("X-API-Version-Used", version)
                .header("X-API-Versions-Supported", String.join(",", getActiveVersions()))
                .build();
            
            // Log version usage for analytics
            logVersionUsage(version, request.getPath().value());
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    private String extractApiVersion(ServerHttpRequest request) {
        // 1. Check URL path for version
        String path = request.getPath().value();
        Matcher matcher = VERSION_PATTERN.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // 2. Check custom API version header
        String apiVersionHeader = request.getHeaders().getFirst(API_VERSION_HEADER);
        if (apiVersionHeader != null && !apiVersionHeader.isEmpty()) {
            return apiVersionHeader;
        }
        
        // 3. Check Accept-Version header
        String acceptVersionHeader = request.getHeaders().getFirst(ACCEPT_VERSION_HEADER);
        if (acceptVersionHeader != null && !acceptVersionHeader.isEmpty()) {
            return acceptVersionHeader;
        }
        
        // 4. Check Accept header for version
        String acceptHeader = request.getHeaders().getFirst(HttpHeaders.ACCEPT);
        if (acceptHeader != null) {
            // Parse Accept header like "application/vnd.waqiti.v2+json"
            Pattern acceptPattern = Pattern.compile("application/vnd\\.waqiti\\.v(\\d+)\\+json");
            Matcher acceptMatcher = acceptPattern.matcher(acceptHeader);
            if (acceptMatcher.find()) {
                return acceptMatcher.group(1);
            }
        }
        
        // 5. Default version
        return DEFAULT_VERSION;
    }

    private ServerHttpRequest modifyRequestForVersion(ServerHttpRequest request, String version) {
        String path = request.getPath().value();
        
        // If version is not in the path, add it
        if (!path.matches("/api/v\\d+/.*")) {
            String newPath = "/api/v" + version + path.replaceFirst("/api", "");
            
            URI newUri = URI.create(newPath);
            if (request.getURI().getQuery() != null) {
                newUri = URI.create(newPath + "?" + request.getURI().getQuery());
            }
            
            return request.mutate().uri(newUri).build();
        }
        
        // If version is in the path but different, update it
        String newPath = path.replaceFirst("/api/v\\d+/", "/api/v" + version + "/");
        if (!newPath.equals(path)) {
            URI newUri = URI.create(newPath);
            if (request.getURI().getQuery() != null) {
                newUri = URI.create(newPath + "?" + request.getURI().getQuery());
            }
            return request.mutate().uri(newUri).build();
        }
        
        return request;
    }

    private Mono<Void> handleInvalidVersion(ServerWebExchange exchange, String version, VersionStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String message;
        if (status == null) {
            message = String.format("{\"error\": \"Invalid API version: %s\", \"supported_versions\": %s}", 
                version, getActiveVersions());
        } else {
            message = String.format("{\"error\": \"%s\", \"supported_versions\": %s}", 
                status.getDeprecationMessage(), getActiveVersions());
        }
        
        return response.writeWith(Mono.just(response.bufferFactory().wrap(message.getBytes())));
    }

    private String[] getActiveVersions() {
        return versionStatusMap.entrySet().stream()
            .filter(entry -> entry.getValue().isActive())
            .map(Map.Entry::getKey)
            .sorted()
            .toArray(String[]::new);
    }

    private void logVersionUsage(String version, String path) {
        log.debug("API version {} used for path: {}", version, path);
        // In production, this would send metrics to monitoring system
    }

    public static class Config {
        private boolean enforceVersioning = true;
        private String defaultVersion = DEFAULT_VERSION;

        public boolean isEnforceVersioning() {
            return enforceVersioning;
        }

        public void setEnforceVersioning(boolean enforceVersioning) {
            this.enforceVersioning = enforceVersioning;
        }

        public String getDefaultVersion() {
            return defaultVersion;
        }

        public void setDefaultVersion(String defaultVersion) {
            this.defaultVersion = defaultVersion;
        }
    }

    private static class VersionStatus {
        private final boolean active;
        private final boolean deprecated;
        private final String deprecationMessage;
        private final String sunsetDate;

        public VersionStatus(boolean active, boolean deprecated, String deprecationMessage) {
            this.active = active;
            this.deprecated = deprecated;
            this.deprecationMessage = deprecationMessage;
            this.sunsetDate = deprecated ? "2024-12-31T23:59:59Z" : null;
        }

        public boolean isActive() {
            return active;
        }

        public boolean isDeprecated() {
            return deprecated;
        }

        public String getDeprecationMessage() {
            return deprecationMessage;
        }

        public String getSunsetDate() {
            return sunsetDate;
        }
    }
}