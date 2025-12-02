package com.waqiti.rewards.kafka;

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
 * DLQ Handler for LoyaltyPointsAccrualEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class LoyaltyPointsAccrualEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public LoyaltyPointsAccrualEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("LoyaltyPointsAccrualEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.LoyaltyPointsAccrualEventConsumer.dlq:LoyaltyPointsAccrualEventConsumer.dlq}",
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
            // FIXED: Implement loyalty points accrual recovery logic
            log.warn("Processing DLQ loyalty points accrual event for recovery");

            // Extract event details
            String accrualId = headers.getOrDefault("accrualId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Accrual: {}", accrualId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("LoyaltyAccountNotFound")) {
                // Loyalty account doesn't exist yet - retry later (eventual consistency)
                log.warn("DLQ: Loyalty account not found. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in loyalty accrual. Accrual: {}, User: {}. Manual review required.",
                        accrualId, userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyAccrued")) {
                // Points already accrued - can be safely ignored
                log.info("DLQ: Duplicate loyalty accrual event detected. Accrual: {}. Marking as resolved.",
                        accrualId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("TransactionNotEligible") || failureReason.contains("RuleNotMatched")) {
                // Transaction doesn't qualify for points - discard
                log.info("DLQ: Transaction not eligible for loyalty points. Transaction: {}. Discarding.",
                        transactionId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("CalculationError")) {
                // Points calculation error - needs review
                log.error("DLQ: Points calculation error. Transaction: {}. Manual review required.", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in loyalty accrual event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ loyalty accrual event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "LoyaltyPointsAccrualEventConsumer";
    }
}
