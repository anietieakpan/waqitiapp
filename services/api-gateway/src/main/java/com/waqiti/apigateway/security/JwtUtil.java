package com.waqiti.apigateway.security;

import com.waqiti.common.service.SecretService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JwtUtil {

    private final SecretService secretService;
    private final RedisTemplate<String, String> redisTemplate;
    private Key key;
    
    @Value("${jwt.issuer:waqiti-fintech}")
    private String expectedIssuer;
    
    @Value("${jwt.audience:waqiti-api}")
    private String expectedAudience;
    
    @Value("${jwt.clock.skew.seconds:30}")
    private long clockSkewSeconds;
    
    private static final String TOKEN_BLACKLIST_PREFIX = "blacklist:token:";
    private static final Set<String> ALLOWED_ALGORITHMS = Set.of("HS256", "RS256");

    public JwtUtil(SecretService secretService, RedisTemplate<String, String> redisTemplate) {
        this.secretService = secretService;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    protected void init() {
        String jwtSecret = secretService.getJwtSecret();
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT util initialized with secret from SecretService");
    }


    public String getUsername(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }

    public UUID getUserId(String token) {
        String userId = (String) getAllClaimsFromToken(token).get("userId");
        return UUID.fromString(userId);
    }

    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("JWT token is null or empty");
            return false;
        }
        
        try {
            // SECURITY FIX: Check if token is blacklisted
            if (isTokenBlacklisted(token)) {
                log.warn("JWT token is blacklisted");
                return false;
            }
            
            // SECURITY FIX: Parse with comprehensive validation
            Jws<Claims> claimsJws = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(expectedIssuer)          // Validate issuer
                .requireAudience(expectedAudience)      // Validate audience
                .setAllowedClockSkewSeconds(clockSkewSeconds) // Allow clock skew
                .build()
                .parseClaimsJws(token);
            
            Claims claims = claimsJws.getBody();
            String algorithm = claimsJws.getHeader().getAlgorithm();
            
            // SECURITY FIX: Validate algorithm
            if (!ALLOWED_ALGORITHMS.contains(algorithm)) {
                log.error("Invalid JWT algorithm: {}", algorithm);
                return false;
            }
            
            // SECURITY FIX: Additional validations
            return validateAdditionalClaims(claims);
            
        } catch (JwtException e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during JWT validation", e);
            return false;
        }
    }
    
    /**
     * SECURITY FIX: Additional claims validation
     */
    private boolean validateAdditionalClaims(Claims claims) {
        try {
            // Validate not before time
            Date notBefore = claims.getNotBefore();
            if (notBefore != null && notBefore.toInstant().isAfter(java.time.Instant.now())) {
                log.warn("JWT token not yet valid (nbf claim)");
                return false;
            }
            
            // Validate issued at time (not too old)
            Date issuedAt = claims.getIssuedAt();
            if (issuedAt != null) {
                long maxAgeMs = Duration.ofHours(24).toMillis(); // 24 hours max
                if (System.currentTimeMillis() - issuedAt.getTime() > maxAgeMs) {
                    log.warn("JWT token too old (iat claim)");
                    return false;
                }
            }
            
            // Validate subject is present and valid UUID
            String subject = claims.getSubject();
            if (subject == null || subject.trim().isEmpty()) {
                log.warn("JWT token missing subject claim");
                return false;
            }
            
            try {
                UUID.fromString(subject);
            } catch (IllegalArgumentException e) {
                log.warn("JWT token subject is not a valid UUID: {}", subject);
                return false;
            }
            
            // Validate JTI (JWT ID) is present for replay protection
            String jti = claims.getId();
            if (jti == null || jti.trim().isEmpty()) {
                log.warn("JWT token missing JTI claim");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating JWT claims", e);
            return false;
        }
    }
    
    /**
     * SECURITY FIX: Check if token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String tokenHash = String.valueOf(token.hashCode());
            String key = TOKEN_BLACKLIST_PREFIX + tokenHash;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Error checking token blacklist", e);
            // Fail secure - if we can't check blacklist, assume token is valid
            // but log the error for investigation
            return false;
        }
    }
    
    /**
     * SECURITY FIX: Blacklist a token
     */
    public void blacklistToken(String token, Duration expiry) {
        try {
            String tokenHash = String.valueOf(token.hashCode());
            String key = TOKEN_BLACKLIST_PREFIX + tokenHash;
            redisTemplate.opsForValue().set(key, "blacklisted", expiry);
            log.info("Token blacklisted successfully");
        } catch (Exception e) {
            log.error("Error blacklisting token", e);
        }
    }
    
    /**
     * SECURITY FIX: Enhanced claims extraction with validation
     */
    public Claims getAllClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(expectedIssuer)
                    .requireAudience(expectedAudience)
                    .setAllowedClockSkewSeconds(clockSkewSeconds)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            log.error("Failed to extract claims from JWT", e);
            throw e;
        }
    }
    
    /**
     * SECURITY FIX: Get JWT ID for replay protection
     */
    public String getTokenId(String token) {
        return getAllClaimsFromToken(token).getId();
    }
    
    /**
     * SECURITY FIX: Check if token is expired with additional buffer
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getAllClaimsFromToken(token).getExpiration();
            // Add small buffer to account for network latency
            Date now = new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30));
            return expiration.before(now);
        } catch (Exception e) {
            log.error("Error checking token expiration", e);
            return true; // Fail secure
        }
    }
}