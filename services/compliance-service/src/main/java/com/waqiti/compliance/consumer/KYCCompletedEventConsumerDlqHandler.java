package com.waqiti.compliance.consumer;

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
 * DLQ Handler for KYCCompletedEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class KYCCompletedEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public KYCCompletedEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("KYCCompletedEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.KYCCompletedEventConsumer.dlq:KYCCompletedEventConsumer.dlq}",
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
            log.info("DLQ: Processing KYCCompleted event recovery");

            String userId = headers.getOrDefault("userId", "").toString();
            String kycId = headers.getOrDefault("kycId", "").toString();
            String kycLevel = headers.getOrDefault("kycLevel", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: User not found (data inconsistency)
            if (failureReason.contains("user not found")) {
                log.error("DLQ: CRITICAL - KYC completed for non-existent user: {}", userId);
                // Alert compliance team immediately
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Account activation failure
            if (failureReason.contains("account activation")) {
                log.warn("DLQ: Account activation failed after KYC completion: userId={}", userId);
                // Retry activating account
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Limit update failure
            if (failureReason.contains("limit") || failureReason.contains("tier upgrade")) {
                log.warn("DLQ: Failed to update user limits after KYC: userId={}, level={}", userId, kycLevel);
                // Retry applying KYC tier limits
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Notification failure
            if (failureReason.contains("notification")) {
                log.info("DLQ: KYC completed but notification failed: userId={}", userId);
                // Retry sending KYC completion notification
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Compliance record update failure
            if (failureReason.contains("compliance record")) {
                log.warn("DLQ: Failed to update compliance records after KYC: userId={}", userId);
                // Retry updating compliance database
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: AML screening initiation failure
            if (failureReason.contains("AML") || failureReason.contains("screening")) {
                log.warn("DLQ: AML screening failed to initiate after KYC: userId={}", userId);
                // Retry triggering AML screening
                return DlqProcessingResult.RETRY;
            }

            // Strategy 7: Document archival failure
            if (failureReason.contains("document") || failureReason.contains("archival")) {
                log.info("DLQ: Document archival failed but KYC complete: userId={}", userId);
                // Non-critical, retry archiving documents
                return DlqProcessingResult.RETRY;
            }

            // Strategy 8: Duplicate KYC completion
            if (failureReason.contains("duplicate") || failureReason.contains("already completed")) {
                log.info("DLQ: Duplicate KYC completion event: userId={}", userId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 9: Database/transient error
            if (failureReason.contains("timeout") || failureReason.contains("database")) {
                log.info("DLQ: Transient error during KYC completion processing: {}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Default: Manual review by compliance team
            log.error("DLQ: Unknown KYC completion failure, compliance review required: {}", failureReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error handling KYC completed event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "KYCCompletedEventConsumer";
    }
}
