package com.waqiti.apigateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Input validation filter to prevent injection attacks
 */
@Slf4j
@Component
public class InputValidationFilter extends AbstractGatewayFilterFactory<InputValidationFilter.Config> {
    
    // Common injection patterns
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
        // SQL Injection patterns
        Pattern.compile("(?i)(union.*select|select.*from|insert.*into|delete.*from|update.*set|drop.*table)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(exec(ute)?|xp_cmdshell|sp_executesql)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(script.*>|<.*script|javascript:|vbscript:|onload=|onerror=|onclick=)", Pattern.CASE_INSENSITIVE),
        
        // XSS patterns
        Pattern.compile("<(script|iframe|object|embed|form|input|button)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(javascript:|data:text/html|vbscript:|file://|jar:)", Pattern.CASE_INSENSITIVE),
        
        // Command injection patterns
        Pattern.compile("[;&|`]\\s*(cat|ls|pwd|whoami|id|uname|ps|netstat|ifconfig|curl|wget)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\$\\(.*\\)|`.*`", Pattern.CASE_INSENSITIVE),
        
        // Path traversal patterns
        Pattern.compile("(\\.\\./|\\.\\.\\\\|%2e%2e%2f|%252e%252e%252f)", Pattern.CASE_INSENSITIVE),
        
        // LDAP injection patterns
        Pattern.compile("[()&|!<>=~*]", Pattern.CASE_INSENSITIVE),
        
        // NoSQL injection patterns
        Pattern.compile("\\$\\{|\\$ne|\\$gt|\\$lt|\\$gte|\\$lte|\\$in|\\$nin", Pattern.CASE_INSENSITIVE)
    );
    
    // Maximum allowed sizes
    private static final int MAX_URI_LENGTH = 2048;
    private static final int MAX_HEADER_SIZE = 8192;
    private static final int MAX_BODY_SIZE = 1048576; // 1MB
    
    public InputValidationFilter() {
        super(Config.class);
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // Validate URI
            if (!validateUri(request)) {
                return handleValidationFailure(exchange, "Invalid URI detected");
            }
            
            // Validate headers
            if (!validateHeaders(request)) {
                return handleValidationFailure(exchange, "Invalid headers detected");
            }
            
            // Validate query parameters
            if (!validateQueryParams(request)) {
                return handleValidationFailure(exchange, "Invalid query parameters detected");
            }
            
            // For POST/PUT/PATCH requests, validate body
            if (shouldValidateBody(request)) {
                return validateAndModifyBody(exchange, chain, config);
            }
            
            return chain.filter(exchange);
        };
    }
    
    private boolean validateUri(ServerHttpRequest request) {
        String uri = request.getURI().toString();
        
        // Check URI length
        if (uri.length() > MAX_URI_LENGTH) {
            log.warn("URI too long: {} characters", uri.length());
            return false;
        }
        
        // Check for injection patterns
        return !containsInjectionPattern(uri);
    }
    
    private boolean validateHeaders(ServerHttpRequest request) {
        for (String headerName : request.getHeaders().keySet()) {
            List<String> headerValues = request.getHeaders().get(headerName);
            
            // Check header size
            int totalSize = headerValues.stream()
                .mapToInt(String::length)
                .sum();
            
            if (totalSize > MAX_HEADER_SIZE) {
                log.warn("Header {} too large: {} bytes", headerName, totalSize);
                return false;
            }
            
            // Check for injection patterns in header values
            for (String value : headerValues) {
                if (containsInjectionPattern(value)) {
                    log.warn("Injection pattern detected in header {}", headerName);
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private boolean validateQueryParams(ServerHttpRequest request) {
        for (String paramName : request.getQueryParams().keySet()) {
            List<String> paramValues = request.getQueryParams().get(paramName);
            
            // Check for injection patterns in parameter names
            if (containsInjectionPattern(paramName)) {
                log.warn("Injection pattern detected in parameter name: {}", paramName);
                return false;
            }
            
            // Check for injection patterns in parameter values
            for (String value : paramValues) {
                if (containsInjectionPattern(value)) {
                    log.warn("Injection pattern detected in parameter value for: {}", paramName);
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private boolean shouldValidateBody(ServerHttpRequest request) {
        String method = request.getMethod().name();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }
    
    private Mono<Void> validateAndModifyBody(ServerWebExchange exchange, 
                                            GatewayFilterChain chain, 
                                            Config config) {
        ServerHttpRequest request = exchange.getRequest();
        
        return DataBufferUtils.join(request.getBody())
            .flatMap(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                
                String body = new String(bytes, StandardCharsets.UTF_8);
                
                // Check body size
                if (bytes.length > MAX_BODY_SIZE) {
                    log.warn("Request body too large: {} bytes", bytes.length);
                    return handleValidationFailure(exchange, "Request body too large");
                }
                
                // Validate body content
                if (containsInjectionPattern(body)) {
                    log.warn("Injection pattern detected in request body");
                    return handleValidationFailure(exchange, "Invalid request body");
                }
                
                // Sanitize body if enabled
                final byte[] finalBytes;
                if (config.isSanitizeEnabled()) {
                    body = sanitizeInput(body);
                    finalBytes = body.getBytes(StandardCharsets.UTF_8);
                } else {
                    finalBytes = bytes;
                }
                
                // Create new request with validated body
                ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(request) {
                    @Override
                    public Flux<DataBuffer> getBody() {
                        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(finalBytes);
                        return Flux.just(buffer);
                    }
                };
                
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            })
            .switchIfEmpty(chain.filter(exchange));
    }
    
    private boolean containsInjectionPattern(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    private String sanitizeInput(String input) {
        // Basic sanitization - can be extended based on requirements
        return input
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#x27;")
            .replaceAll("/", "&#x2F;");
    }
    
    private Mono<Void> handleValidationFailure(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String response = String.format("{\"error\": \"Validation failed\", \"message\": \"%s\"}", message);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(response.getBytes());
        
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
    
    public static class Config {
        private boolean sanitizeEnabled = false;
        private List<String> excludedPaths = Arrays.asList("/health", "/metrics");
        
        public boolean isSanitizeEnabled() { return sanitizeEnabled; }
        public void setSanitizeEnabled(boolean sanitizeEnabled) { this.sanitizeEnabled = sanitizeEnabled; }
        
        public List<String> getExcludedPaths() { return excludedPaths; }
        public void setExcludedPaths(List<String> excludedPaths) { this.excludedPaths = excludedPaths; }
    }
}