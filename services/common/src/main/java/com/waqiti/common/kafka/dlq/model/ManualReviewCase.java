package com.waqiti.common.kafka.dlq.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a manual review case for DLQ messages that require human intervention
 * Used for reviewing, approving, or rejecting failed events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewCase {

    /**
     * Unique case identifier
     */
    private String caseId;

    /**
     * DLQ event ID being reviewed
     */
    private String dlqEventId;

    /**
     * Service that produced the failed message
     */
    private String serviceName;

    /**
     * Topic where the message failed
     */
    private String topicName;

    /**
     * Consumer group that failed to process
     */
    private String consumerGroup;

    /**
     * Priority level for review (HIGH, MEDIUM, LOW)
     */
    @Builder.Default
    private ReviewPriority priority = ReviewPriority.MEDIUM;

    /**
     * Current status of the case
     */
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    /**
     * Original message payload
     */
    private String messagePayload;

    /**
     * Error that caused the failure
     */
    private String errorMessage;

    /**
     * Type/category of error
     */
    private String errorType;

    /**
     * Stack trace if available
     */
    private String stackTrace;

    /**
     * Number of retry attempts
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * Maximum retry attempts allowed
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * Business context for the reviewer
     */
    private Map<String, Object> businessContext;

    /**
     * Category of the failure
     */
    private String failureCategory;

    /**
     * Assigned reviewer
     */
    private String assignedTo;

    /**
     * Resolution notes
     */
    private String resolutionNotes;

    /**
     * Action taken (RETRY, DISCARD, MANUAL_FIX)
     */
    private ResolutionAction resolutionAction;

    /**
     * When the case was created
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * When the case was last updated
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * When the case was assigned
     */
    private LocalDateTime assignedAt;

    /**
     * When the case was resolved
     */
    private LocalDateTime resolvedAt;

    /**
     * SLA deadline for resolution
     */
    private LocalDateTime slaDeadline;

    /**
     * Tags for categorization
     */
    private String[] tags;

    /**
     * Related case IDs
     */
    private String[] relatedCases;

    /**
     * Review priority levels
     */
    public enum ReviewPriority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * Review status
     */
    public enum ReviewStatus {
        PENDING,
        ASSIGNED,
        IN_REVIEW,
        APPROVED,
        REJECTED,
        RESOLVED,
        ESCALATED,
        CLOSED
    }

    /**
     * Resolution actions
     */
    public enum ResolutionAction {
        RETRY,
        RETRY_WITH_CHANGES,
        REPROCESS,
        DISCARD,
        MANUAL_FIX,
        ESCALATE,
        DEFER
    }

    /**
     * Check if case is overdue
     */
    public boolean isOverdue() {
        return slaDeadline != null && LocalDateTime.now().isAfter(slaDeadline);
    }

    /**
     * Check if case is resolved
     */
    public boolean isResolved() {
        return status == ReviewStatus.RESOLVED || status == ReviewStatus.CLOSED;
    }

    /**
     * Check if max retries reached
     */
    public boolean hasExceededMaxRetries() {
        return retryCount >= maxRetries;
    }

    /**
     * Get combined error details
     * @return Formatted error details string
     */
    public String getErrorDetails() {
        if (errorMessage == null && errorType == null) {
            return "No error details available";
        }
        if (errorType != null && errorMessage != null) {
            return errorType + ": " + errorMessage;
        }
        return errorMessage != null ? errorMessage : errorType;
    }
}
