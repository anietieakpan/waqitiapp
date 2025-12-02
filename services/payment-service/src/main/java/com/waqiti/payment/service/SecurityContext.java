package com.waqiti.payment.service;

import com.waqiti.payment.domain.UserRole;
import com.waqiti.payment.domain.Permission;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Security context manager for authentication and authorization.
 * Handles JWT tokens, permissions, and security state.
 */
@Slf4j
@Component
public class SecurityContext {
    
    @Value("${security.jwt.secret:#{null}}")
    private String jwtSecret;
    
    @Value("${security.jwt.expiration:3600}")
    private long jwtExpiration;
    
    @Value("${security.jwt.refresh-expiration:86400}")
    private long refreshExpiration;
    
    @Value("${security.api-key.enabled:true}")
    private boolean apiKeyEnabled;
    
    @Value("${security.mfa.enabled:false}")
    private boolean mfaEnabled;
    
    @Value("${security.session.timeout:1800}")
    private long sessionTimeout;
    
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, ApiKeyInfo> apiKeys = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();
    
    private SecretKey signingKey;
    
    @jakarta.annotation.PostConstruct
    public void initialize() {
        // Initialize signing key
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            log.warn("No JWT secret configured, generating random key for development");
            this.signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }
        
        // Initialize role permissions
        initializeRolePermissions();
        
        // Start session cleanup scheduler
        startSessionCleanup();
        
        log.info("Security context initialized with JWT expiration: {}s, MFA: {}", 
                 jwtExpiration, mfaEnabled);
    }
    
    /**
     * Get current authenticated user
     */
    public AuthenticatedUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        
        // Check if it's JWT authentication
        if (auth.getDetails() instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) auth.getDetails();
            return buildUserFromToken(jwtAuth);
        }
        
        // Check if it's API key authentication
        if (auth.getDetails() instanceof ApiKeyAuthenticationToken) {
            ApiKeyAuthenticationToken apiAuth = (ApiKeyAuthenticationToken) auth.getDetails();
            return buildUserFromApiKey(apiAuth);
        }
        
        // Default Spring Security authentication
        return AuthenticatedUser.builder()
            .userId(auth.getName())
            .username(auth.getName())
            .roles(extractRoles(auth.getAuthorities()))
            .permissions(extractPermissions(auth.getAuthorities()))
            .authenticated(true)
            .build();
    }
    
    /**
     * Check if current user has specific permission
     */
    public boolean hasPermission(String permission) {
        AuthenticatedUser user = getCurrentUser();
        if (user == null) {
            return false;
        }
        
        return user.getPermissions().contains(permission) ||
               hasRoleWithPermission(user.getRoles(), permission);
    }
    
    /**
     * Check if current user has any of the specified permissions
     */
    public boolean hasAnyPermission(String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if current user has all specified permissions
     */
    public boolean hasAllPermissions(String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if current user has specific role
     */
    public boolean hasRole(String role) {
        AuthenticatedUser user = getCurrentUser();
        return user != null && user.getRoles().contains(role);
    }
    
    /**
     * Generate JWT token for user
     */
    public String generateToken(String userId, String username, Set<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtExpiration, ChronoUnit.SECONDS);
        
        String token = Jwts.builder()
            .setSubject(userId)
            .claim("username", username)
            .claim("roles", String.join(",", roles))
            .claim("type", "access")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .setId(UUID.randomUUID().toString())
            .signWith(signingKey)
            .compact();
        
        // Store session info
        storeSession(userId, token, roles);
        
        log.debug("Generated JWT token for user: {} with roles: {}", userId, roles);
        
        return token;
    }
    
    /**
     * Generate refresh token
     */
    public String generateRefreshToken(String userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshExpiration, ChronoUnit.SECONDS);
        
        return Jwts.builder()
            .setSubject(userId)
            .claim("type", "refresh")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .setId(UUID.randomUUID().toString())
            .signWith(signingKey)
            .compact();
    }
    
    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        try {
            // Check if token is blacklisted
            if (blacklistedTokens.contains(token)) {
                log.debug("Token is blacklisted");
                return false;
            }
            
            // Parse and validate token
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            // Check expiration
            if (claims.getExpiration().before(new Date())) {
                log.debug("Token has expired");
                return false;
            }
            
            // Validate session
            String userId = claims.getSubject();
            SessionInfo session = activeSessions.get(userId);
            
            if (session == null || !session.isActive()) {
                log.debug("No active session for user: {}", userId);
                return false;
            }
            
            // Update last activity
            session.updateActivity();
            
            return true;
            
        } catch (Exception e) {
            log.error("Token validation failed", e);
            return false;
        }
    }
    
    /**
     * Extract claims from token
     */
    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
    
    /**
     * Revoke token (logout)
     */
    public void revokeToken(String token) {
        try {
            Claims claims = extractClaims(token);
            String userId = claims.getSubject();
            
            // Add to blacklist
            blacklistedTokens.add(token);
            
            // Remove session
            activeSessions.remove(userId);
            
            log.info("Revoked token for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to revoke token", e);
        }
    }
    
    /**
     * Generate API key
     */
    public String generateApiKey(String clientId, Set<String> scopes) {
        String apiKey = UUID.randomUUID().toString().replace("-", "");
        
        ApiKeyInfo keyInfo = ApiKeyInfo.builder()
            .apiKey(apiKey)
            .clientId(clientId)
            .scopes(scopes)
            .createdAt(Instant.now())
            .lastUsed(Instant.now())
            .active(true)
            .build();
        
        apiKeys.put(apiKey, keyInfo);
        
        log.info("Generated API key for client: {} with scopes: {}", clientId, scopes);
        
        return apiKey;
    }
    
    /**
     * Validate API key
     */
    public boolean validateApiKey(String apiKey) {
        if (!apiKeyEnabled) {
            return true;
        }
        
        ApiKeyInfo keyInfo = apiKeys.get(apiKey);
        
        if (keyInfo == null || !keyInfo.isActive()) {
            log.debug("Invalid or inactive API key");
            return false;
        }
        
        // Update last used
        keyInfo.setLastUsed(Instant.now());
        
        return true;
    }
    
    /**
     * Get API key scopes
     */
    public Set<String> getApiKeyScopes(String apiKey) {
        ApiKeyInfo keyInfo = apiKeys.get(apiKey);
        return keyInfo != null ? keyInfo.getScopes() : Collections.emptySet();
    }
    
    /**
     * Revoke API key
     */
    public void revokeApiKey(String apiKey) {
        ApiKeyInfo keyInfo = apiKeys.get(apiKey);
        if (keyInfo != null) {
            keyInfo.setActive(false);
            log.info("Revoked API key for client: {}", keyInfo.getClientId());
        }
    }
    
    /**
     * Check if account is locked due to failed attempts
     */
    public boolean isAccountLocked(String userId) {
        Integer attempts = failedAttempts.get(userId);
        return attempts != null && attempts >= 5;
    }
    
    /**
     * Record failed login attempt
     */
    public void recordFailedAttempt(String userId) {
        failedAttempts.merge(userId, 1, Integer::sum);
        
        if (failedAttempts.get(userId) >= 5) {
            log.warn("Account locked due to failed attempts: {}", userId);
            // In production, trigger security alert
        }
    }
    
    /**
     * Reset failed attempts after successful login
     */
    public void resetFailedAttempts(String userId) {
        failedAttempts.remove(userId);
    }
    
    /**
     * Get current auth token
     */
    public String getAuthToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.getCredentials() instanceof String) {
            return (String) auth.getCredentials();
        }
        
        return null;
    }
    
    /**
     * Check if MFA is required for user
     */
    public boolean isMfaRequired(String userId) {
        if (!mfaEnabled) {
            return false;
        }
        
        // Check user's MFA settings
        SessionInfo session = activeSessions.get(userId);
        return session != null && session.isMfaRequired() && !session.isMfaVerified();
    }
    
    /**
     * Verify MFA code
     */
    public boolean verifyMfaCode(String userId, String code) {
        // In production, integrate with TOTP/SMS service
        SessionInfo session = activeSessions.get(userId);
        
        if (session != null) {
            // Simulate MFA verification
            boolean valid = code.length() == 6 && code.matches("\\d+");
            
            if (valid) {
                session.setMfaVerified(true);
                log.info("MFA verified for user: {}", userId);
            }
            
            return valid;
        }
        
        return false;
    }
    
    /**
     * Get user permissions based on roles
     */
    public Set<String> getUserPermissions(String userId) {
        AuthenticatedUser user = getCurrentUser();
        
        if (user == null || !user.getUserId().equals(userId)) {
            return Collections.emptySet();
        }
        
        Set<String> allPermissions = new HashSet<>(user.getPermissions());
        
        for (String role : user.getRoles()) {
            Set<String> rolePerms = rolePermissions.get(role);
            if (rolePerms != null) {
                allPermissions.addAll(rolePerms);
            }
        }
        
        return allPermissions;
    }
    
    /**
     * Initialize role-permission mappings
     */
    private void initializeRolePermissions() {
        // Admin role
        rolePermissions.put("ADMIN", Set.of(
            "payment.create", "payment.read", "payment.update", "payment.delete",
            "user.manage", "system.configure", "report.generate", "audit.view"
        ));
        
        // User role
        rolePermissions.put("USER", Set.of(
            "payment.create", "payment.read", "payment.cancel",
            "wallet.view", "transaction.view"
        ));
        
        // Merchant role
        rolePermissions.put("MERCHANT", Set.of(
            "payment.create", "payment.read", "payment.refund",
            "report.view", "webhook.manage", "api.access"
        ));
        
        // Support role
        rolePermissions.put("SUPPORT", Set.of(
            "payment.read", "user.read", "transaction.view",
            "ticket.manage", "refund.process"
        ));
        
        // Compliance role
        rolePermissions.put("COMPLIANCE", Set.of(
            "payment.read", "user.read", "report.generate",
            "audit.view", "aml.review", "kyc.verify"
        ));
    }
    
    /**
     * Start session cleanup scheduler
     */
    private void startSessionCleanup() {
        Timer timer = new Timer("SessionCleanup", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredSessions();
            }
        }, 60000, 60000); // Run every minute
    }
    
    /**
     * Cleanup expired sessions
     */
    private void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(sessionTimeout, ChronoUnit.SECONDS);
        
        activeSessions.entrySet().removeIf(entry -> {
            SessionInfo session = entry.getValue();
            boolean expired = session.getLastActivity().isBefore(cutoff);
            
            if (expired) {
                log.debug("Removing expired session for user: {}", entry.getKey());
            }
            
            return expired;
        });
        
        // Cleanup blacklisted tokens older than refresh expiration
        // In production, store in Redis with TTL
    }
    
    private void storeSession(String userId, String token, Set<String> roles) {
        SessionInfo session = SessionInfo.builder()
            .userId(userId)
            .token(token)
            .roles(roles)
            .createdAt(Instant.now())
            .lastActivity(Instant.now())
            .active(true)
            .mfaRequired(roles.contains("ADMIN"))
            .mfaVerified(false)
            .build();
        
        activeSessions.put(userId, session);
    }
    
    private boolean hasRoleWithPermission(Set<String> roles, String permission) {
        for (String role : roles) {
            Set<String> perms = rolePermissions.get(role);
            if (perms != null && perms.contains(permission)) {
                return true;
            }
        }
        return false;
    }
    
    private AuthenticatedUser buildUserFromToken(JwtAuthenticationToken token) {
        Claims claims = token.getClaims();
        
        return AuthenticatedUser.builder()
            .userId(claims.getSubject())
            .username(claims.get("username", String.class))
            .roles(extractRolesFromClaim(claims.get("roles", String.class)))
            .permissions(new HashSet<>())
            .authenticated(true)
            .tokenExpiry(claims.getExpiration().toInstant())
            .build();
    }
    
    private AuthenticatedUser buildUserFromApiKey(ApiKeyAuthenticationToken token) {
        ApiKeyInfo keyInfo = apiKeys.get(token.getApiKey());
        
        return AuthenticatedUser.builder()
            .userId(keyInfo.getClientId())
            .username(keyInfo.getClientId())
            .roles(Set.of("API_CLIENT"))
            .permissions(keyInfo.getScopes())
            .authenticated(true)
            .apiKey(true)
            .build();
    }
    
    private Set<String> extractRoles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .filter(auth -> auth.startsWith("ROLE_"))
            .map(auth -> auth.substring(5))
            .collect(Collectors.toSet());
    }
    
    private Set<String> extractPermissions(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .filter(auth -> !auth.startsWith("ROLE_"))
            .collect(Collectors.toSet());
    }
    
    private Set<String> extractRolesFromClaim(String rolesClaim) {
        if (rolesClaim == null || rolesClaim.isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(rolesClaim.split(","))
            .collect(Collectors.toSet());
    }
    
    /**
     * Authenticated user details
     */
    @lombok.Builder
    @lombok.Data
    public static class AuthenticatedUser {
        private String userId;
        private String username;
        private Set<String> roles;
        private Set<String> permissions;
        private boolean authenticated;
        private boolean apiKey;
        private Instant tokenExpiry;
    }
    
    /**
     * Session information
     */
    @lombok.Builder
    @lombok.Data
    private static class SessionInfo {
        private String userId;
        private String token;
        private Set<String> roles;
        private Instant createdAt;
        private Instant lastActivity;
        private boolean active;
        private boolean mfaRequired;
        private boolean mfaVerified;
        
        public void updateActivity() {
            this.lastActivity = Instant.now();
        }
    }
    
    /**
     * API key information
     */
    @lombok.Builder
    @lombok.Data
    private static class ApiKeyInfo {
        private String apiKey;
        private String clientId;
        private Set<String> scopes;
        private Instant createdAt;
        private Instant lastUsed;
        private boolean active;
    }
    
    /**
     * Custom authentication tokens
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class JwtAuthenticationToken {
        private Claims claims;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ApiKeyAuthenticationToken {
        private String apiKey;
    }
}