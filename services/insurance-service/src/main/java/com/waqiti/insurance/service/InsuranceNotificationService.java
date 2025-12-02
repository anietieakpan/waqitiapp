package com.waqiti.insurance.service;

import com.waqiti.common.service.DlqNotificationAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Insurance Notification Service
 * Wrapper around DlqNotificationAdapter for insurance-specific notifications
 * Production-ready implementation for claim notifications to policy holders
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceNotificationService {

    private final DlqNotificationAdapter notificationAdapter;
    private final MeterRegistry meterRegistry;

    /**
     * Send claim resolution notification (approved/denied)
     */
    @Async
    public void sendClaimResolutionNotification(String policyHolderId, String claimId,
                                               String resolutionType, BigDecimal approvedAmount,
                                               String correlationId) {
        log.info("Sending claim resolution notification: policyHolderId={} claimId={} resolution={} correlationId={}",
                policyHolderId, claimId, resolutionType, correlationId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("claimId", claimId);
        metadata.put("policyHolderId", policyHolderId);
        metadata.put("resolutionType", resolutionType);
        metadata.put("approvedAmount", approvedAmount != null ? approvedAmount.toString() : "N/A");
        metadata.put("correlationId", correlationId);

        String message;
        if ("APPROVED".equals(resolutionType)) {
            message = String.format(
                    "Your insurance claim %s has been approved. Approved amount: $%s. Payment will be processed within 3-5 business days.",
                    claimId, approvedAmount);
        } else if ("DENIED".equals(resolutionType)) {
            message = String.format(
                    "Your insurance claim %s has been denied. Please contact customer service for details.",
                    claimId);
        } else {
            message = String.format(
                    "Your insurance claim %s has been resolved with status: %s",
                    claimId, resolutionType);
        }

        notificationAdapter.sendNotification(
                policyHolderId,
                "INSURANCE_CLAIM_RESOLUTION",
                message,
                metadata,
                correlationId
        );

        recordMetric("insurance_claim_resolution_notifications_sent_total",
                "resolution_type", resolutionType);

        log.info("Claim resolution notification sent: claimId={} correlationId={}", claimId, correlationId);
    }

    /**
     * Send adjuster review notification
     */
    @Async
    public void sendAdjusterReviewNotification(String policyHolderId, String claimId,
                                              String reviewReason, String correlationId) {
        log.info("Sending adjuster review notification: policyHolderId={} claimId={} correlationId={}",
                policyHolderId, claimId, correlationId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("claimId", claimId);
        metadata.put("policyHolderId", policyHolderId);
        metadata.put("reviewReason", reviewReason);
        metadata.put("correlationId", correlationId);

        String message = String.format(
                "Your insurance claim %s requires additional review by a claims adjuster. Reason: %s. " +
                        "We will contact you within 2-3 business days.",
                claimId, reviewReason);

        notificationAdapter.sendNotification(
                policyHolderId,
                "INSURANCE_CLAIM_ADJUSTER_REVIEW",
                message,
                metadata,
                correlationId
        );

        recordMetric("insurance_adjuster_review_notifications_sent_total");

        log.info("Adjuster review notification sent: claimId={} correlationId={}", claimId, correlationId);
    }

    /**
     * Send claim processing failure notification
     */
    @Async
    public void sendClaimProcessingFailureNotification(String policyHolderId, String claimId,
                                                      String failureReason, String correlationId) {
        log.error("Sending claim processing failure notification: policyHolderId={} claimId={} reason={} correlationId={}",
                policyHolderId, claimId, failureReason, correlationId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("claimId", claimId);
        metadata.put("policyHolderId", policyHolderId);
        metadata.put("failureReason", failureReason);
        metadata.put("severity", "HIGH");
        metadata.put("correlationId", correlationId);

        String message = String.format(
                "We encountered an issue processing your insurance claim %s. Our team has been notified and " +
                        "will resolve this issue. You will be contacted within 24 hours.",
                claimId);

        notificationAdapter.sendNotification(
                policyHolderId,
                "INSURANCE_CLAIM_PROCESSING_FAILURE",
                message,
                metadata,
                correlationId
        );

        recordMetric("insurance_claim_failure_notifications_sent_total");

        log.error("Claim processing failure notification sent: claimId={} correlationId={}",
                claimId, correlationId);
    }

    /**
     * Send fraud investigation notification
     */
    @Async
    public void sendFraudInvestigationNotification(String policyHolderId, String claimId,
                                                  String correlationId) {
        log.info("Sending fraud investigation notification: policyHolderId={} claimId={} correlationId={}",
                policyHolderId, claimId, correlationId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("claimId", claimId);
        metadata.put("policyHolderId", policyHolderId);
        metadata.put("investigationType", "FRAUD");
        metadata.put("correlationId", correlationId);

        String message = String.format(
                "Your insurance claim %s is under investigation. Our fraud prevention team will review " +
                        "your claim and may contact you for additional information.",
                claimId);

        notificationAdapter.sendNotification(
                policyHolderId,
                "INSURANCE_FRAUD_INVESTIGATION",
                message,
                metadata,
                correlationId
        );

        recordMetric("insurance_fraud_investigation_notifications_sent_total");

        log.info("Fraud investigation notification sent: claimId={} correlationId={}", claimId, correlationId);
    }

    /**
     * Send payout confirmation notification
     */
    @Async
    public void sendPayoutConfirmationNotification(String policyHolderId, String claimId,
                                                  BigDecimal payoutAmount, String paymentMethod,
                                                  String correlationId) {
        log.info("Sending payout confirmation: policyHolderId={} claimId={} amount={} correlationId={}",
                policyHolderId, claimId, payoutAmount, correlationId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("claimId", claimId);
        metadata.put("policyHolderId", policyHolderId);
        metadata.put("payoutAmount", payoutAmount.toString());
        metadata.put("paymentMethod", paymentMethod);
        metadata.put("correlationId", correlationId);

        String message = String.format(
                "Payment of $%s for claim %s has been initiated via %s. Funds should arrive within 3-5 business days.",
                payoutAmount, claimId, paymentMethod);

        notificationAdapter.sendNotification(
                policyHolderId,
                "INSURANCE_CLAIM_PAYOUT",
                message,
                metadata,
                correlationId
        );

        recordMetric("insurance_payout_confirmations_sent_total",
                "payment_method", paymentMethod);

        log.info("Payout confirmation sent: claimId={} amount={} correlationId={}",
                claimId, payoutAmount, correlationId);
    }

    /**
     * Send policy voiding notification
     */
    @Async
    public void sendPolicyVoidingNotification(String policyHolderId, String policyId,
                                             String voidReason, String correlationId) {
        log.error("Sending policy voiding notification: policyHolderId={} policyId={} reason={} correlationId={}",
                policyHolderId, policyId, voidReason, correlationId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("policyId", policyId);
        metadata.put("policyHolderId", policyHolderId);
        metadata.put("voidReason", voidReason);
        metadata.put("severity", "CRITICAL");
        metadata.put("correlationId", correlationId);

        String message = String.format(
                "IMPORTANT: Your insurance policy %s is being voided due to: %s. " +
                        "You will receive formal notification by mail. Contact customer service immediately.",
                policyId, voidReason);

        notificationAdapter.sendNotification(
                policyHolderId,
                "INSURANCE_POLICY_VOIDING",
                message,
                metadata,
                correlationId
        );

        recordMetric("insurance_policy_voiding_notifications_sent_total");

        log.error("Policy voiding notification sent: policyId={} correlationId={}", policyId, correlationId);
    }

    /**
     * Send regulatory compliance notification
     */
    @Async
    public void sendRegulatoryComplianceNotification(String policyHolderId, String claimId,
                                                    String complianceIssue, String correlationId) {
        log.warn("Sending regulatory compliance notification: policyHolderId={} claimId={} issue={} correlationId={}",
                policyHolderId, claimId, complianceIssue, correlationId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("claimId", claimId);
        metadata.put("policyHolderId", policyHolderId);
        metadata.put("complianceIssue", complianceIssue);
        metadata.put("correlationId", correlationId);

        String message = String.format(
                "Your insurance claim %s requires additional documentation to comply with regulatory requirements: %s. " +
                        "Please provide the required information within 7 business days.",
                claimId, complianceIssue);

        notificationAdapter.sendNotification(
                policyHolderId,
                "INSURANCE_REGULATORY_COMPLIANCE",
                message,
                metadata,
                correlationId
        );

        recordMetric("insurance_regulatory_compliance_notifications_sent_total");

        log.warn("Regulatory compliance notification sent: claimId={} correlationId={}", claimId, correlationId);
    }

    private void recordMetric(String metricName, String... tags) {
        Counter.Builder builder = Counter.builder(metricName);

        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                builder.tag(tags[i], tags[i + 1]);
            }
        }

        builder.register(meterRegistry).increment();
    }
}
