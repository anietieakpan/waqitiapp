package com.waqiti.account.kafka;

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
 * DLQ Handler for AccountStatusChangesConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AccountStatusChangesConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AccountStatusChangesConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AccountStatusChangesConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AccountStatusChangesConsumer.dlq:AccountStatusChangesConsumer.dlq}",
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
            log.warn("DLQ: Account status change recovery");
            String accountId = headers.getOrDefault("accountId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String statusChange = headers.getOrDefault("statusChange", "").toString();
            String fromStatus = headers.getOrDefault("fromStatus", "").toString();
            String toStatus = headers.getOrDefault("toStatus", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Account freeze/suspension failure (CRITICAL)
            if (toStatus.contains("FROZEN") || toStatus.contains("SUSPENDED")) {
                log.error("DLQ: Failed to freeze/suspend account: accountId={}", accountId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Account activation failure
            if (toStatus.contains("ACTIVE") && failureReason.contains("activation")) {
                log.warn("DLQ: Account activation failed: accountId={}", accountId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Notification failure (non-critical)
            if (failureReason.contains("notification")) {
                log.info("DLQ: Status change notification failed (non-critical): accountId={}", accountId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 4: Duplicate status change
            if (failureReason.contains("duplicate") || fromStatus.equals(toStatus)) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Compliance record update failure
            if (failureReason.contains("compliance")) {
                log.error("DLQ: Compliance record update failed: accountId={}", accountId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }

            log.warn("DLQ: Account status change failed: accountId={}, change={}", accountId, statusChange);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error in account status change handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AccountStatusChangesConsumer";
    }
}
