package com.waqiti.crypto.service;

import com.waqiti.common.notification.DlqNotificationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Regulatory Alert Service
 * Wrapper around DlqNotificationAdapter for regulatory compliance alerts
 * Sends notifications to compliance officers, regulatory teams, and stakeholders
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryAlertService {

    private final DlqNotificationAdapter notificationAdapter;

    /**
     * Send regulatory violation alert
     */
    @Async
    public void sendRegulatoryViolationAlert(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String severity,
            String correlationId) {

        log.warn("Sending regulatory violation alert: transaction={} customer={} violation={} severity={} correlationId={}",
                transactionId, customerId, violationType, severity, correlationId);

        Map<String, Object> alertData = buildRegulatoryAlertPayload(
                transactionId,
                customerId,
                violationType,
                violationDetails,
                severity,
                correlationId,
                "REGULATORY_VIOLATION"
        );

        try {
            notificationAdapter.sendNotification(
                    "REGULATORY_VIOLATION",
                    "Regulatory Violation Detected",
                    formatAlertMessage(violationType, transactionId, severity),
                    alertData,
                    correlationId
            );

            log.warn("Regulatory violation alert sent: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send regulatory violation alert: transaction={} correlationId={}",
                    transactionId, correlationId, e);
        }
    }

    /**
     * Send sanctions screening hit alert (CRITICAL)
     */
    @Async
    public void sendSanctionsHitAlert(
            String transactionId,
            String customerId,
            String walletAddress,
            String sanctionsList,
            String correlationId) {

        log.error("CRITICAL: Sending sanctions hit alert: transaction={} customer={} wallet={} list={} correlationId={}",
                transactionId, customerId, walletAddress, sanctionsList, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertType", "SANCTIONS_HIT");
        alertData.put("severity", "CRITICAL");
        alertData.put("transactionId", transactionId);
        alertData.put("customerId", customerId);
        alertData.put("walletAddress", walletAddress);
        alertData.put("sanctionsList", sanctionsList);
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());
        alertData.put("requiresImmediateAction", true);

        try {
            notificationAdapter.sendNotification(
                    "SANCTIONS_HIT",
                    "CRITICAL: Sanctions Screening Hit",
                    String.format("Sanctions hit detected - Transaction: %s - Wallet: %s - List: %s",
                            transactionId, walletAddress, sanctionsList),
                    alertData,
                    correlationId
            );

            log.error("Sanctions hit alert sent: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send sanctions hit alert: transaction={} correlationId={}",
                    transactionId, correlationId, e);
        }
    }

    /**
     * Send AML threshold breach alert
     */
    @Async
    public void sendAMLThresholdAlert(
            String transactionId,
            String customerId,
            String breachType,
            String thresholdDetails,
            String correlationId) {

        log.warn("Sending AML threshold breach alert: transaction={} customer={} breach={} correlationId={}",
                transactionId, customerId, breachType, correlationId);

        Map<String, Object> alertData = buildRegulatoryAlertPayload(
                transactionId,
                customerId,
                "AML_THRESHOLD_BREACH",
                String.format("%s - %s", breachType, thresholdDetails),
                "HIGH",
                correlationId,
                "AML_THRESHOLD_BREACH"
        );

        try {
            notificationAdapter.sendNotification(
                    "AML_THRESHOLD_BREACH",
                    "AML Threshold Breach Detected",
                    String.format("AML threshold breach - Transaction: %s - Type: %s", transactionId, breachType),
                    alertData,
                    correlationId
            );

            log.warn("AML threshold alert sent: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send AML threshold alert: transaction={} correlationId={}",
                    transactionId, correlationId, e);
        }
    }

    /**
     * Send KYC failure alert
     */
    @Async
    public void sendKYCFailureAlert(
            String transactionId,
            String customerId,
            String failureReason,
            String correlationId) {

        log.warn("Sending KYC failure alert: transaction={} customer={} reason={} correlationId={}",
                transactionId, customerId, failureReason, correlationId);

        Map<String, Object> alertData = buildRegulatoryAlertPayload(
                transactionId,
                customerId,
                "KYC_FAILURE",
                failureReason,
                "MEDIUM",
                correlationId,
                "KYC_FAILURE"
        );

        try {
            notificationAdapter.sendNotification(
                    "KYC_FAILURE",
                    "KYC Verification Failed",
                    String.format("KYC failure - Transaction: %s - Customer: %s - Reason: %s",
                            transactionId, customerId, failureReason),
                    alertData,
                    correlationId
            );

            log.warn("KYC failure alert sent: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send KYC failure alert: transaction={} correlationId={}",
                    transactionId, correlationId, e);
        }
    }

    /**
     * Send manual review required alert
     */
    @Async
    public void sendManualReviewAlert(
            String transactionId,
            String customerId,
            String reviewReason,
            String priority,
            String correlationId) {

        log.info("Sending manual review alert: transaction={} customer={} reason={} priority={} correlationId={}",
                transactionId, customerId, reviewReason, priority, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertType", "MANUAL_REVIEW_REQUIRED");
        alertData.put("severity", "MEDIUM");
        alertData.put("transactionId", transactionId);
        alertData.put("customerId", customerId);
        alertData.put("reviewReason", reviewReason);
        alertData.put("priority", priority);
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());

        try {
            notificationAdapter.sendNotification(
                    "MANUAL_REVIEW_REQUIRED",
                    "Manual Compliance Review Required",
                    String.format("Manual review required - Transaction: %s - Priority: %s - Reason: %s",
                            transactionId, priority, reviewReason),
                    alertData,
                    correlationId
            );

            log.info("Manual review alert sent: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send manual review alert: transaction={} correlationId={}",
                    transactionId, correlationId, e);
        }
    }

    /**
     * Build regulatory alert payload
     */
    private Map<String, Object> buildRegulatoryAlertPayload(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String severity,
            String correlationId,
            String alertType) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("alertType", alertType);
        payload.put("severity", severity);
        payload.put("transactionId", transactionId);
        payload.put("customerId", customerId);
        payload.put("violationType", violationType);
        payload.put("violationDetails", violationDetails);
        payload.put("correlationId", correlationId);
        payload.put("timestamp", Instant.now());
        payload.put("sourceService", "crypto-service");
        payload.put("category", "REGULATORY_COMPLIANCE");

        return payload;
    }

    /**
     * Format alert message for notification
     */
    private String formatAlertMessage(String violationType, String transactionId, String severity) {
        return String.format("[%s] Regulatory violation detected - Type: %s - Transaction: %s",
                severity, violationType, transactionId);
    }
}
