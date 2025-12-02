package com.waqiti.crypto.service;

import com.waqiti.common.model.incident.Incident;
import com.waqiti.common.service.DlqEscalationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Chief Compliance Officer Escalation Service
 * Wrapper around DlqEscalationService for regulatory compliance escalations
 * Handles critical regulatory violations requiring CCO/executive attention
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChiefComplianceOfficerEscalationService {

    private final DlqEscalationService dlqEscalationService;

    /**
     * Escalate critical regulatory violation to Chief Compliance Officer (P0 escalation)
     */
    @Async
    public void escalateToCCO(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String incidentId,
            String correlationId) {

        log.error("ESCALATING TO CHIEF COMPLIANCE OFFICER - Critical regulatory violation: transaction={} customer={} violation={} incident={} correlationId={}",
                transactionId, customerId, violationType, incidentId, correlationId);

        Map<String, Object> escalationDetails = buildCCOEscalationPayload(
                transactionId,
                customerId,
                violationType,
                violationDetails,
                incidentId,
                correlationId,
                "P0"
        );

        try {
            dlqEscalationService.escalateP0(
                    "Chief Compliance Officer",
                    "CRITICAL_REGULATORY_VIOLATION",
                    escalationDetails
            );

            log.error("CCO escalation triggered successfully for incident: {} correlationId: {}",
                    incidentId, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to escalate to CCO for incident: {} correlationId: {}",
                    incidentId, correlationId, e);
            // Attempt fallback notification
            attemptFallbackCCONotification(transactionId, customerId, violationType, incidentId, correlationId);
        }
    }

    /**
     * Escalate high-priority regulatory issue to compliance team (P1 escalation)
     */
    @Async
    public void escalateToComplianceTeam(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String incidentId,
            String correlationId) {

        log.warn("ESCALATING TO COMPLIANCE TEAM - High-priority regulatory issue: transaction={} customer={} violation={} incident={} correlationId={}",
                transactionId, customerId, violationType, incidentId, correlationId);

        Map<String, Object> escalationDetails = buildComplianceTeamEscalationPayload(
                transactionId,
                customerId,
                violationType,
                violationDetails,
                incidentId,
                correlationId,
                "P1"
        );

        try {
            dlqEscalationService.escalateP1(
                    "Compliance Team",
                    "HIGH_PRIORITY_REGULATORY_ISSUE",
                    escalationDetails
            );

            log.warn("Compliance team escalation triggered successfully for incident: {} correlationId: {}",
                    incidentId, correlationId);

        } catch (Exception e) {
            log.error("Failed to escalate to compliance team for incident: {} correlationId: {}",
                    incidentId, correlationId, e);
        }
    }

    /**
     * Escalate sanctions screening hit to CCO and regulatory authorities (Immediate P0)
     */
    @Async
    public void escalateSanctionsHit(
            String transactionId,
            String customerId,
            String walletAddress,
            String sanctionsList,
            String incidentId,
            String correlationId) {

        log.error("IMMEDIATE CCO ESCALATION - SANCTIONS SCREENING HIT: transaction={} customer={} wallet={} list={} incident={} correlationId={}",
                transactionId, customerId, walletAddress, sanctionsList, incidentId, correlationId);

        Map<String, Object> escalationDetails = new HashMap<>();
        escalationDetails.put("escalationType", "SANCTIONS_HIT");
        escalationDetails.put("transactionId", transactionId);
        escalationDetails.put("customerId", customerId);
        escalationDetails.put("walletAddress", walletAddress);
        escalationDetails.put("sanctionsList", sanctionsList);
        escalationDetails.put("incidentId", incidentId);
        escalationDetails.put("correlationId", correlationId);
        escalationDetails.put("timestamp", Instant.now());
        escalationDetails.put("requiresImmediateAction", true);
        escalationDetails.put("regulatoryReportingRequired", true);
        escalationDetails.put("priority", "P0");

        try {
            // P0 escalation with immediate PagerDuty notification
            dlqEscalationService.escalateP0(
                    "Chief Compliance Officer - SANCTIONS HIT",
                    "SANCTIONS_SCREENING_HIT",
                    escalationDetails
            );

            log.error("Sanctions hit CCO escalation triggered successfully for incident: {} correlationId: {}",
                    incidentId, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to escalate sanctions hit for incident: {} correlationId: {}",
                    incidentId, correlationId, e);
            // Attempt emergency fallback
            attemptEmergencySanctionsNotification(transactionId, customerId, walletAddress, sanctionsList, incidentId, correlationId);
        }
    }

    /**
     * Escalate AML threshold breach to compliance leadership
     */
    @Async
    public void escalateAMLThresholdBreach(
            String transactionId,
            String customerId,
            String breachType,
            String thresholdDetails,
            String incidentId,
            String correlationId) {

        log.warn("ESCALATING AML THRESHOLD BREACH to compliance leadership: transaction={} customer={} breach={} incident={} correlationId={}",
                transactionId, customerId, breachType, incidentId, correlationId);

        Map<String, Object> escalationDetails = buildAMLBreachEscalationPayload(
                transactionId,
                customerId,
                breachType,
                thresholdDetails,
                incidentId,
                correlationId
        );

        try {
            dlqEscalationService.escalateP1(
                    "AML Compliance Team",
                    "AML_THRESHOLD_BREACH",
                    escalationDetails
            );

            log.warn("AML breach escalation triggered successfully for incident: {} correlationId: {}",
                    incidentId, correlationId);

        } catch (Exception e) {
            log.error("Failed to escalate AML breach for incident: {} correlationId: {}",
                    incidentId, correlationId, e);
        }
    }

    /**
     * Build CCO escalation payload
     */
    private Map<String, Object> buildCCOEscalationPayload(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String incidentId,
            String correlationId,
            String priority) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("escalationType", "CCO_REGULATORY_VIOLATION");
        payload.put("transactionId", transactionId);
        payload.put("customerId", customerId);
        payload.put("violationType", violationType);
        payload.put("violationDetails", violationDetails);
        payload.put("incidentId", incidentId);
        payload.put("correlationId", correlationId);
        payload.put("timestamp", Instant.now());
        payload.put("priority", priority);
        payload.put("requiresExecutiveAction", true);
        payload.put("escalationTarget", "Chief Compliance Officer");

        return payload;
    }

    /**
     * Build compliance team escalation payload
     */
    private Map<String, Object> buildComplianceTeamEscalationPayload(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String incidentId,
            String correlationId,
            String priority) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("escalationType", "COMPLIANCE_TEAM_REVIEW");
        payload.put("transactionId", transactionId);
        payload.put("customerId", customerId);
        payload.put("violationType", violationType);
        payload.put("violationDetails", violationDetails);
        payload.put("incidentId", incidentId);
        payload.put("correlationId", correlationId);
        payload.put("timestamp", Instant.now());
        payload.put("priority", priority);
        payload.put("requiresTeamReview", true);
        payload.put("escalationTarget", "Compliance Team");

        return payload;
    }

    /**
     * Build AML breach escalation payload
     */
    private Map<String, Object> buildAMLBreachEscalationPayload(
            String transactionId,
            String customerId,
            String breachType,
            String thresholdDetails,
            String incidentId,
            String correlationId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("escalationType", "AML_THRESHOLD_BREACH");
        payload.put("transactionId", transactionId);
        payload.put("customerId", customerId);
        payload.put("breachType", breachType);
        payload.put("thresholdDetails", thresholdDetails);
        payload.put("incidentId", incidentId);
        payload.put("correlationId", correlationId);
        payload.put("timestamp", Instant.now());
        payload.put("priority", "P1");
        payload.put("requiresAMLReview", true);
        payload.put("escalationTarget", "AML Compliance Team");

        return payload;
    }

    /**
     * Attempt fallback CCO notification via email/SMS if escalation fails
     */
    private void attemptFallbackCCONotification(
            String transactionId,
            String customerId,
            String violationType,
            String incidentId,
            String correlationId) {

        log.error("ATTEMPTING FALLBACK CCO NOTIFICATION for incident: {} correlationId: {}",
                incidentId, correlationId);

        // In production, this would use alternative notification channels
        // (direct email, SMS, emergency Slack webhook, etc.)
        // For now, log the critical failure
        log.error("CRITICAL ESCALATION FAILURE - Manual CCO notification required: " +
                        "incident={} transaction={} customer={} violation={} correlationId={}",
                incidentId, transactionId, customerId, violationType, correlationId);
    }

    /**
     * Attempt emergency sanctions notification via all available channels
     */
    private void attemptEmergencySanctionsNotification(
            String transactionId,
            String customerId,
            String walletAddress,
            String sanctionsList,
            String incidentId,
            String correlationId) {

        log.error("ATTEMPTING EMERGENCY SANCTIONS NOTIFICATION for incident: {} correlationId: {}",
                incidentId, correlationId);

        // In production, this would trigger all emergency notification channels
        // (PagerDuty direct API, SMS to CCO, emergency Slack, etc.)
        log.error("CRITICAL SANCTIONS HIT - Emergency CCO notification required: " +
                        "incident={} transaction={} customer={} wallet={} list={} correlationId={}",
                incidentId, transactionId, customerId, walletAddress, sanctionsList, correlationId);
    }
}
