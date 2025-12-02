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
 * DLQ Handler for AuthRevocationsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AuthRevocationsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AuthRevocationsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AuthRevocationsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AuthRevocationsConsumer.dlq:AuthRevocationsConsumer.dlq}",
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
            // FIXED: Implement auth revocation recovery logic - CRITICAL SECURITY
            log.error("Processing DLQ auth revocations event - SECURITY CRITICAL");

            String revocationId = headers.getOrDefault("revocationId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String sessionId = headers.getOrDefault("sessionId", "").toString();
            String reason = headers.getOrDefault("reason", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.error("DLQ: Database error for CRITICAL auth revocation. User: {}. Retrying immediately.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("RevocationFailed") || failureReason.contains("SessionTerminationFailed")) {
                log.error("DLQ: CRITICAL - Failed to revoke auth session. User: {}, Session: {}. Retrying immediately.", userId, sessionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyRevoked")) {
                log.info("DLQ: Auth already revoked. Revocation: {}. Marking as resolved.", revocationId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("SessionNotFound")) {
                log.warn("DLQ: Session not found for revocation. Session: {}. Marking as resolved.", sessionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in CRITICAL auth revocation. Revocation: {}. URGENT manual review.", revocationId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in CRITICAL auth revocation. Event: {}, Headers: {}. URGENT.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ auth revocations event - SECURITY CRITICAL", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AuthRevocationsConsumer";
    }
}
