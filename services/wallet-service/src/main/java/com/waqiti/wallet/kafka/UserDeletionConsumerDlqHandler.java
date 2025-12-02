package com.waqiti.wallet.kafka;

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
 * DLQ Handler for UserDeletionConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class UserDeletionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public UserDeletionConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("UserDeletionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.UserDeletionConsumer.dlq:UserDeletionConsumer.dlq}",
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
            log.error("DLQ: CRITICAL - User deletion recovery (GDPR/regulatory)");
            String userId = headers.getOrDefault("userId", "").toString();
            String deletionType = headers.getOrDefault("deletionType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // User deletion is CRITICAL - GDPR requires completion

            // Strategy 1: Wallet has balance
            if (failureReason.contains("wallet balance") || failureReason.contains("funds remaining")) {
                log.error("DLQ: Cannot delete user with wallet balance: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Active loans/debts
            if (failureReason.contains("active loan") || failureReason.contains("outstanding debt")) {
                log.error("DLQ: Cannot delete user with active loans: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: GDPR compliance - must complete deletion
            if (failureReason.contains("GDPR") || deletionType.contains("RIGHT_TO_BE_FORGOTTEN")) {
                log.error("DLQ: GDPR deletion failed - LEGAL REQUIREMENT: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Audit trail preservation failure
            if (failureReason.contains("audit") || failureReason.contains("archive")) {
                log.error("DLQ: Failed to preserve audit trail during deletion: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Transaction history export failure
            if (failureReason.contains("export") || failureReason.contains("history")) {
                log.warn("DLQ: Failed to export user history before deletion: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Soft delete vs hard delete
            if (deletionType.contains("SOFT")) {
                log.info("DLQ: Soft delete, retrying");
                return DlqProcessingResult.RETRY;
            }

            // Strategy 7: Cascade deletion failures
            if (failureReason.contains("cascade") || failureReason.contains("foreign key")) {
                log.warn("DLQ: Cascade deletion failed: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 8: Transient
            if (failureReason.contains("timeout") || failureReason.contains("database")) {
                return DlqProcessingResult.RETRY;
            }

            // Default: CRITICAL - must review manually
            log.error("DLQ: User deletion failed - MANUAL REVIEW REQUIRED: userId={}", userId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: CRITICAL - Error handling user deletion", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    @Override
    protected String getServiceName() {
        return "UserDeletionConsumer";
    }
}
