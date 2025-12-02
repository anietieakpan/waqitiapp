package com.waqiti.common.dlq;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;

/**
 * Base class for all DLQ recovery results.
 * Provides common fields and methods for tracking recovery outcomes.
 */
@Getter
@Setter
@SuperBuilder
public abstract class BaseDlqRecoveryResult {

    private String eventId;
    private String correlationId;
    private boolean recovered;
    private String failureReason;
    private Instant processingStartTime;
    private Instant processingEndTime;

    protected BaseDlqRecoveryResult() {
        this.processingStartTime = Instant.now();
    }

    public Duration getProcessingTime() {
        if (processingEndTime == null) {
            processingEndTime = Instant.now();
        }
        return Duration.between(processingStartTime, processingEndTime);
    }

    public void markAsRecovered() {
        this.recovered = true;
        this.processingEndTime = Instant.now();
    }

    public void markAsFailed(String reason) {
        this.recovered = false;
        this.failureReason = reason;
        this.processingEndTime = Instant.now();
    }

    public abstract String getRecoveryStatus();
}
