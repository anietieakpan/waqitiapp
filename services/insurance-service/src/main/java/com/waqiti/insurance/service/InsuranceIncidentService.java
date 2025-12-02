package com.waqiti.insurance.service;

import com.waqiti.common.service.IncidentManagementService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Insurance Incident Service
 * Wrapper around IncidentManagementService for insurance-specific incidents
 * Production-ready implementation for fraud, regulatory violations, and claim incidents
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceIncidentService {

    private final IncidentManagementService incidentManagementService;
    private final MeterRegistry meterRegistry;

    /**
     * Create fraud investigation incident
     */
    @Async
    public void createFraudInvestigation(String claimId, String policyId, String policyHolderId,
                                        List<String> fraudIndicators, String eventId,
                                        String correlationId) {
        log.error("Creating fraud investigation incident: claimId={} policyId={} indicators={} correlationId={}",
                claimId, policyId, fraudIndicators, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "INSURANCE_FRAUD");
        incidentData.put("claimId", claimId);
        incidentData.put("policyId", policyId);
        incidentData.put("policyHolderId", policyHolderId);
        incidentData.put("fraudIndicators", String.join(", ", fraudIndicators));
        incidentData.put("eventId", eventId);
        incidentData.put("action", "Claim denied - SIU investigation initiated");
        incidentData.put("regulatoryImpact", "HIGH");
        incidentData.put("potentialFraudAmount", "TBD");

        incidentManagementService.createIncident(
                "P1",
                "INSURANCE_FRAUD_INVESTIGATION",
                String.format("Fraudulent claim detected - Claim: %s - Indicators: %s",
                        claimId, String.join(", ", fraudIndicators)),
                incidentData,
                correlationId
        );

        recordMetric("insurance_fraud_incidents_created_total",
                "indicator_count", String.valueOf(fraudIndicators.size()));

        log.error("Fraud investigation incident created: claimId={} correlationId={}", claimId, correlationId);
    }

    /**
     * Create regulatory violation incident
     */
    @Async
    public void createRegulatoryViolation(String claimId, String policyId, String violationType,
                                         String eventId, String correlationId) {
        log.error("Creating regulatory violation incident: claimId={} policyId={} violation={} correlationId={}",
                claimId, policyId, violationType, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "INSURANCE_REGULATORY_VIOLATION");
        incidentData.put("claimId", claimId);
        incidentData.put("policyId", policyId);
        incidentData.put("violationType", violationType);
        incidentData.put("eventId", eventId);
        incidentData.put("action", "Claim processing halted - regulatory review required");
        incidentData.put("regulatoryImpact", "CRITICAL");
        incidentData.put("potentialFine", "Varies by state - up to $50,000 per violation");

        incidentManagementService.createIncident(
                "P0",
                "INSURANCE_REGULATORY_VIOLATION",
                String.format("Insurance regulatory violation - Claim: %s - Type: %s",
                        claimId, violationType),
                incidentData,
                correlationId
        );

        recordMetric("insurance_regulatory_violation_incidents_created_total",
                "violation_type", violationType);

        log.error("Regulatory violation incident created: claimId={} correlationId={}", claimId, correlationId);
    }

    /**
     * Create claim processing failure incident
     */
    @Async
    public void createClaimProcessingFailure(String claimId, String policyId, String failureReason,
                                            String correlationId) {
        log.error("Creating claim processing failure incident: claimId={} policyId={} reason={} correlationId={}",
                claimId, policyId, failureReason, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "CLAIM_PROCESSING_FAILURE");
        incidentData.put("claimId", claimId);
        incidentData.put("policyId", policyId);
        incidentData.put("failureReason", failureReason);
        incidentData.put("action", "Escalated to insurance operations manager");
        incidentData.put("customerImpact", "HIGH");
        incidentData.put("slaRisk", "YES");

        incidentManagementService.createIncident(
                "P1",
                "INSURANCE_CLAIM_PROCESSING_FAILURE",
                String.format("Claim processing failed - Claim: %s - Reason: %s",
                        claimId, failureReason),
                incidentData,
                correlationId
        );

        recordMetric("insurance_claim_processing_failure_incidents_created_total");

        log.error("Claim processing failure incident created: claimId={} correlationId={}", claimId, correlationId);
    }

    /**
     * Create SLA breach incident
     */
    @Async
    public void createSlaBreachIncident(String claimId, String policyId, String claimType,
                                       int daysOverdue, String correlationId) {
        log.error("Creating SLA breach incident: claimId={} policyId={} daysOverdue={} correlationId={}",
                claimId, policyId, daysOverdue, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "CLAIM_SLA_BREACH");
        incidentData.put("claimId", claimId);
        incidentData.put("policyId", policyId);
        incidentData.put("claimType", claimType);
        incidentData.put("daysOverdue", daysOverdue);
        incidentData.put("action", "Immediate escalation to claims manager");
        incidentData.put("regulatoryImpact", daysOverdue > 30 ? "HIGH" : "MEDIUM");
        incidentData.put("customerSatisfactionRisk", "CRITICAL");

        incidentManagementService.createIncident(
                daysOverdue > 30 ? "P0" : "P1",
                "INSURANCE_CLAIM_SLA_BREACH",
                String.format("Claim SLA breach - Claim: %s - Days overdue: %d",
                        claimId, daysOverdue),
                incidentData,
                correlationId
        );

        recordMetric("insurance_sla_breach_incidents_created_total",
                "days_overdue_range", daysOverdue > 30 ? "30+" : "1-30");

        log.error("SLA breach incident created: claimId={} daysOverdue={} correlationId={}",
                claimId, daysOverdue, correlationId);
    }

    /**
     * Create high-value claim incident (for tracking and oversight)
     */
    @Async
    public void createHighValueClaimIncident(String claimId, String policyId, String claimType,
                                            String claimAmount, String correlationId) {
        log.info("Creating high-value claim incident: claimId={} policyId={} amount={} correlationId={}",
                claimId, policyId, claimAmount, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "HIGH_VALUE_CLAIM");
        incidentData.put("claimId", claimId);
        incidentData.put("policyId", policyId);
        incidentData.put("claimType", claimType);
        incidentData.put("claimAmount", claimAmount);
        incidentData.put("action", "Senior adjuster review and executive notification");
        incidentData.put("financialImpact", "HIGH");
        incidentData.put("requiresExecutiveApproval", true);

        incidentManagementService.createIncident(
                "P2",
                "INSURANCE_HIGH_VALUE_CLAIM",
                String.format("High-value claim submitted - Claim: %s - Amount: %s",
                        claimId, claimAmount),
                incidentData,
                correlationId
        );

        recordMetric("insurance_high_value_claim_incidents_created_total",
                "claim_type", claimType);

        log.info("High-value claim incident created: claimId={} amount={} correlationId={}",
                claimId, claimAmount, correlationId);
    }

    /**
     * Create customer protection incident
     */
    @Async
    public void createCustomerProtectionIncident(String claimId, String policyId,
                                                String protectionReason, String correlationId) {
        log.error("Creating customer protection incident: claimId={} policyId={} reason={} correlationId={}",
                claimId, policyId, protectionReason, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "CUSTOMER_CLAIM_PROTECTION");
        incidentData.put("claimId", claimId);
        incidentData.put("policyId", policyId);
        incidentData.put("protectionReason", protectionReason);
        incidentData.put("action", "Customer claim protection protocol activated");
        incidentData.put("customerImpact", "CRITICAL");
        incidentData.put("requiresImmediateAction", true);

        incidentManagementService.createIncident(
                "P0",
                "INSURANCE_CUSTOMER_PROTECTION",
                String.format("Customer claim protection activated - Claim: %s - Reason: %s",
                        claimId, protectionReason),
                incidentData,
                correlationId
        );

        recordMetric("insurance_customer_protection_incidents_created_total");

        log.error("Customer protection incident created: claimId={} correlationId={}", claimId, correlationId);
    }

    /**
     * Create policy voiding incident
     */
    @Async
    public void createPolicyVoidingIncident(String policyId, String policyHolderId, String voidReason,
                                           String correlationId) {
        log.error("Creating policy voiding incident: policyId={} policyHolderId={} reason={} correlationId={}",
                policyId, policyHolderId, voidReason, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "POLICY_VOIDING");
        incidentData.put("policyId", policyId);
        incidentData.put("policyHolderId", policyHolderId);
        incidentData.put("voidReason", voidReason);
        incidentData.put("action", "Policy voiding process initiated - legal review required");
        incidentData.put("legalImpact", "HIGH");
        incidentData.put("requiresLegalReview", true);

        incidentManagementService.createIncident(
                "P1",
                "INSURANCE_POLICY_VOIDING",
                String.format("Policy voiding initiated - Policy: %s - Reason: %s",
                        policyId, voidReason),
                incidentData,
                correlationId
        );

        recordMetric("insurance_policy_voiding_incidents_created_total");

        log.error("Policy voiding incident created: policyId={} correlationId={}", policyId, correlationId);
    }

    /**
     * Create catastrophic claim incident
     */
    @Async
    public void createCatastrophicClaimIncident(String claimId, String policyId, String catastropheType,
                                               String affectedArea, String correlationId) {
        log.error("Creating catastrophic claim incident: claimId={} type={} area={} correlationId={}",
                claimId, catastropheType, affectedArea, correlationId);

        Map<String, Object> incidentData = new HashMap<>();
        incidentData.put("incidentType", "CATASTROPHIC_CLAIM");
        incidentData.put("claimId", claimId);
        incidentData.put("policyId", policyId);
        incidentData.put("catastropheType", catastropheType);
        incidentData.put("affectedArea", affectedArea);
        incidentData.put("action", "Catastrophe team activated - expedited processing");
        incidentData.put("financialImpact", "CRITICAL");
        incidentData.put("requiresSpecialistTeam", true);

        incidentManagementService.createIncident(
                "P0",
                "INSURANCE_CATASTROPHIC_CLAIM",
                String.format("Catastrophic claim received - Type: %s - Area: %s - Claim: %s",
                        catastropheType, affectedArea, claimId),
                incidentData,
                correlationId
        );

        recordMetric("insurance_catastrophic_claim_incidents_created_total",
                "catastrophe_type", catastropheType);

        log.error("Catastrophic claim incident created: claimId={} type={} correlationId={}",
                claimId, catastropheType, correlationId);
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
