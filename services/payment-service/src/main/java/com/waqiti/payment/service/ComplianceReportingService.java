package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for compliance reporting and regulatory notifications
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceReportingService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Report suspicious activity
     */
    public void reportSuspiciousActivity(String userId, String activityType, String description, Map<String, Object> metadata) {
        log.warn("Reporting suspicious activity for user: {}, type: {}", userId, activityType);
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "SUSPICIOUS_ACTIVITY");
        report.put("userId", userId);
        report.put("activityType", activityType);
        report.put("description", description);
        report.put("metadata", metadata);
        report.put("reportedAt", Instant.now().toString());
        report.put("severity", "HIGH");
        report.put("requiresReview", true);
        
        kafkaTemplate.send("compliance-reports", report);
        
        log.info("Suspicious activity report filed for user: {}", userId);
    }
    
    /**
     * Report AML violation
     */
    public void reportAMLViolation(String userId, String violationType, String transactionId, Map<String, Object> details) {
        log.error("AML violation detected for user: {}, type: {}", userId, violationType);
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "AML_VIOLATION");
        report.put("userId", userId);
        report.put("violationType", violationType);
        report.put("transactionId", transactionId);
        report.put("details", details);
        report.put("reportedAt", Instant.now().toString());
        report.put("severity", "CRITICAL");
        report.put("requiresImmediateAction", true);
        
        kafkaTemplate.send("compliance-reports", report);
        kafkaTemplate.send("aml-violations", report);
        
        log.info("AML violation report filed for user: {}, transaction: {}", userId, transactionId);
    }
    
    /**
     * Report sanctions hit
     */
    public void reportSanctionsHit(String userId, String sanctionsList, String matchType, double matchScore) {
        log.error("Sanctions hit detected for user: {}, list: {}, score: {}", userId, sanctionsList, matchScore);
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "SANCTIONS_HIT");
        report.put("userId", userId);
        report.put("sanctionsList", sanctionsList);
        report.put("matchType", matchType);
        report.put("matchScore", matchScore);
        report.put("reportedAt", Instant.now().toString());
        report.put("severity", "CRITICAL");
        report.put("requiresImmediateFreeze", true);
        
        kafkaTemplate.send("compliance-reports", report);
        kafkaTemplate.send("sanctions-hits", report);
        
        log.info("Sanctions hit report filed for user: {}", userId);
    }
    
    /**
     * Report large transaction
     */
    public void reportLargeTransaction(String transactionId, String userId, double amount, String currency) {
        log.info("Reporting large transaction: {}, amount: {} {}", transactionId, amount, currency);
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "LARGE_TRANSACTION");
        report.put("transactionId", transactionId);
        report.put("userId", userId);
        report.put("amount", amount);
        report.put("currency", currency);
        report.put("reportedAt", Instant.now().toString());
        report.put("requiresReview", amount > 10000);
        
        kafkaTemplate.send("compliance-reports", report);
        
        if (amount > 10000) {
            // CTR (Currency Transaction Report) for transactions over $10,000
            generateCTR(transactionId, userId, amount, currency);
        }
    }
    
    /**
     * Generate Currency Transaction Report (CTR)
     */
    private void generateCTR(String transactionId, String userId, double amount, String currency) {
        Map<String, Object> ctr = new HashMap<>();
        ctr.put("reportType", "CTR");
        ctr.put("transactionId", transactionId);
        ctr.put("userId", userId);
        ctr.put("amount", amount);
        ctr.put("currency", currency);
        ctr.put("reportDate", LocalDateTime.now().toString());
        ctr.put("filingRequired", true);
        
        kafkaTemplate.send("regulatory-filings", ctr);
        
        log.info("CTR generated for transaction: {}, amount: {} {}", transactionId, amount, currency);
    }
    
    /**
     * Report fraud detection
     */
    public void reportFraudDetection(String userId, String transactionId, String fraudType, double riskScore) {
        log.warn("Fraud detected for user: {}, transaction: {}, type: {}, score: {}", 
            userId, transactionId, fraudType, riskScore);
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "FRAUD_DETECTION");
        report.put("userId", userId);
        report.put("transactionId", transactionId);
        report.put("fraudType", fraudType);
        report.put("riskScore", riskScore);
        report.put("reportedAt", Instant.now().toString());
        report.put("severity", riskScore > 0.8 ? "HIGH" : "MEDIUM");
        report.put("actionTaken", riskScore > 0.8 ? "BLOCKED" : "FLAGGED");
        
        kafkaTemplate.send("compliance-reports", report);
        kafkaTemplate.send("fraud-detections", report);
        
        log.info("Fraud detection report filed for transaction: {}", transactionId);
    }
    
    /**
     * Generate compliance audit trail
     */
    public void createAuditTrail(String eventType, String userId, Map<String, Object> eventData) {
        Map<String, Object> auditEntry = new HashMap<>();
        auditEntry.put("eventType", eventType);
        auditEntry.put("userId", userId);
        auditEntry.put("eventData", eventData);
        auditEntry.put("timestamp", Instant.now().toString());
        auditEntry.put("auditId", java.util.UUID.randomUUID().toString());
        
        kafkaTemplate.send("compliance-audit-trail", auditEntry);
        
        log.debug("Audit trail created for event: {}, user: {}", eventType, userId);
    }
    
    /**
     * Report account freeze for compliance
     */
    public void reportAccountFreeze(com.waqiti.payment.domain.AccountControl control, String correlationId) {
        log.error("Reporting account freeze for compliance - user: {}, reason: {}", control.getUserId(), control.getReason());
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "ACCOUNT_FREEZE");
        report.put("userId", control.getUserId());
        report.put("referenceNumber", control.getReferenceNumber());
        report.put("reason", control.getReason());
        report.put("action", control.getAction().toString());
        report.put("correlationId", correlationId);
        report.put("reportedAt", Instant.now().toString());
        report.put("severity", "CRITICAL");
        report.put("regulatoryFilingRequired", true);
        
        kafkaTemplate.send("compliance-reports", report);
        kafkaTemplate.send("account-freezes", report);
        
        log.info("Account freeze report filed for user: {}", control.getUserId());
    }
    
    /**
     * Report sanctions control action
     */
    public void reportSanctionsControl(com.waqiti.payment.domain.AccountControl control, String correlationId) {
        log.error("Reporting sanctions control action - user: {}, action: {}", control.getUserId(), control.getAction());
        
        Map<String, Object> report = new HashMap<>();
        report.put("reportType", "SANCTIONS_CONTROL");
        report.put("userId", control.getUserId());
        report.put("referenceNumber", control.getReferenceNumber());
        report.put("action", control.getAction().toString());
        report.put("reason", control.getReason());
        report.put("correlationId", correlationId);
        report.put("reportedAt", Instant.now().toString());
        report.put("severity", "CRITICAL");
        report.put("ofacReportingRequired", true);
        report.put("immediateActionTaken", true);
        
        kafkaTemplate.send("compliance-reports", report);
        kafkaTemplate.send("sanctions-controls", report);
        
        log.info("Sanctions control report filed for user: {}", control.getUserId());
    }
}