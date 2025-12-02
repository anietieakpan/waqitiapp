package com.waqiti.config.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a feature flag is created, updated, or deleted
 * Used for broadcasting changes to all services via Spring Cloud Bus/Kafka
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagChangeEvent {

    /**
     * Unique event ID
     */
    private String eventId;

    /**
     * Event type: CREATED, UPDATED, DELETED, ENABLED, DISABLED
     */
    private ChangeType changeType;

    /**
     * Feature flag name
     */
    private String flagName;

    /**
     * Environment where the change occurred
     */
    private String environment;

    /**
     * Previous enabled state (for updates)
     */
    private Boolean previousEnabled;

    /**
     * New enabled state
     */
    private Boolean newEnabled;

    /**
     * Previous rollout percentage (for updates)
     */
    private Integer previousRolloutPercentage;

    /**
     * New rollout percentage
     */
    private Integer newRolloutPercentage;

    /**
     * User who made the change
     */
    private String changedBy;

    /**
     * User's email
     */
    private String changedByEmail;

    /**
     * IP address of the change
     */
    private String ipAddress;

    /**
     * Reason for the change
     */
    private String changeReason;

    /**
     * Timestamp of the change
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Services affected by this change (empty = all services)
     */
    private java.util.List<String> affectedServices;

    /**
     * Estimated impact: LOW, MEDIUM, HIGH, CRITICAL
     */
    private Impact estimatedImpact;

    /**
     * Whether this change requires immediate propagation
     */
    @Builder.Default
    private Boolean requiresImmediatePropagation = false;

    /**
     * Additional metadata
     */
    private java.util.Map<String, Object> metadata;

    /**
     * Feature flag change types
     */
    public enum ChangeType {
        CREATED,        // New feature flag created
        UPDATED,        // Feature flag properties updated
        DELETED,        // Feature flag deleted
        ENABLED,        // Feature flag enabled (was disabled)
        DISABLED,       // Feature flag disabled (was enabled)
        ROLLOUT_INCREASED,  // Rollout percentage increased
        ROLLOUT_DECREASED,  // Rollout percentage decreased
        RULES_UPDATED,      // Targeting rules updated
        TARGETS_UPDATED     // Target users/groups updated
    }

    /**
     * Impact levels for feature flag changes
     */
    public enum Impact {
        LOW,        // Minimal impact, affects few users
        MEDIUM,     // Moderate impact, affects some users/features
        HIGH,       // Significant impact, affects many users/features
        CRITICAL    // Critical impact, affects core functionality
    }

    /**
     * Create a simple feature flag change event
     */
    public static FeatureFlagChangeEvent create(String flagName, ChangeType changeType, String changedBy) {
        return FeatureFlagChangeEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .flagName(flagName)
                .changeType(changeType)
                .changedBy(changedBy)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a feature flag enabled event
     */
    public static FeatureFlagChangeEvent enabled(String flagName, String environment, String changedBy) {
        return FeatureFlagChangeEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .flagName(flagName)
                .environment(environment)
                .changeType(ChangeType.ENABLED)
                .previousEnabled(false)
                .newEnabled(true)
                .changedBy(changedBy)
                .timestamp(Instant.now())
                .requiresImmediatePropagation(true)
                .build();
    }

    /**
     * Create a feature flag disabled event
     */
    public static FeatureFlagChangeEvent disabled(String flagName, String environment, String changedBy) {
        return FeatureFlagChangeEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .flagName(flagName)
                .environment(environment)
                .changeType(ChangeType.DISABLED)
                .previousEnabled(true)
                .newEnabled(false)
                .changedBy(changedBy)
                .timestamp(Instant.now())
                .requiresImmediatePropagation(true)
                .build();
    }

    /**
     * Create a feature flag rollout change event
     */
    public static FeatureFlagChangeEvent rolloutChanged(
            String flagName,
            String environment,
            Integer previousPercentage,
            Integer newPercentage,
            String changedBy) {

        ChangeType changeType = newPercentage > previousPercentage
                ? ChangeType.ROLLOUT_INCREASED
                : ChangeType.ROLLOUT_DECREASED;

        return FeatureFlagChangeEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .flagName(flagName)
                .environment(environment)
                .changeType(changeType)
                .previousRolloutPercentage(previousPercentage)
                .newRolloutPercentage(newPercentage)
                .changedBy(changedBy)
                .timestamp(Instant.now())
                .requiresImmediatePropagation(true)
                .build();
    }

    /**
     * Determine if this is a critical change requiring immediate attention
     */
    public boolean isCriticalChange() {
        return estimatedImpact == Impact.CRITICAL
                || requiresImmediatePropagation
                || changeType == ChangeType.DISABLED
                || (changeType == ChangeType.ROLLOUT_DECREASED && newRolloutPercentage != null && newRolloutPercentage < 10);
    }

    /**
     * Get a human-readable description of the change
     */
    public String getChangeDescription() {
        return switch (changeType) {
            case CREATED -> String.format("Feature flag '%s' created", flagName);
            case UPDATED -> String.format("Feature flag '%s' updated", flagName);
            case DELETED -> String.format("Feature flag '%s' deleted", flagName);
            case ENABLED -> String.format("Feature flag '%s' enabled in %s", flagName, environment);
            case DISABLED -> String.format("Feature flag '%s' disabled in %s", flagName, environment);
            case ROLLOUT_INCREASED -> String.format("Feature flag '%s' rollout increased from %d%% to %d%%",
                    flagName, previousRolloutPercentage, newRolloutPercentage);
            case ROLLOUT_DECREASED -> String.format("Feature flag '%s' rollout decreased from %d%% to %d%%",
                    flagName, previousRolloutPercentage, newRolloutPercentage);
            case RULES_UPDATED -> String.format("Feature flag '%s' targeting rules updated", flagName);
            case TARGETS_UPDATED -> String.format("Feature flag '%s' target users/groups updated", flagName);
        };
    }
}
