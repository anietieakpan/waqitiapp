package com.waqiti.accounting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.accounting.service.DlqRecoveryService;
import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DLQ Handler for PaymentRefundAccountingConsumer
 *
 * Handles failed messages from the dead letter topic with automated recovery
 * Stores messages in DLQ recovery system for retry with exponential backoff
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0 - Enhanced with DLQ recovery system
 */
@Service
@Slf4j
public class PaymentRefundAccountingConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DlqRecoveryService dlqRecoveryService;
    private final ObjectMapper objectMapper;

    public PaymentRefundAccountingConsumerDlqHandler(
            MeterRegistry meterRegistry,
            DlqRecoveryService dlqRecoveryService,
            ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRecoveryService = dlqRecoveryService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PaymentRefundAccountingConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PaymentRefundAccountingConsumer.dlq:PaymentRefundAccountingConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION_ID, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            log.info("Processing DLQ event for PaymentRefundAccountingConsumer: topic={}", headers.get("topic"));

            // Extract metadata from headers
            String topic = (String) headers.getOrDefault("topic", "payment-refund-events");
            Integer partition = (Integer) headers.get("partition");
            Long offset = (Long) headers.get("offset");
            String consumerGroup = (String) headers.getOrDefault("consumerGroup", "accounting-service-group");

            // Generate unique message ID for deduplication
            String messageId = generateMessageId(event, headers);

            // Convert event to Map for storage
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = event instanceof Map
                ? (Map<String, Object>) event
                : objectMapper.convertValue(event, Map.class);

            // Store in DLQ recovery system
            Exception dlqException = new Exception("Payment refund accounting processing failed - stored for retry");
            dlqRecoveryService.storeFailedMessage(
                topic,
                messageId,
                payload,
                dlqException,
                consumerGroup,
                offset,
                partition
            );

            log.info("Payment refund DLQ message stored successfully for automated retry: messageId={}", messageId);

            // Return success - message is now in recovery system
            return DlqProcessingResult.RECOVERED;

        } catch (Exception e) {
            log.error("Critical error storing payment refund DLQ message in recovery system", e);
            // This is a permanent failure - we couldn't even store it for retry
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "PaymentRefundAccountingConsumer";
    }

    /**
     * Generate unique message ID for deduplication
     */
    private String generateMessageId(Object event, Map<String, Object> headers) {
        try {
            // Try to extract transaction ID or refund ID from event
            if (event instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> eventMap = (Map<String, Object>) event;

                String transactionId = (String) eventMap.get("transactionId");
                String refundId = (String) eventMap.get("refundId");

                if (transactionId != null) {
                    return "payment-refund-" + transactionId;
                }
                if (refundId != null) {
                    return "payment-refund-" + refundId;
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract ID from event, generating UUID", e);
        }

        // Fallback to UUID
        return "payment-refund-" + UUID.randomUUID().toString();
    }
}
