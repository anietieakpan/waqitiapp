package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import jakarta.servlet.ReadListener;

/**
 * Server-side filter to handle encrypted requests and verify signatures
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecureServiceFilter extends OncePerRequestFilter {
    
    private final SecureServiceCommunication.MessageEncryptionService encryptionService;
    private final SecureServiceCommunication.RequestSigningService signingService;
    private final ObjectMapper objectMapper;
    
    // Maximum allowed time difference for replay attack prevention (5 minutes)
    private static final long MAX_TIME_DIFF_SECONDS = 300;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
            FilterChain filterChain) throws ServletException, IOException {
        
        // Skip filter for health and metrics endpoints
        if (shouldSkipFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            // Verify request timestamp (prevent replay attacks)
            if (!verifyTimestamp(request)) {
                log.warn("Request timestamp verification failed: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Request expired or invalid timestamp\"}");
                return;
            }
            
            // Verify request signature
            if (!verifyRequestSignature(request)) {
                log.warn("Request signature verification failed: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid request signature\"}");
                return;
            }
            
            // Decrypt request body if encrypted
            HttpServletRequest processedRequest = processEncryptedRequest(request);
            
            // Add security headers to response
            addSecurityHeaders(response);
            
            // Continue with filter chain
            filterChain.doFilter(processedRequest, response);
            
        } catch (Exception e) {
            log.error("Security filter error: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Security processing failed\"}");
        }
    }
    
    /**
     * Check if filter should be skipped for this request
     */
    private boolean shouldSkipFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.contains("/health") || 
               uri.contains("/metrics") || 
               uri.contains("/actuator") ||
               uri.contains("/swagger") ||
               uri.contains("/api-docs");
    }
    
    /**
     * Verify request timestamp to prevent replay attacks
     */
    private boolean verifyTimestamp(HttpServletRequest request) {
        String timestampHeader = request.getHeader("X-Timestamp");
        if (timestampHeader == null) {
            return false;
        }
        
        try {
            Instant requestTime = Instant.parse(timestampHeader);
            Instant now = Instant.now();
            
            long secondsDiff = Math.abs(ChronoUnit.SECONDS.between(requestTime, now));
            
            return secondsDiff <= MAX_TIME_DIFF_SECONDS;
            
        } catch (Exception e) {
            log.error("Failed to parse timestamp: {}", timestampHeader, e);
            return false;
        }
    }
    
    /**
     * Verify request signature
     */
    private boolean verifyRequestSignature(HttpServletRequest request) {
        String signature = request.getHeader("X-Signature");
        String timestamp = request.getHeader("X-Timestamp");
        
        if (signature == null || timestamp == null) {
            log.debug("Missing signature or timestamp headers");
            return false;
        }
        
        try {
            // Read request body
            String body = request.getReader().lines()
                .reduce("", (accumulator, actual) -> accumulator + actual);
            
            // Verify signature
            return signingService.verifySignature(
                signature,
                request.getMethod(),
                request.getRequestURI(),
                body.isEmpty() ? null : body,
                timestamp
            );
            
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }
    
    /**
     * Process encrypted request body
     */
    private HttpServletRequest processEncryptedRequest(HttpServletRequest request) throws IOException {
        String encryptionAlgorithm = request.getHeader("X-Encryption-Algorithm");
        String sessionKeyId = request.getHeader("X-Session-Key-Id");
        
        if (encryptionAlgorithm == null || sessionKeyId == null) {
            // Request is not encrypted
            return request;
        }
        
        try {
            // Read encrypted body
            String encryptedBody = request.getReader().lines()
                .reduce("", (accumulator, actual) -> accumulator + actual);
            
            // Parse encrypted message
            SecureServiceCommunication.EncryptedMessage encryptedMessage = 
                objectMapper.readValue(encryptedBody, SecureServiceCommunication.EncryptedMessage.class);
            
            // Decrypt message
            String decryptedBody = encryptionService.decryptMessage(encryptedMessage);
            
            // Create wrapper request with decrypted body
            return new DecryptedRequestWrapper(request, decryptedBody);
            
        } catch (Exception e) {
            log.error("Failed to decrypt request body", e);
            throw new SecurityException("Decryption failed", e);
        }
    }
    
    /**
     * Add security headers to response
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Content-Security-Policy", "default-src 'self'");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    }
    
    /**
     * Wrapper for decrypted request
     */
    private static class DecryptedRequestWrapper extends HttpServletRequestWrapper {
        private final String decryptedBody;
        
        public DecryptedRequestWrapper(HttpServletRequest request, String decryptedBody) {
            super(request);
            this.decryptedBody = decryptedBody;
        }
        
        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new ServletInputStream() {
                private final ByteArrayInputStream stream = 
                    new ByteArrayInputStream(decryptedBody.getBytes(StandardCharsets.UTF_8));
                
                @Override
                public int read() throws IOException {
                    return stream.read();
                }
                
                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }
                
                @Override
                public boolean isReady() {
                    return true;
                }
                
                @Override
                public void setReadListener(ReadListener listener) {
                    // Not implemented
                }
            };
        }
        
        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }
}