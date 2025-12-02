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
 * DLQ Handler for ATODetectionCriticalFailuresConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class ATODetectionCriticalFailuresConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public ATODetectionCriticalFailuresConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ATODetectionCriticalFailuresConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ATODetectionCriticalFailuresConsumer.dlq:ATODetectionCriticalFailuresConsumer.dlq}",
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
            // FIXED: Implement ATO critical failures recovery - HIGHEST PRIORITY
            log.error("Processing DLQ ATO CRITICAL FAILURE - IMMEDIATE ESCALATION REQUIRED");

            String failureId = headers.getOrDefault("failureId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String atoScore = headers.getOrDefault("atoScore", "0").toString();
            String failureType = headers.getOrDefault("failureType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Critical failures indicate system-level issues that prevented ATO detection/response
            // These are highest priority as they represent potential active account takeovers

            if (failureReason.contains("AccountLockSystemDown") && Double.parseDouble(atoScore) > 90) {
                log.error("DLQ: CRITICAL SYSTEM FAILURE - Cannot lock high-risk ATO account. User: {}, Score: {}. IMMEDIATE ESCALATION.", userId, atoScore);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED; // Requires immediate human intervention
            } else if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.error("DLQ: Database failure for CRITICAL ATO. User: {}. Retrying with high priority.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AlertingSystemDown")) {
                log.error("DLQ: CRITICAL - Alerting system down for ATO. User: {}. URGENT manual escalation.", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Duplicate")) {
                log.info("DLQ: Duplicate critical failure already escalated. Failure: {}. Marking as resolved.", failureId);
                return DlqProcessingResult.DISCARDED;
            } else {
                log.error("DLQ: Unknown CRITICAL ATO failure. User: {}, Type: {}. URGENT escalation required.", userId, failureType);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ ATO CRITICAL FAILURE - REQUIRES IMMEDIATE ATTENTION", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "ATODetectionCriticalFailuresConsumer";
    }
}
