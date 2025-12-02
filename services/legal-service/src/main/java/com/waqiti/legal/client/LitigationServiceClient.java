package com.waqiti.legal.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Litigation Service Feign Client
 *
 * Provides integration with litigation-service for:
 * - Suspending lawsuits during bankruptcy automatic stay
 * - Retrieving litigation history
 * - Stay compliance verification
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 */
@FeignClient(
    name = "litigation-service",
    fallback = LitigationServiceClientFallback.class
)
public interface LitigationServiceClient {

    /**
     * Get all active lawsuits for a customer
     *
     * @param customerId Customer ID
     * @return List of active lawsuits
     */
    @GetMapping("/api/v1/litigation/customer/{customerId}/active")
    List<LawsuitDto> getActiveLawsuits(@PathVariable("customerId") String customerId);

    /**
     * Get lawsuits filed since a specific date
     * Used for bankruptcy stay compliance verification
     *
     * @param customerId Customer ID
     * @param sinceDate Date to check from (typically bankruptcy filing date)
     * @return List of lawsuits filed since date
     */
    @GetMapping("/api/v1/litigation/customer/{customerId}/since/{sinceDate}")
    List<LawsuitDto> getLawsuitsSinceDate(
        @PathVariable("customerId") String customerId,
        @PathVariable("sinceDate") LocalDate sinceDate
    );

    /**
     * Suspend a lawsuit due to automatic stay
     *
     * @param lawsuitId Lawsuit ID
     * @param reason Reason (e.g., "AUTOMATIC_STAY")
     * @param bankruptcyId Bankruptcy case ID
     * @return Suspension confirmation
     */
    @PostMapping("/api/v1/litigation/lawsuits/{lawsuitId}/suspend")
    Map<String, Object> suspendLawsuit(
        @PathVariable("lawsuitId") String lawsuitId,
        @RequestParam("reason") String reason,
        @RequestParam("bankruptcyId") String bankruptcyId
    );

    /**
     * Suspend all lawsuits for a customer
     *
     * @param customerId Customer ID
     * @param reason Reason (e.g., "AUTOMATIC_STAY")
     * @param bankruptcyId Bankruptcy case ID
     * @return Suspension result
     */
    @PostMapping("/api/v1/litigation/customer/{customerId}/suspend-all")
    Map<String, Object> suspendAllLawsuits(
        @PathVariable("customerId") String customerId,
        @RequestParam("reason") String reason,
        @RequestParam("bankruptcyId") String bankruptcyId
    );

    /**
     * Resume a lawsuit after stay lift
     *
     * @param lawsuitId Lawsuit ID
     * @param reason Reason (e.g., "STAY_LIFTED")
     * @param referenceId Reference ID
     * @return Resume confirmation
     */
    @PostMapping("/api/v1/litigation/lawsuits/{lawsuitId}/resume")
    Map<String, Object> resumeLawsuit(
        @PathVariable("lawsuitId") String lawsuitId,
        @RequestParam("reason") String reason,
        @RequestParam("referenceId") String referenceId
    );
}
