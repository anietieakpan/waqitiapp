package com.waqiti.business.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.business.service.DlqRetryService;
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
 * DLQ Handler for BusinessAccountTransactionsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class BusinessAccountTransactionsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DlqRetryService dlqRetryService;

    public BusinessAccountTransactionsConsumerDlqHandler(
            MeterRegistry meterRegistry,
            DlqRetryService dlqRetryService) {
        super(meterRegistry);
        this.dlqRetryService = dlqRetryService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("BusinessAccountTransactionsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.BusinessAccountTransactionsConsumer.dlq:BusinessAccountTransactionsConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            log.info("Processing DLQ event for BusinessAccountTransactionsConsumer: headers={}", headers);

            // Extract message details
            String topic = (String) headers.getOrDefault("kafka_receivedTopic", "unknown");
            Integer partition = (Integer) headers.get("kafka_receivedPartitionId");
            Long offset = (Long) headers.get("kafka_offset");
            String messageKey = (String) headers.get("kafka_receivedMessageKey");

            // Persist to DLQ for automated retry
            dlqRetryService.persistFailedMessage(
                    "BusinessAccountTransactionsConsumer",
                    topic,
                    partition,
                    offset,
                    messageKey,
                    convertEventToMap(event),
                    headers,
                    new Exception("DLQ processing - original failure")
            );

            log.info("DLQ message persisted for automated retry: topic={}, partition={}, offset={}",
                    topic, partition, offset);

            // Return AUTO_RETRY to indicate message was successfully queued for retry
            return DlqProcessingResult.AUTO_RETRY;

        } catch (Exception e) {
            log.error("Critical error handling DLQ event - manual intervention required", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Convert event object to Map for JSON storage
     */
    private Map<String, Object> convertEventToMap(Object event) {
        if (event instanceof Map) {
            return (Map<String, Object>) event;
        }
        // Use ObjectMapper for complex objects
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(event);
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Could not convert event to map, storing as string", e);
            return Map.of("raw", event.toString());
        }
    }

    @Override
    protected String getServiceName() {
        return "BusinessAccountTransactionsConsumer";
    }
}
