package com.waqiti.user.saga.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Saga Step
 *
 * Represents a single step in a saga with compensation tracking
 * Stored as JSON in saga_states.completed_steps column
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStep {

    /**
     * Name of the step (e.g., "CREATE_LOCAL_USER")
     */
    private String stepName;

    /**
     * Result of the step (e.g., user ID, external ID, etc.)
     */
    private String stepResult;

    /**
     * Human-readable description
     */
    private String description;

    /**
     * When step was completed
     */
    private LocalDateTime completedAt;

    /**
     * Whether compensation was executed
     */
    @Builder.Default
    private Boolean compensated = false;

    /**
     * When compensation was executed
     */
    private LocalDateTime compensatedAt;

    /**
     * Whether compensation failed
     */
    @Builder.Default
    private Boolean compensationFailed = false;

    /**
     * Error message if compensation failed
     */
    private String compensationError;
}
