package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

/**
 * Result of token validation containing user information and authorities
 */
@Data
@Builder
public class TokenValidationResult {
    private boolean valid;
    private String userId;
    private List<String> authorities;
    private String errorMessage;
    private Instant validatedAt;
    private String tokenType;
    private Instant expiresAt;
    
    public boolean hasAuthority(String authority) {
        return authorities != null && authorities.contains(authority);
    }
    
    public boolean hasAnyAuthority(String... authoritiesToCheck) {
        if (authorities == null || authoritiesToCheck == null) {
            return false;
        }
        
        for (String authority : authoritiesToCheck) {
            if (authorities.contains(authority)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}