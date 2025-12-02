package com.waqiti.common.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

/**
 * Authentication interceptor for HTTP requests.
 * 
 * Automatically adds authentication headers to outbound HTTP requests
 * based on the current security context.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
public class AuthenticationClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
    
    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        
        // Add authentication headers if available
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            // Add bearer token if available
            Object credentials = authentication.getCredentials();
            if (credentials != null) {
                request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.toString());
            }
            
            // Add user context headers
            request.getHeaders().add("X-User-ID", authentication.getName());
            request.getHeaders().add("X-User-Roles", String.join(",", 
                authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .toList()));
        }
        
        // Add service identification headers
        request.getHeaders().add("X-Service-Name", "waqiti-bank-integration");
        request.getHeaders().add("X-Service-Version", "1.0.0");
        
        // Add correlation ID for tracing
        String correlationId = org.slf4j.MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        request.getHeaders().add("X-Correlation-ID", correlationId);
        
        return execution.execute(request, body);
    }
}