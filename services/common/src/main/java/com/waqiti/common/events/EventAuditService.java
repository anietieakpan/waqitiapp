package com.waqiti.common.events;

import com.waqiti.common.audit.SecureAuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for auditing financial events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAuditService {
    
    private final SecureAuditLogger auditLogger;
    
    /**
     * Audit payment event
     */
    public void auditPaymentEvent(PaymentEvent event) {
        try {
            Map<String, Object> metadata = Map.of(
                "paymentId", event.getPaymentId(),
                "amount", event.getAmount(),
                "currency", event.getCurrency(),
                "status", event.getStatus(),
                "paymentMethod", event.getPaymentMethod(),
                "eventType", event.getEventType()
            );
            
            auditLogger.logFinancialEvent(
                mapToFinancialEventType(event.getEventType()),
                event.getPaymentId(),
                metadata
            );
            
        } catch (Exception e) {
            log.error("Failed to audit payment event: {}", event.getPaymentId(), e);
        }
    }
    
    /**
     * Audit transaction event
     */
    public void auditTransactionEvent(TransactionEvent event) {
        try {
            Map<String, Object> metadata = Map.of(
                "transactionId", event.getTransactionId(),
                "amount", event.getAmount(),
                "currency", event.getCurrency(),
                "status", event.getStatus(),
                "transactionType", event.getTransactionType(),
                "eventType", event.getEventType()
            );
            
            auditLogger.logFinancialEvent(
                mapToFinancialEventType(event.getEventType()),
                event.getTransactionId(),
                metadata
            );
            
        } catch (Exception e) {
            log.error("Failed to audit transaction event: {}", event.getTransactionId(), e);
        }
    }
    
    /**
     * Audit general financial event
     */
    public void auditFinancialEvent(String eventType, UUID entityId, Map<String, Object> details) {
        try {
            auditLogger.logFinancialEvent(
                mapToFinancialEventType(eventType),
                entityId,
                details
            );
        } catch (Exception e) {
            log.error("Failed to audit financial event: {} for entity: {}", eventType, entityId, e);
        }
    }
    
    /**
     * Audit security-related financial event
     */
    public void auditSecurityEvent(String eventType, String details) {
        try {
            SecureAuditLogger.SecurityEventType securityEventType = switch (eventType.toLowerCase()) {
                case "suspicious_payment" -> SecureAuditLogger.SecurityEventType.SUSPICIOUS_ACTIVITY;
                case "fraud_detected" -> SecureAuditLogger.SecurityEventType.SECURITY_ALERT;
                case "account_locked" -> SecureAuditLogger.SecurityEventType.ACCOUNT_LOCKED;
                case "unusual_transaction" -> SecureAuditLogger.SecurityEventType.SUSPICIOUS_ACTIVITY;
                default -> SecureAuditLogger.SecurityEventType.SECURITY_ALERT;
            };
            
            auditLogger.logSecurityEvent(securityEventType, details);
            
        } catch (Exception e) {
            log.error("Failed to audit security event: {}", eventType, e);
        }
    }
    
    /**
     * Audit compliance event
     */
    public void auditComplianceEvent(String eventType, UUID entityId, String rule, Map<String, Object> metadata) {
        try {
            SecureAuditLogger.ComplianceEventType complianceEventType = switch (eventType.toLowerCase()) {
                case "aml_check" -> SecureAuditLogger.ComplianceEventType.AML_CHECK;
                case "kyc_verification" -> SecureAuditLogger.ComplianceEventType.KYC_VERIFICATION;
                case "sanctions_screening" -> SecureAuditLogger.ComplianceEventType.SANCTIONS_SCREENING;
                case "risk_assessment" -> SecureAuditLogger.ComplianceEventType.RISK_ASSESSMENT;
                default -> SecureAuditLogger.ComplianceEventType.REGULATORY_REPORT;
            };
            
            auditLogger.logComplianceEvent(complianceEventType, entityId, rule, metadata);
            
        } catch (Exception e) {
            log.error("Failed to audit compliance event: {} for entity: {}", eventType, entityId, e);
        }
    }
    
    /**
     * Map event type string to financial event type enum
     */
    private SecureAuditLogger.FinancialEventType mapToFinancialEventType(String eventType) {
        return switch (eventType.toLowerCase()) {
            case "payment_initiated" -> SecureAuditLogger.FinancialEventType.PAYMENT_INITIATED;
            case "payment_completed" -> SecureAuditLogger.FinancialEventType.PAYMENT_COMPLETED;
            case "payment_failed" -> SecureAuditLogger.FinancialEventType.PAYMENT_FAILED;
            case "transfer_initiated" -> SecureAuditLogger.FinancialEventType.TRANSFER_INITIATED;
            case "transfer_completed" -> SecureAuditLogger.FinancialEventType.TRANSFER_COMPLETED;
            case "transfer_failed" -> SecureAuditLogger.FinancialEventType.TRANSFER_FAILED;
            case "deposit_initiated" -> SecureAuditLogger.FinancialEventType.DEPOSIT_INITIATED;
            case "deposit_completed" -> SecureAuditLogger.FinancialEventType.DEPOSIT_COMPLETED;
            case "deposit_failed" -> SecureAuditLogger.FinancialEventType.DEPOSIT_FAILED;
            case "withdrawal_initiated" -> SecureAuditLogger.FinancialEventType.WITHDRAWAL_INITIATED;
            case "withdrawal_completed" -> SecureAuditLogger.FinancialEventType.WITHDRAWAL_COMPLETED;
            case "withdrawal_failed" -> SecureAuditLogger.FinancialEventType.WITHDRAWAL_FAILED;
            case "balance_inquiry" -> SecureAuditLogger.FinancialEventType.BALANCE_INQUIRY;
            case "refund_issued" -> SecureAuditLogger.FinancialEventType.REFUND_ISSUED;
            case "chargeback_received" -> SecureAuditLogger.FinancialEventType.CHARGEBACK_RECEIVED;
            default -> SecureAuditLogger.FinancialEventType.PAYMENT_INITIATED;
        };
    }
    
    /**
     * Log event published to Kafka
     */
    public void logEventPublished(FinancialEvent event, RecordMetadata metadata) {
        try {
            log.info("Event published: eventId={}, topic={}, partition={}, offset={}", 
                event.getEventId(), metadata.topic(), metadata.partition(), metadata.offset());
        } catch (Exception e) {
            log.error("Failed to log event publication", e);
        }
    }
    
    /**
     * Log dead letter event
     */
    public void logDeadLetterEvent(FinancialEvent event, String originalTopic, Throwable error) {
        try {
            log.warn("Event sent to DLQ: eventId={}, originalTopic={}, error={}", 
                event.getEventId(), originalTopic, error.getMessage());
        } catch (Exception e) {
            log.error("Failed to log dead letter event", e);
        }
    }
    
    /**
     * Archive compliance event for regulatory purposes
     */
    public void archiveComplianceEvent(ComplianceEvent event) {
        try {
            Map<String, Object> metadata = Map.of(
                "complianceType", event.getComplianceType(),
                "entityId", event.getEntityId(),
                "timestamp", event.getTimestamp()
            );
            
            SecureAuditLogger.ComplianceEventType complianceEventType = switch (event.getComplianceType().toLowerCase()) {
                case "kyc" -> SecureAuditLogger.ComplianceEventType.KYC_VERIFICATION;
                case "aml" -> SecureAuditLogger.ComplianceEventType.AML_CHECK;
                case "sanctions" -> SecureAuditLogger.ComplianceEventType.SANCTIONS_SCREENING;
                case "risk_assessment", "risk" -> SecureAuditLogger.ComplianceEventType.RISK_ASSESSMENT;
                default -> SecureAuditLogger.ComplianceEventType.REGULATORY_REPORT;
            };
            
            auditLogger.logComplianceEvent(
                complianceEventType,
                event.getEntityId(),
                event.getRegulation(),
                metadata
            );
        } catch (Exception e) {
            log.error("Failed to archive compliance event", e);
        }
    }
}