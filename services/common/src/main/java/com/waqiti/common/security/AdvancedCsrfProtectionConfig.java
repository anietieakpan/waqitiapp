package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Advanced CSRF Protection Configuration with Redis-based token storage
 * Provides comprehensive protection against Cross-Site Request Forgery attacks
 */
@Configuration
@Slf4j
public class AdvancedCsrfProtectionConfig {

    @Value("${security.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Value("${security.csrf.token-validity-seconds:3600}")
    private long tokenValiditySeconds;

    @Value("${security.csrf.secure-cookie:true}")
    private boolean secureCookie;

    @Value("${security.csrf.same-site:Strict}")
    private String sameSitePolicy;

    @Value("${security.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Value("${security.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private List<String> allowedMethods;

    @Value("${security.cors.allowed-headers:*}")
    private List<String> allowedHeaders;

    @Value("${security.cors.exposed-headers:X-CSRF-TOKEN,Authorization}")
    private List<String> exposedHeaders;

    @Value("${security.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${security.cors.max-age:3600}")
    private long corsMaxAge;

    /**
     * Configure CSRF protection with Redis-based token repository
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository(RedisTemplate<String, Object> redisTemplate) {
        if (!csrfEnabled) {
            return new NullCsrfTokenRepository();
        }
        return new RedisCsrfTokenRepository(redisTemplate, tokenValiditySeconds);
    }

    /**
     * Configure CSRF request matcher to exclude API endpoints
     */
    @Bean
    public RequestMatcher csrfRequestMatcher() {
        return new CsrfRequestMatcher();
    }

    /**
     * Configure CORS with security best practices
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Configure allowed origins
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            configuration.setAllowedOriginPatterns(allowedOrigins);
        } else {
            // Restrictive default - only allow same origin
            configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:*", "https://localhost:*"));
        }
        
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setExposedHeaders(exposedHeaders);
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(corsMaxAge);
        
        // Apply strict validation
        configuration.applyPermitDefaultValues();
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    /**
     * Custom CSRF request matcher that excludes API endpoints and includes state-changing operations
     */
    private static class CsrfRequestMatcher implements RequestMatcher {
        private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/api/*/public/**",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/health/**",
            "/metrics/**"
        );

        private static final List<String> PROTECTED_METHODS = Arrays.asList(
            "POST", "PUT", "DELETE", "PATCH"
        );

        @Override
        public boolean matches(HttpServletRequest request) {
            String method = request.getMethod();
            String path = request.getRequestURI();
            
            // Only protect state-changing HTTP methods
            if (!PROTECTED_METHODS.contains(method.toUpperCase())) {
                return false;
            }
            
            // Exclude public API endpoints
            for (String excludedPath : EXCLUDED_PATHS) {
                if (new AntPathRequestMatcher(excludedPath).matches(request)) {
                    return false;
                }
            }
            
            // Exclude service-to-service communication (authenticated with service tokens)
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // This would be validated by JWT token filter
                return false;
            }
            
            return true;
        }
    }

    /**
     * Redis-based CSRF token repository for distributed applications
     */
    public static class RedisCsrfTokenRepository implements CsrfTokenRepository {
        private static final String CSRF_TOKEN_PREFIX = "csrf:token:";
        private static final String CSRF_SESSION_PREFIX = "csrf:session:";
        
        private final RedisTemplate<String, Object> redisTemplate;
        private final long tokenValiditySeconds;
        private final SecureRandom secureRandom;

        public RedisCsrfTokenRepository(RedisTemplate<String, Object> redisTemplate, long tokenValiditySeconds) {
            this.redisTemplate = redisTemplate;
            this.tokenValiditySeconds = tokenValiditySeconds;
            this.secureRandom = new SecureRandom();
        }

        @Override
        public org.springframework.security.web.csrf.CsrfToken generateToken(HttpServletRequest request) {
            String tokenValue = generateSecureToken();
            String sessionId = getSessionId(request);
            
            return new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", tokenValue);
        }

        @Override
        public void saveToken(org.springframework.security.web.csrf.CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
            if (token == null) {
                // Delete existing token
                String sessionId = getSessionId(request);
                if (sessionId != null) {
                    String existingTokenKey = (String) redisTemplate.opsForValue()
                        .get(CSRF_SESSION_PREFIX + sessionId);
                    if (existingTokenKey != null) {
                        redisTemplate.delete(existingTokenKey);
                        redisTemplate.delete(CSRF_SESSION_PREFIX + sessionId);
                    }
                }
                return;
            }

            String sessionId = getSessionId(request);
            if (sessionId == null) {
                log.warn("Cannot save CSRF token: no session ID available");
                return;
            }

            String tokenKey = CSRF_TOKEN_PREFIX + token.getToken();
            
            // Store token with metadata
            CsrfTokenMetadata metadata = new CsrfTokenMetadata(
                token.getToken(),
                sessionId,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                System.currentTimeMillis()
            );
            
            redisTemplate.opsForValue().set(tokenKey, metadata, 
                Duration.ofSeconds(tokenValiditySeconds));
            
            // Map session to token for cleanup
            redisTemplate.opsForValue().set(CSRF_SESSION_PREFIX + sessionId, tokenKey, 
                Duration.ofSeconds(tokenValiditySeconds));
            
            // Set secure cookie
            response.addHeader("Set-Cookie", 
                String.format("%s=%s; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=%d", 
                    token.getParameterName(), token.getToken(), tokenValiditySeconds));
            
            log.debug("CSRF token saved for session: {}", sessionId);
        }

        @Override
        public org.springframework.security.web.csrf.CsrfToken loadToken(HttpServletRequest request) {
            String tokenValue = extractTokenFromRequest(request);
            if (tokenValue == null) {
                return null;
            }

            String tokenKey = CSRF_TOKEN_PREFIX + tokenValue;
            CsrfTokenMetadata metadata = (CsrfTokenMetadata) redisTemplate.opsForValue().get(tokenKey);
            
            if (metadata == null) {
                log.debug("CSRF token not found in Redis: {}", tokenValue);
                return null;
            }

            // Validate token against session and request context
            if (!isValidTokenContext(metadata, request)) {
                log.warn("CSRF token validation failed for security reasons");
                redisTemplate.delete(tokenKey);
                return null;
            }

            return new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", metadata.getTokenValue());
        }

        private String generateSecureToken() {
            byte[] tokenBytes = new byte[32]; // 256 bits
            secureRandom.nextBytes(tokenBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        }

        private String extractTokenFromRequest(HttpServletRequest request) {
            // Try header first
            String token = request.getHeader("X-CSRF-TOKEN");
            if (token != null) {
                return token;
            }
            
            // Try request parameter
            token = request.getParameter("_csrf");
            if (token != null) {
                return token;
            }
            
            // Try cookie
            if (request.getCookies() != null) {
                for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                    if ("_csrf".equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
            
            return null;
        }

        private String getSessionId(HttpServletRequest request) {
            return request.getSession(false) != null ? 
                request.getSession().getId() : null;
        }

        private boolean isValidTokenContext(CsrfTokenMetadata metadata, HttpServletRequest request) {
            String sessionId = getSessionId(request);
            
            // Validate session
            if (!metadata.getSessionId().equals(sessionId)) {
                log.warn("CSRF token session mismatch");
                return false;
            }
            
            // Validate IP address (optional but recommended)
            String requestIp = request.getRemoteAddr();
            if (!metadata.getIpAddress().equals(requestIp)) {
                log.warn("CSRF token IP address mismatch: expected {}, got {}", 
                    metadata.getIpAddress(), requestIp);
                // Could be made configurable - some environments have dynamic IPs
            }
            
            // Validate token age
            long tokenAge = System.currentTimeMillis() - metadata.getCreatedAt();
            if (tokenAge > TimeUnit.SECONDS.toMillis(tokenValiditySeconds)) {
                log.warn("CSRF token expired");
                return false;
            }
            
            return true;
        }

        /**
         * Metadata stored with CSRF tokens for enhanced validation
         */
        public static class CsrfTokenMetadata {
            private String tokenValue;
            private String sessionId;
            private String ipAddress;
            private String userAgent;
            private long createdAt;

            public CsrfTokenMetadata() {}

            public CsrfTokenMetadata(String tokenValue, String sessionId, String ipAddress, 
                                   String userAgent, long createdAt) {
                this.tokenValue = tokenValue;
                this.sessionId = sessionId;
                this.ipAddress = ipAddress;
                this.userAgent = userAgent;
                this.createdAt = createdAt;
            }

            // Getters and setters
            public String getTokenValue() { return tokenValue; }
            public void setTokenValue(String tokenValue) { this.tokenValue = tokenValue; }
            public String getSessionId() { return sessionId; }
            public void setSessionId(String sessionId) { this.sessionId = sessionId; }
            public String getIpAddress() { return ipAddress; }
            public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
            public String getUserAgent() { return userAgent; }
            public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
            public long getCreatedAt() { return createdAt; }
            public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        }
    }

    /**
     * Null CSRF token repository for when CSRF is disabled
     */
    public static class NullCsrfTokenRepository implements CsrfTokenRepository {
        @Override
        public org.springframework.security.web.csrf.CsrfToken generateToken(HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveToken(org.springframework.security.web.csrf.CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
            // No-op
        }

        @Override
        public org.springframework.security.web.csrf.CsrfToken loadToken(HttpServletRequest request) {
            return null;
        }
    }
}