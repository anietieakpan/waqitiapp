package com.waqiti.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token Validator
 *
 * Validates JWT tokens and extracts claims.
 * Works in conjunction with JwtTokenRevocationService for complete token lifecycle management.
 *
 * @author Waqiti Security Team
 */
@Component
@Slf4j
public class JwtTokenValidator {

    private final SecretKey secretKey;
    private final String issuer;

    public JwtTokenValidator(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer:waqiti}") String issuer) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    /**
     * Validate a JWT token
     *
     * @param token JWT token string
     * @return true if valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);

            return true;

        } catch (ExpiredJwtException e) {
            log.warn("SECURITY: Expired JWT token");
            return false;
        } catch (MalformedJwtException e) {
            log.warn("SECURITY: Malformed JWT token");
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("SECURITY: Unsupported JWT token");
            return false;
        } catch (SignatureException e) {
            log.warn("SECURITY: Invalid JWT signature");
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("SECURITY: JWT claims string is empty");
            return false;
        } catch (Exception e) {
            log.error("SECURITY ERROR: JWT validation failed", e);
            return false;
        }
    }

    /**
     * Extract claims from a JWT token
     *
     * @param token JWT token string
     * @return Claims object
     * @throws RuntimeException if token is invalid
     */
    public Claims extractClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        } catch (Exception e) {
            log.error("SECURITY: Failed to extract JWT claims", e);
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    /**
     * Extract user ID from token
     */
    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true; // Treat any error as expired (fail-secure)
        }
    }
}
