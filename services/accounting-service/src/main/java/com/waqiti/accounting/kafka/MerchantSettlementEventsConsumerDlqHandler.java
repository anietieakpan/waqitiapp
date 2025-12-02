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
 * DLQ Handler for MerchantSettlementEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MerchantSettlementEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MerchantSettlementEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MerchantSettlementEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MerchantSettlementEventsConsumer.dlq:MerchantSettlementEventsConsumer.dlq}",
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

    public MerchantSettlementEventsConsumerDlqHandler(
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
            log.info("Processing DLQ event for MerchantSettlementEvents");
            String topic = (String) headers.getOrDefault("topic", "merchant-settlement-events");
            Integer partition = (Integer) headers.get("partition");
            Long offset = (Long) headers.get("offset");
            String messageId = extractMessageId(event, "merchant-settlement-events");

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = event instanceof Map
                ? (Map<String, Object>) event
                : objectMapper.convertValue(event, Map.class);

            dlqRecoveryService.storeFailedMessage(topic, messageId, payload,
                new Exception("Merchant settlement events processing failed"),
                "accounting-service-group", offset, partition);

            return DlqProcessingResult.RECOVERED;
        } catch (Exception e) {
            log.error("Critical error storing DLQ message", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private String extractMessageId(Object event, String prefix) {
        try {
            if (event instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) event;
                String id = (String) map.getOrDefault("settlementId", map.get("id"));
                if (id != null) return prefix + "-" + id;
            }
        } catch (Exception e) {
            log.debug("Could not extract ID", e);
        }
        return prefix + "-" + java.util.UUID.randomUUID();
    }

    @Override
    protected String getServiceName() {
        return "MerchantSettlementEventsConsumer";
    }
}
