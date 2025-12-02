package com.waqiti.frauddetection.kafka;

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
 * DLQ Handler for AuthAnomalyCriticalFailuresConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AuthAnomalyCriticalFailuresConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AuthAnomalyCriticalFailuresConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AuthAnomalyCriticalFailuresConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AuthAnomalyCriticalFailuresConsumer.dlq:AuthAnomalyCriticalFailuresConsumer.dlq}",
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
            // FIXED: Implement auth anomaly critical failures recovery - HIGHEST PRIORITY
            log.error("Processing DLQ Auth Anomaly CRITICAL FAILURE - IMMEDIATE ESCALATION REQUIRED");

            String failureId = headers.getOrDefault("failureId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String anomalyScore = headers.getOrDefault("anomalyScore", "0").toString();
            String failureType = headers.getOrDefault("failureType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("SessionRevocationSystemDown") && Double.parseDouble(anomalyScore) > 90) {
                log.error("DLQ: CRITICAL - Cannot revoke high-anomaly session. User: {}, Score: {}. IMMEDIATE ESCALATION.", userId, anomalyScore);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.error("DLQ: Database failure for CRITICAL auth anomaly. User: {}. Retrying with high priority.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("SecuritySystemFailure")) {
                log.error("DLQ: CRITICAL - Security system failure for auth anomaly. User: {}. URGENT manual intervention.", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Duplicate")) {
                log.info("DLQ: Duplicate critical auth anomaly failure. Failure: {}. Marking as resolved.", failureId);
                return DlqProcessingResult.DISCARDED;
            } else {
                log.error("DLQ: Unknown CRITICAL auth anomaly failure. User: {}, Type: {}. URGENT escalation.", userId, failureType);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ Auth Anomaly CRITICAL FAILURE - IMMEDIATE ATTENTION REQUIRED", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AuthAnomalyCriticalFailuresConsumer";
    }
}
