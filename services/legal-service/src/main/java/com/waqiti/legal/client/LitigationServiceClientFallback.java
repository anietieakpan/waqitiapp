package com.waqiti.legal.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Litigation Service Client Fallback
 *
 * Provides graceful degradation when litigation-service is unavailable.
 * For bankruptcy stay enforcement, failure to suspend litigation is a
 * CRITICAL violation risk.
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class LitigationServiceClientFallback implements LitigationServiceClient {

    @Override
    public List<LawsuitDto> getActiveLawsuits(String customerId) {
        log.error("FALLBACK: litigation-service unavailable for getActiveLawsuits({})", customerId);
        log.error("CRITICAL: Cannot verify active lawsuits - manual review required");

        return Collections.emptyList();
    }

    @Override
    public List<LawsuitDto> getLawsuitsSinceDate(String customerId, LocalDate sinceDate) {
        log.error("FALLBACK: litigation-service unavailable for getLawsuitsSinceDate({}, {})",
            customerId, sinceDate);
        log.error("CRITICAL: Cannot verify stay compliance for lawsuits - manual audit required");

        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> suspendLawsuit(String lawsuitId, String reason, String bankruptcyId) {
        log.error("FALLBACK: litigation-service unavailable for suspendLawsuit({})", lawsuitId);
        log.error("CRITICAL STAY VIOLATION RISK: Lawsuit {} may still be active", lawsuitId);

        return Map.of(
            "success", false,
            "fallback", true,
            "lawsuitId", lawsuitId,
            "bankruptcyId", bankruptcyId,
            "error", "litigation-service unavailable",
            "action", "ESCALATE_TO_LEGAL_TEAM",
            "severity", "CRITICAL"
        );
    }

    @Override
    public Map<String, Object> suspendAllLawsuits(String customerId, String reason, String bankruptcyId) {
        log.error("FALLBACK: litigation-service unavailable for suspendAllLawsuits({})", customerId);
        log.error("CRITICAL BANKRUPTCY STAY VIOLATION RISK: Cannot suspend lawsuits for customer {}", customerId);
        log.error("ACTION REQUIRED: Manually suspend all litigation immediately");

        return Map.of(
            "success", false,
            "fallback", true,
            "customerId", customerId,
            "bankruptcyId", bankruptcyId,
            "error", "litigation-service unavailable",
            "lawsuitsSuspended", 0,
            "action", "IMMEDIATE_ESCALATION_REQUIRED",
            "severity", "CRITICAL"
        );
    }

    @Override
    public Map<String, Object> resumeLawsuit(String lawsuitId, String reason, String referenceId) {
        log.error("FALLBACK: litigation-service unavailable for resumeLawsuit({})", lawsuitId);
        log.warn("Cannot resume lawsuit - will remain suspended until service recovers");

        return Map.of(
            "success", false,
            "fallback", true,
            "lawsuitId", lawsuitId,
            "error", "litigation-service unavailable"
        );
    }
}
