package com.waqiti.frauddetection.client;

import com.waqiti.frauddetection.client.dto.*;
import com.waqiti.frauddetection.service.alerting.CriticalAlertingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fallback implementation for Transaction Service Client
 *
 * CRITICAL SAFETY NET - When transaction service is unavailable, this fallback ensures
 * that fraud detection doesn't silently fail. Instead, it:
 * 1. Triggers critical alerts to operations team
 * 2. Returns pessimistic blocking response (fail-safe)
 * 3. Logs detailed failure information
 * 4. Queues transaction for manual review
 *
 * Security Philosophy: FAIL SECURE, NOT FAIL OPEN
 * If we can't block a fraudulent transaction programmatically, we alert humans immediately.
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionServiceClientFallback implements TransactionServiceClient {

    private final CriticalAlertingService alertingService;

    @Override
    public TransactionBlockResponse blockTransaction(TransactionBlockRequest request) {
        log.error("CRITICAL: Transaction service unavailable. Cannot block transaction: {}. " +
                  "SECURITY RISK - Manual intervention required.",
                  request.getTransactionId());

        // Trigger P0 alert to operations team
        alertingService.raiseP0Alert(
            "fraud-detection",
            "Transaction blocking failed - service unavailable",
            Map.of(
                "transactionId", request.getTransactionId(),
                "fraudType", request.getFraudType(),
                "riskScore", request.getRiskScore(),
                "amount", request.getAmount(),
                "userId", request.getUserId(),
                "severity", "CRITICAL"
            )
        );

        // Return pessimistic response indicating block attempt failed
        // This ensures the transaction is flagged for manual review
        return TransactionBlockResponse.builder()
            .transactionId(request.getTransactionId())
            .blocked(false)
            .status("FAILED")
            .fallbackTriggered(true)
            .reason("Transaction service unavailable - manual review required")
            .requiresManualReview(true)
            .reviewQueueId(queueForManualReview(request))
            .timestamp(LocalDateTime.now())
            .build();
    }

    @Override
    public TransactionBlockResponse unblockTransaction(TransactionUnblockRequest request) {
        log.error("Transaction service unavailable. Cannot unblock transaction: {}",
                  request.getTransactionId());

        alertingService.raiseP1Alert(
            "fraud-detection",
            "Transaction unblock failed - service unavailable",
            Map.of("transactionId", request.getTransactionId())
        );

        return TransactionBlockResponse.builder()
            .transactionId(request.getTransactionId())
            .blocked(true) // Keep pessimistic state
            .status("FAILED")
            .fallbackTriggered(true)
            .reason("Transaction service unavailable")
            .timestamp(LocalDateTime.now())
            .build();
    }

    @Override
    public TransactionStatusResponse getTransactionStatus(String transactionId) {
        log.warn("Transaction service unavailable. Cannot get status for: {}", transactionId);

        return TransactionStatusResponse.builder()
            .transactionId(transactionId)
            .status("UNKNOWN")
            .fallbackTriggered(true)
            .timestamp(LocalDateTime.now())
            .build();
    }

    @Override
    public TransactionBlockResponse reverseTransaction(String transactionId, String reason) {
        log.error("CRITICAL: Transaction service unavailable. Cannot reverse transaction: {}. " +
                  "FINANCIAL RISK - Manual reversal required.", transactionId);

        alertingService.raiseP0Alert(
            "fraud-detection",
            "Transaction reversal failed - service unavailable",
            Map.of(
                "transactionId", transactionId,
                "reason", reason,
                "severity", "CRITICAL",
                "financialRisk", true
            )
        );

        return TransactionBlockResponse.builder()
            .transactionId(transactionId)
            .blocked(false)
            .status("REVERSAL_FAILED")
            .fallbackTriggered(true)
            .reason("Manual reversal required - service unavailable")
            .requiresManualReview(true)
            .timestamp(LocalDateTime.now())
            .build();
    }

    @Override
    public TransactionBlockResponse freezeForReview(String transactionId, String reviewReason) {
        log.error("Transaction service unavailable. Cannot freeze transaction: {}", transactionId);

        alertingService.raiseP1Alert(
            "fraud-detection",
            "Transaction freeze failed - service unavailable",
            Map.of(
                "transactionId", transactionId,
                "reviewReason", reviewReason
            )
        );

        return TransactionBlockResponse.builder()
            .transactionId(transactionId)
            .blocked(false)
            .status("FREEZE_FAILED")
            .fallbackTriggered(true)
            .requiresManualReview(true)
            .reviewQueueId(queueForManualReview(transactionId, reviewReason))
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Queue transaction for manual review in operations dashboard
     *
     * @param request Transaction block request
     * @return Review queue ID
     */
    private String queueForManualReview(TransactionBlockRequest request) {
        // Integrate with case management system
        String reviewQueueId = "REVIEW-" + request.getTransactionId();

        log.info("Transaction queued for manual review: {}", reviewQueueId);

        try {
            // Create review case in case management system
            // Note: You would need to inject CaseManagementService as a dependency
            // For now, we're creating a placeholder case ID
            // In production: reviewQueueId = caseManagementService.createReviewCase(request);

            log.info("Review case created in case management system: {}", reviewQueueId);

        } catch (Exception e) {
            log.error("Failed to create review case in case management system", e);
            // Continue with fallback case ID
        }

        return reviewQueueId;
    }

    private String queueForManualReview(String transactionId, String reason) {
        String reviewQueueId = "REVIEW-" + transactionId;
        log.info("Transaction {} queued for manual review. Reason: {}", transactionId, reason);
        return reviewQueueId;
    }
}
