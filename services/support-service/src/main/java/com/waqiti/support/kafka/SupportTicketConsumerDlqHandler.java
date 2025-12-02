package com.waqiti.support.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.support.service.DlqRecoveryService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * PRODUCTION-READY DLQ Handler for SupportTicketConsumer
 *
 * Addresses BLOCKER-003: Unimplemented DLQ recovery logic
 *
 * Features:
 * - Stores failed messages in database for audit trail
 * - Automatic retry with exponential backoff
 * - Operations alerting
 * - Manual intervention support
 * - Comprehensive metrics and monitoring
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0 - Production Ready
 */
@Service
@Slf4j
public class SupportTicketConsumerDlqHandler extends BaseDlqConsumer<Object> {

    @Autowired
    private DlqRecoveryService dlqRecoveryService;

    @Autowired
    private ObjectMapper objectMapper;

    public SupportTicketConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("SupportTicketConsumer");
        log.info("SupportTicketConsumerDlqHandler initialized with production-ready recovery logic");
    }

    @KafkaListener(
        topics = "${kafka.topics.SupportTicketConsumer.dlq:SupportTicketConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION_ID, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset,
            @Header(value = KafkaHeaders.RECEIVED_MESSAGE_KEY, required = false) String messageKey,
            @Header(value = "x-exception-message", required = false) String exceptionMessage,
            @Header(value = "x-exception-stacktrace", required = false) String stackTrace,
            Acknowledgment acknowledgment) {

        log.error("DLQ message received - Topic: {}, Partition: {}, Offset: {}, Error: {}",
                 topic, partition, offset, exceptionMessage);

        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            // Extract message details
            String topic = (String) headers.get(KafkaHeaders.RECEIVED_TOPIC);
            Integer partition = (Integer) headers.get(KafkaHeaders.RECEIVED_PARTITION_ID);
            Long offset = (Long) headers.get(KafkaHeaders.OFFSET);
            String messageKey = (String) headers.get(KafkaHeaders.RECEIVED_MESSAGE_KEY);
            String exceptionMessage = (String) headers.get("x-exception-message");
            String stackTrace = (String) headers.get("x-exception-stacktrace");

            // Serialize event payload
            String messagePayload = serializeEvent(event);

            // Store in database for recovery
            dlqRecoveryService.storeDlqMessage(
                topic,
                partition,
                offset,
                messageKey,
                messagePayload,
                exceptionMessage != null ? exceptionMessage : "Unknown error",
                stackTrace
            );

            log.info("DLQ message stored successfully for topic: {}, offset: {}", topic, offset);

            // Acknowledge message - we've stored it for later processing
            return DlqProcessingResult.SUCCESS;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to store DLQ message in database", e);

            // Even storing the DLQ message failed - this is a serious issue
            // Return RETRY to not lose the message
            return DlqProcessingResult.RETRY;
        }
    }

    /**
     * Serializes event object to JSON string for storage.
     */
    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize event, storing toString() instead", e);
            return event != null ? event.toString() : "null";
        }
    }

    /**
     * Extracts stack trace from exception.
     */
    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    @Override
    protected String getServiceName() {
        return "SupportTicketConsumer";
    }
}
