package com.waqiti.accounting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.accounting.service.DlqRecoveryService;
import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

/**
 * Base Accounting DLQ Handler
 * Provides common DLQ recovery logic for all accounting service Kafka consumers
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Slf4j
public abstract class AccountingDlqHandler extends BaseDlqConsumer<Object> {

    protected final DlqRecoveryService dlqRecoveryService;
    protected final ObjectMapper objectMapper;

    protected AccountingDlqHandler(
            MeterRegistry meterRegistry,
            DlqRecoveryService dlqRecoveryService,
            ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRecoveryService = dlqRecoveryService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            String serviceName = getServiceName();
            log.info("Processing DLQ event for {}: topic={}", serviceName, headers.get("topic"));

            // Extract metadata
            String topic = (String) headers.getOrDefault("topic", getDefaultTopic());
            Integer partition = (Integer) headers.get("partition");
            Long offset = (Long) headers.get("offset");
            String consumerGroup = (String) headers.getOrDefault("consumerGroup", "accounting-service-group");

            // Generate unique message ID
            String messageId = generateMessageId(event, headers);

            // Convert event to Map for storage
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = event instanceof Map
                ? (Map<String, Object>) event
                : objectMapper.convertValue(event, Map.class);

            // Store in DLQ recovery system with exponential backoff retry
            dlqRecoveryService.storeFailedMessage(
                topic,
                messageId,
                payload,
                new Exception(serviceName + " processing failed - stored for automated retry"),
                consumerGroup,
                offset,
                partition
            );

            log.info("{} DLQ message stored successfully for automated retry: messageId={}",
                serviceName, messageId);

            return DlqProcessingResult.RECOVERED;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to store DLQ message in recovery system for {}", getServiceName(), e);
            // This is a permanent failure - couldn't even store for retry
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Generate unique message ID for deduplication
     * Subclasses can override to customize ID extraction logic
     */
    protected String generateMessageId(Object event, Map<String, Object> headers) {
        try {
            if (event instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> eventMap = (Map<String, Object>) event;

                // Try common ID fields
                for (String field : getIdFields()) {
                    String id = (String) eventMap.get(field);
                    if (id != null) {
                        return getMessageIdPrefix() + "-" + id;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract ID from event for {}, generating UUID", getServiceName(), e);
        }

        // Fallback to UUID
        return getMessageIdPrefix() + "-" + UUID.randomUUID();
    }

    /**
     * Get the list of fields to try extracting as message ID
     * Override in subclasses to customize
     */
    protected String[] getIdFields() {
        return new String[]{"transactionId", "settlementId", "merchantId", "loanId", "id"};
    }

    /**
     * Get message ID prefix for this handler
     */
    protected abstract String getMessageIdPrefix();

    /**
     * Get default topic name if not in headers
     */
    protected abstract String getDefaultTopic();
}
