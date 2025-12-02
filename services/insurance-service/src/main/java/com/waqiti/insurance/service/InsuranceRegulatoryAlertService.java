package com.waqiti.insurance.service;

import com.waqiti.common.service.DlqEscalationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Insurance Regulatory Alert Service
 * Handles all alerts for insurance operations, regulatory compliance, and fraud
 * Production-ready implementation with escalation and fallback mechanisms
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceRegulatoryAlertService {

    private final DlqEscalationService escalationService;
    private final MeterRegistry meterRegistry;

    private static final String INSURANCE_OPERATIONS_ALERT_CHANNEL = "insurance-operations";
    private static final String INSURANCE_REGULATORY_ALERT_CHANNEL = "insurance-regulatory";
    private static final String INSURANCE_FRAUD_ALERT_CHANNEL = "insurance-fraud";

    /**
     * Send critical regulatory alert
     */
    @Async
    public void sendRegulatoryAlert(String violationType, String claimId, String policyId,
                                   String correlationId) {
        log.error("Sending regulatory alert: claimId={} policyId={} violation={} correlationId={}",
                claimId, policyId, violationType, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("claimId", claimId);
        alertData.put("policyId", policyId);
        alertData.put("violationType", violationType);
        alertData.put("action", "Claim processing halted - regulatory review required");
        alertData.put("regulatoryImpact", "CRITICAL");
        alertData.put("potentialFine", "Varies by state - up to $50,000 per violation");

        try {
            escalationService.escalate(
                    "CRITICAL",
                    "INSURANCE_REGULATORY_VIOLATION",
                    INSURANCE_REGULATORY_ALERT_CHANNEL,
                    String.format("CRITICAL: Insurance regulatory violation - Claim: %s - Type: %s",
                            claimId, violationType),
                    alertData,
                    correlationId
            );

            recordMetric("insurance_regulatory_alerts_sent_total",
                    "violation_type", violationType);

            log.error("Regulatory alert sent: claimId={} correlationId={}", claimId, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send regulatory alert: claimId={} correlationId={}",
                    claimId, correlationId, e);
            // Fallback escalation
            sendFallbackRegulatoryAlert(claimId, policyId, violationType, correlationId);
        }
    }

    /**
     * Send fraud alert
     */
    @Async
    public void sendFraudAlert(String claimId, String policyId, String policyHolderId,
                              List<String> fraudIndicators, String correlationId) {
        log.error("Sending fraud alert: claimId={} policyId={} indicators={} correlationId={}",
                claimId, policyId, fraudIndicators, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("claimId", claimId);
        alertData.put("policyId", policyId);
        alertData.put("policyHolderId", policyHolderId);
        alertData.put("fraudIndicators", String.join(", ", fraudIndicators));
        alertData.put("action", "Claim denied - SIU investigation initiated");
        alertData.put("fraudScore", calculateFraudSeverity(fraudIndicators));

        try {
            escalationService.escalate(
                    "HIGH",
                    "INSURANCE_FRAUD_DETECTED",
                    INSURANCE_FRAUD_ALERT_CHANNEL,
                    String.format("Fraud detected - Claim: %s - Indicators: %s",
                            claimId, String.join(", ", fraudIndicators)),
                    alertData,
                    correlationId
            );

            recordMetric("insurance_fraud_alerts_sent_total",
                    "indicator_count", String.valueOf(fraudIndicators.size()));

            log.error("Fraud alert sent: claimId={} correlationId={}", claimId, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send fraud alert: claimId={} correlationId={}",
                    claimId, correlationId, e);
        }
    }

    /**
     * Escalate to insurance operations manager
     */
    @Async
    public void escalateToOperationsManager(String claimId, String policyId, String claimType,
                                           String escalationReason, String eventId,
                                           String correlationId) {
        log.error("Escalating to operations manager: claimId={} policyId={} reason={} correlationId={}",
                claimId, policyId, escalationReason, correlationId);

        Map<String, Object> escalationData = new HashMap<>();
        escalationData.put("claimId", claimId);
        escalationData.put("policyId", policyId);
        escalationData.put("claimType", claimType);
        escalationData.put("escalationReason", escalationReason);
        escalationData.put("eventId", eventId);
        escalationData.put("action", "Immediate manager intervention required");
        escalationData.put("customerImpact", "HIGH");
        escalationData.put("slaRisk", "YES");

        try {
            escalationService.escalate(
                    "HIGH",
                    "INSURANCE_OPERATIONS_ESCALATION",
                    INSURANCE_OPERATIONS_ALERT_CHANNEL,
                    String.format("Claim processing failure - Manager intervention required - Claim: %s - Reason: %s",
                            claimId, escalationReason),
                    escalationData,
                    correlationId
            );

            recordMetric("insurance_operations_escalations_total");

            log.error("Operations escalation sent: claimId={} correlationId={}", claimId, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to escalate to operations: claimId={} correlationId={}",
                    claimId, correlationId, e);
        }
    }

    /**
     * Send claim deadline risk alert
     */
    @Async
    public void alertClaimDeadlineRisk(String claimId, Instant deadline, int daysRemaining,
                                      String correlationId) {
        log.warn("Alerting claim deadline risk: claimId={} deadline={} daysRemaining={} correlationId={}",
                claimId, deadline, daysRemaining, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("claimId", claimId);
        alertData.put("deadline", deadline.toString());
        alertData.put("daysRemaining", daysRemaining);
        alertData.put("action", daysRemaining <= 1
                ? "IMMEDIATE ACTION REQUIRED - Deadline within 24 hours"
                : "Expedite claim processing to meet deadline");
        alertData.put("slaRisk", daysRemaining <= 2 ? "CRITICAL" : "HIGH");

        try {
            String priority = daysRemaining <= 1 ? "CRITICAL" : "HIGH";

            escalationService.escalate(
                    priority,
                    "INSURANCE_CLAIM_DEADLINE_RISK",
                    INSURANCE_OPERATIONS_ALERT_CHANNEL,
                    String.format("Claim approaching deadline - Claim: %s - Days remaining: %d",
                            claimId, daysRemaining),
                    alertData,
                    correlationId
            );

            recordMetric("insurance_deadline_risk_alerts_sent_total",
                    "days_remaining", daysRemaining <= 1 ? "0-1" : "2-3");

            log.warn("Deadline risk alert sent: claimId={} daysRemaining={} correlationId={}",
                    claimId, daysRemaining, correlationId);

        } catch (Exception e) {
            log.error("Failed to send deadline risk alert: claimId={} correlationId={}",
                    claimId, correlationId, e);
        }
    }

    /**
     * Send adjuster backlog alert
     */
    @Async
    public void alertAdjusterBacklog(int queueSize, int overdueCount, String correlationId) {
        log.warn("Alerting adjuster backlog: queueSize={} overdueCount={} correlationId={}",
                queueSize, overdueCount, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("queueSize", queueSize);
        alertData.put("overdueCount", overdueCount);
        alertData.put("action", overdueCount > 0
                ? "Immediate adjuster resource allocation required"
                : "Monitor adjuster queue - consider resource reallocation");
        alertData.put("slaRisk", overdueCount > 5 ? "CRITICAL" : "MEDIUM");

        try {
            String priority = overdueCount > 5 ? "CRITICAL" : "MEDIUM";

            escalationService.escalate(
                    priority,
                    "INSURANCE_ADJUSTER_BACKLOG",
                    INSURANCE_OPERATIONS_ALERT_CHANNEL,
                    String.format("Adjuster review backlog - Queue: %d - Overdue: %d",
                            queueSize, overdueCount),
                    alertData,
                    correlationId
            );

            recordMetric("insurance_adjuster_backlog_alerts_sent_total");

            log.warn("Adjuster backlog alert sent: queueSize={} overdueCount={} correlationId={}",
                    queueSize, overdueCount, correlationId);

        } catch (Exception e) {
            log.error("Failed to send adjuster backlog alert: correlationId={}", correlationId, e);
        }
    }

    /**
     * Send high-value claim alert
     */
    @Async
    public void alertHighValueClaim(String claimId, String policyId, String claimAmount,
                                   String correlationId) {
        log.info("Alerting high-value claim: claimId={} policyId={} amount={} correlationId={}",
                claimId, policyId, claimAmount, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("claimId", claimId);
        alertData.put("policyId", policyId);
        alertData.put("claimAmount", claimAmount);
        alertData.put("action", "Senior adjuster review and executive notification required");
        alertData.put("requiresExecutiveApproval", true);

        try {
            escalationService.escalate(
                    "MEDIUM",
                    "INSURANCE_HIGH_VALUE_CLAIM",
                    INSURANCE_OPERATIONS_ALERT_CHANNEL,
                    String.format("High-value claim submitted - Claim: %s - Amount: %s",
                            claimId, claimAmount),
                    alertData,
                    correlationId
            );

            recordMetric("insurance_high_value_claim_alerts_sent_total");

            log.info("High-value claim alert sent: claimId={} amount={} correlationId={}",
                    claimId, claimAmount, correlationId);

        } catch (Exception e) {
            log.error("Failed to send high-value claim alert: claimId={} correlationId={}",
                    claimId, correlationId, e);
        }
    }

    /**
     * Send SIU (Special Investigations Unit) alert
     */
    @Async
    public void alertSIU(String claimId, String policyId, String investigationType,
                        String correlationId) {
        log.error("Alerting SIU: claimId={} policyId={} type={} correlationId={}",
                claimId, policyId, investigationType, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("claimId", claimId);
        alertData.put("policyId", policyId);
        alertData.put("investigationType", investigationType);
        alertData.put("action", "SIU investigation initiated");
        alertData.put("priority", "HIGH");

        try {
            escalationService.escalate(
                    "HIGH",
                    "INSURANCE_SIU_INVESTIGATION",
                    INSURANCE_FRAUD_ALERT_CHANNEL,
                    String.format("SIU investigation required - Claim: %s - Type: %s",
                            claimId, investigationType),
                    alertData,
                    correlationId
            );

            recordMetric("insurance_siu_alerts_sent_total",
                    "investigation_type", investigationType);

            log.error("SIU alert sent: claimId={} type={} correlationId={}",
                    claimId, investigationType, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send SIU alert: claimId={} correlationId={}",
                    claimId, correlationId, e);
        }
    }

    /**
     * Send catastrophic event alert
     */
    @Async
    public void alertCatastrophicEvent(String catastropheType, String affectedArea,
                                      int affectedClaims, String correlationId) {
        log.error("Alerting catastrophic event: type={} area={} affectedClaims={} correlationId={}",
                catastropheType, affectedArea, affectedClaims, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("catastropheType", catastropheType);
        alertData.put("affectedArea", affectedArea);
        alertData.put("affectedClaims", affectedClaims);
        alertData.put("action", "Catastrophe team activation - expedited processing protocols");
        alertData.put("financialImpact", "CRITICAL");
        alertData.put("requiresExecutiveAction", true);

        try {
            escalationService.escalate(
                    "CRITICAL",
                    "INSURANCE_CATASTROPHIC_EVENT",
                    INSURANCE_OPERATIONS_ALERT_CHANNEL,
                    String.format("CATASTROPHIC EVENT: %s in %s - Affected claims: %d",
                            catastropheType, affectedArea, affectedClaims),
                    alertData,
                    correlationId
            );

            recordMetric("insurance_catastrophic_event_alerts_sent_total",
                    "catastrophe_type", catastropheType);

            log.error("Catastrophic event alert sent: type={} area={} correlationId={}",
                    catastropheType, affectedArea, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send catastrophic event alert: correlationId={}",
                    correlationId, e);
        }
    }

    /**
     * Calculate fraud severity from indicators
     */
    private String calculateFraudSeverity(List<String> fraudIndicators) {
        int count = fraudIndicators.size();
        if (count >= 4) {
            return "SEVERE";
        } else if (count >= 2) {
            return "HIGH";
        } else {
            return "MODERATE";
        }
    }

    /**
     * Fallback regulatory alert (if primary escalation fails)
     */
    private void sendFallbackRegulatoryAlert(String claimId, String policyId, String violationType,
                                            String correlationId) {
        log.error("FALLBACK: Sending regulatory alert via fallback channel: claimId={} correlationId={}",
                claimId, correlationId);

        try {
            Map<String, Object> fallbackData = new HashMap<>();
            fallbackData.put("claimId", claimId);
            fallbackData.put("policyId", policyId);
            fallbackData.put("violationType", violationType);
            fallbackData.put("alertType", "FALLBACK");
            fallbackData.put("originalEscalationFailed", true);

            escalationService.escalate(
                    "CRITICAL",
                    "INSURANCE_REGULATORY_VIOLATION_FALLBACK",
                    "fallback-alerts",
                    String.format("FALLBACK ALERT: Regulatory violation - Claim: %s",
                            claimId),
                    fallbackData,
                    correlationId
            );

            recordMetric("insurance_fallback_alerts_sent_total");

        } catch (Exception e) {
            log.error("CRITICAL: Fallback regulatory alert also failed: claimId={} correlationId={}",
                    claimId, correlationId, e);
        }
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
