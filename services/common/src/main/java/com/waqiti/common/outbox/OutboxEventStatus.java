package com.waqiti.common.outbox;

/**
 * Production-ready Outbox Event Status Enum
 *
 * Defines the lifecycle states of an outbox event from creation to processing.
 * This enum replaces the boolean 'processed' flag to handle transient states
 * and prevent "stuck in processing" scenarios.
 *
 * State Transitions:
 * PENDING → PROCESSING → PROCESSED (success path)
 * PENDING → PROCESSING → FAILED → PENDING (retry path)
 * FAILED → DEAD_LETTER (after max retries)
 *
 * @author Waqiti Platform Team
 * @version 2.0 - Production Ready
 */
public enum OutboxEventStatus {

    /**
     * Event created but not yet picked up by a processor
     * This is the initial state for all events
     */
    PENDING,

    /**
     * Event currently being processed by a worker
     * Prevents duplicate processing by multiple workers
     * If a worker crashes, cleanup job resets stuck PROCESSING events to PENDING
     */
    PROCESSING,

    /**
     * Event successfully processed and published to Kafka
     * Final state - event can be archived/deleted
     */
    PROCESSED,

    /**
     * Event processing failed (will be retried with exponential backoff)
     * nextRetryAt determines when to retry
     * After max retries, moves to DEAD_LETTER
     */
    FAILED,

    /**
     * Event failed max retry attempts
     * Requires manual intervention or automated recovery workflow
     */
    DEAD_LETTER
}
