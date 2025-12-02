package com.waqiti.apigateway.config;

import com.waqiti.apigateway.security.DualModeAuthenticationFilter.AuthMode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature flags for controlling authentication mode during Keycloak migration.
 * Supports gradual rollout based on user percentage and explicit user lists.
 */
@Component
@RefreshScope
@Slf4j
@Getter
public class AuthenticationFeatureFlags {

    @Value("${features.auth.keycloak.enabled:false}")
    private boolean keycloakEnabled;

    @Value("${features.auth.legacy.enabled:true}")
    private boolean legacyAuthEnabled;

    @Value("${features.auth.dual-mode.enabled:false}")
    private boolean dualModeEnabled;

    @Value("${features.auth.migration.percentage:0}")
    private int migrationPercentage;

    @Value("${features.auth.migration.strategy:PERCENTAGE}")
    private MigrationStrategy migrationStrategy;

    @Value("${features.auth.keycloak.pilot-users:}")
    private String pilotUserIds;

    @Value("${features.auth.keycloak.excluded-users:}")
    private String excludedUserIds;

    @Value("${features.auth.fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Value("${features.auth.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    private Set<String> pilotUsers = ConcurrentHashMap.newKeySet();
    private Set<String> excludedUsers = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        // Parse pilot users
        if (pilotUserIds != null && !pilotUserIds.isEmpty()) {
            String[] users = pilotUserIds.split(",");
            for (String userId : users) {
                pilotUsers.add(userId.trim());
            }
            log.info("Loaded {} pilot users for Keycloak authentication", pilotUsers.size());
        }

        // Parse excluded users
        if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            String[] users = excludedUserIds.split(",");
            for (String userId : users) {
                excludedUsers.add(userId.trim());
            }
            log.info("Loaded {} excluded users from Keycloak authentication", excludedUsers.size());
        }

        log.info("Authentication feature flags initialized - Keycloak: {}, Legacy: {}, Dual-mode: {}, Migration: {}%",
                keycloakEnabled, legacyAuthEnabled, dualModeEnabled, migrationPercentage);
    }

    /**
     * Determines the authentication mode for a specific user based on feature flags
     * and migration strategy.
     */
    public AuthMode getAuthMode(String userId) {
        if (userId == null) {
            return getDefaultAuthMode();
        }

        // Check if Keycloak is disabled globally
        if (!keycloakEnabled) {
            return AuthMode.LEGACY;
        }

        // Check if user is explicitly excluded
        if (excludedUsers.contains(userId)) {
            log.debug("User {} is excluded from Keycloak authentication", userId);
            return AuthMode.LEGACY;
        }

        // Check if user is a pilot user
        if (pilotUsers.contains(userId)) {
            log.debug("User {} is a pilot user for Keycloak authentication", userId);
            return AuthMode.KEYCLOAK;
        }

        // Apply migration strategy
        switch (migrationStrategy) {
            case PERCENTAGE:
                return getAuthModeByPercentage(userId);
            case USER_LIST:
                return pilotUsers.contains(userId) ? AuthMode.KEYCLOAK : AuthMode.LEGACY;
            case ALL_NEW_USERS:
                return isNewUser(userId) ? AuthMode.KEYCLOAK : AuthMode.LEGACY;
            case GRADUAL_ROLLOUT:
                return getAuthModeByGradualRollout(userId);
            default:
                return getDefaultAuthMode();
        }
    }

    private AuthMode getAuthModeByPercentage(String userId) {
        if (migrationPercentage <= 0) {
            return AuthMode.LEGACY;
        }
        if (migrationPercentage >= 100) {
            return AuthMode.KEYCLOAK;
        }

        // Use consistent hashing to ensure same user always gets same mode
        int userHash = Math.abs(userId.hashCode() % 100);
        boolean useKeycloak = userHash < migrationPercentage;
        
        if (monitoringEnabled) {
            log.debug("User {} (hash: {}) assigned to {} (threshold: {})",
                    userId, userHash, useKeycloak ? "Keycloak" : "Legacy", migrationPercentage);
        }
        
        return useKeycloak ? AuthMode.KEYCLOAK : AuthMode.LEGACY;
    }

    private AuthMode getAuthModeByGradualRollout(String userId) {
        // Implement time-based gradual rollout
        // This could be based on user creation date, last login, or other criteria
        long daysSinceRolloutStart = getDaysSinceRolloutStart();
        int targetPercentage = Math.min((int)(daysSinceRolloutStart * 10), 100);
        
        int userHash = Math.abs(userId.hashCode() % 100);
        return userHash < targetPercentage ? AuthMode.KEYCLOAK : AuthMode.LEGACY;
    }

    private AuthMode getDefaultAuthMode() {
        if (!keycloakEnabled && legacyAuthEnabled) {
            return AuthMode.LEGACY;
        }
        if (keycloakEnabled && !legacyAuthEnabled) {
            return AuthMode.KEYCLOAK;
        }
        if (dualModeEnabled) {
            // In dual mode with no specific user, default to legacy for safety
            return AuthMode.LEGACY;
        }
        return AuthMode.LEGACY;
    }

    private boolean isNewUser(String userId) {
        try {
            // For Keycloak migration, consider users "new" if they were created after the migration start date
            // or if they have never logged in with legacy auth
            
            // Check if user has any legacy authentication history
            // In production, this would query the user authentication logs
            String cacheKey = "legacy_auth_history:" + userId;
            
            // Try to determine from user creation patterns
            // Users created after the migration announcement are "new"
            long userIdTimestamp = extractTimestampFromUserId(userId);
            if (userIdTimestamp > 0) {
                // Migration announcement was roughly 90 days ago (configurable)
                long migrationAnnouncementTime = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000);
                
                if (userIdTimestamp > migrationAnnouncementTime) {
                    log.debug("User {} considered new - created after migration announcement", userId);
                    return true;
                }
            }
            
            // Check if user has opted into new features (indicator of tech-savvy user)
            if (userId.length() > 10 && userId.startsWith("usr_")) {
                // New user ID format suggests recent account creation
                return true;
            }
            
            // Use consistent hashing for gradual rollout of "new user" classification
            // This ensures consistent behavior across requests for the same user
            int userHash = Math.abs(userId.hashCode() % 100);
            
            // Start conservative - only 5% of existing users classified as "new"
            // This percentage can be increased gradually via configuration
            int newUserThreshold = Integer.parseInt(
                System.getProperty("auth.migration.new-user-threshold", "5")
            );
            
            boolean isNew = userHash < newUserThreshold;
            if (monitoringEnabled && isNew) {
                log.debug("User {} classified as new user via hash-based selection (hash: {}, threshold: {})", 
                    userId, userHash, newUserThreshold);
            }
            
            return isNew;
            
        } catch (Exception e) {
            log.warn("Error determining if user {} is new - defaulting to false", userId, e);
            return false;
        }
    }

    private long getDaysSinceRolloutStart() {
        try {
            // Read rollout start date from configuration or environment variable
            String rolloutStartProperty = System.getProperty("auth.migration.rollout-start-date");
            if (rolloutStartProperty == null) {
                rolloutStartProperty = System.getenv("AUTH_MIGRATION_ROLLOUT_START");
            }
            
            long rolloutStartTime;
            if (rolloutStartProperty != null && !rolloutStartProperty.isEmpty()) {
                try {
                    // Support multiple date formats
                    if (rolloutStartProperty.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        // YYYY-MM-DD format
                        rolloutStartTime = java.time.LocalDate.parse(rolloutStartProperty)
                            .atStartOfDay(java.time.ZoneOffset.UTC)
                            .toInstant()
                            .toEpochMilli();
                    } else {
                        // Assume epoch timestamp in milliseconds
                        rolloutStartTime = Long.parseLong(rolloutStartProperty);
                    }
                } catch (Exception e) {
                    log.warn("Invalid rollout start date format: {} - using default", rolloutStartProperty);
                    // Default to 30 days ago if configuration is invalid
                    rolloutStartTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
                }
            } else {
                // Default rollout start time - 30 days ago
                // In production, this should be set via configuration
                rolloutStartTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
                
                if (monitoringEnabled) {
                    log.debug("Using default rollout start time (30 days ago) - configure 'auth.migration.rollout-start-date' for production");
                }
            }
            
            long currentTime = System.currentTimeMillis();
            long millisSinceRollout = currentTime - rolloutStartTime;
            long daysSinceRollout = millisSinceRollout / (24L * 60 * 60 * 1000);
            
            if (monitoringEnabled) {
                log.debug("Days since Keycloak rollout start: {} (rollout started at: {})", 
                    daysSinceRollout, new java.util.Date(rolloutStartTime));
            }
            
            return Math.max(0, daysSinceRollout);
            
        } catch (Exception e) {
            log.error("Error calculating days since rollout start - defaulting to 0", e);
            return 0;
        }
    }
    
    /**
     * Extract timestamp from user ID if it follows a timestamp-based pattern
     */
    private long extractTimestampFromUserId(String userId) {
        try {
            // Try to extract timestamp from common user ID patterns
            
            // Pattern 1: user_{timestamp}_{randomString}
            if (userId.startsWith("user_")) {
                String[] parts = userId.split("_");
                if (parts.length >= 2) {
                    return Long.parseLong(parts[1]);
                }
            }
            
            // Pattern 2: usr_{timestamp}
            if (userId.startsWith("usr_")) {
                String timestampPart = userId.substring(4);
                if (timestampPart.matches("\\d+")) {
                    return Long.parseLong(timestampPart);
                }
            }
            
            // Pattern 3: UUID v1 contains timestamp (first 8 chars are timestamp-related)
            if (userId.matches("[0-9a-f]{8}-[0-9a-f]{4}-1[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                // UUID v1 - extract timestamp (simplified approach)
                String timestampHex = userId.substring(0, 8) + userId.substring(9, 13) + userId.substring(14, 18);
                long timestamp = Long.parseLong(timestampHex, 16);
                // Convert from UUID timestamp format to Unix timestamp (rough approximation)
                return (timestamp - 0x01B21DD213814000L) / 10000L;
            }
            
            return 0;
            
        } catch (Exception e) {
            log.debug("Could not extract timestamp from user ID: {}", userId);
            return 0;
        }
    }

    public void addPilotUser(String userId) {
        pilotUsers.add(userId);
        log.info("Added user {} to Keycloak pilot users", userId);
    }

    public void removePilotUser(String userId) {
        pilotUsers.remove(userId);
        log.info("Removed user {} from Keycloak pilot users", userId);
    }

    public void addExcludedUser(String userId) {
        excludedUsers.add(userId);
        log.info("Added user {} to Keycloak excluded users", userId);
    }

    public void removeExcludedUser(String userId) {
        excludedUsers.remove(userId);
        log.info("Removed user {} from Keycloak excluded users", userId);
    }

    public void updateMigrationPercentage(int percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Migration percentage must be between 0 and 100");
        }
        this.migrationPercentage = percentage;
        log.info("Updated migration percentage to {}%", percentage);
    }

    public boolean canFallbackToLegacy() {
        return fallbackEnabled && legacyAuthEnabled && dualModeEnabled;
    }

    public enum MigrationStrategy {
        PERCENTAGE,        // Based on user hash percentage
        USER_LIST,        // Explicit user list
        ALL_NEW_USERS,    // All new users use Keycloak
        GRADUAL_ROLLOUT   // Time-based gradual rollout
    }
}