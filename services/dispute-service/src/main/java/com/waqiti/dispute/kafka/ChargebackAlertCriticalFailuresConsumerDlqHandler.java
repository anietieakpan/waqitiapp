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
 * DLQ Handler for ChargebackAlertCriticalFailuresConsumer - PRODUCTION READY
 *
 * MEDIUM PRIORITY: Handles failed critical alert notifications
 * Alerts notify teams of urgent chargeback issues
 * Failures mean teams aren't alerted but underlying issue is logged
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class ChargebackAlertCriticalFailuresConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public ChargebackAlertCriticalFailuresConsumerDlqHandler(MeterRegistry meterRegistry,
                                                               DLQEntryRepository dlqRepository,
                                                               ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ChargebackAlertCriticalFailuresConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ChargebackAlertCriticalFailuresConsumer.dlq:ChargebackAlertCriticalFailuresConsumer.dlq}",
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
            log.warn("⚠️ MEDIUM: Critical alert notification FAILED - Team not notified");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String alertId = extractValue(eventData, "alertId", "eventId");
            String alertType = extractValue(eventData, "alertType", "type");
            String chargebackId = extractValue(eventData, "chargebackId");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(alertId)
                    .sourceTopic("ChargebackAlertCriticalFailures")
                    .eventJson(objectMapper.writeValueAsString(eventData))
                    .errorMessage(errorMessage)
                    .status(DLQStatus.PENDING_REVIEW)
                    .recoveryStrategy(RecoveryStrategy.RETRY_WITH_BACKOFF)
                    .retryCount(0)
                    .maxRetries(2)
                    .createdAt(LocalDateTime.now())
                    .alertSent(false)
                    .build();

            dlqRepository.save(dlqEntry);

            log.warn("Critical alert failure logged: Type={}, ChargebackId={}", alertType, chargebackId);

            return DlqProcessingResult.RETRY_WITH_BACKOFF;

        } catch (Exception e) {
            log.error("Failed to process chargeback alert critical failure DLQ!", e);
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
        return "ChargebackAlertCriticalFailuresConsumer";
    }
}
