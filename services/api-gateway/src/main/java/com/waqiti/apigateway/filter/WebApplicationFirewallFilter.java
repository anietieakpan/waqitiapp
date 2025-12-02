package com.waqiti.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebApplicationFirewallFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    // SQL Injection patterns
    private static final List<Pattern> SQL_INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\b(union|select|insert|update|delete|drop|create|alter|exec|execute)\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\b(script|javascript|vbscript|onload|onerror|onclick)\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*'\\s*(or|and)\\s*'?\\d*'?\\s*=\\s*'?\\d*.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*;\\s*(shutdown|drop|delete|truncate).*", Pattern.CASE_INSENSITIVE)
    );

    // XSS patterns
    private static final List<Pattern> XSS_PATTERNS = Arrays.asList(
        Pattern.compile(".*<script.*?>.*</script>.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile(".*<iframe.*?>.*</iframe>.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile(".*javascript:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*on\\w+\\s*=.*", Pattern.CASE_INSENSITIVE)
    );

    // Path traversal patterns
    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\.\\./.*"),
        Pattern.compile(".*\\.\\\\.*"),
        Pattern.compile(".*%2e%2e.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*%252e%252e.*", Pattern.CASE_INSENSITIVE)
    );

    // Command injection patterns
    private static final List<Pattern> COMMAND_INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile(".*;\\s*(ls|cat|rm|mv|cp|wget|curl|nc|telnet)\\s+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\|\\s*(ls|cat|rm|mv|cp|wget|curl|nc|telnet)\\s+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*`.*`.*"),
        Pattern.compile(".*\\$\\(.*\\).*")
    );

    // LDAP injection patterns
    private static final List<Pattern> LDAP_INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\(\\s*\\|.*\\).*"),
        Pattern.compile(".*\\(\\s*&.*\\).*"),
        Pattern.compile(".*\\(\\s*!.*\\).*")
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Check request URI
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        
        if (containsMaliciousPattern(path) || (query != null && containsMaliciousPattern(query))) {
            log.warn("Malicious pattern detected in request URI: {} from IP: {}", 
                request.getURI(), getClientIP(request));
            return handleMaliciousRequest(exchange, "Malicious pattern detected in request");
        }

        // Check headers
        for (Map.Entry<String, List<String>> header : request.getHeaders().entrySet()) {
            for (String value : header.getValue()) {
                if (containsMaliciousPattern(value)) {
                    log.warn("Malicious pattern detected in header {}: {} from IP: {}", 
                        header.getKey(), value, getClientIP(request));
                    return handleMaliciousRequest(exchange, "Malicious pattern detected in headers");
                }
            }
        }

        // Add security headers to response
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add("X-Content-Type-Options", "nosniff");
        response.getHeaders().add("X-Frame-Options", "DENY");
        response.getHeaders().add("X-XSS-Protection", "1; mode=block");
        response.getHeaders().add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.getHeaders().add("Content-Security-Policy", 
            "default-src 'self'; script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; " +
            "style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:");
        response.getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");
        response.getHeaders().add("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

        return chain.filter(exchange);
    }

    private boolean containsMaliciousPattern(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        String decodedInput;
        try {
            decodedInput = URLDecoder.decode(input, StandardCharsets.UTF_8);
        } catch (Exception e) {
            decodedInput = input;
        }

        // Check all pattern types
        return checkPatterns(decodedInput, SQL_INJECTION_PATTERNS) ||
               checkPatterns(decodedInput, XSS_PATTERNS) ||
               checkPatterns(decodedInput, PATH_TRAVERSAL_PATTERNS) ||
               checkPatterns(decodedInput, COMMAND_INJECTION_PATTERNS) ||
               checkPatterns(decodedInput, LDAP_INJECTION_PATTERNS);
    }

    private boolean checkPatterns(String input, List<Pattern> patterns) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(input).matches());
    }

    private Mono<Void> handleMaliciousRequest(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Security Violation");
        error.put("message", reason);
        error.put("timestamp", java.time.Instant.now());
        error.put("path", exchange.getRequest().getURI().getPath());

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(error);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Error writing error response", e);
            return response.setComplete();
        }
    }

    private String getClientIP(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIP = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}