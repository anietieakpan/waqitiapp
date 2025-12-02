package com.waqiti.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Trail Service - Provides comprehensive audit logging and compliance tracking
 * 
 * Handles audit trail management for:
 * - Complete transaction audit logging
 * - Regulatory compliance tracking
 * - User activity monitoring
 * - System event auditing
 * - Data change tracking
 * - Security event logging
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditTrailService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${audit.retention.days:2555}")
    private int auditRetentionDays; // 7 years default

    @Value("${audit.compliance.enabled:true}")
    private boolean complianceAuditEnabled;

    /**
     * Creates comprehensive audit trail entry
     */
    public void createAuditEntry(
            String eventId,
            String eventType,
            String auditCategory,
            String referenceId,
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            String description,
            Map<String, String> metadata,
            LocalDateTime timestamp) {

        if (!auditEnabled) {
            log.debug("Audit trail disabled, skipping audit entry creation");
            return;
        }

        try {
            log.debug("Creating audit trail entry for event: {} - Type: {}", eventId, eventType);

            // Create audit entry
            AuditEntry entry = AuditEntry.builder()
                .auditId(UUID.randomUUID().toString())
                .eventId(eventId)
                .eventType(eventType)
                .auditCategory(auditCategory)
                .referenceId(referenceId)
                .accountNumber(accountNumber)
                .debitAmount(debitAmount)
                .creditAmount(creditAmount)
                .currency(currency)
                .description(description)
                .metadata(metadata)
                .eventTimestamp(timestamp)
                .auditTimestamp(LocalDateTime.now())
                .build();

            // Store audit entry
            storeAuditEntry(entry);

            // Update audit metrics
            updateAuditMetrics(eventType, auditCategory);

            // Check compliance requirements
            if (complianceAuditEnabled) {
                checkComplianceRequirements(entry);
            }

            log.debug("Audit trail entry created: {}", entry.getAuditId());

        } catch (Exception e) {
            log.error("Failed to create audit trail entry for event: {}", eventId, e);
        }
    }

    /**
     * Stores audit entry in persistent storage
     */
    private void storeAuditEntry(AuditEntry entry) {
        try {
            String auditKey = "audit:entry:" + entry.getAuditId();

            // Use HashMap instead of Map.of() for more than 10 entries
            Map<String, String> auditData = new java.util.HashMap<>();
            auditData.put("audit_id", entry.getAuditId());
            auditData.put("event_id", entry.getEventId());
            auditData.put("event_type", entry.getEventType());
            auditData.put("audit_category", entry.getAuditCategory());
            auditData.put("reference_id", entry.getReferenceId() != null ? entry.getReferenceId() : "");
            auditData.put("account_number", entry.getAccountNumber() != null ? entry.getAccountNumber() : "");
            auditData.put("debit_amount", entry.getDebitAmount() != null ? entry.getDebitAmount().toString() : "0");
            auditData.put("credit_amount", entry.getCreditAmount() != null ? entry.getCreditAmount().toString() : "0");
            auditData.put("currency", entry.getCurrency());
            auditData.put("description", entry.getDescription() != null ? entry.getDescription() : "");
            auditData.put("event_timestamp", entry.getEventTimestamp().toString());
            auditData.put("audit_timestamp", entry.getAuditTimestamp().toString());

            redisTemplate.opsForHash().putAll(auditKey, auditData);
            redisTemplate.expire(auditKey, Duration.ofDays(auditRetentionDays));

            // Add to audit index by date
            String indexKey = "audit:index:" + entry.getAuditTimestamp().toLocalDate();
            redisTemplate.opsForList().rightPush(indexKey, entry.getAuditId());
            redisTemplate.expire(indexKey, Duration.ofDays(auditRetentionDays));

            // Add to audit index by event type
            String typeIndexKey = "audit:index:type:" + entry.getEventType() + ":" +
                entry.getAuditTimestamp().toLocalDate();
            redisTemplate.opsForList().rightPush(typeIndexKey, entry.getAuditId());
            redisTemplate.expire(typeIndexKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to store audit entry", e);
        }
    }

    /**
     * Updates audit metrics
     */
    private void updateAuditMetrics(String eventType, String auditCategory) {
        try {
            // Update audit event count
            String countKey = "audit:metrics:count:" + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(90));

            // Update by event type
            String typeKey = "audit:metrics:type:" + eventType + ":" + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(typeKey);
            redisTemplate.expire(typeKey, Duration.ofDays(90));

            // Update by category
            String categoryKey = "audit:metrics:category:" + auditCategory + ":" + 
                LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(categoryKey);
            redisTemplate.expire(categoryKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to update audit metrics", e);
        }
    }

    /**
     * Checks compliance requirements for audit entry
     */
    private void checkComplianceRequirements(AuditEntry entry) {
        try {
            // Check for high-value transactions requiring special compliance
            if (isHighValueTransaction(entry)) {
                flagForComplianceReview(entry, "HIGH_VALUE_TRANSACTION");
            }

            // Check for suspicious patterns
            if (isSuspiciousPattern(entry)) {
                flagForComplianceReview(entry, "SUSPICIOUS_PATTERN");
            }

            // Check for regulatory reporting requirements
            if (requiresRegulatoryReporting(entry)) {
                triggerRegulatoryReporting(entry);
            }

        } catch (Exception e) {
            log.error("Failed to check compliance requirements", e);
        }
    }

    /**
     * Checks if transaction is high-value
     */
    private boolean isHighValueTransaction(AuditEntry entry) {
        BigDecimal threshold = new BigDecimal("10000");
        
        BigDecimal amount = BigDecimal.ZERO;
        if (entry.getDebitAmount() != null) {
            amount = amount.add(entry.getDebitAmount());
        }
        if (entry.getCreditAmount() != null) {
            amount = amount.add(entry.getCreditAmount());
        }
        
        return amount.compareTo(threshold) > 0;
    }

    /**
     * Checks for suspicious patterns
     */
    private boolean isSuspiciousPattern(AuditEntry entry) {
        // Check for rapid successive transactions
        String patternKey = "audit:pattern:" + entry.getAccountNumber();
        Long count = redisTemplate.opsForValue().increment(patternKey);
        redisTemplate.expire(patternKey, Duration.ofMinutes(5));
        
        return count != null && count > 10; // More than 10 transactions in 5 minutes
    }

    /**
     * Checks if regulatory reporting is required
     */
    private boolean requiresRegulatoryReporting(AuditEntry entry) {
        return entry.getEventType().contains("CHARGEBACK") ||
               entry.getEventType().contains("FRAUD") ||
               entry.getEventType().contains("COMPLIANCE") ||
               isHighValueTransaction(entry);
    }

    /**
     * Flags audit entry for compliance review
     */
    private void flagForComplianceReview(AuditEntry entry, String reason) {
        try {
            log.info("Flagging audit entry for compliance review: {} - Reason: {}", 
                entry.getAuditId(), reason);

            String flagKey = "audit:compliance:review:" + LocalDateTime.now().toLocalDate();
            Map<String, String> flagData = Map.of(
                "audit_id", entry.getAuditId(),
                "reason", reason,
                "flagged_at", LocalDateTime.now().toString()
            );

            redisTemplate.opsForList().rightPush(flagKey, flagData);
            redisTemplate.expire(flagKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to flag for compliance review", e);
        }
    }

    /**
     * Triggers regulatory reporting for audit entry
     */
    private void triggerRegulatoryReporting(AuditEntry entry) {
        try {
            log.info("Triggering regulatory reporting for audit entry: {}", entry.getAuditId());

            String reportingKey = "audit:regulatory:reporting:" + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForList().rightPush(reportingKey, entry.getAuditId());
            redisTemplate.expire(reportingKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to trigger regulatory reporting", e);
        }
    }

    /**
     * Audit entry data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class AuditEntry {
        private String auditId;
        private String eventId;
        private String eventType;
        private String auditCategory;
        private String referenceId;
        private String accountNumber;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private String currency;
        private String description;
        private Map<String, String> metadata;
        private LocalDateTime eventTimestamp;
        private LocalDateTime auditTimestamp;
    }
}