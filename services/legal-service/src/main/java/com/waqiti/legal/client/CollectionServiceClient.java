package com.waqiti.legal.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Collection Service Feign Client
 *
 * Provides integration with collection-service for:
 * - Stopping collection activities during bankruptcy automatic stay
 * - Retrieving collection activity history
 * - Resuming collections after stay lift
 * - Compliance verification
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 */
@FeignClient(
    name = "collection-service",
    fallback = CollectionServiceClientFallback.class
)
public interface CollectionServiceClient {

    /**
     * Get all active collection activities for a customer
     *
     * @param customerId Customer ID
     * @return List of active collection activities
     */
    @GetMapping("/api/v1/collections/customer/{customerId}/active")
    List<CollectionActivityDto> getActiveCollections(@PathVariable("customerId") String customerId);

    /**
     * Get collection activities since a specific date
     * Used for bankruptcy stay compliance verification
     *
     * @param customerId Customer ID
     * @param sinceDate Date to check from (typically bankruptcy filing date)
     * @return List of collection activities since date
     */
    @GetMapping("/api/v1/collections/customer/{customerId}/since/{sinceDate}")
    List<CollectionActivityDto> getActivitiesSinceDate(
        @PathVariable("customerId") String customerId,
        @PathVariable("sinceDate") LocalDate sinceDate
    );

    /**
     * Stop a specific collection activity
     *
     * @param activityId Collection activity ID
     * @param reason Reason for stopping (e.g., "AUTOMATIC_STAY")
     * @param referenceId Reference ID (e.g., bankruptcy case ID)
     * @return Stop confirmation
     */
    @PostMapping("/api/v1/collections/activities/{activityId}/stop")
    Map<String, Object> stopActivity(
        @PathVariable("activityId") String activityId,
        @RequestParam("reason") String reason,
        @RequestParam("referenceId") String referenceId
    );

    /**
     * Stop all collection activities for a customer
     * Used when automatic stay is enforced
     *
     * @param customerId Customer ID
     * @param reason Reason (e.g., "AUTOMATIC_STAY")
     * @param bankruptcyId Bankruptcy case ID
     * @return Stop result with count of activities stopped
     */
    @PostMapping("/api/v1/collections/customer/{customerId}/stop-all")
    Map<String, Object> stopAllActivities(
        @PathVariable("customerId") String customerId,
        @RequestParam("reason") String reason,
        @RequestParam("bankruptcyId") String bankruptcyId
    );

    /**
     * Resume collection activities for a customer
     * Used when automatic stay is lifted
     *
     * @param customerId Customer ID
     * @param reason Reason (e.g., "STAY_LIFTED")
     * @param referenceId Reference ID
     * @return Resume confirmation
     */
    @PostMapping("/api/v1/collections/customer/{customerId}/resume")
    Map<String, Object> resumeActivities(
        @PathVariable("customerId") String customerId,
        @RequestParam("reason") String reason,
        @RequestParam("referenceId") String referenceId
    );
}
