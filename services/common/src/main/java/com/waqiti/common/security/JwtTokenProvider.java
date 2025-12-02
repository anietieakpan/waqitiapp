package com.waqiti.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT Token Provider for authentication and authorization
 * Supports both legacy and modern JWT token formats
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String AUTHORITIES_KEY = "authorities";
    private static final String USER_ID_KEY = "userId";
    private static final String USERNAME_KEY = "username";
    private static final String EMAIL_KEY = "email";
    private static final String ROLES_KEY = "roles";
    private static final String SCOPES_KEY = "scopes";
    private static final String TENANT_KEY = "tenant";
    private static final String SESSION_KEY = "sessionId";
    private static final String DEVICE_KEY = "deviceId";
    private static final String IP_ADDRESS_KEY = "ipAddress";

    @Autowired(required = false)
    private SecureJwtConfigurationService secureJwtConfigurationService;

    @Value("${jwt.vault.enabled:true}")
    private boolean vaultEnabled;

    @Value("${jwt.secret:#{null}}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:86400000}") // 24 hours
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration-ms:604800000}") // 7 days
    private long refreshExpirationMs;

    @Value("${jwt.issuer:waqiti}")
    private String issuer;

    @Value("${jwt.audience:waqiti-services}")
    private String audience;

    @Value("${jwt.algorithm:HS512}")
    private String algorithm;

    @Value("${jwt.legacy.enabled:true}")
    private boolean legacyTokensEnabled;

    @Value("${jwt.legacy.secret:}")
    private String legacyJwtSecret;

    private SecretKey signingKey;
    private SecretKey legacySigningKey;

    /**
     * Initialize signing keys with enhanced security
     */
    public void init() {
        if (vaultEnabled && secureJwtConfigurationService != null) {
            // Use Vault-based secure configuration
            try {
                String currentSecret = secureJwtConfigurationService.getCurrentJwtSecret();
                if (currentSecret == null || !secureJwtConfigurationService.isValidSecret(currentSecret)) {
                    throw new IllegalStateException("Failed to retrieve valid JWT secret from Vault");
                }
                
                this.signingKey = Keys.hmacShaKeyFor(currentSecret.getBytes(StandardCharsets.UTF_8));
                
                // Initialize legacy key if available
                if (legacyTokensEnabled) {
                    String legacySecret = secureJwtConfigurationService.getLegacyJwtSecret();
                    if (legacySecret != null && secureJwtConfigurationService.isValidSecret(legacySecret)) {
                        this.legacySigningKey = Keys.hmacShaKeyFor(legacySecret.getBytes(StandardCharsets.UTF_8));
                    }
                }
                
                log.info("JWT Token Provider initialized with Vault-managed secrets and algorithm: {}", algorithm);
                return;
                
            } catch (Exception e) {
                log.error("Failed to initialize JWT with Vault configuration, falling back to configuration-based", e);
                // Fall through to configuration-based initialization
            }
        }

        // Fallback to configuration-based initialization
        initializeFromConfiguration();
    }

    /**
     * Initialize from configuration properties (fallback method)
     */
    private void initializeFromConfiguration() {
        // Validate JWT secret is provided
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT secret must be provided via configuration. " +
                "Set jwt.secret property with a cryptographically secure value of at least 512 bits (64 characters). " +
                "Consider using Vault by setting jwt.vault.enabled=true for enhanced security.");
        }

        // Validate minimum key length for security
        if (jwtSecret.length() < 64) {
            throw new IllegalStateException("JWT secret must be at least 64 characters (512 bits) for HS512 algorithm. " +
                "Current length: " + jwtSecret.length() + ". Use a cryptographically secure random generator.");
        }

        // Validate key entropy (basic check)
        if (isWeakKey(jwtSecret)) {
            throw new IllegalStateException("JWT secret appears to be weak or predictable. " +
                "Use a cryptographically secure random string generator.");
        }

        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        // Initialize legacy signing key if configured with same security checks
        if (legacyTokensEnabled && legacyJwtSecret != null && !legacyJwtSecret.isEmpty()) {
            if (legacyJwtSecret.length() < 64) {
                throw new IllegalStateException("Legacy JWT secret must also be at least 64 characters (512 bits).");
            }
            if (isWeakKey(legacyJwtSecret)) {
                throw new IllegalStateException("Legacy JWT secret appears to be weak or predictable.");
            }
            this.legacySigningKey = Keys.hmacShaKeyFor(legacyJwtSecret.getBytes(StandardCharsets.UTF_8));
        }

        log.warn("JWT Token Provider initialized with configuration-based secrets. Consider using Vault for enhanced security.");
    }

    /**
     * ENHANCED: Production-ready weak key detection
     *
     * Implements multiple security checks:
     * 1. Shannon entropy calculation (information theory)
     * 2. Dictionary word detection
     * 3. Repeated character detection
     * 4. Sequential character detection
     * 5. Character class diversity
     *
     * Based on NIST SP 800-63B guidelines for password/secret strength
     */
    private boolean isWeakKey(String key) {
        // 1. Shannon Entropy Check (Primary security measure)
        double entropy = calculateShannonEntropy(key);
        if (entropy < 5.5) {
            log.error("SECURITY_CRITICAL: JWT secret has insufficient entropy: {}/8.0 bits/char " +
                "(NIST recommends >= 5.5 for strong secrets)", String.format("%.2f", entropy));
            return true;
        }

        // 2. Dictionary Word Detection (with word list)
        if (containsDictionaryWords(key)) {
            log.error("SECURITY_CRITICAL: JWT secret contains dictionary words - vulnerable to attacks");
            return true;
        }

        // 3. Check for repeated characters (reduced tolerance)
        if (key.matches("(.)\\1{5,}")) { // 5+ repeated characters (more strict)
            log.error("SECURITY_CRITICAL: JWT secret contains repeated characters");
            return true;
        }

        // 4. Check for sequential characters
        if (containsSequentialChars(key, 6)) {
            log.error("SECURITY_CRITICAL: JWT secret contains sequential characters");
            return true;
        }

        // 5. Character class diversity check
        if (!hasCharacterDiversity(key)) {
            log.error("SECURITY_CRITICAL: JWT secret lacks character diversity (needs mix of upper/lower/numbers/symbols)");
            return true;
        }

        log.info("JWT secret passed all security validations (entropy: {}/8.0 bits/char)",
            String.format("%.2f", entropy));
        return false;
    }

    /**
     * Calculate Shannon entropy (information theory measure)
     *
     * Formula: H(X) = -Σ p(x) * log₂(p(x))
     * where p(x) is the probability of character x
     *
     * Returns: Entropy in bits per character (0-8 for byte strings)
     * - < 4.0: Weak (predictable patterns)
     * - 4.0-5.5: Medium (some randomness)
     * - >= 5.5: Strong (high randomness) ← REQUIRED
     * - ~7.2: Maximum practical for printable ASCII
     * - 8.0: Theoretical maximum
     */
    private double calculateShannonEntropy(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        // Count character frequencies
        Map<Character, Integer> frequencies = new HashMap<>();
        for (char c : text.toCharArray()) {
            frequencies.merge(c, 1, Integer::sum);
        }

        // Calculate entropy using Shannon's formula
        double entropy = 0.0;
        int length = text.length();

        for (int frequency : frequencies.values()) {
            double probability = (double) frequency / length;
            if (probability > 0) {
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }

        return entropy;
    }

    /**
     * Detect dictionary words that reduce secret strength
     *
     * Uses comprehensive word list of:
     * - Common dictionary words
     * - Technical terms
     * - Product/company names
     * - Weak patterns
     */
    private boolean containsDictionaryWords(String key) {
        String lowerKey = key.toLowerCase();

        // Common weak strings (expanded list)
        String[] weakPatterns = {
            // Authentication terms
            "default", "secret", "password", "passphrase", "key", "token", "jwt",
            "auth", "authentication", "authorization", "bearer", "oauth", "api",

            // Company/Product
            "waqiti", "fintech", "payment", "wallet", "banking", "finance",

            // Roles
            "admin", "administrator", "user", "superuser", "root", "system",

            // Environments
            "test", "testing", "dev", "develop", "development", "prod", "production",
            "staging", "stage", "demo", "local", "localhost",

            // Weak patterns
            "12345", "abcde", "qwerty", "asdf", "zxcv", "password1", "letmein",
            "welcome", "monkey", "dragon", "master", "sunshine", "princess",

            // Sequential
            "abc", "xyz", "123", "789", "000", "111",

            // Common substitutions (bypass attempts)
            "p@ssw0rd", "s3cr3t", "k3y", "t0k3n", "adm1n"
        };

        for (String pattern : weakPatterns) {
            if (lowerKey.contains(pattern)) {
                return true;
            }
        }

        // Check for common keyboard patterns
        String[] keyboardPatterns = {
            "qwerty", "asdfgh", "zxcvbn", "qazwsx", "1qaz2wsx",
            "!qaz@wsx", "1234qwer", "asdf1234"
        };

        for (String pattern : keyboardPatterns) {
            if (lowerKey.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check for character class diversity
     * Strong secrets should use multiple character classes
     */
    private boolean hasCharacterDiversity(String key) {
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : key.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        // Require at least 3 out of 4 character classes
        int classCount = (hasLower ? 1 : 0) + (hasUpper ? 1 : 0) +
                        (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);

        return classCount >= 3;
    }

    /**
     * Check for sequential characters that indicate weak randomness
     */
    private boolean containsSequentialChars(String key, int minLength) {
        for (int i = 0; i <= key.length() - minLength; i++) {
            boolean isSequential = true;
            for (int j = 1; j < minLength; j++) {
                if (key.charAt(i + j) != key.charAt(i + j - 1) + 1) {
                    isSequential = false;
                    break;
                }
            }
            if (isSequential) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate access token
     */
    public String generateToken(Authentication authentication) {
        return generateToken(authentication, jwtExpirationMs);
    }

    /**
     * Generate token with custom expiration
     */
    public String generateToken(Authentication authentication, long expirationMs) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        return generateToken(userPrincipal, expirationMs, TokenType.ACCESS);
    }

    /**
     * Generate refresh token
     */
    public String generateRefreshToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        return generateToken(userPrincipal, refreshExpirationMs, TokenType.REFRESH);
    }

    /**
     * Generate token for user details
     */
    public String generateToken(UserDetails userDetails, long expirationMs, TokenType tokenType) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationMs, ChronoUnit.MILLIS);

        // Extract authorities
        Set<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        JwtBuilder builder = Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .setId(UUID.randomUUID().toString())
                .claim(USERNAME_KEY, userDetails.getUsername())
                .claim(AUTHORITIES_KEY, authorities)
                .claim("tokenType", tokenType.name())
                .signWith(getSigningKey(), SignatureAlgorithm.valueOf(algorithm));

        // Add additional claims if available
        if (userDetails instanceof EnhancedUserDetails) {
            EnhancedUserDetails enhancedUser = (EnhancedUserDetails) userDetails;

            if (enhancedUser.getUserId() != null) {
                builder.claim(USER_ID_KEY, enhancedUser.getUserId());
            }
            if (enhancedUser.getEmail() != null) {
                builder.claim(EMAIL_KEY, enhancedUser.getEmail());
            }
            if (enhancedUser.getRoles() != null && !enhancedUser.getRoles().isEmpty()) {
                builder.claim(ROLES_KEY, enhancedUser.getRoles());
            }
            if (enhancedUser.getScopes() != null && !enhancedUser.getScopes().isEmpty()) {
                builder.claim(SCOPES_KEY, enhancedUser.getScopes());
            }
            if (enhancedUser.getTenantId() != null) {
                builder.claim(TENANT_KEY, enhancedUser.getTenantId());
            }
            if (enhancedUser.getSessionId() != null) {
                builder.claim(SESSION_KEY, enhancedUser.getSessionId());
            }
            if (enhancedUser.getDeviceId() != null) {
                builder.claim(DEVICE_KEY, enhancedUser.getDeviceId());
            }
            if (enhancedUser.getIpAddress() != null) {
                builder.claim(IP_ADDRESS_KEY, enhancedUser.getIpAddress());
            }
        }

        return builder.compact();
    }

    /**
     * Validate JWT token with enhanced security checks
     */
    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.debug("JWT token is null or empty");
            return false;
        }

        // Basic format validation
        if (!isValidTokenFormat(token)) {
            log.warn("JWT token has invalid format");
            return false;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith((SecretKey) getSigningKey())
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Additional security validations
            return performAdditionalValidations(claims);

        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token arguments are invalid: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during JWT validation: {}", e.getMessage());
        }

        // Try legacy key if primary validation failed and legacy is enabled
        if (legacyTokensEnabled && legacySigningKey != null) {
            return validateTokenWithLegacyKey(token);
        }

        return false;
    }

    /**
     * Validate basic JWT token format
     */
    private boolean isValidTokenFormat(String token) {
        String[] parts = token.split("\\.");
        return parts.length == 3 && 
               parts[0].length() > 0 && 
               parts[1].length() > 0 && 
               parts[2].length() > 0;
    }

    /**
     * Perform additional security validations on claims
     */
    private boolean performAdditionalValidations(Claims claims) {
        // Validate token ID exists (prevents replay attacks)
        String jti = claims.getId();
        if (jti == null || jti.trim().isEmpty()) {
            log.warn("JWT token missing JTI (token ID)");
            return false;
        }

        // Validate issued at time is not in the future
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt != null && issuedAt.after(new Date(System.currentTimeMillis() + 60000))) { // 1 minute tolerance
            log.warn("JWT token issued in the future");
            return false;
        }

        // Validate not before claim if present
        Date notBefore = claims.getNotBefore();
        if (notBefore != null && notBefore.after(new Date())) {
            log.warn("JWT token not yet valid (not before constraint)");
            return false;
        }

        return true;
    }

    /**
     * Validate token with legacy signing key
     */
    private boolean validateTokenWithLegacyKey(String token) {
        try {
            Jwts.parser()
                    .verifyWith((SecretKey) legacySigningKey)
                    .build()
                    .parseSignedClaims(token);
            log.debug("Token validated with legacy key");
            return true;
        } catch (Exception e) {
            log.debug("Legacy token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract username from JWT token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * Extract user ID from JWT token
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get(USER_ID_KEY, String.class) : null;
    }

    /**
     * Extract email from JWT token
     */
    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get(EMAIL_KEY, String.class) : null;
    }

    /**
     * Extract authorities from JWT token
     */
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> getAuthoritiesFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return Collections.emptyList();
        }

        Collection<String> authorities = claims.get(AUTHORITIES_KEY, Collection.class);
        if (authorities == null || authorities.isEmpty()) {
            return Collections.emptyList();
        }

        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /**
     * Extract roles from JWT token
     */
    @SuppressWarnings("unchecked")
    public Set<String> getRolesFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return Collections.emptySet();
        }

        Collection<String> roles = claims.get(ROLES_KEY, Collection.class);
        return roles != null ? new HashSet<>(roles) : Collections.emptySet();
    }

    /**
     * Extract scopes from JWT token
     */
    @SuppressWarnings("unchecked")
    public Set<String> getScopesFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return Collections.emptySet();
        }

        Collection<String> scopes = claims.get(SCOPES_KEY, Collection.class);
        return scopes != null ? new HashSet<>(scopes) : Collections.emptySet();
    }

    /**
     * Extract tenant ID from JWT token
     */
    public String getTenantIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get(TENANT_KEY, String.class) : null;
    }

    /**
     * Extract session ID from JWT token
     */
    public String getSessionIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get(SESSION_KEY, String.class) : null;
    }

    /**
     * Extract device ID from JWT token
     */
    public String getDeviceIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get(DEVICE_KEY, String.class) : null;
    }

    /**
     * Extract IP address from JWT token
     */
    public String getIpAddressFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get(IP_ADDRESS_KEY, String.class) : null;
    }

    /**
     * Get token expiration
     */
    public Date getExpirationFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getExpiration() : null;
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        Date expiration = getExpirationFromToken(token);
        return expiration != null && expiration.before(new Date());
    }

    /**
     * Get time until token expiration in seconds
     */
    public long getTokenExpirationSeconds(String token) {
        Date expiration = getExpirationFromToken(token);
        if (expiration == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long expirationTime = expiration.getTime();
        return Math.max(0, (expirationTime - now) / 1000);
    }

    /**
     * Get authentication from JWT token
     */
    public Authentication getAuthentication(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }

        String username = claims.getSubject();
        Collection<GrantedAuthority> authorities = getAuthoritiesFromToken(token);

        UserDetails userDetails = User.builder()
                .username(username)
                .password("") // Password not needed for token-based auth
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(userDetails, token, authorities);
    }

    /**
     * Refresh token (create new token with updated expiration)
     */
    public String refreshToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }

        String username = claims.getSubject();
        Collection<GrantedAuthority> authorities = getAuthoritiesFromToken(token);

        UserDetails userDetails = User.builder()
                .username(username)
                .password("")
                .authorities(authorities)
                .build();

        return generateToken(userDetails, jwtExpirationMs, TokenType.ACCESS);
    }

    /**
     * Extract all claims from token
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith((SecretKey) getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // Try with legacy key
            if (legacyTokensEnabled && legacySigningKey != null) {
                return getClaimsWithLegacyKey(token);
            }
            log.debug("Failed to extract claims from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract claims with legacy signing key
     */
    private Claims getClaimsWithLegacyKey(String token) {
        try {
            return Jwts.parser()
                    .verifyWith((SecretKey) legacySigningKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("Failed to extract claims with legacy key: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create token for service-to-service communication
     */
    public String generateServiceToken(String serviceId, Set<String> scopes) {
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtExpirationMs, ChronoUnit.MILLIS);

        return Jwts.builder()
                .setSubject(serviceId)
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .setId(UUID.randomUUID().toString())
                .claim("serviceId", serviceId)
                .claim(SCOPES_KEY, scopes)
                .claim("tokenType", TokenType.SERVICE.name())
                .signWith(getSigningKey(), SignatureAlgorithm.valueOf(algorithm))
                .compact();
    }

    /**
     * Validate service token
     */
    public boolean validateServiceToken(String token, String expectedServiceId) {
        if (!validateToken(token)) {
            return false;
        }

        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return false;
        }

        String tokenType = claims.get("tokenType", String.class);
        String serviceId = claims.get("serviceId", String.class);

        return TokenType.SERVICE.name().equals(tokenType) &&
                expectedServiceId.equals(serviceId);
    }

    private SecretKey getSigningKey() {
        if (signingKey == null) {
            init();
        }
        
        // Refresh key from Vault if enabled and service is available
        if (vaultEnabled && secureJwtConfigurationService != null) {
            try {
                String currentSecret = secureJwtConfigurationService.getCurrentJwtSecret();
                if (currentSecret != null && secureJwtConfigurationService.isValidSecret(currentSecret)) {
                    // Update signing key if it has changed (key rotation)
                    SecretKey newKey = Keys.hmacShaKeyFor(currentSecret.getBytes(StandardCharsets.UTF_8));
                    if (!newKey.equals(signingKey)) {
                        log.debug("JWT signing key updated from Vault (key rotation detected)");
                        this.signingKey = newKey;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to refresh JWT key from Vault, using cached key: {}", e.getMessage());
            }
        }
        
        return signingKey;
    }

    /**
     * Extract claims from a JWT token
     * Public method that provides secure access to token claims without exposing the signing key
     *
     * @param token JWT token string
     * @return Claims from the token
     * @throws RuntimeException if token is invalid or expired
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Token types
     */
    public enum TokenType {
        ACCESS,
        REFRESH,
        SERVICE
    }

    /**
     * Enhanced user details interface
     */
    public interface EnhancedUserDetails extends UserDetails {
        String getUserId();
        String getEmail();
        Set<String> getRoles();
        Set<String> getScopes();
        String getTenantId();
        String getSessionId();
        String getDeviceId();
        String getIpAddress();
    }
}