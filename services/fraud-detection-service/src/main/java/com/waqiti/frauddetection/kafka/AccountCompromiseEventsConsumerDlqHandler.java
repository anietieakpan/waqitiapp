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
 * DLQ Handler for AccountCompromiseEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AccountCompromiseEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AccountCompromiseEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AccountCompromiseEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AccountCompromiseEventsConsumer.dlq:AccountCompromiseEventsConsumer.dlq}",
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
            // FIXED: Implement account compromise recovery logic - CRITICAL security event
            log.error("Processing DLQ account compromise event - SECURITY CRITICAL");

            String userId = headers.getOrDefault("userId", "").toString();
            String accountId = headers.getOrDefault("accountId", "").toString();
            String compromiseType = headers.getOrDefault("compromiseType", "").toString();
            String severity = headers.getOrDefault("severity", "HIGH").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.error("DLQ: Database error for CRITICAL account compromise. User: {}. Retrying immediately.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("AccountLockFailed") || failureReason.contains("FreezeFailed")) {
                log.error("DLQ: CRITICAL - Failed to lock compromised account. User: {}. Retrying immediately.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyProcessed")) {
                log.warn("DLQ: Account compromise already processed. User: {}. Marking as resolved.", userId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("NotificationFailed") && "CRITICAL".equals(severity)) {
                log.error("DLQ: Failed to notify user of account compromise. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("UserNotFound")) {
                log.warn("DLQ: User not found for compromise event. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in account compromise. User: {}. URGENT manual review.", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in CRITICAL account compromise event. User: {}. URGENT review.", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ account compromise event - SECURITY CRITICAL", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AccountCompromiseEventsConsumer";
    }
}
