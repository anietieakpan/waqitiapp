package com.waqiti.security.converter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * User Information extracted from Keycloak JWT token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    
    // Core user identifiers
    private String userId;
    private String username;
    private String email;
    
    // Personal information
    private String firstName;
    private String lastName;
    private String fullName;
    
    // Verification status
    private Boolean emailVerified;
    
    // Session information
    private String sessionId;
    private Instant issuedAt;
    private Instant expiresAt;
    
    // Token metadata
    private String issuer;
    private List<String> audience;
    private Map<String, Object> claims;
    
    // Convenience methods
    public String getDisplayName() {
        if (fullName != null && !fullName.trim().isEmpty()) {
            return fullName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (username != null) {
            return username;
        }
        return email;
    }
    
    public boolean isEmailVerified() {
        return emailVerified != null && emailVerified;
    }
    
    public boolean isTokenExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
    
    public Object getClaim(String claimName) {
        return claims != null ? claims.get(claimName) : null;
    }
    
    public String getClaimAsString(String claimName) {
        Object claim = getClaim(claimName);
        return claim != null ? claim.toString() : null;
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getClaimAsList(String claimName) {
        Object claim = getClaim(claimName);
        if (claim instanceof List) {
            return (List<String>) claim;
        }
        return List.of();
    }
}