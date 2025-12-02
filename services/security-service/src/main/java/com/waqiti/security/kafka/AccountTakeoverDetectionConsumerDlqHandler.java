package com.waqiti.security.kafka;

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
 * DLQ Handler for AccountTakeoverDetectionConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AccountTakeoverDetectionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AccountTakeoverDetectionConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AccountTakeoverDetectionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AccountTakeoverDetectionConsumer.dlq:AccountTakeoverDetectionConsumer.dlq}",
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
            log.error("DLQ: CRITICAL - Account takeover detection");
            String detectionId = headers.getOrDefault("detectionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String atoScore = headers.getOrDefault("atoScore", "0").toString();
            String riskLevel = headers.getOrDefault("riskLevel", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Account takeover = customer account compromise - CRITICAL

            // Strategy 1: High risk ATO detected but freeze failed
            if ((riskLevel.contains("HIGH") || riskLevel.contains("CRITICAL")) &&
                failureReason.contains("freeze failed")) {
                log.error("DLQ: CRITICAL - ATO detected but freeze failed: userId={}, score={}", userId, atoScore);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Customer notification failure
            if (failureReason.contains("notification") && riskLevel.contains("HIGH")) {
                log.error("DLQ: Failed to notify customer of ATO: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Session termination failure
            if (failureReason.contains("session") || failureReason.contains("logout")) {
                log.error("DLQ: Failed to terminate sessions for ATO: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Fraud case creation failure
            if (failureReason.contains("fraud case") || failureReason.contains("case management")) {
                log.warn("DLQ: Failed to create fraud case for ATO: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Duplicate detection
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }

            log.error("DLQ: ATO detection failure - SECURITY REVIEW: detectionId={}, user={}", detectionId, userId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in ATO detection handler", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    @Override
    protected String getServiceName() {
        return "AccountTakeoverDetectionConsumer";
    }
}
