package com.waqiti.compliance.service;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.compliance.AMLScreeningEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.compliance.domain.AMLScreening;
import com.waqiti.compliance.domain.AMLScreening.AMLDecision;
import com.waqiti.compliance.domain.AMLScreening.AMLResult;
import com.waqiti.compliance.domain.AMLRiskLevel;
import com.waqiti.compliance.domain.AMLScreeningStatus;
import com.waqiti.compliance.domain.SanctionMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Production-grade service for automated AML decision making.
 * Analyzes screening results and makes risk-based decisions on
 * whether to approve, reject, escalate, or request additional information.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AMLDecisionService {

    private final MetricsService metricsService;
    private final AuditLogger auditLogger;

    // Risk score thresholds for automated decisions
    private static final int CRITICAL_RISK_THRESHOLD = 90;
    private static final int HIGH_RISK_THRESHOLD = 75;
    private static final int MEDIUM_RISK_THRESHOLD = 50;
    private static final int LOW_RISK_THRESHOLD = 25;

    /**
     * Make automated AML decision based on screening results
     */
    public AMLDecision makeAutomatedDecision(
            AMLScreening screening,
            AMLScreeningEvent event,
            List<SanctionMatch> allMatches) {

        log.info("Making automated AML decision for entity {}: riskLevel={}, riskScore={}, matches={}",
                event.getEntityId(), screening.getRiskLevel(), screening.getRiskScore(), allMatches.size());

        AMLDecision decision;
        String decisionReason;

        // PROHIBITED risk level - immediate rejection
        if (screening.getRiskLevel() == AMLRiskLevel.PROHIBITED) {
            decision = AMLDecision.REJECT;
            decisionReason = "Entity on prohibited sanctions list - must be rejected";
            screening.setBlocked(true);

            log.error("PROHIBITED ENTITY DETECTED: {} - AUTOMATIC REJECTION", event.getEntityId());

            auditLogger.logCriticalAlert("AML_PROHIBITED_ENTITY",
                "Prohibited entity detected - automatic rejection",
                Map.of(
                    "entityId", event.getEntityId(),
                    "entityName", event.getEntityName(),
                    "riskLevel", screening.getRiskLevel().toString(),
                    "matches", String.valueOf(allMatches.size())
                ));
        }
        // CRITICAL risk level - escalate immediately
        else if (screening.getRiskLevel() == AMLRiskLevel.CRITICAL
                || screening.getRiskScore() >= CRITICAL_RISK_THRESHOLD) {
            decision = AMLDecision.ESCALATE;
            decisionReason = "Critical risk level detected - requires senior compliance review";
            screening.setEscalated(true);
            screening.setEscalationReason(decisionReason);

            log.warn("CRITICAL RISK: {} - ESCALATING to senior compliance", event.getEntityId());

            auditLogger.logCriticalAlert("AML_CRITICAL_RISK",
                "Critical AML risk - escalating to senior compliance",
                Map.of(
                    "entityId", event.getEntityId(),
                    "riskLevel", screening.getRiskLevel().toString(),
                    "riskScore", String.valueOf(screening.getRiskScore())
                ));
        }
        // HIGH risk level - escalate for review
        else if (screening.getRiskLevel() == AMLRiskLevel.HIGH
                || screening.getRiskScore() >= HIGH_RISK_THRESHOLD) {
            decision = AMLDecision.ESCALATE;
            decisionReason = "High risk level - requires compliance team review";
            screening.setEscalated(true);
            screening.setEscalationReason(decisionReason);

            log.warn("HIGH RISK: {} - ESCALATING to compliance team", event.getEntityId());
        }
        // MEDIUM risk level - request more information or ongoing monitoring
        else if (screening.getRiskLevel() == AMLRiskLevel.MEDIUM
                || screening.getRiskScore() >= MEDIUM_RISK_THRESHOLD) {

            // If it's customer onboarding, request more info
            if (screening.getScreeningType() == AMLScreening.AMLScreeningType.CUSTOMER_ONBOARDING) {
                decision = AMLDecision.REQUEST_MORE_INFO;
                decisionReason = "Medium risk - enhanced due diligence (EDD) required";

                log.info("MEDIUM RISK: {} - REQUESTING additional documentation", event.getEntityId());
            }
            // For transactions, approve with ongoing monitoring
            else {
                decision = AMLDecision.ONGOING_MONITORING;
                decisionReason = "Medium risk - approved with enhanced monitoring";

                log.info("MEDIUM RISK: {} - APPROVED with ongoing monitoring", event.getEntityId());
            }
        }
        // LOW risk level - approve
        else if (screening.getRiskLevel() == AMLRiskLevel.LOW
                || screening.getRiskScore() >= LOW_RISK_THRESHOLD) {
            decision = AMLDecision.APPROVE;
            decisionReason = "Low risk - approved with standard monitoring";

            log.info("LOW RISK: {} - APPROVED", event.getEntityId());
        }
        // NO_RISK - approve immediately
        else {
            decision = AMLDecision.APPROVE;
            decisionReason = "No risk identified - approved";

            log.info("NO RISK: {} - APPROVED", event.getEntityId());
        }

        // Set decision on screening record
        screening.setDecision(decision);
        screening.setDecisionReason(decisionReason);

        // Update status based on decision
        updateScreeningStatus(screening, decision);

        // Record metrics
        metricsService.incrementCounter("aml.decision.made",
            Map.of(
                "decision", decision.toString(),
                "risk_level", screening.getRiskLevel().toString(),
                "entity_type", event.getEntityType()
            ));

        // Audit the decision
        auditLogger.logEvent("AML_DECISION_MADE",
            event.getEntityId(),
            Map.of(
                "decision", decision.toString(),
                "decisionReason", decisionReason,
                "riskLevel", screening.getRiskLevel().toString(),
                "riskScore", String.valueOf(screening.getRiskScore()),
                "matches", String.valueOf(allMatches.size()),
                "screeningId", screening.getId()
            ));

        log.info("AML decision made for entity {}: decision={}, reason={}",
                event.getEntityId(), decision, decisionReason);

        return decision;
    }

    /**
     * Update screening status based on decision
     */
    private void updateScreeningStatus(AMLScreening screening, AMLDecision decision) {
        switch (decision) {
            case APPROVE:
                screening.setStatus(AMLScreeningStatus.APPROVED);
                break;
            case REJECT:
                screening.setStatus(AMLScreeningStatus.REJECTED);
                break;
            case ESCALATE:
                screening.setStatus(AMLScreeningStatus.ESCALATED);
                break;
            case REQUEST_MORE_INFO:
                screening.setStatus(AMLScreeningStatus.PENDING_INFO);
                break;
            case ONGOING_MONITORING:
                screening.setStatus(AMLScreeningStatus.APPROVED);
                break;
            default:
                screening.setStatus(AMLScreeningStatus.UNDER_REVIEW);
        }
    }

    /**
     * Check if decision requires immediate transaction blocking
     */
    public boolean requiresTransactionBlocking(AMLDecision decision, AMLRiskLevel riskLevel) {
        return decision == AMLDecision.REJECT
                || riskLevel == AMLRiskLevel.PROHIBITED
                || riskLevel == AMLRiskLevel.CRITICAL;
    }

    /**
     * Check if decision requires manual review
     */
    public boolean requiresManualReview(AMLDecision decision) {
        return decision == AMLDecision.ESCALATE
                || decision == AMLDecision.REQUEST_MORE_INFO;
    }

    /**
     * Check if decision requires enhanced monitoring
     */
    public boolean requiresEnhancedMonitoring(AMLDecision decision, AMLRiskLevel riskLevel) {
        return decision == AMLDecision.ONGOING_MONITORING
                || riskLevel == AMLRiskLevel.MEDIUM
                || riskLevel == AMLRiskLevel.HIGH;
    }

    /**
     * Get recommended actions based on decision
     */
    public List<String> getRecommendedActions(AMLDecision decision, AMLRiskLevel riskLevel) {
        switch (decision) {
            case REJECT:
                return List.of(
                    "Block all transactions",
                    "Freeze account",
                    "File SAR (Suspicious Activity Report)",
                    "Notify compliance officer",
                    "Notify authorities if required"
                );
            case ESCALATE:
                if (riskLevel == AMLRiskLevel.CRITICAL) {
                    return List.of(
                        "Hold transaction pending review",
                        "Senior compliance review required",
                        "Consider filing SAR",
                        "Enhanced due diligence required"
                    );
                } else {
                    return List.of(
                        "Compliance team review required",
                        "Enhanced due diligence recommended",
                        "Additional documentation needed"
                    );
                }
            case REQUEST_MORE_INFO:
                return List.of(
                    "Request additional documentation",
                    "Verify source of funds",
                    "Enhanced customer due diligence (CDD)",
                    "Verify beneficial ownership"
                );
            case ONGOING_MONITORING:
                return List.of(
                    "Approve with enhanced monitoring",
                    "Set up transaction alerts",
                    "Periodic review required",
                    "Monitor for unusual patterns"
                );
            case APPROVE:
                return List.of(
                    "Approve transaction",
                    "Standard monitoring applies"
                );
            default:
                return List.of("Manual review required");
        }
    }

    /**
     * Make AML decision (alternative method signature for consumer)
     * Returns String instead of AMLDecision enum
     */
    public String makeAMLDecision(AMLScreening screening, AMLScreeningEvent event) {
        AMLDecision decision = makeAutomatedDecision(screening, event, screening.getMatches());
        return decision.toString();
    }

    /**
     * Get decision reason for a screening
     */
    public String getDecisionReason(AMLScreening screening) {
        if (screening.getDecisionReason() != null) {
            return screening.getDecisionReason();
        }

        // Generate decision reason based on risk level
        if (screening.getRiskLevel() == null) {
            return "Screening incomplete - no risk level determined";
        }

        switch (screening.getRiskLevel()) {
            case PROHIBITED:
                return "Entity on prohibited sanctions list - must be rejected";
            case CRITICAL:
                return "Critical risk level detected - requires senior compliance review";
            case HIGH:
                return "High risk level - requires compliance team review";
            case MEDIUM:
                return "Medium risk - enhanced due diligence recommended";
            case LOW:
                return "Low risk - approved with standard monitoring";
            case MINIMAL:
                return "Minimal risk - approved";
            case NO_RISK:
                return "No risk identified - approved";
            default:
                return "Unknown risk level - manual review required";
        }
    }
}
