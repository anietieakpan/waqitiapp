package com.waqiti.common.messaging.recovery;

import com.waqiti.common.messaging.recovery.model.*;
import lombok.RequiredArgsConstructor;
import java.util.Optional;

@RequiredArgsConstructor
public class DefaultRecoveryStrategy implements RecoveryStrategy {

    private final DLQEvent dlqEvent;

    @Override
    public RecoveryAction determineAction(DLQEvent event) {
        // Max retries exceeded - dead storage
        if (event.isMaxRetriesExceeded()) {
            return RecoveryAction.DEAD_STORAGE;
        }

        // Financial/critical events - manual review after 2 failures
        if ("CRITICAL".equals(event.getPriority()) && event.getRetryCount() >= 2) {
            return RecoveryAction.MANUAL_REVIEW;
        }

        // Transient errors - immediate retry
        if (event.getRetryCount() == 0) {
            return RecoveryAction.IMMEDIATE_RETRY;
        }

        // Default - exponential backoff
        return RecoveryAction.EXPONENTIAL_BACKOFF;
    }

    @Override
    public boolean retryHandler(DLQEvent event) throws Exception {
        // Default implementation - subclasses should override
        return false;
    }

    @Override
    public Optional<Object> fetchFromSource(DLQEvent event) {
        // Default implementation - no source replay
        return Optional.empty();
    }

    @Override
    public CompensationResult compensate(DLQEvent event) {
        // Default implementation - no compensation
        return CompensationResult.builder()
                .success(false)
                .message("No compensation strategy defined")
                .build();
    }
}