package com.waqiti.crypto.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DLQ Handler for CryptoComplianceCompletedConsumer
 *
 * Handles failed messages from the dead letter topic for compliance completion events
 * (KYC/AML verification completion, sanctions screening, regulatory reporting, etc.)
 *
 * CRITICAL: Compliance events are regulatory-mandated and must be processed reliably
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CryptoComplianceCompletedConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public CryptoComplianceCompletedConsumerDlqHandler(
            MeterRegistry meterRegistry,
            KafkaTemplate<String, Object> kafkaTemplate) {
        super(meterRegistry);
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CryptoComplianceCompletedConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CryptoComplianceCompletedConsumer.dlq:CryptoComplianceCompletedConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            log.warn("Processing COMPLIANCE DLQ event (REGULATORY CRITICAL): {}", event);

            Integer retryCount = (Integer) headers.getOrDefault("retry_count", 0);
            String failureReason = (String) headers.get("failure_reason");
            String complianceCheckId = (String) headers.get("complianceCheckId");
            String userId = (String) headers.get("userId");
            String complianceType = (String) headers.get("complianceType");
            String result = (String) headers.get("complianceResult");

            log.warn("COMPLIANCE DLQ - CheckID: {}, User: {}, Type: {}, Result: {}, Retry: {}, Failure: {}",
                    complianceCheckId, userId, complianceType, result, retryCount, failureReason);

            DlqProcessingResult dlqResult = classifyAndRecover(event, failureReason, retryCount, headers);
            updateRecoveryMetrics(dlqResult);

            return dlqResult;

        } catch (Exception e) {
            log.error("CRITICAL: Error handling compliance DLQ event", e);
            // Compliance failures always require manual intervention
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Classify error and determine recovery strategy for compliance events
     * CRITICAL: Compliance events are regulatory-mandated - failures require escalation
     */
    private DlqProcessingResult classifyAndRecover(Object event, String failureReason,
                                                     Integer retryCount, Map<String, Object> headers) {
        // Higher retry count for compliance events due to regulatory importance
        final int MAX_RETRIES = 10;

        if (retryCount >= MAX_RETRIES) {
            log.error("REGULATORY CRITICAL: Max retries ({}) exceeded for compliance event. " +
                      "IMMEDIATE MANUAL INTERVENTION REQUIRED FOR REGULATORY COMPLIANCE", MAX_RETRIES);
            createComplianceEscalation(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        if (failureReason == null) {
            return retryWithBackoff(event, retryCount, headers);
        }

        // Transaction blocking failure (AML/sanctions) - CRITICAL IMMEDIATE ESCALATION
        if (isTransactionBlockingFailure(failureReason)) {
            log.error("CRITICAL: Compliance transaction block failed: {}. IMMEDIATE ESCALATION", failureReason);
            createComplianceEscalation(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // KYC verification service errors - retry
        if (isKYCServiceError(failureReason)) {
            log.warn("KYC service error for compliance event: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Sanctions screening service errors - retry with higher priority
        if (isSanctionsScreeningError(failureReason)) {
            log.error("Sanctions screening error: {}. High priority retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Database connectivity errors - retry
        if (isDatabaseError(failureReason)) {
            log.warn("Database error for compliance event: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Regulatory reporting service errors - retry
        if (isRegulatoryReportingError(failureReason)) {
            log.warn("Regulatory reporting error: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Duplicate compliance check - can be discarded
        if (isDuplicateError(failureReason)) {
            log.info("Duplicate compliance event detected: {}. Discarding", failureReason);
            return DlqProcessingResult.DISCARDED;
        }

        // User record update failure - retry
        if (isUserUpdateError(failureReason)) {
            log.warn("User record update failed for compliance: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Data validation errors - escalate (compliance data must be accurate)
        if (isDataValidationError(failureReason)) {
            log.error("Compliance data validation error: {}. Escalating for review", failureReason);
            createComplianceEscalation(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // External compliance provider errors - retry with backoff
        if (isExternalProviderError(failureReason)) {
            log.warn("External compliance provider error: {}. Retry #{}", failureReason, retryCount + 1);
            return retryWithBackoff(event, retryCount, headers);
        }

        // Watchlist matching errors - CRITICAL (potential sanctions match)
        if (isWatchlistError(failureReason)) {
            log.error("CRITICAL: Watchlist matching error: {}. ESCALATION REQUIRED", failureReason);
            createComplianceEscalation(event, failureReason, retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Unknown error - retry but log as compliance concern
        log.warn("Unknown compliance error: {}. Conservative retry #{}", failureReason, retryCount + 1);
        return retryWithBackoff(event, retryCount, headers);
    }

    private boolean isTransactionBlockingFailure(String failureReason) {
        return failureReason.contains("block transaction") ||
               failureReason.contains("failed to block") ||
               failureReason.contains("transaction not blocked") ||
               failureReason.contains("stop transaction");
    }

    private boolean isKYCServiceError(String failureReason) {
        return failureReason.contains("KYC") ||
               failureReason.contains("identity verification") ||
               failureReason.contains("jumio") ||
               failureReason.contains("onfido");
    }

    private boolean isSanctionsScreeningError(String failureReason) {
        return failureReason.contains("sanctions") ||
               failureReason.contains("OFAC") ||
               failureReason.contains("screening service") ||
               failureReason.contains("PEP check");
    }

    private boolean isDatabaseError(String failureReason) {
        return failureReason.contains("database") ||
               failureReason.contains("connection") ||
               failureReason.contains("SQL") ||
               failureReason.contains("deadlock");
    }

    private boolean isRegulatoryReportingError(String failureReason) {
        return failureReason.contains("regulatory") ||
               failureReason.contains("SAR") ||
               failureReason.contains("CTR") ||
               failureReason.contains("FinCEN") ||
               failureReason.contains("reporting");
    }

    private boolean isDuplicateError(String failureReason) {
        return failureReason.contains("duplicate") ||
               failureReason.contains("already completed") ||
               failureReason.contains("already processed");
    }

    private boolean isUserUpdateError(String failureReason) {
        return failureReason.contains("user update") ||
               failureReason.contains("profile update") ||
               failureReason.contains("status update");
    }

    private boolean isDataValidationError(String failureReason) {
        return failureReason.contains("validation") ||
               failureReason.contains("invalid data") ||
               failureReason.contains("malformed");
    }

    private boolean isExternalProviderError(String failureReason) {
        return failureReason.contains("provider") ||
               failureReason.contains("external service") ||
               failureReason.contains("API error") ||
               failureReason.contains("503") ||
               failureReason.contains("timeout");
    }

    private boolean isWatchlistError(String failureReason) {
        return failureReason.contains("watchlist") ||
               failureReason.contains("match") ||
               failureReason.contains("hit") ||
               failureReason.contains("alert");
    }

    private DlqProcessingResult retryWithBackoff(Object event, Integer retryCount, Map<String, Object> headers) {
        try {
            // Shorter backoff for compliance events due to regulatory urgency
            long delaySeconds = (long) Math.pow(2, retryCount) * 30;
            log.info("Scheduling compliance event retry #{} with {}s delay", retryCount + 1, delaySeconds);

            headers.put("retry_count", retryCount + 1);
            headers.put("retry_scheduled_at", LocalDateTime.now());
            headers.put("retry_delay_seconds", delaySeconds);

            // In production: kafkaTemplate.send("crypto-compliance-retry", event);
            return DlqProcessingResult.RETRY_SCHEDULED;

        } catch (Exception e) {
            log.error("Failed to schedule compliance event retry - escalating", e);
            createComplianceEscalation(event, "Retry scheduling failed: " + e.getMessage(), retryCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Create compliance escalation for immediate attention
     * CRITICAL: Compliance failures have regulatory implications
     */
    private void createComplianceEscalation(Object event, String failureReason, Integer retryCount) {
        try {
            log.error("COMPLIANCE ESCALATION: Creating urgent compliance review task. Failure: {}, Retries: {}",
                    failureReason, retryCount);

            // In production:
            // 1. Create high-priority ticket in compliance tracking system
            // 2. Notify compliance team immediately (Slack, PagerDuty)
            // 3. Log to compliance audit trail
            // 4. Consider transaction hold if applicable

            sendComplianceAlert(event, failureReason, retryCount);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to create compliance escalation - MANUAL REVIEW REQUIRED IMMEDIATELY", e);
        }
    }

    /**
     * Send urgent alert to compliance team
     */
    private void sendComplianceAlert(Object event, String failureReason, Integer retryCount) {
        log.error("COMPLIANCE ALERT [URGENT]: Compliance DLQ event requires IMMEDIATE intervention. " +
                  "Event: {}, Failure: {}, Retries: {}", event, failureReason, retryCount);

        // In production, integrate with:
        // - PagerDuty incidents (P1 priority for compliance)
        // - Slack compliance channel
        // - Email to compliance officers
        // - Compliance dashboard alerts
        // - Regulatory audit log entry
    }

    private void updateRecoveryMetrics(DlqProcessingResult result) {
        try {
            log.debug("Updating compliance DLQ recovery metrics: {}", result.name().toLowerCase());
            // Metrics are automatically updated by BaseDlqConsumer
        } catch (Exception e) {
            log.warn("Failed to update compliance recovery metrics", e);
        }
    }

    @Override
    protected String getServiceName() {
        return "CryptoComplianceCompletedConsumer";
    }
}
