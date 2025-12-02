package com.waqiti.common.security.dto;

import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Token validation result with comprehensive details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationResult {
    
    private boolean valid;
    private boolean expired;
    private String errorMessage;
    private Claims claims;
    private String subject;
    private List<String> authorities;
    private Map<String, Object> additionalClaims;
    private Instant issuedAt;
    private Instant expiresAt;
    private String tokenType;
    private String keyVersion;
    
    /**
     * Create a valid result
     */
    public static TokenValidationResult valid(Claims claims) {
        return TokenValidationResult.builder()
            .valid(true)
            .expired(false)
            .claims(claims)
            .subject(claims.getSubject())
            .issuedAt(claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null)
            .expiresAt(claims.getExpiration() != null ? claims.getExpiration().toInstant() : null)
            .tokenType(claims.get("token_type", String.class))
            .keyVersion(claims.get("key_version", String.class))
            .build();
    }
    
    /**
     * Create an expired result
     */
    public static TokenValidationResult expired() {
        return TokenValidationResult.builder()
            .valid(false)
            .expired(true)
            .errorMessage("Token has expired")
            .build();
    }
    
    /**
     * Create an invalid result
     */
    public static TokenValidationResult invalid(String errorMessage) {
        return TokenValidationResult.builder()
            .valid(false)
            .expired(false)
            .errorMessage(errorMessage)
            .build();
    }
    
    /**
     * Check if token is an access token
     */
    public boolean isAccessToken() {
        return "access_token".equals(tokenType);
    }
    
    /**
     * Check if token is a refresh token
     */
    public boolean isRefreshToken() {
        return "refresh_token".equals(tokenType);
    }
}