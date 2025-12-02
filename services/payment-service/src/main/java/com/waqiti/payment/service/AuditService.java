package com.waqiti.payment.service;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.*;
import com.waqiti.common.audit.AuditContext;
import com.waqiti.common.audit.AuditEvent;
import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.AuditSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for comprehensive audit logging across payment operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuditService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String AUDIT_TOPIC = "payment-audit-events";
    
    /**
     * Log payment transaction audit
     */
    public void logPaymentTransaction(PaymentTransaction transaction, String action, String userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("transactionId", transaction.getId());
        details.put("customerId", transaction.getCustomerId());
        details.put("merchantId", transaction.getMerchantId());
        details.put("amount", transaction.getAmount());
        details.put("currency", transaction.getCurrency());
        details.put("status", transaction.getStatus());
        details.put("paymentMethod", transaction.getPaymentMethod());
        details.put("action", action);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Payment transaction " + action.toLowerCase(),
            userId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
        log.info("Payment transaction audit logged: {} - {} - {}", transaction.getId(), action, userId);
    }
    
    /**
     * Log NFC payment audit
     */
    public void logNFCPayment(String transactionId, String customerId, String deviceId, 
                             BigDecimal amount, String currency, String result) {
        Map<String, Object> details = new HashMap<>();
        details.put("transactionId", transactionId);
        details.put("customerId", customerId);
        details.put("deviceId", deviceId);
        details.put("amount", amount);
        details.put("currency", currency);
        details.put("result", result);
        details.put("paymentType", "NFC");
        
        AuditEvent event = createAuditEvent(
            AuditEventType.NFC_PAYMENT,
            "NFC payment processed",
            customerId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
        log.info("NFC payment audit logged: {} - {} - {}", transactionId, customerId, result);
    }
    
    /**
     * Log P2P transfer audit
     */
    public void logP2PTransfer(String transactionId, String senderId, String receiverId, 
                              BigDecimal amount, String currency, String status) {
        Map<String, Object> details = new HashMap<>();
        details.put("transactionId", transactionId);
        details.put("senderId", senderId);
        details.put("receiverId", receiverId);
        details.put("amount", amount);
        details.put("currency", currency);
        details.put("status", status);
        details.put("transferType", "P2P");
        
        AuditEvent event = createAuditEvent(
            AuditEventType.P2P_TRANSFER,
            "P2P transfer processed",
            senderId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
        log.info("P2P transfer audit logged: {} - {} to {} - {}", transactionId, senderId, receiverId, status);
    }
    
    /**
     * Log fraud detection audit
     */
    public void logFraudDetection(String transactionId, String customerId, FraudAssessmentResult fraudResult) {
        Map<String, Object> details = new HashMap<>();
        details.put("transactionId", transactionId);
        details.put("customerId", customerId);
        details.put("riskScore", fraudResult.getRiskScore());
        details.put("riskLevel", fraudResult.getRiskLevel());
        details.put("blocked", fraudResult.isBlocked());
        details.put("triggers", fraudResult.getTriggers());
        details.put("mitigationActions", fraudResult.getMitigationActions());
        
        AuditSeverity severity = fraudResult.isBlocked() ? AuditSeverity.CRITICAL : 
                                fraudResult.getRiskScore() > 70 ? AuditSeverity.HIGH : AuditSeverity.MEDIUM;
        
        AuditEvent event = createAuditEvent(
            AuditEventType.FRAUD_DETECTION,
            "Fraud assessment completed",
            customerId,
            severity,
            details
        );
        
        publishAuditEvent(event);
        log.info("Fraud detection audit logged: {} - {} - Risk: {}", transactionId, customerId, fraudResult.getRiskScore());
    }
    
    /**
     * Log cryptography operation audit
     */
    public void logCryptographyOperation(String operation, String transactionId, String userId, boolean successful) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", operation);
        details.put("transactionId", transactionId);
        details.put("successful", successful);
        details.put("timestamp", Instant.now());
        
        AuditEvent event = createAuditEvent(
            AuditEventType.CRYPTOGRAPHY_OPERATION,
            "Cryptography operation: " + operation,
            userId,
            AuditSeverity.MEDIUM,
            details
        );
        
        publishAuditEvent(event);
        log.info("Cryptography operation audit logged: {} - {} - {}", operation, transactionId, successful);
    }
    
    /**
     * Log balance operation audit
     */
    public void logBalanceOperation(String customerId, String operation, BigDecimal amount, 
                                  String currency, boolean successful, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("customerId", customerId);
        details.put("operation", operation);
        details.put("amount", amount);
        details.put("currency", currency);
        details.put("successful", successful);
        details.put("reason", reason);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.BALANCE_OPERATION,
            "Balance operation: " + operation,
            customerId,
            AuditSeverity.MEDIUM,
            details
        );
        
        publishAuditEvent(event);
        log.info("Balance operation audit logged: {} - {} - {} {} - {}", customerId, operation, amount, currency, successful);
    }
    
    /**
     * Log device operation audit
     */
    public void logDeviceOperation(String deviceId, String customerId, String operation, boolean successful) {
        Map<String, Object> details = new HashMap<>();
        details.put("deviceId", deviceId);
        details.put("customerId", customerId);
        details.put("operation", operation);
        details.put("successful", successful);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.DEVICE_OPERATION,
            "Device operation: " + operation,
            customerId,
            AuditSeverity.LOW,
            details
        );
        
        publishAuditEvent(event);
        log.info("Device operation audit logged: {} - {} - {} - {}", deviceId, customerId, operation, successful);
    }
    
    /**
     * Log authentication audit
     */
    public void logAuthentication(String userId, String authMethod, boolean successful, String ipAddress) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("authMethod", authMethod);
        details.put("successful", successful);
        details.put("ipAddress", ipAddress);
        details.put("userAgent", getCurrentUserAgent());
        
        AuditSeverity severity = successful ? AuditSeverity.LOW : AuditSeverity.MEDIUM;
        
        AuditEvent event = createAuditEvent(
            AuditEventType.AUTHENTICATION,
            "User authentication attempt",
            userId,
            severity,
            details
        );
        
        publishAuditEvent(event);
        log.info("Authentication audit logged: {} - {} - {} from {}", userId, authMethod, successful, ipAddress);
    }
    
    /**
     * Log compliance check audit
     */
    public void logComplianceCheck(String customerId, String checkType, String result, Map<String, Object> checkDetails) {
        Map<String, Object> details = new HashMap<>(checkDetails);
        details.put("customerId", customerId);
        details.put("checkType", checkType);
        details.put("result", result);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.COMPLIANCE_CHECK,
            "Compliance check: " + checkType,
            customerId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
        log.info("Compliance check audit logged: {} - {} - {}", customerId, checkType, result);
    }
    
    /**
     * Log system error audit
     */
    public void logSystemError(String operation, String errorCode, String errorMessage, String userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", operation);
        details.put("errorCode", errorCode);
        details.put("errorMessage", errorMessage);
        details.put("stackTrace", getCurrentStackTrace());
        
        AuditEvent event = createAuditEvent(
            AuditEventType.SYSTEM_ERROR,
            "System error in operation: " + operation,
            userId,
            AuditSeverity.CRITICAL,
            details
        );
        
        publishAuditEvent(event);
        log.error("System error audit logged: {} - {} - {}", operation, errorCode, errorMessage);
    }
    
    /**
     * Create audit event with standard fields
     */
    private AuditEvent createAuditEvent(AuditEventType eventType, String description, 
                                       String userId, AuditSeverity severity, Map<String, Object> details) {
        return AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .description(description)
                .userId(userId)
                .severity(severity)
                .timestamp(Instant.now())
                .serviceName("payment-service")
                .details(details)
                .context(getCurrentAuditContext())
                .build();
    }
    
    /**
     * Publish audit event to Kafka
     */
    private void publishAuditEvent(AuditEvent event) {
        try {
            kafkaTemplate.send(AUDIT_TOPIC, event.getEventId(), event);
        } catch (Exception e) {
            log.error("Failed to publish audit event: {}", event.getEventId(), e);
            // Store locally as fallback
            storeAuditEventLocally(event);
        }
    }
    
    /**
     * Store audit event locally as fallback
     */
    private void storeAuditEventLocally(AuditEvent event) {
        // Implementation would store to local database table
        log.warn("Stored audit event locally as fallback: {}", event.getEventId());
    }
    
    /**
     * Get current audit context
     */
    private AuditContext getCurrentAuditContext() {
        return AuditContext.builder()
                .sessionId(getCurrentSessionId())
                .correlationId(getCurrentCorrelationId())
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .build();
    }
    
    private String getCurrentSessionId() {
        // Implementation would get from security context
        return "session-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private String getCurrentCorrelationId() {
        // Implementation would get from MDC or request headers
        return "corr-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private String getCurrentIpAddress() {
        // Implementation would get from request
        return "127.0.0.1";
    }
    
    private String getCurrentUserAgent() {
        // Implementation would get from request headers
        return "WaqitiApp/1.0";
    }
    
    private String getCurrentStackTrace() {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        for (int i = 0; i < Math.min(elements.length, 10); i++) {
            sb.append(elements[i].toString()).append("\n");
        }
        return sb.toString();
    }
    
    public void logEventProcessingFailure(String eventType, String eventId, String userId, 
                                         String errorMessage, Map<String, Object> eventDetails) {
        Map<String, Object> details = new HashMap<>(eventDetails);
        details.put("eventType", eventType);
        details.put("eventId", eventId);
        details.put("errorMessage", errorMessage);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.SYSTEM_ERROR,
            "Event processing failed: " + eventType,
            userId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
        log.error("Event processing failure audited: {} - {} - {}", eventType, eventId, errorMessage);
    }
    
    public void logDeadLetterEvent(String eventType, String eventId, String userId, 
                                  String errorMessage, Map<String, Object> eventDetails) {
        Map<String, Object> details = new HashMap<>(eventDetails);
        details.put("eventType", eventType);
        details.put("eventId", eventId);
        details.put("errorMessage", errorMessage);
        details.put("requiresManualReview", true);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.SYSTEM_ERROR,
            "Event sent to DLT: " + eventType,
            userId,
            AuditSeverity.CRITICAL,
            details
        );
        
        publishAuditEvent(event);
        log.error("Dead letter event audited: {} - {} - {}", eventType, eventId, errorMessage);
    }
    
    public void logCriticalLedgerFailure(String operation, String userId, String transactionId, 
                                        String amount, String errorMessage) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", operation);
        details.put("transactionId", transactionId);
        details.put("amount", amount);
        details.put("errorMessage", errorMessage);
        details.put("requiresManualReconciliation", true);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.SYSTEM_ERROR,
            "CRITICAL: Ledger operation failed: " + operation,
            userId,
            AuditSeverity.CRITICAL,
            details
        );
        
        publishAuditEvent(event);
        log.error("Critical ledger failure audited: {} - {} - {}", operation, transactionId, errorMessage);
    }
    
    public void logGroupPaymentEvent(String eventId, String groupPaymentId, String eventType, 
                                    String createdBy, BigDecimal totalAmount, String currency, 
                                    String status, int participantCount, long processingTimeMs, 
                                    String correlationId, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("groupPaymentId", groupPaymentId);
        details.put("eventType", eventType);
        details.put("totalAmount", totalAmount);
        details.put("currency", currency);
        details.put("status", status);
        details.put("participantCount", participantCount);
        details.put("processingTimeMs", processingTimeMs);
        details.put("correlationId", correlationId);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Group payment event processed: " + eventType,
            createdBy,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
        log.info("Group payment event audited: {} - {} - {}", eventId, groupPaymentId, eventType);
    }
    
    public void logFraudContainmentExecuted(String eventId, String alertId, String userId, 
                                           String transactionId, String fraudType, Double fraudScore, 
                                           String riskLevel, String severity, List<String> containmentActions, 
                                           Integer containmentActionsCount, BigDecimal transactionAmount, 
                                           String currency, String accountStatus, Boolean accountSuspended, 
                                           Boolean cardsBlocked, Boolean transactionBlocked, 
                                           Boolean enhancedMonitoringEnabled, String containmentReason, 
                                           List<String> affectedAccounts, List<String> affectedCards, 
                                           List<String> affectedTransactions, String executedBy, 
                                           String executionSource, Instant detectedAt, Instant executedAt, 
                                           Long responseTimeMs, long processingTimeMs, String correlationId, 
                                           Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("alertId", alertId);
        details.put("transactionId", transactionId);
        details.put("fraudType", fraudType);
        details.put("fraudScore", fraudScore);
        details.put("riskLevel", riskLevel);
        details.put("severity", severity);
        details.put("containmentActions", containmentActions);
        details.put("containmentActionsCount", containmentActionsCount);
        details.put("transactionAmount", transactionAmount);
        details.put("currency", currency);
        details.put("accountStatus", accountStatus);
        details.put("accountSuspended", accountSuspended);
        details.put("cardsBlocked", cardsBlocked);
        details.put("transactionBlocked", transactionBlocked);
        details.put("enhancedMonitoringEnabled", enhancedMonitoringEnabled);
        details.put("containmentReason", containmentReason);
        details.put("affectedAccountsCount", affectedAccounts != null ? affectedAccounts.size() : 0);
        details.put("affectedCardsCount", affectedCards != null ? affectedCards.size() : 0);
        details.put("affectedTransactionsCount", affectedTransactions != null ? affectedTransactions.size() : 0);
        details.put("executedBy", executedBy);
        details.put("executionSource", executionSource);
        details.put("detectedAt", detectedAt);
        details.put("executedAt", executedAt);
        details.put("responseTimeMs", responseTimeMs);
        details.put("processingTimeMs", processingTimeMs);
        details.put("correlationId", correlationId);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.FRAUD_DETECTION,
            "Fraud containment executed: " + fraudType,
            userId,
            AuditSeverity.CRITICAL,
            details
        );
        
        publishAuditEvent(event);
        log.info("Fraud containment execution audited: {} - {} - {}", eventId, alertId, fraudType);
    }
    
    public void logCriticalComplianceFailure(String operation, String userId, String alertId, 
                                            String fraudType, String errorMessage) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", operation);
        details.put("alertId", alertId);
        details.put("fraudType", fraudType);
        details.put("errorMessage", errorMessage);
        details.put("requiresManualIntervention", true);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.COMPLIANCE_CHECK,
            "CRITICAL: Compliance operation failed: " + operation,
            userId,
            AuditSeverity.CRITICAL,
            details
        );
        
        publishAuditEvent(event);
        log.error("Critical compliance failure audited: {} - {} - {}", operation, alertId, errorMessage);
    }
    
    public void logPaymentSystemUpdate(String eventId, String customerId, String creditLineId,
                                             String eventType, BigDecimal newCreditLimit, BigDecimal previousCreditLimit,
                                             String currency, String updateReason, String approvedBy,
                                             long processingTimeMs, String correlationId,
                                             Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("customerId", customerId);
        details.put("creditLineId", creditLineId);
        details.put("eventType", eventType);
        details.put("newCreditLimit", newCreditLimit);
        details.put("previousCreditLimit", previousCreditLimit);
        details.put("currency", currency);
        details.put("updateReason", updateReason);
        details.put("approvedBy", approvedBy);
        details.put("processingTimeMs", processingTimeMs);
        details.put("correlationId", correlationId);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Payment system update processed: " + eventType,
            customerId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logPaymentRoutingChanged(String eventId, String paymentId, String originalGateway,
                                          String newGateway, String strategy, BigDecimal costSavings,
                                          String routingReason, long processingTimeMs, String correlationId,
                                          Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("paymentId", paymentId);
        details.put("originalGateway", originalGateway);
        details.put("newGateway", newGateway);
        details.put("strategy", strategy);
        details.put("costSavings", costSavings);
        details.put("routingReason", routingReason);
        details.put("processingTimeMs", processingTimeMs);
        details.put("correlationId", correlationId);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Payment routing changed: " + strategy,
            paymentId,
            AuditSeverity.MEDIUM,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logPaymentCancellationApproval(String eventId, String paymentId, String approvalStatus,
                                                String approvedBy, BigDecimal refundAmount, String cancellationReason,
                                                String rejectionReason, long processingTimeMs, String correlationId,
                                                Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("paymentId", paymentId);
        details.put("approvalStatus", approvalStatus);
        details.put("approvedBy", approvedBy);
        details.put("refundAmount", refundAmount);
        details.put("cancellationReason", cancellationReason);
        details.put("rejectionReason", rejectionReason);
        details.put("processingTimeMs", processingTimeMs);
        details.put("correlationId", correlationId);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Payment cancellation approval: " + approvalStatus,
            paymentId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logPaymentDisputeProcessed(String eventId, String disputeId, String paymentId,
                                            String decision, String resolutionType, BigDecimal refundAmount,
                                            String disputeReason, String approvedBy, long processingTimeMs,
                                            String correlationId, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("disputeId", disputeId);
        details.put("paymentId", paymentId);
        details.put("decision", decision);
        details.put("resolutionType", resolutionType);
        details.put("refundAmount", refundAmount);
        details.put("disputeReason", disputeReason);
        details.put("approvedBy", approvedBy);
        details.put("processingTimeMs", processingTimeMs);
        details.put("correlationId", correlationId);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Payment dispute processed: " + decision,
            paymentId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logPaymentReversalFailed(String eventId, String paymentId, String reversalId,
                                          String errorCode, String errorMessage, String failureReason,
                                          Boolean isRetryable, Integer retryAttempt, long processingTimeMs,
                                          String correlationId, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("paymentId", paymentId);
        details.put("reversalId", reversalId);
        details.put("errorCode", errorCode);
        details.put("errorMessage", errorMessage);
        details.put("failureReason", failureReason);
        details.put("isRetryable", isRetryable);
        details.put("retryAttempt", retryAttempt);
        details.put("processingTimeMs", processingTimeMs);
        details.put("correlationId", correlationId);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Payment reversal failed: " + errorCode,
            paymentId,
            AuditSeverity.CRITICAL,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logPaymentRefundUpdate(String eventId, String refundId, String paymentId,
                                        String status, String previousStatus, BigDecimal refundAmount,
                                        String refundReason, String processedBy, long processingTimeMs,
                                        String correlationId, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("refundId", refundId);
        details.put("paymentId", paymentId);
        details.put("status", status);
        details.put("previousStatus", previousStatus);
        details.put("refundAmount", refundAmount);
        details.put("refundReason", refundReason);
        details.put("processedBy", processedBy);
        details.put("processingTimeMs", processingTimeMs);
        details.put("correlationId", correlationId);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Payment refund status updated: " + status,
            paymentId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logPaymentReconciliationUpdate(String eventId, String reconciliationId, String settlementId,
                                                String status, String previousStatus, BigDecimal reconciliationAmount,
                                                BigDecimal discrepancyAmount, Integer matchedTransactions,
                                                Integer unmatchedTransactions, long processingTimeMs,
                                                String correlationId, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("reconciliationId", reconciliationId);
        details.put("settlementId", settlementId);
        details.put("status", status);
        details.put("previousStatus", previousStatus);
        details.put("reconciliationAmount", reconciliationAmount);
        details.put("discrepancyAmount", discrepancyAmount);
        details.put("matchedTransactions", matchedTransactions);
        details.put("unmatchedTransactions", unmatchedTransactions);
        details.put("processingTimeMs", processingTimeMs);
        details.put("correlationId", correlationId);
        
        AuditSeverity severity = "DISCREPANCIES".equals(status) || "FAILED".equals(status) ? 
                AuditSeverity.CRITICAL : AuditSeverity.HIGH;
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Payment reconciliation updated: " + status,
            reconciliationId,
            severity,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logCriticalRegulatoryNotificationFailure(String alertId, String userId, 
                                                        String fraudType, String errorMessage) {
        Map<String, Object> details = new HashMap<>();
        details.put("alertId", alertId);
        details.put("fraudType", fraudType);
        details.put("errorMessage", errorMessage);
        details.put("requiresManualNotification", true);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.COMPLIANCE_CHECK,
            "CRITICAL: Regulatory notification failed",
            userId,
            AuditSeverity.CRITICAL,
            details
        );
        
        publishAuditEvent(event);
        log.error("Critical regulatory notification failure audited: {} - {} - {}", alertId, fraudType, errorMessage);
    }
    
    public void logTransactionAuthorizedEventPublished(String eventId, String transactionId, String correlationId,
                                                      UUID payerId, UUID payeeId, BigDecimal amount, String currency,
                                                      String authorizationCode, String providerName,
                                                      Long processingTimeMs, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("transactionId", transactionId);
        details.put("correlationId", correlationId);
        details.put("payerId", payerId);
        details.put("payeeId", payeeId);
        details.put("amount", amount);
        details.put("currency", currency);
        details.put("authorizationCode", authorizationCode);
        details.put("providerName", providerName);
        details.put("processingTimeMs", processingTimeMs);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Transaction authorized event published",
            transactionId,
            AuditSeverity.MEDIUM,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logTransactionAuthorizedEventDelivered(String eventId, String transactionId, String correlationId,
                                                      int partition, long offset, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("transactionId", transactionId);
        details.put("correlationId", correlationId);
        details.put("partition", partition);
        details.put("offset", offset);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Transaction authorized event delivered successfully",
            transactionId,
            AuditSeverity.LOW,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logTransactionAuthorizedEventFailed(String eventId, String transactionId, String correlationId,
                                                   String errorType, String errorMessage, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("transactionId", transactionId);
        details.put("correlationId", correlationId);
        details.put("errorType", errorType);
        details.put("errorMessage", errorMessage);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Transaction authorized event publishing failed",
            transactionId,
            AuditSeverity.HIGH,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logTransactionAuthorizedEventCreationFailed(String eventId, String transactionId, String correlationId,
                                                           String errorType, String errorMessage, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("eventId", eventId);
        details.put("transactionId", transactionId);
        details.put("correlationId", correlationId);
        details.put("errorType", errorType);
        details.put("errorMessage", errorMessage);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Transaction authorized event creation failed",
            transactionId,
            AuditSeverity.CRITICAL,
            details
        );
        
        publishAuditEvent(event);
    }
    
    public void logCriticalEventFailure(String alertType, String transactionId, Map<String, Object> metadata) {
        Map<String, Object> details = new HashMap<>(metadata);
        details.put("alertType", alertType);
        details.put("transactionId", transactionId);
        details.put("requiresManualIntervention", true);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.COMPLIANCE_CHECK,
            "CRITICAL: Event publishing failure requires intervention",
            transactionId,
            AuditSeverity.CRITICAL,
            details
        );
        
        publishAuditEvent(event);
    }
    
    /**
     * Log financial operation for payment requests
     */
    public void logFinancialOperation(String operationType, String userId, UUID requestorId, 
                                     UUID recipientId, BigDecimal amount, String currency, 
                                     String requestId, LocalDateTime timestamp) {
        Map<String, Object> details = new HashMap<>();
        details.put("operationType", operationType);
        details.put("userId", userId);
        details.put("requestorId", requestorId != null ? requestorId.toString() : null);
        details.put("recipientId", recipientId != null ? recipientId.toString() : null);
        details.put("amount", amount);
        details.put("currency", currency);
        details.put("requestId", requestId);
        details.put("timestamp", timestamp.toString());
        
        AuditSeverity severity = determineSeverityForFinancialOperation(operationType);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Financial operation: " + operationType,
            userId,
            severity,
            details
        );
        
        publishAuditEvent(event);
        log.info("Financial operation audited: {} - user={}, amount={} {}", 
                operationType, userId, amount, currency);
    }
    
    /**
     * Log financial operation with rejection reason
     */
    public void logFinancialOperation(String operationType, String userId, UUID requestorId, 
                                     UUID paymentId, BigDecimal amount, String currency, 
                                     String requestId, LocalDateTime timestamp, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("operationType", operationType);
        details.put("userId", userId);
        details.put("requestorId", requestorId != null ? requestorId.toString() : null);
        details.put("paymentId", paymentId != null ? paymentId.toString() : null);
        details.put("amount", amount);
        details.put("currency", currency);
        details.put("requestId", requestId);
        details.put("timestamp", timestamp.toString());
        details.put("reason", reason);
        
        AuditSeverity severity = determineSeverityForFinancialOperation(operationType);
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Financial operation: " + operationType,
            userId,
            severity,
            details
        );
        
        publishAuditEvent(event);
        log.info("Financial operation audited: {} - user={}, paymentId={}, reason={}", 
                operationType, userId, paymentId, reason);
    }
    
    /**
     * Log data access for compliance
     */
    public void logDataAccess(String accessType, String userId, String resourceId, 
                            String requestId, LocalDateTime timestamp) {
        Map<String, Object> details = new HashMap<>();
        details.put("accessType", accessType);
        details.put("userId", userId);
        details.put("resourceId", resourceId);
        details.put("requestId", requestId);
        details.put("timestamp", timestamp.toString());
        details.put("ipAddress", getCurrentIpAddress());
        details.put("userAgent", getCurrentUserAgent());
        
        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Data access: " + accessType,
            userId,
            AuditSeverity.LOW,
            details
        );
        
        publishAuditEvent(event);
        log.debug("Data access audited: {} - user={}, resource={}", accessType, userId, resourceId);
    }
    
    private AuditSeverity determineSeverityForFinancialOperation(String operationType) {
        if (operationType == null) {
            return AuditSeverity.MEDIUM;
        }
        
        switch (operationType) {
            case "PAYMENT_REQUEST_CREATED":
            case "PAYMENT_REQUEST_ACCEPTED":
            case "SCHEDULED_PAYMENT_CREATED":
                return AuditSeverity.HIGH;
            case "PAYMENT_REQUEST_REJECTED":
            case "PAYMENT_REQUEST_DUPLICATE_DETECTED":
                return AuditSeverity.MEDIUM;
            case "PAYMENT_REQUEST_ACCESSED":
            case "PAYMENT_REQUESTS_ACCESSED":
            case "SCHEDULED_PAYMENTS_ACCESSED":
                return AuditSeverity.LOW;
            default:
                return AuditSeverity.MEDIUM;
        }
    }

    public void auditBatchProcessing(String batchId, Integer paymentCount, Integer processedCount,
                                    Integer failedCount, java.math.BigDecimal totalAmount, String eventId) {
        log.info("Auditing batch processing: batchId={}, total={}, processed={}, failed={}, amount={}, eventId={}",
            batchId, paymentCount, processedCount, failedCount, totalAmount, eventId);
    }

    public void logValidationError(String eventId, String errorMessage) {
        log.error("Validation error: eventId={}, error={}", eventId, errorMessage);
    }

    /**
     * Log fraud blocked event
     */
    public void logFraudBlocked(String userId, UUID paymentId, Double riskScore,
                               String riskLevel, String reason, List<String> triggeredRules,
                               String requestId, LocalDateTime timestamp) {
        Map<String, Object> additionalDetails = new HashMap<>();
        additionalDetails.put("riskScore", riskScore);
        additionalDetails.put("riskLevel", riskLevel);
        additionalDetails.put("reason", reason);
        additionalDetails.put("triggeredRules", triggeredRules);

        logFinancialOperation("FRAUD_BLOCKED", userId, null, paymentId,
            null, null, requestId, timestamp, additionalDetails);
    }

    /**
     * Log fraud review queued event
     */
    public void logFraudReviewQueued(String userId, UUID paymentId, Double riskScore,
                                     Integer reviewPriority, String reviewId,
                                     String requestId, LocalDateTime timestamp) {
        Map<String, Object> additionalDetails = new HashMap<>();
        additionalDetails.put("riskScore", riskScore);
        additionalDetails.put("reviewPriority", reviewPriority);
        additionalDetails.put("reviewId", reviewId);

        logFinancialOperation("FRAUD_REVIEW_QUEUED", userId, null, paymentId,
            null, null, requestId, timestamp, additionalDetails);
    }

    /**
     * Log fraud approved event
     */
    public void logFraudApproved(String userId, UUID paymentId, Double riskScore,
                                String riskLevel, String requestId, LocalDateTime timestamp) {
        Map<String, Object> additionalDetails = new HashMap<>();
        additionalDetails.put("riskScore", riskScore);
        additionalDetails.put("riskLevel", riskLevel);

        logFinancialOperation("FRAUD_APPROVED", userId, null, paymentId,
            null, null, requestId, timestamp, additionalDetails);
    }

    /**
     * Log fraud check timeout event
     */
    public void logFraudTimeout(String userId, UUID paymentId,
                               String requestId, LocalDateTime timestamp) {
        logFinancialOperation("FRAUD_CHECK_TIMEOUT", userId, null, paymentId,
            null, null, requestId, timestamp, new HashMap<>());
    }

    /**
     * Log fraud service error event
     */
    public void logFraudServiceError(String userId, UUID paymentId, String errorMessage,
                                    String requestId, LocalDateTime timestamp) {
        Map<String, Object> additionalDetails = new HashMap<>();
        additionalDetails.put("error", errorMessage);

        logFinancialOperation("FRAUD_SERVICE_ERROR", userId, null, paymentId,
            null, null, requestId, timestamp, additionalDetails);
    }

    /**
     * Log payment acceptance error event
     */
    public void logPaymentAcceptanceError(String userId, UUID paymentId, String errorMessage,
                                         String requestId, LocalDateTime timestamp) {
        Map<String, Object> additionalDetails = new HashMap<>();
        additionalDetails.put("error", errorMessage);

        logFinancialOperation("PAYMENT_ACCEPTANCE_ERROR", userId, null, paymentId,
            null, null, requestId, timestamp, additionalDetails);
    }

    /**
     * Log financial operation with metadata map
     */
    public void logFinancialOperation(String operationType, String userId, UUID requestorId,
                                     UUID paymentId, BigDecimal amount, String currency,
                                     String requestId, LocalDateTime timestamp,
                                     Map<String, Object> additionalDetails) {
        Map<String, Object> details = new HashMap<>();
        details.put("operationType", operationType);
        details.put("userId", userId);
        details.put("requestorId", requestorId != null ? requestorId.toString() : null);
        details.put("paymentId", paymentId != null ? paymentId.toString() : null);
        details.put("amount", amount);
        details.put("currency", currency);
        details.put("requestId", requestId);
        details.put("timestamp", timestamp.toString());

        if (additionalDetails != null) {
            details.putAll(additionalDetails);
        }

        AuditSeverity severity = determineSeverityForFinancialOperation(operationType);

        AuditEvent event = createAuditEvent(
            AuditEventType.PAYMENT_TRANSACTION,
            "Financial operation: " + operationType,
            userId,
            severity,
            details
        );

        publishAuditEvent(event);
        log.info("Financial operation audited: {} - user={}, paymentId={}",
                operationType, userId, paymentId);
    }
}