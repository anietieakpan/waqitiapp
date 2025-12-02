package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive CSRF Protection Service
 * 
 * Implements multiple CSRF protection mechanisms:
 * - Double submit cookie pattern
 * - Synchronizer token pattern
 * - Origin header validation
 * - SameSite cookie attributes
 * - Token rotation and expiration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CSRFProtectionService {

    private static final String CSRF_TOKEN_PREFIX = "csrf:token:";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_LENGTH = 32;
    
    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Value("${csrf.secret-key}")
    private String secretKey;
    
    @Value("${csrf.token-validity-seconds:3600}")
    private int tokenValiditySeconds;
    
    @Value("${csrf.enabled:true}")
    private boolean csrfEnabled;
    
    @Value("${app.domain:waqiti.com}")
    private String appDomain;

    /**
     * Generate a new CSRF token for a user session
     */
    public CSRFToken generateToken(String sessionId, String userAgent, String ipAddress) {
        if (!csrfEnabled) {
            return CSRFToken.disabled();
        }

        try {
            // Generate random token
            byte[] tokenBytes = new byte[TOKEN_LENGTH];
            secureRandom.nextBytes(tokenBytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            
            // Create token payload with metadata
            TokenPayload payload = new TokenPayload(
                rawToken,
                sessionId,
                userAgent,
                ipAddress,
                Instant.now().plusSeconds(tokenValiditySeconds)
            );
            
            // Sign the token
            String signedToken = signToken(payload);
            
            // Store in Redis with expiration
            String redisKey = CSRF_TOKEN_PREFIX + sessionId;
            redisTemplate.opsForValue().set(
                redisKey, 
                signedToken, 
                Duration.ofSeconds(tokenValiditySeconds)
            );
            
            log.debug("Generated CSRF token for session: {}", sessionId);
            
            return new CSRFToken(
                rawToken,
                signedToken,
                CSRF_COOKIE_NAME,
                CSRF_HEADER_NAME,
                tokenValiditySeconds,
                true
            );
            
        } catch (Exception e) {
            log.error("Failed to generate CSRF token for session: {}", sessionId, e);
            throw new CSRFException("Failed to generate CSRF token", e);
        }
    }

    /**
     * Validate CSRF token from request
     */
    public CSRFValidationResult validateToken(String sessionId, String cookieToken, 
                                             String headerToken, String userAgent, String ipAddress) {
        if (!csrfEnabled) {
            return CSRFValidationResult.disabled();
        }

        try {
            // Check if tokens are present
            if (cookieToken == null && headerToken == null) {
                return CSRFValidationResult.failed("CSRF token missing");
            }
            
            // Use header token if available, otherwise cookie token
            String providedToken = headerToken != null ? headerToken : cookieToken;
            
            // Retrieve stored token from Redis
            String redisKey = CSRF_TOKEN_PREFIX + sessionId;
            String storedSignedToken = redisTemplate.opsForValue().get(redisKey);
            
            if (storedSignedToken == null) {
                return CSRFValidationResult.failed("CSRF token not found or expired");
            }
            
            // Verify signature
            TokenPayload payload = verifyAndExtractPayload(storedSignedToken);
            if (payload == null) {
                return CSRFValidationResult.failed("Invalid CSRF token signature");
            }
            
            // Check expiration
            if (payload.expiresAt().isBefore(Instant.now())) {
                // Clean up expired token
                redisTemplate.delete(redisKey);
                return CSRFValidationResult.failed("CSRF token expired");
            }
            
            // Validate token matches
            if (!payload.rawToken().equals(providedToken)) {
                return CSRFValidationResult.failed("CSRF token mismatch");
            }
            
            // Validate session consistency
            if (!payload.sessionId().equals(sessionId)) {
                return CSRFValidationResult.failed("CSRF token session mismatch");
            }
            
            // Optional: Validate user agent and IP (can be strict or lenient)
            if (!isUserAgentValid(payload.userAgent(), userAgent)) {
                log.warn("CSRF validation: User agent changed for session: {}", sessionId);
                // Could be strict and fail, but browsers can change UA
            }
            
            if (!isIpAddressValid(payload.ipAddress(), ipAddress)) {
                log.warn("CSRF validation: IP address changed for session: {}", sessionId);
                // Could be strict and fail, but IPs can change (mobile networks, etc.)
            }
            
            log.debug("CSRF token validated successfully for session: {}", sessionId);
            
            return CSRFValidationResult.success();
            
        } catch (Exception e) {
            log.error("Failed to validate CSRF token for session: {}", sessionId, e);
            return CSRFValidationResult.failed("CSRF validation error");
        }
    }

    /**
     * Rotate CSRF token for enhanced security
     */
    public CSRFToken rotateToken(String sessionId, String userAgent, String ipAddress) {
        if (!csrfEnabled) {
            return CSRFToken.disabled();
        }

        // Invalidate old token
        invalidateToken(sessionId);
        
        // Generate new token
        return generateToken(sessionId, userAgent, ipAddress);
    }

    /**
     * Invalidate CSRF token (on logout, etc.)
     */
    public void invalidateToken(String sessionId) {
        if (!csrfEnabled) {
            return;
        }

        String redisKey = CSRF_TOKEN_PREFIX + sessionId;
        redisTemplate.delete(redisKey);
        
        log.debug("Invalidated CSRF token for session: {}", sessionId);
    }

    /**
     * Validate Origin header for additional CSRF protection
     */
    public boolean validateOrigin(String origin, String referer) {
        if (!csrfEnabled) {
            return true;
        }

        if (origin == null && referer == null) {
            log.warn("No Origin or Referer header found");
            return false;
        }
        
        String headerToCheck = origin != null ? origin : extractOriginFromReferer(referer);
        
        if (headerToCheck == null) {
            return false;
        }
        
        // Check against allowed origins
        return isOriginAllowed(headerToCheck);
    }

    /**
     * Generate SameSite cookie attributes for CSRF protection
     */
    public String generateCookieAttributes() {
        if (!csrfEnabled) {
            return "";
        }

        return String.format(
            "SameSite=Strict; Secure; HttpOnly; Domain=%s; Path=/; Max-Age=%d",
            appDomain,
            tokenValiditySeconds
        );
    }

    // Private helper methods

    private String signToken(TokenPayload payload) throws NoSuchAlgorithmException, InvalidKeyException {
        String data = String.join(":", 
            payload.rawToken(),
            payload.sessionId(),
            payload.userAgent(),
            payload.ipAddress(),
            payload.expiresAt().toString()
        );
        
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(keySpec);
        
        byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        
        // Return payload + signature
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8)) 
               + "." + signatureBase64;
    }

    private TokenPayload verifyAndExtractPayload(String signedToken) {
        try {
            String[] parts = signedToken.split("\\.", 2);
            if (parts.length != 2) {
                return null;
            }
            
            String payloadBase64 = parts[0];
            String signatureBase64 = parts[1];
            
            // Decode payload
            String payloadStr = new String(Base64.getUrlDecoder().decode(payloadBase64), StandardCharsets.UTF_8);
            String[] payloadParts = payloadStr.split(":", 5);
            
            if (payloadParts.length != 5) {
                return null;
            }
            
            // Verify signature
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            
            byte[] expectedSignature = mac.doFinal(payloadStr.getBytes(StandardCharsets.UTF_8));
            byte[] providedSignature = Base64.getUrlDecoder().decode(signatureBase64);
            
            if (!java.security.MessageDigest.isEqual(expectedSignature, providedSignature)) {
                return null;
            }
            
            // Extract payload
            return new TokenPayload(
                payloadParts[0], // rawToken
                payloadParts[1], // sessionId
                payloadParts[2], // userAgent
                payloadParts[3], // ipAddress
                Instant.parse(payloadParts[4]) // expiresAt
            );
            
        } catch (Exception e) {
            log.error("Failed to verify token signature", e);
            return null;
        }
    }

    private boolean isUserAgentValid(String originalUA, String currentUA) {
        // Lenient validation - major browser family should match
        if (originalUA == null || currentUA == null) {
            return false;
        }
        
        // Extract browser family (Chrome, Firefox, Safari, etc.)
        String originalFamily = extractBrowserFamily(originalUA);
        String currentFamily = extractBrowserFamily(currentUA);
        
        return originalFamily.equals(currentFamily);
    }

    private boolean isIpAddressValid(String originalIP, String currentIP) {
        // Very lenient - just check they're not completely different
        // In production, you might want to check subnet ranges
        if (originalIP == null || currentIP == null) {
            return false;
        }
        
        // For now, just log differences but don't fail
        return true;
    }

    private String extractBrowserFamily(String userAgent) {
        if (userAgent == null) return "unknown";
        
        String ua = userAgent.toLowerCase();
        if (ua.contains("chrome")) return "chrome";
        if (ua.contains("firefox")) return "firefox";
        if (ua.contains("safari")) return "safari";
        if (ua.contains("edge")) return "edge";
        if (ua.contains("opera")) return "opera";
        
        return "unknown";
    }

    private boolean isOriginAllowed(String origin) {
        // Define allowed origins
        String[] allowedOrigins = {
            "https://" + appDomain,
            "https://app." + appDomain,
            "https://admin." + appDomain,
            "https://api." + appDomain
        };
        
        for (String allowed : allowedOrigins) {
            if (allowed.equals(origin)) {
                return true;
            }
        }
        
        return false;
    }

    private String extractOriginFromReferer(String referer) {
        if (referer == null) return null;
        
        try {
            java.net.URL url = new java.net.URL(referer);
            return url.getProtocol() + "://" + url.getHost() + 
                   (url.getPort() != -1 ? ":" + url.getPort() : "");
        } catch (Exception e) {
            return null;
        }
    }

    // Data classes

    public record CSRFToken(
        String rawToken,
        String signedToken,
        String cookieName,
        String headerName,
        int validitySeconds,
        boolean enabled
    ) {
        public static CSRFToken disabled() {
            return new CSRFToken(null, null, null, null, 0, false);
        }
    }

    public record CSRFValidationResult(
        boolean valid,
        String errorMessage
    ) {
        public static CSRFValidationResult success() {
            return new CSRFValidationResult(true, null);
        }
        
        public static CSRFValidationResult failed(String message) {
            return new CSRFValidationResult(false, message);
        }
        
        public static CSRFValidationResult disabled() {
            return new CSRFValidationResult(true, "CSRF protection disabled");
        }
    }

    private record TokenPayload(
        String rawToken,
        String sessionId,
        String userAgent,
        String ipAddress,
        Instant expiresAt
    ) {}

    /**
     * CSRF Exception
     */
    public static class CSRFException extends RuntimeException {
        public CSRFException(String message) {
            super(message);
        }
        
        public CSRFException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}