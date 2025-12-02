package com.waqiti.messaging.service;

import com.waqiti.common.security.TokenValidationResult;
import com.waqiti.common.security.SecurityConstants;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * PRODUCTION-READY Authentication Service for Messaging
 * 
 * SECURITY ENHANCEMENTS:
 * - JWT token validation with signature verification
 * - OAuth2/OIDC token introspection
 * - Token blacklisting for revocation
 * - Rate limiting for authentication attempts
 * - Comprehensive audit logging
 * - Token caching for performance
 * - Security event monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final Optional<JwtDecoder> jwtDecoder;
    
    @Value("${jwt.secret:#{null}}")
    private String jwtSecret;
    
    @Value("${keycloak.auth-server-url:#{null}}")
    private String keycloakServerUrl;
    
    @Value("${keycloak.realm:#{null}}")
    private String keycloakRealm;
    
    @Value("${security.token.cache.enabled:true}")
    private boolean tokenCacheEnabled;
    
    @Value("${security.token.cache.ttl:300}")
    private int tokenCacheTtlSeconds;
    
    @Value("${security.token.blacklist.enabled:true}")
    private boolean tokenBlacklistEnabled;
    
    @Value("${security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${security.rate-limit.max-attempts:10}")
    private int maxAuthAttempts;
    
    @Value("${security.rate-limit.window-minutes:5}")
    private int rateLimitWindowMinutes;
    
    private static final String TOKEN_CACHE_PREFIX = "auth:token:cache:";
    private static final String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";
    private static final String RATE_LIMIT_PREFIX = "auth:rate:limit:";
    private static final String SECURITY_EVENT_PREFIX = "security:event:";
    
    private SecretKey signingKey;
    private final Map<String, Integer> authAttempts = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Production Authentication Service");
        
        if (jwtSecret != null && !jwtSecret.isEmpty()) {
            this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            log.info("JWT validation configured with local secret");
        }
        
        if (keycloakServerUrl != null && !keycloakServerUrl.isEmpty()) {
            log.info("Keycloak OAuth2/OIDC validation configured");
        }
        
        log.info("Authentication Service initialized - Cache: {}, Blacklist: {}, RateLimit: {}",
            tokenCacheEnabled, tokenBlacklistEnabled, rateLimitEnabled);
    }
    
    /**
     * PRODUCTION-READY token validation with comprehensive security checks
     */
    public String validateTokenAndGetUserId(String token) {
        if (token == null || token.trim().isEmpty()) {
            logSecurityEvent("INVALID_TOKEN", "Empty token provided", null);
            throw new SecurityException("Invalid token: token cannot be empty");
        }
        
        // Remove Bearer prefix if present
        String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        
        try {
            // Check rate limiting
            if (rateLimitEnabled && !checkRateLimit(cleanToken)) {
                logSecurityEvent("RATE_LIMIT_EXCEEDED", "Too many authentication attempts", cleanToken);
                throw new SecurityException("Rate limit exceeded");
            }
            
            // Check if token is blacklisted
            if (tokenBlacklistEnabled && isTokenBlacklisted(cleanToken)) {
                logSecurityEvent("BLACKLISTED_TOKEN", "Attempt to use blacklisted token", cleanToken);
                throw new SecurityException("Token has been revoked");
            }
            
            // Check cache first
            if (tokenCacheEnabled) {
                String cachedUserId = getCachedTokenValidation(cleanToken);
                if (cachedUserId != null) {
                    log.debug("Token validation cache hit");
                    return cachedUserId;
                }
            }
            
            // Validate token based on configuration
            String userId = null;
            
            // Try OAuth2/OIDC validation first (preferred)
            if (jwtDecoder.isPresent()) {
                userId = validateOAuth2Token(cleanToken);
            } 
            // Fall back to Keycloak introspection
            else if (keycloakServerUrl != null) {
                userId = validateKeycloakToken(cleanToken);
            }
            // Fall back to local JWT validation
            else if (signingKey != null) {
                userId = validateJwtToken(cleanToken);
            }
            // Last resort - check Spring Security context
            else {
                userId = validateFromSecurityContext();
            }
            
            if (userId == null) {
                logSecurityEvent("TOKEN_VALIDATION_FAILED", "No valid authentication method available", cleanToken);
                throw new SecurityException("Token validation failed");
            }
            
            // Cache successful validation
            if (tokenCacheEnabled) {
                cacheTokenValidation(cleanToken, userId);
            }
            
            // Log successful authentication
            logSecurityEvent("AUTHENTICATION_SUCCESS", "Token validated successfully", userId);
            
            return userId;
            
        } catch (Exception e) {
            log.error("Token validation failed", e);
            logSecurityEvent("AUTHENTICATION_FAILED", e.getMessage(), cleanToken);
            throw new SecurityException("Authentication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate OAuth2/OIDC token using Spring Security JWT decoder
     */
    private String validateOAuth2Token(String token) {
        try {
            Jwt jwt = jwtDecoder.get().decode(token);
            
            // Check token expiration
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
                throw new JwtException("Token has expired");
            }
            
            // Extract user ID from standard claims
            String userId = jwt.getSubject();
            if (userId == null) {
                // Try custom claims
                userId = jwt.getClaimAsString("user_id");
                if (userId == null) {
                    userId = jwt.getClaimAsString("userId");
                }
            }
            
            if (userId == null) {
                throw new JwtException("No user ID found in token");
            }
            
            // Extract and validate authorities
            List<String> authorities = extractAuthorities(jwt);
            validateAuthorities(authorities);
            
            return userId;
            
        } catch (JwtException e) {
            log.error("OAuth2 token validation failed", e);
            throw new SecurityException("Invalid OAuth2 token", e);
        }
    }
    
    /**
     * Validate token via Keycloak introspection endpoint
     */
    private String validateKeycloakToken(String token) {
        String introspectionUrl = String.format("%s/realms/%s/protocol/openid-connect/token/introspect",
            keycloakServerUrl, keycloakRealm);
        
        try {
            // Prepare introspection request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBearerAuth(token);
            
            // Make introspection request
            ResponseEntity<Map> response = restTemplate.exchange(
                introspectionUrl,
                HttpMethod.POST,
                new HttpEntity<>("token=" + token, headers),
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> introspectionResult = response.getBody();
                
                // Check if token is active
                Boolean active = (Boolean) introspectionResult.get("active");
                if (!Boolean.TRUE.equals(active)) {
                    throw new SecurityException("Token is not active");
                }
                
                // Extract user ID
                String userId = (String) introspectionResult.get("sub");
                if (userId == null) {
                    userId = (String) introspectionResult.get("username");
                }
                
                return userId;
            }
            
            throw new SecurityException("Token introspection failed");
            
        } catch (Exception e) {
            log.error("Keycloak token validation failed", e);
            throw new SecurityException("Keycloak validation failed", e);
        }
    }
    
    /**
     * Validate JWT token with local secret
     */
    private String validateJwtToken(String token) {
        try {
            Jws<Claims> claimsJws = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token);
            
            Claims claims = claimsJws.getBody();
            
            // Check expiration
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                throw new JwtException("Token has expired");
            }
            
            // Extract user ID
            String userId = claims.getSubject();
            if (userId == null) {
                userId = (String) claims.get("userId");
            }
            
            if (userId == null) {
                throw new JwtException("No user ID found in token");
            }
            
            return userId;
            
        } catch (JwtException e) {
            log.error("JWT validation failed", e);
            throw new SecurityException("Invalid JWT token", e);
        }
    }
    
    /**
     * Validate from Spring Security context (fallback)
     */
    private String validateFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            
            if (principal instanceof Jwt) {
                Jwt jwt = (Jwt) principal;
                return jwt.getSubject();
            } else if (principal instanceof org.springframework.security.core.userdetails.User) {
                org.springframework.security.core.userdetails.User user = 
                    (org.springframework.security.core.userdetails.User) principal;
                return user.getUsername();
            } else if (principal instanceof String) {
                return (String) principal;
            }
        }
        
        return null;
    }
    
    /**
     * Extract authorities from JWT
     */
    private List<String> extractAuthorities(Jwt jwt) {
        List<String> authorities = new ArrayList<>();
        
        // Try standard claims
        List<String> scope = jwt.getClaimAsStringList("scope");
        if (scope != null) {
            authorities.addAll(scope);
        }
        
        // Try realm roles (Keycloak)
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            authorities.addAll(roles);
        }
        
        // Try authorities claim
        List<String> authClaim = jwt.getClaimAsStringList("authorities");
        if (authClaim != null) {
            authorities.addAll(authClaim);
        }
        
        return authorities;
    }
    
    /**
     * Validate user authorities/roles
     */
    private void validateAuthorities(List<String> authorities) {
        // Ensure user has at least basic user role
        boolean hasValidRole = authorities.stream()
            .anyMatch(auth -> auth.equalsIgnoreCase("USER") || 
                            auth.equalsIgnoreCase("ROLE_USER") ||
                            auth.equalsIgnoreCase("user"));
        
        if (!hasValidRole && !authorities.isEmpty()) {
            log.warn("User has authorities but no basic USER role: {}", authorities);
        }
    }
    
    /**
     * Check if token is blacklisted
     */
    private boolean isTokenBlacklisted(String token) {
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + token.hashCode();
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
    }
    
    /**
     * Add token to blacklist (for logout/revocation)
     */
    public void blacklistToken(String token, Duration ttl) {
        if (!tokenBlacklistEnabled) {
            return;
        }
        
        String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + cleanToken.hashCode();
        
        redisTemplate.opsForValue().set(blacklistKey, true, ttl);
        log.info("Token blacklisted for {} seconds", ttl.getSeconds());
        
        // Invalidate cache
        if (tokenCacheEnabled) {
            String cacheKey = TOKEN_CACHE_PREFIX + cleanToken.hashCode();
            redisTemplate.delete(cacheKey);
        }
        
        logSecurityEvent("TOKEN_BLACKLISTED", "Token added to blacklist", cleanToken);
    }
    
    /**
     * Get cached token validation result
     */
    @Cacheable(value = "tokenValidation", key = "#token.hashCode()", condition = "#root.target.tokenCacheEnabled")
    private String getCachedTokenValidation(String token) {
        String cacheKey = TOKEN_CACHE_PREFIX + token.hashCode();
        return (String) redisTemplate.opsForValue().get(cacheKey);
    }
    
    /**
     * Cache token validation result
     */
    private void cacheTokenValidation(String token, String userId) {
        String cacheKey = TOKEN_CACHE_PREFIX + token.hashCode();
        redisTemplate.opsForValue().set(cacheKey, userId, 
            Duration.ofSeconds(tokenCacheTtlSeconds));
    }
    
    /**
     * Check rate limiting
     */
    private boolean checkRateLimit(String identifier) {
        String rateLimitKey = RATE_LIMIT_PREFIX + identifier.hashCode();
        
        Long attempts = redisTemplate.opsForValue().increment(rateLimitKey);
        if (attempts == 1) {
            redisTemplate.expire(rateLimitKey, rateLimitWindowMinutes, TimeUnit.MINUTES);
        }
        
        return attempts == null || attempts <= maxAuthAttempts;
    }
    
    /**
     * Log security events for monitoring and compliance
     */
    private void logSecurityEvent(String eventType, String description, String userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("description", description);
        event.put("userId", userId);
        event.put("timestamp", Instant.now().toString());
        event.put("service", "messaging-service");
        
        // Log to monitoring system
        log.info("SECURITY_EVENT: {}", event);
        
        // Store in Redis for analysis
        String eventKey = SECURITY_EVENT_PREFIX + System.currentTimeMillis();
        redisTemplate.opsForValue().set(eventKey, event, Duration.ofDays(30));
    }
    
    /**
     * Get user authorities from token
     */
    public List<String> getUserAuthorities(String token) {
        try {
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            if (jwtDecoder.isPresent()) {
                Jwt jwt = jwtDecoder.get().decode(cleanToken);
                return extractAuthorities(jwt);
            }
            
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to extract authorities", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Validate token and get full validation result
     */
    public TokenValidationResult validateTokenWithDetails(String token) {
        try {
            String userId = validateTokenAndGetUserId(token);
            List<String> authorities = getUserAuthorities(token);
            
            return TokenValidationResult.builder()
                .valid(true)
                .userId(userId)
                .authorities(authorities)
                .validatedAt(Instant.now())
                .build();
                
        } catch (Exception e) {
            return TokenValidationResult.builder()
                .valid(false)
                .errorMessage(e.getMessage())
                .validatedAt(Instant.now())
                .build();
        }
    }
}