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
 * DLQ Handler for AccountFreezesConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AccountFreezesConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AccountFreezesConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AccountFreezesConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AccountFreezesConsumer.dlq:AccountFreezesConsumer.dlq}",
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
            log.error("DLQ: CRITICAL - Processing account freeze recovery");
            String accountId = headers.getOrDefault("accountId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String freezeReason = headers.getOrDefault("freezeReason", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            log.error("DLQ: Account Freeze - accountId={}, user={}, reason={}",
                accountId, userId, freezeReason);

            // Strategy 1: Account not found
            if (failureReason.contains("account not found")) {
                log.error("DLQ: Freeze for non-existent account: accountId={}", accountId);
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Strategy 2: Account already frozen
            if (failureReason.contains("already frozen") || failureReason.contains("duplicate")) {
                log.info("DLQ: Account already frozen: accountId={}", accountId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 3: Transaction blocking failure
            if (failureReason.contains("transaction block") || failureReason.contains("payment block")) {
                log.error("DLQ: CRITICAL - Failed to block transactions: accountId={}", accountId);
                // Must prevent transactions during freeze
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Card blocking failure
            if (failureReason.contains("card") || failureReason.contains("ATM")) {
                log.error("DLQ: Failed to block cards for frozen account: accountId={}", accountId);
                // Must block all cards
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: User notification failure
            if (failureReason.contains("notification")) {
                log.warn("DLQ: Failed to notify user of account freeze: userId={}", userId);
                // Account frozen but user not notified - retry notification
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Compliance record update failure
            if (failureReason.contains("compliance") || failureReason.contains("audit")) {
                log.error("DLQ: Failed to update compliance records for freeze: accountId={}", accountId);
                // Legal requirement
                return DlqProcessingResult.RETRY;
            }

            // Strategy 7: Fraud-related freeze - highest priority
            if (freezeReason.contains("fraud") || freezeReason.contains("suspicious")) {
                log.error("DLQ: FRAUD freeze failed: accountId={}, MANUAL ACTION REQUIRED", accountId);
                // Escalate immediately
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 8: AML/regulatory freeze - highest priority
            if (freezeReason.contains("AML") || freezeReason.contains("compliance") ||
                freezeReason.contains("sanctions")) {
                log.error("DLQ: AML/regulatory freeze failed: accountId={}, IMMEDIATE ACTION", accountId);
                // Legal requirement - must freeze
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 9: Database/transient error
            if (failureReason.contains("database") || failureReason.contains("timeout")) {
                log.warn("DLQ: Transient error during freeze, retrying: accountId={}", accountId);
                return DlqProcessingResult.RETRY;
            }

            // Default: CRITICAL - account freezes must succeed
            log.error("DLQ: Account freeze failed with unknown reason - MANUAL REVIEW REQUIRED: {}",
                failureReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: CRITICAL - Error handling account freeze event", e);
            // Never ignore freeze failures
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    @Override
    protected String getServiceName() {
        return "AccountFreezesConsumer";
    }
}
