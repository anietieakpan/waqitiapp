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
 * DLQ Handler for CircuitBreakerMetricsConsumer - PRODUCTION READY
 *
 * LOW PRIORITY: Handles failed circuit breaker metrics - monitoring only
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class CircuitBreakerMetricsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public CircuitBreakerMetricsConsumerDlqHandler(MeterRegistry meterRegistry,
                                                    DLQEntryRepository dlqRepository,
                                                    ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CircuitBreakerMetricsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CircuitBreakerMetricsConsumer.dlq:CircuitBreakerMetricsConsumer.dlq}",
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
            log.info("ℹ️ LOW: Circuit breaker metrics FAILED - Monitoring gap");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(extractValue(eventData, "metricId", "eventId"))
                    .sourceTopic("CircuitBreakerMetrics")
                    .eventJson(objectMapper.writeValueAsString(eventData))
                    .errorMessage((String) headers.getOrDefault("x-error-message", "Unknown error"))
                    .status(DLQStatus.PENDING_REVIEW)
                    .recoveryStrategy(RecoveryStrategy.DISCARD_WITH_AUDIT)
                    .createdAt(LocalDateTime.now())
                    .build();

            dlqRepository.save(dlqEntry);
            return DlqProcessingResult.DISCARD_WITH_AUDIT;

        } catch (Exception e) {
            log.error("Failed to process circuit breaker metrics DLQ!", e);
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
        return "CircuitBreakerMetricsConsumer";
    }
}
