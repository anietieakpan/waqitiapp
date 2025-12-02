package com.waqiti.crypto.service;

import com.waqiti.common.model.incident.Incident;
import com.waqiti.common.model.incident.IncidentPriority;
import com.waqiti.common.service.IncidentManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Regulatory Violation Incident Service
 * Wrapper around IncidentManagementService for crypto regulatory violations
 * Handles P0/P1 incidents for AML/KYC/Sanctions violations with proper categorization
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryViolationIncidentService {

    private final IncidentManagementService incidentManagementService;

    /**
     * Create P0 incident for critical regulatory violation (Sanctions hit, Critical AML)
     */
    @Transactional
    public Incident createP0RegulatoryViolation(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String correlationId) {

        log.error("CRITICAL REGULATORY VIOLATION - Creating P0 incident: transaction={} customer={} violation={} correlationId={}",
                transactionId, customerId, violationType, correlationId);

        Map<String, Object> violationAlert = buildRegulatoryViolationAlert(
                transactionId,
                customerId,
                violationType,
                violationDetails,
                correlationId,
                "CRITICAL",
                "P0_REGULATORY_VIOLATION"
        );

        Incident incident = incidentManagementService.createIncident(violationAlert);

        log.error("P0 Regulatory violation incident created: {} for transaction: {} correlationId: {}",
                incident.getId(), transactionId, correlationId);

        return incident;
    }

    /**
     * Create P1 incident for high-priority regulatory violation (High-risk customer, Large transaction AML)
     */
    @Transactional
    public Incident createP1RegulatoryViolation(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String correlationId) {

        log.warn("HIGH PRIORITY REGULATORY VIOLATION - Creating P1 incident: transaction={} customer={} violation={} correlationId={}",
                transactionId, customerId, violationType, correlationId);

        Map<String, Object> violationAlert = buildRegulatoryViolationAlert(
                transactionId,
                customerId,
                violationType,
                violationDetails,
                correlationId,
                "HIGH",
                "P1_REGULATORY_VIOLATION"
        );

        Incident incident = incidentManagementService.createIncident(violationAlert);

        log.warn("P1 Regulatory violation incident created: {} for transaction: {} correlationId: {}",
                incident.getId(), transactionId, correlationId);

        return incident;
    }

    /**
     * Create P2 incident for medium-priority regulatory violation (KYC gap, Manual review required)
     */
    @Transactional
    public Incident createP2RegulatoryViolation(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String correlationId) {

        log.warn("REGULATORY VIOLATION - Creating P2 incident: transaction={} customer={} violation={} correlationId={}",
                transactionId, customerId, violationType, correlationId);

        Map<String, Object> violationAlert = buildRegulatoryViolationAlert(
                transactionId,
                customerId,
                violationType,
                violationDetails,
                correlationId,
                "MEDIUM",
                "P2_REGULATORY_VIOLATION"
        );

        Incident incident = incidentManagementService.createIncident(violationAlert);

        log.info("P2 Regulatory violation incident created: {} for transaction: {} correlationId: {}",
                incident.getId(), transactionId, correlationId);

        return incident;
    }

    /**
     * Create compliance failure incident (general compliance issues)
     */
    @Transactional
    public Incident createComplianceFailureIncident(
            String transactionId,
            String customerId,
            String failureReason,
            String correlationId) {

        log.warn("COMPLIANCE FAILURE - Creating incident: transaction={} customer={} reason={} correlationId={}",
                transactionId, customerId, failureReason, correlationId);

        Map<String, Object> failureAlert = buildComplianceFailureAlert(
                transactionId,
                customerId,
                failureReason,
                correlationId
        );

        Incident incident = incidentManagementService.createIncident(failureAlert);

        log.info("Compliance failure incident created: {} for transaction: {} correlationId: {}",
                incident.getId(), transactionId, correlationId);

        return incident;
    }

    /**
     * Build regulatory violation alert structure
     */
    private Map<String, Object> buildRegulatoryViolationAlert(
            String transactionId,
            String customerId,
            String violationType,
            String violationDetails,
            String correlationId,
            String severity,
            String alertType) {

        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", alertType);
        alert.put("severity", severity);
        alert.put("sourceService", "crypto-service");
        alert.put("correlationId", correlationId);
        alert.put("timestamp", Instant.now());
        alert.put("transactionId", transactionId);
        alert.put("customerId", customerId);
        alert.put("violationType", violationType);
        alert.put("violationDetails", violationDetails);
        alert.put("requiresImmediateAction", severity.equals("CRITICAL"));
        alert.put("category", "REGULATORY_COMPLIANCE");
        alert.put("subcategory", violationType);

        return alert;
    }

    /**
     * Build compliance failure alert structure
     */
    private Map<String, Object> buildComplianceFailureAlert(
            String transactionId,
            String customerId,
            String failureReason,
            String correlationId) {

        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "COMPLIANCE_FAILURE");
        alert.put("severity", "MEDIUM");
        alert.put("sourceService", "crypto-service");
        alert.put("correlationId", correlationId);
        alert.put("timestamp", Instant.now());
        alert.put("transactionId", transactionId);
        alert.put("customerId", customerId);
        alert.put("failureReason", failureReason);
        alert.put("requiresImmediateAction", false);
        alert.put("category", "COMPLIANCE");
        alert.put("subcategory", "GENERAL_COMPLIANCE_FAILURE");

        return alert;
    }

    /**
     * Escalate regulatory violation to next level
     */
    @Transactional
    public void escalateRegulatoryViolation(String incidentId, String escalationReason) {
        log.warn("Escalating regulatory violation incident: {} reason: {}", incidentId, escalationReason);

        try {
            incidentManagementService.escalateIncident(incidentId, escalationReason);
            log.info("Successfully escalated regulatory violation incident: {}", incidentId);
        } catch (Exception e) {
            log.error("Failed to escalate regulatory violation incident: {}", incidentId, e);
            throw new RuntimeException("Failed to escalate incident", e);
        }
    }

    /**
     * Resolve regulatory violation incident
     */
    @Transactional
    public void resolveRegulatoryViolation(String incidentId, String resolution) {
        log.info("Resolving regulatory violation incident: {} resolution: {}", incidentId, resolution);

        try {
            incidentManagementService.resolveIncident(incidentId, resolution);
            log.info("Successfully resolved regulatory violation incident: {}", incidentId);
        } catch (Exception e) {
            log.error("Failed to resolve regulatory violation incident: {}", incidentId, e);
            throw new RuntimeException("Failed to resolve incident", e);
        }
    }
}
