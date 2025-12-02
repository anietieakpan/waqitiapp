package com.waqiti.common.audit.service;

import com.waqiti.common.audit.domain.AuditLog;
import com.waqiti.common.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Service for verifying audit log integrity
 *
 * SECURITY: Detects tampering with audit logs by verifying:
 * 1. Hash integrity - each log's hash matches its content
 * 2. Chain integrity - each log correctly links to previous log
 * 3. Sequence integrity - no gaps in sequence numbers
 *
 * COMPLIANCE: Required for SOX 404, PCI DSS 10.5.4, GDPR Art. 32
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditIntegrityVerificationService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Verify integrity of audit log chain
     *
     * @param startSequence Starting sequence number (null = beginning)
     * @param endSequence Ending sequence number (null = latest)
     * @return Verification result with any violations found
     */
    public IntegrityVerificationResult verifyChainIntegrity(Long startSequence, Long endSequence) {
        log.info("Starting audit chain integrity verification: seq {} to {}", startSequence, endSequence);

        IntegrityVerificationResult result = new IntegrityVerificationResult();
        result.setVerificationTimestamp(java.time.LocalDateTime.now());
        result.setStartSequence(startSequence);
        result.setEndSequence(endSequence);

        try {
            // Get audit logs in sequence order
            List<AuditLog> logs;
            if (startSequence != null && endSequence != null) {
                logs = auditLogRepository.findBySequenceNumberBetweenOrderBySequenceNumberAsc(
                    startSequence, endSequence);
            } else {
                logs = auditLogRepository.findAll().stream()
                    .sorted((a, b) -> Long.compare(
                        a.getSequenceNumber() != null ? a.getSequenceNumber() : 0,
                        b.getSequenceNumber() != null ? b.getSequenceNumber() : 0
                    ))
                    .toList();
            }

            result.setTotalRecordsVerified(logs.size());

            if (logs.isEmpty()) {
                log.warn("No audit logs found for verification");
                result.setIntegrityViolationDetected(false);
                return result;
            }

            // Verify each log
            String expectedPreviousHash = "GENESIS_BLOCK";

            for (int i = 0; i < logs.size(); i++) {
                AuditLog auditLogEntry = logs.get(i);

                // Check 1: Verify hash matches content
                String recalculatedHash = calculateHash(auditLogEntry);
                if (!recalculatedHash.equals(auditLogEntry.getHash())) {
                    String violation = String.format(
                        "Hash mismatch at sequence %d: expected %s, got %s",
                        auditLogEntry.getSequenceNumber(),
                        recalculatedHash,
                        auditLogEntry.getHash()
                    );
                    result.addViolation(violation);
                    log.error("SECURITY ALERT: Audit log tampering detected - {}", violation);
                }

                // Check 2: Verify chain links to previous log
                if (!expectedPreviousHash.equals(auditLogEntry.getPreviousHash())) {
                    String violation = String.format(
                        "Chain break at sequence %d: expected previous hash %s, got %s",
                        auditLogEntry.getSequenceNumber(),
                        expectedPreviousHash,
                        auditLogEntry.getPreviousHash()
                    );
                    result.addViolation(violation);
                    log.error("SECURITY ALERT: Audit chain broken - {}", violation);
                }

                // Check 3: Verify sequence is consecutive
                if (i > 0) {
                    AuditLog previousLog = logs.get(i - 1);
                    long expectedSequence = previousLog.getSequenceNumber() + 1;
                    if (auditLogEntry.getSequenceNumber() != expectedSequence) {
                        String violation = String.format(
                            "Sequence gap: expected %d, got %d - possible deleted logs",
                            expectedSequence,
                            auditLogEntry.getSequenceNumber()
                        );
                        result.addViolation(violation);
                        log.error("SECURITY ALERT: Audit sequence gap - {}", violation);
                    }
                }

                expectedPreviousHash = auditLogEntry.getHash();
            }

            result.setIntegrityViolationDetected(!result.getViolations().isEmpty());
            result.setOverallStatus(result.isIntegrityViolationDetected() ?
                "COMPROMISED - TAMPERING DETECTED" : "VERIFIED - INTEGRITY INTACT");

            if (result.isIntegrityViolationDetected()) {
                log.error("CRITICAL: Audit log integrity compromised - {} violations found",
                    result.getViolations().size());
            } else {
                log.info("Audit log integrity verified successfully - {} records checked",
                    result.getTotalRecordsVerified());
            }

        } catch (Exception e) {
            log.error("Error during integrity verification", e);
            result.setOverallStatus("ERROR - Verification failed: " + e.getMessage());
            result.addViolation("Verification error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Calculate hash for an audit log (same algorithm as AuditLoggingAspect)
     */
    private String calculateHash(AuditLog auditLog) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            String canonicalData = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s",
                auditLog.getId() != null ? auditLog.getId().toString() : "",
                auditLog.getSequenceNumber() != null ? auditLog.getSequenceNumber().toString() : "",
                auditLog.getTimestamp() != null ? auditLog.getTimestamp().toEpochMilli() : "",
                auditLog.getUserId() != null ? auditLog.getUserId() : "",
                auditLog.getAction() != null ? auditLog.getAction() : "",
                auditLog.getEntityType() != null ? auditLog.getEntityType() : "",
                auditLog.getEntityId() != null ? auditLog.getEntityId() : "",
                auditLog.getResult() != null ? auditLog.getResult().toString() : "",
                auditLog.getPreviousHash() != null ? auditLog.getPreviousHash() : ""
            );

            byte[] hashBytes = digest.digest(canonicalData.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Result of integrity verification
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class IntegrityVerificationResult {
        private java.time.LocalDateTime verificationTimestamp;
        private Long startSequence;
        private Long endSequence;
        private long totalRecordsVerified;
        private boolean integrityViolationDetected;
        private String overallStatus;

        @lombok.Builder.Default
        private List<String> violations = new ArrayList<>();

        public void addViolation(String violation) {
            this.violations.add(violation);
        }

        public int getViolationCount() {
            return violations.size();
        }
    }
}
