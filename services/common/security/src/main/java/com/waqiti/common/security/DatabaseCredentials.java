package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Represents dynamic database credentials from Vault
 * with lease management metadata
 */
@Data
@Builder
@Jacksonized
public class DatabaseCredentials {
    
    private final String username;
    private final String password;
    private final String leaseId;
    private final Integer leaseDuration;
    private final boolean renewable;
    private final Instant createdAt;
    private final Instant expiresAt;
    
    /**
     * Check if credentials are expired
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Check if credentials need renewal (within 10% of expiration)
     */
    public boolean needsRenewal() {
        if (expiresAt == null || !renewable) {
            return false;
        }
        
        long totalDuration = java.time.Duration.between(createdAt, expiresAt).toSeconds();
        long renewalThreshold = Math.round(totalDuration * 0.1); // 10% threshold
        
        return Instant.now().plusSeconds(renewalThreshold).isAfter(expiresAt);
    }
    
    /**
     * Get remaining lease time in seconds
     */
    public long getRemainingLeaseSeconds() {
        if (expiresAt == null) {
            return Long.MAX_VALUE;
        }
        
        long remaining = java.time.Duration.between(Instant.now(), expiresAt).toSeconds();
        return Math.max(0, remaining);
    }
}