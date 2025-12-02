package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Metadata for Vault secrets including rotation information
 */
@Data
@Builder
@Jacksonized
public class SecretMetadata {
    
    private final String path;
    private final String key;
    private final Instant lastAccessed;
    private final Instant nextRotation;
    private final boolean rotationRequired;
    private final String version;
    private final Instant createdAt;
    private final Instant updatedAt;
    
    /**
     * Check if secret should be rotated soon (within next hour)
     */
    public boolean shouldRotateSoon() {
        if (nextRotation == null) {
            return false;
        }
        return nextRotation.minusSeconds(3600).isBefore(Instant.now());
    }
    
    /**
     * Get time until next rotation
     */
    public long getSecondsUntilRotation() {
        if (nextRotation == null) {
            return Long.MAX_VALUE;
        }
        
        long seconds = java.time.Duration.between(Instant.now(), nextRotation).toSeconds();
        return Math.max(0, seconds);
    }
    
    /**
     * Get secret age in seconds
     */
    public long getAgeSeconds() {
        if (lastAccessed == null) {
            return 0;
        }
        return java.time.Duration.between(lastAccessed, Instant.now()).toSeconds();
    }
}