package com.waqiti.compliance.fincen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FinCEN Audit Trail Service
 *
 * CRITICAL REGULATORY REQUIREMENT:
 * Maintains comprehensive audit trail of all FinCEN SAR filing activities
 *
 * AUDIT REQUIREMENTS (BSA/AML):
 * - Record all SAR submissions
 * - Track filing status changes
 * - Log all API interactions
 * - Maintain 7-year retention
 * - Support regulatory audits
 *
 * COMPLIANCE STANDARDS:
 * - BSA/AML Act requirements
 * - FinCEN recordkeeping (31 CFR 1020.320)
 * - SOX 404 controls
 * - GLBA privacy requirements
 *
 * @author Waqiti Compliance Team
 * @version 1.0.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinCenAuditService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String AUDIT_TOPIC = "fincen-audit-trail";
    private static final String ALERT_TOPIC = "compliance-critical-alerts";

    /**
     * Log SAR submission to FinCEN
     *
     * @param sarId Internal SAR ID
     * @param sarNumber FinCEN SAR number
     * @param sarXml SAR XML content
     * @param filingNumber FinCEN filing number (if successful)
     * @param status Filing status
     * @param responseTimeMs API response time
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class,
        timeout = 30
    )
    public void logSarSubmission(
            String sarId,
            String sarNumber,
            String sarXml,
            String filingNumber,
            String status,
            Long responseTimeMs) {

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventId", UUID.randomUUID().toString());
            auditEvent.put("eventType", "FINCEN_SAR_SUBMISSION");
            auditEvent.put("eventTimestamp", Instant.now().toString());

            // SAR details
            auditEvent.put("sarId", sarId);
            auditEvent.put("sarNumber", sarNumber);
            auditEvent.put("filingNumber", filingNumber);
            auditEvent.put("status", status);

            // Performance metrics
            auditEvent.put("responseTimeMs", responseTimeMs);

            // SAR XML (encrypted for security)
            auditEvent.put("sarXmlLength", sarXml != null ? sarXml.length() : 0);
            auditEvent.put("sarXmlHash", sarXml != null ? generateHash(sarXml) : null);
            // Note: Full XML stored in secure document storage, not in audit log

            // Metadata
            auditEvent.put("service", "compliance-service");
            auditEvent.put("operation", "submitSar");
            auditEvent.put("retentionYears", 7);

            // Publish to audit trail topic
            kafkaTemplate.send(AUDIT_TOPIC, sarId, auditEvent);

            log.info("FINCEN AUDIT: SAR submission logged - sarId={}, filingNumber={}, status={}",
                sarId, filingNumber, status);

        } catch (Exception e) {
            log.error("FINCEN AUDIT: Failed to log SAR submission - sarId={}", sarId, e);
            // Don't throw - audit failure shouldn't fail main operation
        }
    }

    /**
     * Log SAR filing failure
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class,
        timeout = 30
    )
    public void logSarFilingFailure(
            String sarId,
            String sarNumber,
            String errorCode,
            String errorMessage,
            String apiResponse) {

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventId", UUID.randomUUID().toString());
            auditEvent.put("eventType", "FINCEN_SAR_FILING_FAILURE");
            auditEvent.put("eventTimestamp", Instant.now().toString());
            auditEvent.put("severity", "CRITICAL");

            // SAR details
            auditEvent.put("sarId", sarId);
            auditEvent.put("sarNumber", sarNumber);

            // Error details
            auditEvent.put("errorCode", errorCode);
            auditEvent.put("errorMessage", errorMessage);
            auditEvent.put("apiResponse", apiResponse);

            // Metadata
            auditEvent.put("service", "compliance-service");
            auditEvent.put("operation", "submitSar");
            auditEvent.put("status", "FAILED");

            // Publish to audit trail
            kafkaTemplate.send(AUDIT_TOPIC, sarId, auditEvent);

            // Also publish to alert topic for immediate attention
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", "FINCEN_SAR_FILING_FAILURE");
            alert.put("severity", "CRITICAL");
            alert.put("sarId", sarId);
            alert.put("errorCode", errorCode);
            alert.put("errorMessage", errorMessage);
            alert.put("timestamp", Instant.now().toString());
            alert.put("action", "Manual SAR filing required via FinCEN BSA E-Filing portal");

            kafkaTemplate.send(ALERT_TOPIC, sarId, alert);

            log.error("FINCEN AUDIT: SAR filing failure logged - sarId={}, error={}", sarId, errorCode);

        } catch (Exception e) {
            log.error("FINCEN AUDIT: Failed to log SAR filing failure - sarId={}", sarId, e);
        }
    }

    /**
     * Log SAR status check
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class,
        timeout = 30
    )
    public void logStatusCheck(
            String filingNumber,
            String status,
            String statusMessage) {

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventId", UUID.randomUUID().toString());
            auditEvent.put("eventType", "FINCEN_STATUS_CHECK");
            auditEvent.put("eventTimestamp", Instant.now().toString());

            auditEvent.put("filingNumber", filingNumber);
            auditEvent.put("status", status);
            auditEvent.put("statusMessage", statusMessage);

            auditEvent.put("service", "compliance-service");
            auditEvent.put("operation", "checkFilingStatus");

            kafkaTemplate.send(AUDIT_TOPIC, filingNumber, auditEvent);

            log.debug("FINCEN AUDIT: Status check logged - filingNumber={}, status={}", filingNumber, status);

        } catch (Exception e) {
            log.error("FINCEN AUDIT: Failed to log status check - filingNumber={}", filingNumber, e);
        }
    }

    /**
     * Log SAR amendment
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class,
        timeout = 30
    )
    public void logSarAmendment(
            String originalFilingNumber,
            String newFilingNumber,
            String sarId,
            String amendmentReason,
            String status) {

        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventId", UUID.randomUUID().toString());
            auditEvent.put("eventType", "FINCEN_SAR_AMENDMENT");
            auditEvent.put("eventTimestamp", Instant.now().toString());

            auditEvent.put("originalFilingNumber", originalFilingNumber);
            auditEvent.put("newFilingNumber", newFilingNumber);
            auditEvent.put("sarId", sarId);
            auditEvent.put("amendmentReason", amendmentReason);
            auditEvent.put("status", status);

            auditEvent.put("service", "compliance-service");
            auditEvent.put("operation", "amendSar");

            kafkaTemplate.send(AUDIT_TOPIC, sarId, auditEvent);

            log.info("FINCEN AUDIT: SAR amendment logged - original={}, new={}, status={}",
                originalFilingNumber, newFilingNumber, status);

        } catch (Exception e) {
            log.error("FINCEN AUDIT: Failed to log SAR amendment - sarId={}", sarId, e);
        }
    }

    /**
     * Log critical FinCEN API error
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class,
        timeout = 30
    )
    public void logCriticalError(
            String operation,
            String errorType,
            String errorMessage,
            Map<String, Object> context) {

        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertId", UUID.randomUUID().toString());
            alert.put("alertType", "FINCEN_CRITICAL_ERROR");
            alert.put("severity", "CRITICAL");
            alert.put("timestamp", Instant.now().toString());

            alert.put("operation", operation);
            alert.put("errorType", errorType);
            alert.put("errorMessage", errorMessage);
            alert.put("context", context);

            alert.put("service", "compliance-service");
            alert.put("action", "Immediate manual intervention required");

            // Send to both audit and alert topics
            kafkaTemplate.send(AUDIT_TOPIC, operation, alert);
            kafkaTemplate.send(ALERT_TOPIC, operation, alert);

            log.error("FINCEN AUDIT: Critical error logged - operation={}, error={}", operation, errorType);

        } catch (Exception e) {
            log.error("FINCEN AUDIT: Failed to log critical error - operation={}", operation, e);
        }
    }

    /**
     * Log configuration issue
     */
    public void logConfigurationError(String configKey, String errorMessage) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", "FINCEN_CONFIGURATION_ERROR");
            alert.put("severity", "CRITICAL");
            alert.put("timestamp", Instant.now().toString());
            alert.put("configKey", configKey);
            alert.put("errorMessage", errorMessage);
            alert.put("action", "Configure FinCEN credentials in Vault/environment variables");

            kafkaTemplate.send(ALERT_TOPIC, "config-error", alert);

            log.error("FINCEN AUDIT: Configuration error logged - key={}, error={}", configKey, errorMessage);

        } catch (Exception e) {
            log.error("FINCEN AUDIT: Failed to log configuration error", e);
        }
    }

    /**
     * Generate SHA-256 hash of SAR XML for audit trail
     */
    private String generateHash(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("FINCEN AUDIT: Failed to generate hash", e);
            return null;
        }
    }
}
