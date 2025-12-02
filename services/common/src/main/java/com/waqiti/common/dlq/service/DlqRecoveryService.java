package com.waqiti.common.dlq.service;

import com.waqiti.common.dlq.BaseDlqRecoveryResult;

/**
 * Base interface for all DLQ recovery services.
 * Defines the contract for processing failed messages from DLQ topics.
 *
 * @param <T> The type of recovery result this service produces
 */
public interface DlqRecoveryService<T extends BaseDlqRecoveryResult> {

    /**
     * Process a DLQ event and attempt recovery
     *
     * @param eventData The event data as JSON string
     * @param eventKey The Kafka message key
     * @param correlationId Correlation ID for tracking
     * @param timestamp Event timestamp
     * @return Recovery result with status and details
     */
    T processDlqEvent(String eventData, String eventKey, String correlationId,
                      java.time.Instant timestamp);

    /**
     * Validate event data before processing
     *
     * @param eventData The event data to validate
     * @throws com.waqiti.common.exception.ValidationException if validation fails
     */
    void validateEventData(String eventData);

    /**
     * Get the DLQ topic this service handles
     *
     * @return Topic name
     */
    String getDlqTopic();

    /**
     * Check if this event requires immediate escalation
     *
     * @param eventData The event data
     * @return true if immediate escalation is required
     */
    boolean requiresImmediateEscalation(String eventData);
}
