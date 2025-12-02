package com.waqiti.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * Filter to validate service-to-service authentication
 */
@Slf4j
@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SERVICE_ID_HEADER = "X-Service-ID";
    private static final String TIMESTAMP_HEADER = "X-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final long MAX_TIMESTAMP_DIFF = 300; // 5 minutes

    @Value("${service.auth.secret:#{null}}")
    private String serviceSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip authentication for public endpoints
        String requestUri = request.getRequestURI();
        if (isPublicEndpoint(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip if this is not a service-to-service call
        String serviceId = request.getHeader(SERVICE_ID_HEADER);
        if (serviceId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!validateServiceAuthentication(request)) {
            log.warn("Invalid service authentication from service: {}, path: {}", 
                serviceId, requestUri);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid service authentication");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String requestUri) {
        return requestUri.startsWith("/api/v1/auth/") ||
               requestUri.startsWith("/api/v1/users/register") ||
               requestUri.startsWith("/api/v1/users/verify/") ||
               requestUri.startsWith("/actuator/") ||
               requestUri.startsWith("/v3/api-docs/") ||
               requestUri.startsWith("/swagger-ui/");
    }

    private boolean validateServiceAuthentication(HttpServletRequest request) {
        if (serviceSecret == null || serviceSecret.isEmpty()) {
            return true; // Skip validation if not configured
        }

        String serviceId = request.getHeader(SERVICE_ID_HEADER);
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);

        if (serviceId == null || timestamp == null || signature == null) {
            return false;
        }

        // Validate timestamp to prevent replay attacks
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = Instant.now().getEpochSecond();
            if (Math.abs(currentTime - requestTime) > MAX_TIMESTAMP_DIFF) {
                log.warn("Request timestamp too old: {} vs {}", requestTime, currentTime);
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // Validate signature
        String expectedSignature = generateSignature(serviceId, timestamp, request.getRequestURI());
        return expectedSignature.equals(signature);
    }

    private String generateSignature(String serviceId, String timestamp, String path) {
        try {
            String payload = serviceId + ":" + timestamp + ":" + path;
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                serviceSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate service signature", e);
        }
    }
}