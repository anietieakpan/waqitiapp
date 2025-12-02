package com.waqiti.accounting.kafka;

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
import java.util.Map;

/**
 * DLQ Handler for MerchantSettlementEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MerchantSettlementEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MerchantSettlementEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MerchantSettlementEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MerchantSettlementEventConsumer.dlq:MerchantSettlementEventConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    private final com.waqiti.accounting.service.DlqRecoveryService dlqRecoveryService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public MerchantSettlementEventConsumerDlqHandler(
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            com.waqiti.accounting.service.DlqRecoveryService dlqRecoveryService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRecoveryService = dlqRecoveryService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            log.info("Processing DLQ event for MerchantSettlementEvent: topic={}", headers.get("topic"));
            String topic = (String) headers.getOrDefault("topic", "merchant-settlement-events");
            Integer partition = (Integer) headers.get("partition");
            Long offset = (Long) headers.get("offset");
            String consumerGroup = (String) headers.getOrDefault("consumerGroup", "accounting-service-group");
            String messageId = generateMessageId(event, headers, "merchant-settlement");

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = event instanceof Map
                ? (Map<String, Object>) event
                : objectMapper.convertValue(event, Map.class);

            dlqRecoveryService.storeFailedMessage(
                topic, messageId, payload,
                new Exception("Merchant settlement accounting failed - stored for retry"),
                consumerGroup, offset, partition
            );

            log.info("Merchant settlement DLQ message stored for retry: messageId={}", messageId);
            return DlqProcessingResult.RECOVERED;

        } catch (Exception e) {
            log.error("Critical error storing merchant settlement DLQ message", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private String generateMessageId(Object event, Map<String, Object> headers, String prefix) {
        try {
            if (event instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> eventMap = (Map<String, Object>) event;
                String id = (String) eventMap.getOrDefault("settlementId",
                    eventMap.getOrDefault("merchantId", eventMap.get("transactionId")));
                if (id != null) return prefix + "-" + id;
            }
        } catch (Exception e) {
            log.warn("Could not extract ID from event", e);
        }
        return prefix + "-" + java.util.UUID.randomUUID();
    }

    @Override
    protected String getServiceName() {
        return "MerchantSettlementEventConsumer";
    }
}
