package com.waqiti.legal.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Collection Service Client Fallback
 *
 * Provides graceful degradation when collection-service is unavailable.
 * For bankruptcy stay enforcement, this is CRITICAL - we must err on the
 * side of caution and assume collections should be stopped.
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class CollectionServiceClientFallback implements CollectionServiceClient {

    @Override
    public List<CollectionActivityDto> getActiveCollections(String customerId) {
        log.error("FALLBACK: collection-service unavailable for getActiveCollections({})", customerId);
        log.error("CRITICAL: Cannot verify active collections - manual review required");

        // Return empty list - calling service should handle appropriately
        // and escalate to legal team for manual verification
        return Collections.emptyList();
    }

    @Override
    public List<CollectionActivityDto> getActivitiesSinceDate(String customerId, LocalDate sinceDate) {
        log.error("FALLBACK: collection-service unavailable for getActivitiesSinceDate({}, {})",
            customerId, sinceDate);
        log.error("CRITICAL: Cannot verify stay compliance - manual audit required");

        // Return empty list - compliance check will need manual review
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> stopActivity(String activityId, String reason, String referenceId) {
        log.error("FALLBACK: collection-service unavailable for stopActivity({})", activityId);
        log.error("CRITICAL: Collection activity {} may still be active - MANUAL INTERVENTION REQUIRED", activityId);

        // Publish alert to Kafka for immediate attention
        return Map.of(
            "success", false,
            "fallback", true,
            "activityId", activityId,
            "error", "collection-service unavailable",
            "action", "ESCALATE_TO_LEGAL_TEAM"
        );
    }

    @Override
    public Map<String, Object> stopAllActivities(String customerId, String reason, String bankruptcyId) {
        log.error("FALLBACK: collection-service unavailable for stopAllActivities({})", customerId);
        log.error("CRITICAL BANKRUPTCY STAY VIOLATION RISK: Cannot stop collections for customer {}", customerId);
        log.error("ACTION REQUIRED: Manually halt all collection activities immediately");

        // This is a CRITICAL failure - potential stay violation
        // Must escalate immediately
        return Map.of(
            "success", false,
            "fallback", true,
            "customerId", customerId,
            "bankruptcyId", bankruptcyId,
            "error", "collection-service unavailable",
            "activitiesStopped", 0,
            "action", "IMMEDIATE_ESCALATION_REQUIRED",
            "severity", "CRITICAL"
        );
    }

    @Override
    public Map<String, Object> resumeActivities(String customerId, String reason, String referenceId) {
        log.error("FALLBACK: collection-service unavailable for resumeActivities({})", customerId);
        log.warn("Cannot resume collections - will remain suspended until service recovers");

        // Safer to NOT resume than to violate stay
        return Map.of(
            "success", false,
            "fallback", true,
            "customerId", customerId,
            "error", "collection-service unavailable",
            "activitiesResumed", 0
        );
    }
}
