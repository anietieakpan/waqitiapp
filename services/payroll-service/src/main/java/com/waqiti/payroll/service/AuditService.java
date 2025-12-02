package com.waqiti.payroll.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Audit Service for Payroll Processing
 * Comprehensive audit trail logging for compliance and security
 * Supports SOX, GDPR, and financial audit requirements
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String AUDIT_TOPIC = "audit-events";

    /**
     * Log payroll batch initiated event
     */
    public void logPayrollBatchInitiated(String companyId, String batchId, String correlationId,
                                        int employeeCount, BigDecimal totalAmount) {
        log.info("Audit: Payroll batch initiated - Company: {}, Batch: {}, Employees: {}, Amount: ${}",
                 companyId, batchId, employeeCount, totalAmount);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.PAYROLL_BATCH_INITIATED)
            .entityType(AuditEntityType.PAYROLL_BATCH)
            .entityId(batchId)
            .companyId(companyId)
            .correlationId(correlationId)
            .action("INITIATED")
            .status("STARTED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "employee_count", employeeCount,
                "total_amount", totalAmount.toString()
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log payroll calculation completed
     */
    public void logPayrollCalculationCompleted(String companyId, String batchId, String correlationId,
                                               int employeeCount, BigDecimal grossAmount, BigDecimal netAmount) {
        log.info("Audit: Payroll calculation completed - Company: {}, Batch: {}, Gross: ${}, Net: ${}",
                 companyId, batchId, grossAmount, netAmount);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.PAYROLL_CALCULATION_COMPLETED)
            .entityType(AuditEntityType.PAYROLL_BATCH)
            .entityId(batchId)
            .companyId(companyId)
            .correlationId(correlationId)
            .action("CALCULATED")
            .status("COMPLETED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "employee_count", employeeCount,
                "gross_amount", grossAmount.toString(),
                "net_amount", netAmount.toString()
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log tax calculation event
     */
    public void logTaxCalculation(String companyId, String batchId, String correlationId,
                                  BigDecimal totalTaxWithheld, BigDecimal federalTax, BigDecimal stateTax) {
        log.info("Audit: Tax calculation - Company: {}, Batch: {}, Total Tax: ${}",
                 companyId, batchId, totalTaxWithheld);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.TAX_CALCULATION)
            .entityType(AuditEntityType.TAX_WITHHOLDING)
            .entityId(batchId)
            .companyId(companyId)
            .correlationId(correlationId)
            .action("TAX_CALCULATED")
            .status("COMPLETED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "total_tax_withheld", totalTaxWithheld.toString(),
                "federal_tax", federalTax.toString(),
                "state_tax", stateTax.toString()
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log compliance check event
     */
    public void logComplianceCheck(String companyId, String batchId, String correlationId,
                                   boolean compliant, int violationCount) {
        log.info("Audit: Compliance check - Company: {}, Batch: {}, Compliant: {}, Violations: {}",
                 companyId, batchId, compliant, violationCount);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.COMPLIANCE_CHECK)
            .entityType(AuditEntityType.COMPLIANCE)
            .entityId(batchId)
            .companyId(companyId)
            .correlationId(correlationId)
            .action("COMPLIANCE_CHECKED")
            .status(compliant ? "PASSED" : "FAILED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "compliant", compliant,
                "violation_count", violationCount
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log compliance violation
     */
    public void logComplianceViolation(String companyId, String batchId, String employeeId,
                                       String violationType, String regulation, String details) {
        log.warn("Audit: Compliance violation - Company: {}, Employee: {}, Type: {}, Regulation: {}",
                 companyId, employeeId, violationType, regulation);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.COMPLIANCE_VIOLATION)
            .entityType(AuditEntityType.COMPLIANCE)
            .entityId(employeeId)
            .companyId(companyId)
            .action("VIOLATION_DETECTED")
            .status("WARNING")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "batch_id", batchId,
                "violation_type", violationType,
                "regulation", regulation,
                "details", details
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log fund reservation
     */
    public void logFundReservation(String companyId, String reservationId, BigDecimal amount, String correlationId) {
        log.info("Audit: Fund reservation - Company: {}, Reservation: {}, Amount: ${}",
                 companyId, reservationId, amount);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.FUND_RESERVATION)
            .entityType(AuditEntityType.FUNDS)
            .entityId(reservationId)
            .companyId(companyId)
            .correlationId(correlationId)
            .action("FUNDS_RESERVED")
            .status("RESERVED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "reservation_id", reservationId,
                "amount", amount.toString()
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log bank transfer initiated
     */
    public void logBankTransferInitiated(String companyId, String batchId, String transferId,
                                        String employeeId, BigDecimal amount, String accountLast4) {
        log.info("Audit: Bank transfer initiated - Employee: {}, Transfer: {}, Amount: ${}",
                 employeeId, transferId, amount);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.BANK_TRANSFER_INITIATED)
            .entityType(AuditEntityType.BANK_TRANSFER)
            .entityId(transferId)
            .companyId(companyId)
            .action("TRANSFER_INITIATED")
            .status("PENDING")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "batch_id", batchId,
                "employee_id", employeeId,
                "amount", amount.toString(),
                "account_last4", accountLast4
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log bank transfer completed
     */
    public void logBankTransferCompleted(String companyId, String transferId, String transactionId,
                                        String employeeId, BigDecimal amount, boolean success) {
        log.info("Audit: Bank transfer {} - Employee: {}, Transfer: {}, Amount: ${}",
                 success ? "completed" : "failed", employeeId, transferId, amount);

        AuditEvent event = AuditEvent.builder()
            .eventType(success ? AuditEventType.BANK_TRANSFER_COMPLETED : AuditEventType.BANK_TRANSFER_FAILED)
            .entityType(AuditEntityType.BANK_TRANSFER)
            .entityId(transferId)
            .companyId(companyId)
            .action(success ? "TRANSFER_COMPLETED" : "TRANSFER_FAILED")
            .status(success ? "SUCCESS" : "FAILED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "transaction_id", transactionId,
                "employee_id", employeeId,
                "amount", amount.toString(),
                "success", success
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log payroll batch completed
     */
    public void logPayrollBatchCompleted(String companyId, String batchId, String correlationId,
                                        int successCount, int failureCount, BigDecimal totalAmount) {
        log.info("Audit: Payroll batch completed - Company: {}, Batch: {}, Success: {}, Failed: {}, Total: ${}",
                 companyId, batchId, successCount, failureCount, totalAmount);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.PAYROLL_BATCH_COMPLETED)
            .entityType(AuditEntityType.PAYROLL_BATCH)
            .entityId(batchId)
            .companyId(companyId)
            .correlationId(correlationId)
            .action("BATCH_COMPLETED")
            .status(failureCount > 0 ? "PARTIAL_SUCCESS" : "SUCCESS")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "success_count", successCount,
                "failure_count", failureCount,
                "total_amount", totalAmount.toString()
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log payroll batch failed
     */
    public void logPayrollBatchFailed(String companyId, String batchId, String correlationId,
                                     String reason, String errorMessage) {
        log.error("Audit: Payroll batch failed - Company: {}, Batch: {}, Reason: {}",
                  companyId, batchId, reason);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.PAYROLL_BATCH_FAILED)
            .entityType(AuditEntityType.PAYROLL_BATCH)
            .entityId(batchId)
            .companyId(companyId)
            .correlationId(correlationId)
            .action("BATCH_FAILED")
            .status("FAILED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "reason", reason,
                "error_message", errorMessage
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log employee payment event
     */
    public void logEmployeePayment(String companyId, String batchId, String employeeId,
                                   BigDecimal grossAmount, BigDecimal netAmount, BigDecimal taxWithheld) {
        log.info("Audit: Employee payment - Employee: {}, Gross: ${}, Net: ${}, Tax: ${}",
                 employeeId, grossAmount, netAmount, taxWithheld);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.EMPLOYEE_PAYMENT)
            .entityType(AuditEntityType.PAYMENT)
            .entityId(employeeId)
            .companyId(companyId)
            .action("PAYMENT_PROCESSED")
            .status("COMPLETED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "batch_id", batchId,
                "gross_amount", grossAmount.toString(),
                "net_amount", netAmount.toString(),
                "tax_withheld", taxWithheld.toString()
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log data access event (GDPR compliance)
     */
    public void logDataAccess(String userId, String action, String entityType, String entityId,
                             String companyId, String purpose) {
        log.info("Audit: Data access - User: {}, Action: {}, Entity: {}:{}, Purpose: {}",
                 userId, action, entityType, entityId, purpose);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.DATA_ACCESS)
            .entityType(AuditEntityType.valueOf(entityType))
            .entityId(entityId)
            .companyId(companyId)
            .userId(userId)
            .action(action)
            .status("ACCESSED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "purpose", purpose
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log sensitive data access (SSN, bank account, salary)
     */
    public void logSensitiveDataAccess(String userId, String dataType, String employeeId,
                                      String companyId, String reason) {
        log.warn("Audit: Sensitive data access - User: {}, DataType: {}, Employee: {}, Reason: {}",
                 userId, dataType, employeeId, reason);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.SENSITIVE_DATA_ACCESS)
            .entityType(AuditEntityType.EMPLOYEE)
            .entityId(employeeId)
            .companyId(companyId)
            .userId(userId)
            .action("SENSITIVE_ACCESS")
            .status("ACCESSED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "data_type", dataType,
                "reason", reason
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log configuration change
     */
    public void logConfigurationChange(String userId, String configKey, String oldValue,
                                       String newValue, String companyId) {
        log.info("Audit: Configuration change - User: {}, Key: {}, Old: {}, New: {}",
                 userId, configKey, oldValue, newValue);

        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.CONFIGURATION_CHANGE)
            .entityType(AuditEntityType.CONFIGURATION)
            .entityId(configKey)
            .companyId(companyId)
            .userId(userId)
            .action("CONFIG_CHANGED")
            .status("UPDATED")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "config_key", configKey,
                "old_value", oldValue,
                "new_value", newValue
            ))
            .build();

        publishAuditEvent(event);
    }

    /**
     * Log generic event
     */
    public void logEvent(AuditEventType eventType, String companyId, String entityId,
                        String action, String correlationId) {
        log.info("Audit: {} - Company: {}, Entity: {}, Action: {}",
                 eventType, companyId, entityId, action);

        AuditEvent event = AuditEvent.builder()
            .eventType(eventType)
            .entityType(AuditEntityType.GENERIC)
            .entityId(entityId)
            .companyId(companyId)
            .correlationId(correlationId)
            .action(action)
            .status("COMPLETED")
            .timestamp(LocalDateTime.now())
            .metadata(new HashMap<>())
            .build();

        publishAuditEvent(event);
    }

    /**
     * Publish audit event to Kafka
     */
    private void publishAuditEvent(AuditEvent event) {
        try {
            kafkaTemplate.send(AUDIT_TOPIC, event.getCompanyId(), event);
            log.debug("Audit event published - Type: {}, Entity: {}, Action: {}",
                      event.getEventType(), event.getEntityId(), event.getAction());
        } catch (Exception e) {
            // CRITICAL: Audit events MUST be logged even if Kafka fails
            log.error("CRITICAL: Failed to publish audit event to Kafka, logging to file: {}",
                      event, e);
            // Fallback to file logging for audit trail integrity
            logAuditEventToFile(event);
        }
    }

    /**
     * Fallback logging to file (ensures audit trail even if Kafka is down)
     */
    private void logAuditEventToFile(AuditEvent event) {
        log.warn("AUDIT_FALLBACK: {} | Company: {} | Entity: {} | Action: {} | Status: {} | Timestamp: {} | Metadata: {}",
                 event.getEventType(), event.getCompanyId(), event.getEntityId(),
                 event.getAction(), event.getStatus(), event.getTimestamp(), event.getMetadata());
    }

    // ============= DTOs =============

    @lombok.Builder
    @lombok.Data
    public static class AuditEvent {
        private AuditEventType eventType;
        private AuditEntityType entityType;
        private String entityId;
        private String companyId;
        private String userId;
        private String correlationId;
        private String action;
        private String status;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }

    public enum AuditEventType {
        PAYROLL_BATCH_INITIATED,
        PAYROLL_CALCULATION_COMPLETED,
        TAX_CALCULATION,
        COMPLIANCE_CHECK,
        COMPLIANCE_VIOLATION,
        FUND_RESERVATION,
        BANK_TRANSFER_INITIATED,
        BANK_TRANSFER_COMPLETED,
        BANK_TRANSFER_FAILED,
        PAYROLL_BATCH_COMPLETED,
        PAYROLL_BATCH_FAILED,
        EMPLOYEE_PAYMENT,
        DATA_ACCESS,
        SENSITIVE_DATA_ACCESS,
        CONFIGURATION_CHANGE,
        GENERIC
    }

    public enum AuditEntityType {
        PAYROLL_BATCH,
        PAYMENT,
        TAX_WITHHOLDING,
        COMPLIANCE,
        FUNDS,
        BANK_TRANSFER,
        EMPLOYEE,
        CONFIGURATION,
        GENERIC
    }
}
