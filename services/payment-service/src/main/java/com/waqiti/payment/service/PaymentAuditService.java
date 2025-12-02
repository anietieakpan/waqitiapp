package com.waqiti.payment.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.audit.FinancialAuditLog;
import com.waqiti.common.events.model.AuditEvent;
import com.waqiti.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Payment Audit Service - PRODUCTION READY
 *
 * Provides comprehensive audit logging for all payment operations
 * Ensures regulatory compliance (PCI DSS, SOC 2, GDPR, SOX)
 *
 * P0 FIX - Created for PaymentTransactionService integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentAuditService {

    private final AuditService auditService;

    /**
     * Log payment initiation with comprehensive details
     */
    public void logPaymentInitiated(Payment payment) {
        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", payment.getPaymentId());
        details.put("userId", payment.getUserId());
        details.put("merchantId", payment.getMerchantId());
        details.put("amount", payment.getAmount());
        details.put("currency", payment.getCurrency());
        details.put("status", payment.getStatus());
        details.put("paymentMethod", payment.getPaymentMethod());
        details.put("provider", payment.getProvider());
        details.put("idempotencyKey", payment.getIdempotencyKey());

        // Create financial audit log
        FinancialAuditLog auditLog = FinancialAuditLog.builder()
            .transactionId(payment.getPaymentId().toString())
            .transactionType(FinancialAuditLog.TransactionType.PAYMENT)
            .userId(payment.getUserId().toString())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(FinancialAuditLog.TransactionStatus.INITIATED)
            .details(details)
            .timestamp(LocalDateTime.now())
            .build();

        auditService.logFinancialTransaction(auditLog);

        // Also log as general audit event
        auditService.logEvent(AuditEvent.builder()
            .eventType("PAYMENT_INITIATED")
            .entityType("PAYMENT")
            .entityId(payment.getPaymentId().toString())
            .userId(payment.getUserId().toString())
            .action("CREATE")
            .status("SUCCESS")
            .details(details)
            .timestamp(LocalDateTime.now())
            .build());

        log.info("AUDIT: Payment initiated - PaymentId: {}, User: {}, Amount: {} {}",
                payment.getPaymentId(), payment.getUserId(), payment.getAmount(), payment.getCurrency());
    }

    /**
     * Log payment completion with transaction details
     */
    public void logPaymentCompleted(Payment payment) {
        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", payment.getPaymentId());
        details.put("userId", payment.getUserId());
        details.put("merchantId", payment.getMerchantId());
        details.put("amount", payment.getAmount());
        details.put("currency", payment.getCurrency());
        details.put("status", payment.getStatus());
        details.put("completedAt", payment.getCompletedAt());
        details.put("externalTransactionId", payment.getExternalTransactionId());
        details.put("fraudScore", payment.getFraudScore());
        details.put("riskLevel", payment.getRiskLevel());
        details.put("provider", payment.getProvider());
        details.put("providerPaymentId", payment.getProviderPaymentId());

        // Create financial audit log
        FinancialAuditLog auditLog = FinancialAuditLog.builder()
            .transactionId(payment.getPaymentId().toString())
            .transactionType(FinancialAuditLog.TransactionType.PAYMENT)
            .userId(payment.getUserId().toString())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(FinancialAuditLog.TransactionStatus.COMPLETED)
            .details(details)
            .timestamp(LocalDateTime.now())
            .build();

        auditService.logFinancialTransaction(auditLog);

        // Also log as general audit event
        auditService.logEvent(AuditEvent.builder()
            .eventType("PAYMENT_COMPLETED")
            .entityType("PAYMENT")
            .entityId(payment.getPaymentId().toString())
            .userId(payment.getUserId().toString())
            .action("COMPLETE")
            .status("SUCCESS")
            .details(details)
            .timestamp(LocalDateTime.now())
            .build());

        log.info("AUDIT: Payment completed - PaymentId: {}, User: {}, Amount: {} {}, FraudScore: {}",
                payment.getPaymentId(), payment.getUserId(), payment.getAmount(),
                payment.getCurrency(), payment.getFraudScore());
    }

    /**
     * Log payment failure with error details
     */
    public void logPaymentFailed(Payment payment, Exception exception) {
        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", payment.getPaymentId());
        details.put("userId", payment.getUserId());
        details.put("merchantId", payment.getMerchantId());
        details.put("amount", payment.getAmount());
        details.put("currency", payment.getCurrency());
        details.put("status", payment.getStatus());
        details.put("failureReason", payment.getFailureReason());
        details.put("errorMessage", exception.getMessage());
        details.put("errorType", exception.getClass().getSimpleName());
        details.put("retryCount", payment.getRetryCount());
        details.put("failedAt", payment.getFailedAt());

        // Create financial audit log
        FinancialAuditLog auditLog = FinancialAuditLog.builder()
            .transactionId(payment.getPaymentId().toString())
            .transactionType(FinancialAuditLog.TransactionType.PAYMENT)
            .userId(payment.getUserId().toString())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(FinancialAuditLog.TransactionStatus.FAILED)
            .details(details)
            .errorMessage(exception.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

        auditService.logFinancialTransaction(auditLog);

        // Also log as general audit event
        auditService.logEvent(AuditEvent.builder()
            .eventType("PAYMENT_FAILED")
            .entityType("PAYMENT")
            .entityId(payment.getPaymentId().toString())
            .userId(payment.getUserId().toString())
            .action("FAIL")
            .status("FAILED")
            .details(details)
            .errorMessage(exception.getMessage())
            .timestamp(LocalDateTime.now())
            .build());

        log.error("AUDIT: Payment failed - PaymentId: {}, User: {}, Reason: {}, Error: {}",
                payment.getPaymentId(), payment.getUserId(), payment.getFailureReason(), exception.getMessage());
    }

    /**
     * Log payment declined due to fraud or policy violation
     */
    public void logPaymentDeclined(Payment payment, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", payment.getPaymentId());
        details.put("userId", payment.getUserId());
        details.put("merchantId", payment.getMerchantId());
        details.put("amount", payment.getAmount());
        details.put("currency", payment.getCurrency());
        details.put("declineReason", reason);
        details.put("fraudScore", payment.getFraudScore());
        details.put("riskLevel", payment.getRiskLevel());
        details.put("fraudCheckedAt", payment.getFraudCheckedAt());
        details.put("failureReason", payment.getFailureReason());

        // Create financial audit log
        FinancialAuditLog auditLog = FinancialAuditLog.builder()
            .transactionId(payment.getPaymentId().toString())
            .transactionType(FinancialAuditLog.TransactionType.PAYMENT)
            .userId(payment.getUserId().toString())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(FinancialAuditLog.TransactionStatus.DECLINED)
            .details(details)
            .errorMessage(reason)
            .timestamp(LocalDateTime.now())
            .build();

        auditService.logFinancialTransaction(auditLog);

        // Also log as general audit event (HIGH PRIORITY for compliance)
        auditService.logEvent(AuditEvent.builder()
            .eventType("PAYMENT_DECLINED")
            .entityType("PAYMENT")
            .entityId(payment.getPaymentId().toString())
            .userId(payment.getUserId().toString())
            .action("DECLINE")
            .status("DECLINED")
            .details(details)
            .errorMessage(reason)
            .timestamp(LocalDateTime.now())
            .severity("HIGH") // Mark as high severity for compliance reporting
            .build());

        log.warn("AUDIT: Payment declined - PaymentId: {}, User: {}, Reason: {}, FraudScore: {}",
                payment.getPaymentId(), payment.getUserId(), reason, payment.getFraudScore());
    }

    /**
     * Log payment processing event (for intermediate states)
     */
    public void logPaymentProcessing(Payment payment, String stage) {
        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", payment.getPaymentId());
        details.put("userId", payment.getUserId());
        details.put("amount", payment.getAmount());
        details.put("currency", payment.getCurrency());
        details.put("status", payment.getStatus());
        details.put("processingStage", stage);

        auditService.logEvent(AuditEvent.builder()
            .eventType("PAYMENT_PROCESSING")
            .entityType("PAYMENT")
            .entityId(payment.getPaymentId().toString())
            .userId(payment.getUserId().toString())
            .action("PROCESS")
            .status("IN_PROGRESS")
            .details(details)
            .timestamp(LocalDateTime.now())
            .build());

        log.debug("AUDIT: Payment processing - PaymentId: {}, Stage: {}", payment.getPaymentId(), stage);
    }

    /**
     * Log payment retry attempt
     */
    public void logPaymentRetry(Payment payment, int retryAttempt) {
        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", payment.getPaymentId());
        details.put("userId", payment.getUserId());
        details.put("amount", payment.getAmount());
        details.put("currency", payment.getCurrency());
        details.put("retryAttempt", retryAttempt);
        details.put("previousFailureReason", payment.getFailureReason());
        details.put("lastRetryAt", payment.getLastRetryAt());

        auditService.logEvent(AuditEvent.builder()
            .eventType("PAYMENT_RETRY")
            .entityType("PAYMENT")
            .entityId(payment.getPaymentId().toString())
            .userId(payment.getUserId().toString())
            .action("RETRY")
            .status("IN_PROGRESS")
            .details(details)
            .timestamp(LocalDateTime.now())
            .build());

        log.info("AUDIT: Payment retry - PaymentId: {}, Attempt: {}/{}",
                payment.getPaymentId(), retryAttempt, 3);
    }
}
