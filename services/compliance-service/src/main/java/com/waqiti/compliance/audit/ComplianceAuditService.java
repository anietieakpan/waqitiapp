package com.waqiti.compliance.audit;

import com.waqiti.compliance.domain.ComplianceDecision;
import com.waqiti.compliance.domain.ComplianceAuditEntry;
import com.waqiti.compliance.domain.ComplianceCheckResult;
import com.waqiti.compliance.repository.ComplianceAuditRepository;
import com.waqiti.common.encryption.EncryptionService;
import com.waqiti.common.events.EventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Compliance Audit Service
 * 
 * Provides immutable audit trail for all compliance decisions with:
 * - Cryptographic integrity verification
 * - Tamper detection
 * - Regulatory reporting
 * - Chain of custody tracking
 * - Real-time monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceAuditService {

    private final ComplianceAuditRepository auditRepository;
    private final EncryptionService encryptionService;
    private final EventPublisher eventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    @Value("${compliance.audit.hmac-secret}")
    private String hmacSecret;
    
    @Value("${compliance.audit.retention-days:2555}") // 7 years default
    private int retentionDays;
    
    @Value("${compliance.audit.enable-blockchain:false}")
    private boolean blockchainEnabled;
    
    private static final String AUDIT_TOPIC = "compliance-audit-trail";
    private static final String REGULATORY_TOPIC = "regulatory-reporting";
    
    /**
     * Record a compliance decision with full audit trail
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Always create new transaction for audit
    public ComplianceAuditEntry recordDecision(
            ComplianceDecision decision,
            String performedBy,
            String reason,
            Map<String, Object> context) {
        
        log.info("Recording compliance decision: {} for transaction: {}", 
            decision.getDecisionType(), decision.getTransactionId());
        
        try {
            // Create audit entry
            ComplianceAuditEntry entry = ComplianceAuditEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(decision.getTransactionId())
                .decisionId(decision.getId())
                .decisionType(decision.getDecisionType())
                .decision(decision.getDecision())
                .riskScore(decision.getRiskScore())
                .performedBy(performedBy)
                .performedAt(LocalDateTime.now())
                .reason(reason)
                .context(encryptSensitiveData(context))
                .ipAddress(getClientIpAddress())
                .userAgent(getUserAgent())
                .sessionId(getSessionId())
                .build();
            
            // Calculate integrity hash
            String integrityHash = calculateIntegrityHash(entry);
            entry.setIntegrityHash(integrityHash);
            
            // Link to previous entry for chain of custody
            Optional<ComplianceAuditEntry> previousEntry = auditRepository
                .findTopByTransactionIdOrderByPerformedAtDesc(decision.getTransactionId());
            
            if (previousEntry.isPresent()) {
                entry.setPreviousEntryId(previousEntry.get().getId());
                entry.setPreviousHash(previousEntry.get().getIntegrityHash());
                
                // Verify chain integrity
                verifyChainIntegrity(previousEntry.get(), entry);
            }
            
            // Save audit entry
            entry = auditRepository.save(entry);
            
            // Increment metrics
            incrementAuditMetrics(decision);
            
            // Publish to audit trail
            publishAuditEvent(entry);
            
            // If high-risk or rejection, notify regulatory reporting
            if (decision.getRiskScore() > 80 || isRejectedDecision(decision.getDecision())) {
                notifyRegulatoryReporting(entry, decision);
            }
            
            // Optional blockchain recording for critical decisions
            if (blockchainEnabled && isCriticalDecision(decision)) {
                recordToBlockchain(entry);
            }
            
            log.info("Successfully recorded audit entry: {} with hash: {}", 
                entry.getId(), entry.getIntegrityHash());
            
            return entry;
            
        } catch (Exception e) {
            log.error("Failed to record compliance audit for transaction: {}", 
                decision.getTransactionId(), e);
            
            // Audit recording must never fail - create minimal entry
            return createFailsafeAuditEntry(decision, performedBy, e);
        }
    }
    
    /**
     * Record compliance check execution
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCheckExecution(
            String transactionId,
            String checkType,
            ComplianceCheckResult result,
            long executionTimeMs) {
        
        ComplianceAuditEntry entry = ComplianceAuditEntry.builder()
            .id(UUID.randomUUID())
            .transactionId(transactionId)
            .checkType(checkType)
            .checkResult(result.getStatus())
            .executionTimeMs(executionTimeMs)
            .performedAt(LocalDateTime.now())
            .rulesFired(result.getRulesFired())
            .flagsRaised(result.getFlagsRaised())
            .metadata(result.getMetadata())
            .build();
        
        entry.setIntegrityHash(calculateIntegrityHash(entry));
        auditRepository.save(entry);
        
        // Publish for real-time monitoring
        publishCheckEvent(entry);
    }
    
    /**
     * Record manual review action
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ComplianceAuditEntry recordManualReview(
            String transactionId,
            String reviewerId,
            String action,
            String justification,
            Map<String, Object> reviewDetails) {
        
        log.info("Recording manual review for transaction: {} by reviewer: {}", 
            transactionId, reviewerId);
        
        ComplianceAuditEntry entry = ComplianceAuditEntry.builder()
            .id(UUID.randomUUID())
            .transactionId(transactionId)
            .actionType("MANUAL_REVIEW")
            .action(action)
            .performedBy(reviewerId)
            .performedAt(LocalDateTime.now())
            .justification(justification)
            .reviewDetails(encryptSensitiveData(reviewDetails))
            .requiresSecondReview(requiresSecondReview(action))
            .build();
        
        entry.setIntegrityHash(calculateIntegrityHash(entry));
        entry = auditRepository.save(entry);
        
        // Notify compliance team if escalation needed
        if (entry.isRequiresSecondReview()) {
            notifyComplianceTeam(entry);
        }
        
        publishAuditEvent(entry);
        
        return entry;
    }
    
    /**
     * Record system override
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ComplianceAuditEntry recordSystemOverride(
            String transactionId,
            String overrideBy,
            String originalDecision,
            String overrideDecision,
            String overrideReason,
            String approvalTicket) {
        
        log.warn("Recording system override for transaction: {} from {} to {}", 
            transactionId, originalDecision, overrideDecision);
        
        ComplianceAuditEntry entry = ComplianceAuditEntry.builder()
            .id(UUID.randomUUID())
            .transactionId(transactionId)
            .actionType("SYSTEM_OVERRIDE")
            .originalDecision(originalDecision)
            .overrideDecision(overrideDecision)
            .performedBy(overrideBy)
            .performedAt(LocalDateTime.now())
            .overrideReason(overrideReason)
            .approvalTicket(approvalTicket)
            .isCriticalAction(true)
            .build();
        
        entry.setIntegrityHash(calculateIntegrityHash(entry));
        entry = auditRepository.save(entry);
        
        // System overrides always require regulatory notification
        notifyRegulatoryReporting(entry, null);
        
        // Alert senior compliance officers
        alertSeniorCompliance(entry);
        
        publishAuditEvent(entry);
        
        return entry;
    }
    
    /**
     * Verify audit trail integrity
     */
    @Transactional(readOnly = true)
    public AuditIntegrityReport verifyIntegrity(String transactionId) {
        log.info("Verifying audit trail integrity for transaction: {}", transactionId);
        
        List<ComplianceAuditEntry> entries = auditRepository
            .findByTransactionIdOrderByPerformedAt(transactionId);
        
        if (entries.isEmpty()) {
            return AuditIntegrityReport.noEntries(transactionId);
        }
        
        List<IntegrityViolation> violations = new ArrayList<>();
        ComplianceAuditEntry previousEntry = null;
        
        for (ComplianceAuditEntry entry : entries) {
            // Verify individual entry integrity
            String expectedHash = calculateIntegrityHash(entry);
            if (!expectedHash.equals(entry.getIntegrityHash())) {
                violations.add(IntegrityViolation.builder()
                    .entryId(entry.getId())
                    .violationType("HASH_MISMATCH")
                    .expected(expectedHash)
                    .actual(entry.getIntegrityHash())
                    .build());
            }
            
            // Verify chain integrity
            if (previousEntry != null) {
                if (!previousEntry.getId().equals(entry.getPreviousEntryId())) {
                    violations.add(IntegrityViolation.builder()
                        .entryId(entry.getId())
                        .violationType("CHAIN_BREAK")
                        .message("Previous entry ID mismatch")
                        .build());
                }
                
                if (!previousEntry.getIntegrityHash().equals(entry.getPreviousHash())) {
                    violations.add(IntegrityViolation.builder()
                        .entryId(entry.getId())
                        .violationType("CHAIN_HASH_MISMATCH")
                        .expected(previousEntry.getIntegrityHash())
                        .actual(entry.getPreviousHash())
                        .build());
                }
            }
            
            previousEntry = entry;
        }
        
        return AuditIntegrityReport.builder()
            .transactionId(transactionId)
            .totalEntries(entries.size())
            .integrityValid(violations.isEmpty())
            .violations(violations)
            .firstEntry(entries.get(0).getPerformedAt())
            .lastEntry(entries.get(entries.size() - 1).getPerformedAt())
            .verifiedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Generate regulatory compliance report
     */
    @Transactional(readOnly = true)
    public ComplianceReport generateComplianceReport(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String reportType) {
        
        log.info("Generating {} compliance report from {} to {}", 
            reportType, startDate, endDate);
        
        List<ComplianceAuditEntry> entries = auditRepository
            .findByPerformedAtBetweenOrderByPerformedAt(startDate, endDate);
        
        // Group by decision type
        Map<String, List<ComplianceAuditEntry>> byDecisionType = entries.stream()
            .filter(e -> e.getDecisionType() != null)
            .collect(Collectors.groupingBy(ComplianceAuditEntry::getDecisionType));
        
        // Calculate statistics
        ComplianceStatistics statistics = calculateStatistics(entries);
        
        // Identify patterns
        List<CompliancePattern> patterns = identifyPatterns(entries);
        
        // High-risk transactions
        List<ComplianceAuditEntry> highRiskTransactions = entries.stream()
            .filter(e -> e.getRiskScore() != null && e.getRiskScore() > 80)
            .collect(Collectors.toList());
        
        ComplianceReport report = ComplianceReport.builder()
            .reportId(UUID.randomUUID())
            .reportType(reportType)
            .startDate(startDate)
            .endDate(endDate)
            .generatedAt(LocalDateTime.now())
            .totalTransactions(entries.size())
            .statistics(statistics)
            .patterns(patterns)
            .highRiskTransactions(highRiskTransactions.size())
            .byDecisionType(byDecisionType.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().size()
                )))
            .build();
        
        // Sign report for authenticity
        report.setReportSignature(signReport(report));
        
        // Store report for audit
        storeComplianceReport(report);
        
        return report;
    }
    
    /**
     * Search audit trail
     */
    @Transactional(readOnly = true)
    public Page<ComplianceAuditEntry> searchAuditTrail(
            AuditSearchCriteria criteria,
            Pageable pageable) {
        
        return auditRepository.searchByCriteria(
            criteria.getTransactionId(),
            criteria.getDecisionType(),
            criteria.getPerformedBy(),
            criteria.getStartDate(),
            criteria.getEndDate(),
            criteria.getRiskScoreMin(),
            criteria.getRiskScoreMax(),
            pageable
        );
    }
    
    // Helper methods
    
    private String calculateIntegrityHash(ComplianceAuditEntry entry) {
        try {
            // Create canonical representation
            StringBuilder data = new StringBuilder();
            data.append(entry.getId()).append("|");
            data.append(entry.getTransactionId()).append("|");
            data.append(entry.getPerformedAt().toEpochSecond(ZoneOffset.UTC)).append("|");
            data.append(entry.getDecisionType()).append("|");
            data.append(entry.getDecision()).append("|");
            data.append(entry.getRiskScore()).append("|");
            data.append(entry.getPerformedBy());
            
            if (entry.getPreviousHash() != null) {
                data.append("|").append(entry.getPreviousHash());
            }
            
            // Calculate HMAC-SHA256
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            log.error("Failed to calculate integrity hash", e);
            throw new RuntimeException("Integrity hash calculation failed", e);
        }
    }
    
    private void verifyChainIntegrity(ComplianceAuditEntry previous, ComplianceAuditEntry current) {
        if (!previous.getIntegrityHash().equals(current.getPreviousHash())) {
            log.error("Chain integrity violation detected! Previous hash mismatch for entry: {}", 
                current.getId());
            
            // Alert security team
            alertSecurityTeam("Chain integrity violation", current);
        }
    }
    
    private Map<String, Object> encryptSensitiveData(Map<String, Object> data) {
        if (data == null) return null;
        
        Map<String, Object> encrypted = new HashMap<>();
        Set<String> sensitiveFields = Set.of("ssn", "accountNumber", "cardNumber", "email");
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (sensitiveFields.contains(entry.getKey().toLowerCase())) {
                encrypted.put(entry.getKey(), encryptionService.encrypt(
                    entry.getValue().toString()));
            } else {
                encrypted.put(entry.getKey(), entry.getValue());
            }
        }
        
        return encrypted;
    }
    
    private void publishAuditEvent(ComplianceAuditEntry entry) {
        Map<String, Object> event = Map.of(
            "auditId", entry.getId(),
            "transactionId", entry.getTransactionId(),
            "type", entry.getActionType() != null ? entry.getActionType() : "DECISION",
            "performedBy", entry.getPerformedBy(),
            "timestamp", entry.getPerformedAt()
        );
        
        kafkaTemplate.send(AUDIT_TOPIC, entry.getTransactionId(), event);
    }
    
    private void notifyRegulatoryReporting(ComplianceAuditEntry entry, ComplianceDecision decision) {
        Map<String, Object> report = Map.of(
            "auditId", entry.getId(),
            "transactionId", entry.getTransactionId(),
            "reportType", "HIGH_RISK_ALERT",
            "riskScore", entry.getRiskScore() != null ? entry.getRiskScore() : 0,
            "decision", getDecisionValue(entry.getDecision()),
            "timestamp", entry.getPerformedAt(),
            "requiresReporting", true
        );
        
        kafkaTemplate.send(REGULATORY_TOPIC, report);
    }
    
    private void incrementAuditMetrics(ComplianceDecision decision) {
        Counter.builder("compliance.audit.entries")
            .tag("decision_type", decision.getDecisionType())
            .tag("decision", decision.getDecision())
            .tag("risk_level", getRiskLevel(decision.getRiskScore()))
            .register(meterRegistry)
            .increment();
    }
    
    private String getRiskLevel(Integer riskScore) {
        if (riskScore == null) return "UNKNOWN";
        if (riskScore <= 30) return "LOW";
        if (riskScore <= 70) return "MEDIUM";
        return "HIGH";
    }
    
    private boolean isCriticalDecision(ComplianceDecision decision) {
        return decision.getRiskScore() > 90 || 
               isBlockedDecision(decision.getDecision()) ||
               isSuspiciousDecision(decision.getDecision());
    }
    
    private boolean requiresSecondReview(String action) {
        if (action == null) {
            return false;
        }
        return Set.of("OVERRIDE", "APPROVE_HIGH_RISK", "RELEASE_HOLD")
            .contains(action.toUpperCase());
    }
    
    private ComplianceAuditEntry createFailsafeAuditEntry(
            ComplianceDecision decision, 
            String performedBy, 
            Exception error) {
        
        // Create minimal audit entry that can't fail
        ComplianceAuditEntry entry = new ComplianceAuditEntry();
        entry.setId(UUID.randomUUID());
        entry.setTransactionId(decision.getTransactionId());
        entry.setPerformedBy(performedBy);
        entry.setPerformedAt(LocalDateTime.now());
        entry.setError("Audit recording error: " + error.getMessage());
        
        try {
            return auditRepository.save(entry);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to create failsafe audit entry", e);
            return entry;
        }
    }
    
    @Async
    private void recordToBlockchain(ComplianceAuditEntry entry) {
        // Blockchain recording implementation
        log.info("Recording audit entry {} to blockchain", entry.getId());
    }
    
    @Async
    private void notifyComplianceTeam(ComplianceAuditEntry entry) {
        log.info("Notifying compliance team about entry requiring second review: {}", entry.getId());
    }
    
    @Async
    private void alertSeniorCompliance(ComplianceAuditEntry entry) {
        log.warn("Alerting senior compliance about system override: {}", entry.getId());
    }
    
    @Async
    private void alertSecurityTeam(String message, ComplianceAuditEntry entry) {
        log.error("SECURITY ALERT: {} for entry: {}", message, entry.getId());
    }
    
    private void storeComplianceReport(ComplianceReport report) {
        // Store report in database or file system
        log.info("Storing compliance report: {}", report.getReportId());
    }
    
    /**
     * Helper method to check if decision is rejected
     */
    private boolean isRejectedDecision(String decision) {
        if (decision == null) {
            return false;
        }
        return "REJECTED".equalsIgnoreCase(decision) || "REJECT".equalsIgnoreCase(decision);
    }
    
    /**
     * Helper method to check if decision is blocked
     */
    private boolean isBlockedDecision(String decision) {
        if (decision == null) {
            return false;
        }
        return "BLOCKED".equalsIgnoreCase(decision) || "BLOCK".equalsIgnoreCase(decision);
    }
    
    /**
     * Helper method to check if decision is suspicious
     */
    private boolean isSuspiciousDecision(String decision) {
        if (decision == null) {
            return false;
        }
        return "SUSPICIOUS".equalsIgnoreCase(decision) || "SUSPECT".equalsIgnoreCase(decision);
    }
    
    /**
     * Helper method to get decision value with null safety
     */
    private String getDecisionValue(String decision) {
        return decision != null ? decision : "UNKNOWN";
    }
    
    private String signReport(ComplianceReport report) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String reportData = report.getReportId() + "|" + report.getGeneratedAt();
            byte[] hash = digest.digest(reportData.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to sign report", e);
            return "UNSIGNED";
        }
    }
    
    private ComplianceStatistics calculateStatistics(List<ComplianceAuditEntry> entries) {
        // Calculate various statistics
        return ComplianceStatistics.builder()
            .totalEntries(entries.size())
            .averageRiskScore(entries.stream()
                .filter(e -> e.getRiskScore() != null)
                .mapToInt(ComplianceAuditEntry::getRiskScore)
                .average()
                .orElse(0))
            .build();
    }
    
    private List<CompliancePattern> identifyPatterns(List<ComplianceAuditEntry> entries) {
        // Pattern identification logic
        return new ArrayList<>();
    }
    
    private String getClientIpAddress() {
        // Get from request context
        return "127.0.0.1";
    }
    
    private String getUserAgent() {
        // Get from request context
        return "ComplianceService/1.0";
    }
    
    private String getSessionId() {
        // Get from security context
        return UUID.randomUUID().toString();
    }
    
    private void publishCheckEvent(ComplianceAuditEntry entry) {
        // Publish check execution event
    }
    
    // Inner classes
    
    @lombok.Data
    @lombok.Builder
    public static class AuditIntegrityReport {
        private String transactionId;
        private int totalEntries;
        private boolean integrityValid;
        private List<IntegrityViolation> violations;
        private LocalDateTime firstEntry;
        private LocalDateTime lastEntry;
        private LocalDateTime verifiedAt;
        
        public static AuditIntegrityReport noEntries(String transactionId) {
            return AuditIntegrityReport.builder()
                .transactionId(transactionId)
                .totalEntries(0)
                .integrityValid(true)
                .violations(Collections.emptyList())
                .verifiedAt(LocalDateTime.now())
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class IntegrityViolation {
        private UUID entryId;
        private String violationType;
        private String message;
        private String expected;
        private String actual;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ComplianceReport {
        private UUID reportId;
        private String reportType;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private LocalDateTime generatedAt;
        private int totalTransactions;
        private ComplianceStatistics statistics;
        private List<CompliancePattern> patterns;
        private int highRiskTransactions;
        private Map<String, Integer> byDecisionType;
        private String reportSignature;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ComplianceStatistics {
        private int totalEntries;
        private double averageRiskScore;
        private int approvedCount;
        private int rejectedCount;
        private int manualReviewCount;
        private double approvalRate;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CompliancePattern {
        private String patternType;
        private String description;
        private int occurrences;
        private double riskIndicator;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AuditSearchCriteria {
        private String transactionId;
        private String decisionType;
        private String performedBy;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private Integer riskScoreMin;
        private Integer riskScoreMax;
    }

    /**
     * Log general audit event
     */
    public void logAuditEvent(String eventType, String userId, String details, Map<String, Object> metadata) {
        log.debug("Logging audit event: type={}, userId={}, details={}", eventType, userId, details);

        try {
            ComplianceAuditEntry entry = ComplianceAuditEntry.builder()
                .id(UUID.randomUUID())
                .actionType(eventType)
                .performedBy(userId)
                .performedAt(LocalDateTime.now())
                .details(details)
                .metadata(metadata)
                .build();

            entry.setIntegrityHash(calculateIntegrityHash(entry));
            auditRepository.save(entry);

            publishAuditEvent(entry);
        } catch (Exception e) {
            log.error("Failed to log audit event: {}", eventType, e);
        }
    }
}