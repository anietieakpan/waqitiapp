package com.waqiti.dispute.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.dispute.entity.DLQEntry;
import com.waqiti.dispute.entity.DLQStatus;
import com.waqiti.dispute.entity.RecoveryStrategy;
import com.waqiti.dispute.repository.DLQEntryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DLQ Handler for ChargebackPreventionEventsConsumer - PRODUCTION READY
 *
 * MEDIUM PRIORITY: Handles failed chargeback prevention event tracking
 * Prevention events log proactive measures to reduce chargebacks
 * Failures impact analytics but not real-time operations
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class ChargebackPreventionEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public ChargebackPreventionEventsConsumerDlqHandler(MeterRegistry meterRegistry,
                                                         DLQEntryRepository dlqRepository,
                                                         ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ChargebackPreventionEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ChargebackPreventionEventsConsumer.dlq:ChargebackPreventionEventsConsumer.dlq}",
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
            log.warn("⚠️ MEDIUM: Chargeback prevention event FAILED - Analytics gap");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String preventionId = extractValue(eventData, "preventionId", "eventId");
            String preventionType = extractValue(eventData, "preventionType", "type");
            String merchantId = extractValue(eventData, "merchantId");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(preventionId)
                    .sourceTopic("ChargebackPreventionEvents")
                    .eventJson(objectMapper.writeValueAsString(eventData))
                    .errorMessage(errorMessage)
                    .status(DLQStatus.PENDING_REVIEW)
                    .recoveryStrategy(RecoveryStrategy.DISCARD_WITH_AUDIT)
                    .retryCount(0)
                    .maxRetries(1)
                    .createdAt(LocalDateTime.now())
                    .alertSent(false)
                    .build();

            dlqRepository.save(dlqEntry);

            log.warn("Prevention event logged: Type={}, MerchantId={}", preventionType, merchantId);

            return DlqProcessingResult.DISCARD_WITH_AUDIT;

        } catch (Exception e) {
            log.error("Failed to process chargeback prevention event DLQ!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private String extractValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key).toString();
            }
        }
        return "UNKNOWN";
    }

    @Override
    protected String getServiceName() {
        return "ChargebackPreventionEventsConsumer";
    }
}
