package com.waqiti.common.kafka.dlq;

import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Interface for DLQ message reprocessing handlers
 *
 * Each service implementing DLQ handling must provide a concrete
 * implementation of this interface for each event type it processes.
 */
public interface DlqMessageHandler {

    /**
     * Get the event type that this handler processes
     *
     * @return The DLQ event type
     */
    DlqEventType getEventType();

    /**
     * Reprocess a failed Kafka message from DLQ
     *
     * @param record Original Kafka consumer record (recreated from DLQ storage)
     * @param dlqRecord DLQ metadata including retry count, failure history
     * @return Result of reprocessing attempt
     */
    DlqProcessingResult reprocess(ConsumerRecord<String, String> record, DlqRecordEntity dlqRecord);
}
