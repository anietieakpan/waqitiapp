package com.waqiti.common.kafka.dlq;

/**
 * DLQ Processing Status
 */
public enum DlqStatus {
    /**
     * Message is pending reprocessing (waiting for retry backoff)
     */
    PENDING,

    /**
     * Message is currently being reprocessed
     */
    PROCESSING,

    /**
     * Message was successfully reprocessed and removed from DLQ
     */
    REPROCESSED,

    /**
     * Message failed all retry attempts and is parked for manual review
     */
    PARKED,

    /**
     * Message was manually resolved/skipped by operations team
     */
    MANUALLY_RESOLVED,

    /**
     * Message was determined to be invalid and discarded
     */
    DISCARDED
}
