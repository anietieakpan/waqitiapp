package com.waqiti.common.messaging.recovery;

import com.waqiti.common.messaging.recovery.model.*;
import java.util.Optional;

public interface RecoveryStrategy {

    /**
     * Determine the appropriate recovery action for this event
     */
    RecoveryAction determineAction(DLQEvent dlqEvent);

    /**
     * Attempt immediate retry of the event
     */
    boolean retryHandler(DLQEvent dlqEvent) throws Exception;

    /**
     * Fetch event from source of truth for replay
     */
    Optional<Object> fetchFromSource(DLQEvent dlqEvent);

    /**
     * Execute compensation transaction
     */
    CompensationResult compensate(DLQEvent dlqEvent);
}