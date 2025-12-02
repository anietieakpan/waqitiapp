package com.waqiti.transaction.consumer;

import com.waqiti.events.fraud.FraudCheckCompletedEvent;
import com.waqiti.transaction.model.TransactionStatus;
import com.waqiti.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for fraud check events - breaks circular dependency with fraud-service
 * Instead of transaction-service calling fraud-service synchronously, it listens to fraud events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudEventConsumer {

    private final TransactionService transactionService;

    @KafkaListener(
        topics = "fraud-check-completed-events",
        groupId = "transaction-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFraudCheckCompleted(FraudCheckCompletedEvent event, Acknowledgment acknowledgment) {
        try {
            log.info("Received fraud check completed event: transactionId={}, decision={}, riskLevel={}",
                event.getTransactionId(), event.getDecision(), event.getRiskLevel());

            // Update transaction status based on fraud check result
            // This breaks the circular dependency - no synchronous call to fraud-service needed
            switch (event.getDecision()) {
                case APPROVED:
                    transactionService.updateTransactionStatus(
                        event.getTransactionId(),
                        TransactionStatus.APPROVED,
                        "Fraud check passed: " + event.getRiskLevel()
                    );
                    log.info("Transaction approved after fraud check: {}", event.getTransactionId());
                    break;

                case REVIEW:
                    transactionService.updateTransactionStatus(
                        event.getTransactionId(),
                        TransactionStatus.PENDING_REVIEW,
                        "Manual review required - Risk: " + event.getRiskLevel() +
                        ", Score: " + event.getFraudScore()
                    );
                    log.warn("Transaction flagged for review: {}", event.getTransactionId());
                    break;

                case BLOCKED:
                    transactionService.updateTransactionStatus(
                        event.getTransactionId(),
                        TransactionStatus.BLOCKED,
                        "Blocked by fraud check - Reasons: " + String.join(", ", event.getReasons())
                    );
                    log.error("Transaction blocked by fraud check: {}", event.getTransactionId());
                    break;

                default:
                    log.error("Unknown fraud decision: {}", event.getDecision());
            }

            // Store fraud score and metadata for audit trail
            transactionService.storeFraudCheckResult(
                event.getTransactionId(),
                event.getFraudScore(),
                event.getRiskLevel().toString(),
                event.getReasons(),
                event.getMetadata()
            );

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process fraud check completed event: transactionId={}",
                event.getTransactionId(), e);
            // Don't acknowledge - message will be retried or sent to DLQ
            throw e;
        }
    }
}
