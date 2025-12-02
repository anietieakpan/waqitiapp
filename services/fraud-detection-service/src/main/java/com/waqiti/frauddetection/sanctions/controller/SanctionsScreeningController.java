package com.waqiti.frauddetection.sanctions.controller;

import com.waqiti.frauddetection.sanctions.dto.SanctionsScreeningRequest;
import com.waqiti.frauddetection.sanctions.dto.SanctionsScreeningResult;
import com.waqiti.frauddetection.sanctions.entity.SanctionsCheckRecord;
import com.waqiti.frauddetection.sanctions.entity.SanctionsCheckRecord.Resolution;
import com.waqiti.frauddetection.sanctions.service.SanctionsListCacheService;
import com.waqiti.frauddetection.sanctions.service.SanctionsScreeningService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for OFAC Sanctions Screening Operations.
 *
 * This controller provides endpoints for:
 * - Real-time user sanctions screening
 * - Transaction party sanctions screening
 * - Manual resolution workflow for sanctions matches
 * - Screening history retrieval
 * - Compliance reporting and statistics
 *
 * Security:
 * - All endpoints require authentication via Keycloak JWT
 * - Fine-grained authorization with role-based access control
 * - Rate limiting to prevent API abuse
 * - Comprehensive audit logging
 *
 * Compliance:
 * - OFAC SDN List screening (31 CFR Part 501)
 * - EU Sanctions List screening
 * - UN Sanctions List screening
 * - SAR filing integration for suspicious matches
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@RestController
@RequestMapping("/api/v1/sanctions")
@RequiredArgsConstructor
@Slf4j
@Validated
public class SanctionsScreeningController {

    private final SanctionsScreeningService sanctionsScreeningService;
    private final SanctionsListCacheService sanctionsListCacheService;

    // Rate limiting: 100 requests per minute per IP
    private final Bucket screeningRateLimiter = Bucket.builder()
        .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
        .build();

    // Rate limiting: 20 manual reviews per minute per user
    private final Bucket manualReviewRateLimiter = Bucket.builder()
        .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1))))
        .build();

    /**
     * Screen a user against OFAC and other sanctions lists.
     *
     * Performs real-time screening against:
     * - OFAC SDN (Specially Designated Nationals) List
     * - EU Consolidated Sanctions List
     * - UN Sanctions List
     *
     * Uses multi-algorithm fuzzy matching for name variations.
     *
     * @param request Screening request with user details
     * @return Async screening result with match details and risk assessment
     */
    @PostMapping("/screen/user")
    @PreAuthorize("hasAuthority('SCOPE_sanctions:screen') or hasRole('COMPLIANCE_OFFICER') or hasRole('FRAUD_ANALYST')")
    public CompletableFuture<ResponseEntity<SanctionsScreeningResult>> screenUser(
            @Valid @RequestBody SanctionsScreeningRequest request) {

        // Rate limiting check
        if (!screeningRateLimiter.tryConsume(1)) {
            log.warn("SECURITY: Rate limit exceeded for sanctions screening - Request: {}", request.getUserId());
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(SanctionsScreeningResult.builder()
                        .checkStatus(SanctionsCheckRecord.CheckStatus.FAILED)
                        .build())
            );
        }

        log.info("COMPLIANCE: Screening user against sanctions lists - UserId: {}, Name: {}, Source: {}",
            request.getUserId(), maskName(request.getFullName()), request.getCheckSource());

        return sanctionsScreeningService.screenUser(request)
            .thenApply(result -> {
                if (result.getMatchFound()) {
                    log.warn("COMPLIANCE ALERT: Sanctions match found - UserId: {}, MatchScore: {}, RiskLevel: {}",
                        request.getUserId(), result.getMatchScore(), result.getRiskLevel());
                }
                return ResponseEntity.ok(result);
            })
            .exceptionally(ex -> {
                log.error("COMPLIANCE ERROR: Failed to screen user - UserId: {}", request.getUserId(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SanctionsScreeningResult.builder()
                        .checkStatus(SanctionsCheckRecord.CheckStatus.FAILED)
                        .build());
            });
    }

    /**
     * Screen transaction parties (sender and beneficiary) against sanctions lists.
     *
     * Essential for wire transfers, international payments, and high-value transactions.
     *
     * @param request Transaction screening request
     * @return Async screening result for transaction parties
     */
    @PostMapping("/screen/transaction")
    @PreAuthorize("hasAuthority('SCOPE_sanctions:screen') or hasRole('COMPLIANCE_OFFICER')")
    public CompletableFuture<ResponseEntity<SanctionsScreeningResult>> screenTransaction(
            @Valid @RequestBody SanctionsScreeningRequest request) {

        // Rate limiting check
        if (!screeningRateLimiter.tryConsume(1)) {
            log.warn("SECURITY: Rate limit exceeded for transaction screening - TransactionId: {}",
                request.getTransactionId());
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
            );
        }

        log.info("COMPLIANCE: Screening transaction parties - TransactionId: {}, Amount: {}",
            request.getTransactionId(), request.getTransactionAmount());

        return sanctionsScreeningService.screenTransactionParties(request)
            .thenApply(result -> {
                if (result.getMatchFound()) {
                    log.warn("COMPLIANCE ALERT: Transaction sanctions match - TransactionId: {}, RiskLevel: {}",
                        request.getTransactionId(), result.getRiskLevel());
                }
                return ResponseEntity.ok(result);
            })
            .exceptionally(ex -> {
                log.error("COMPLIANCE ERROR: Failed to screen transaction - TransactionId: {}",
                    request.getTransactionId(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    /**
     * Manual resolution for sanctions check matches.
     *
     * Compliance officers can manually review and resolve sanctions matches as:
     * - CLEARED: False positive, entity is not sanctioned
     * - BLOCKED: True positive, block all transactions
     * - ESCALATED: Requires senior review or legal consultation
     *
     * @param checkId Sanctions check record ID
     * @param resolution Resolution decision
     * @param reviewNotes Detailed notes explaining the resolution
     * @param reviewedBy User ID of compliance officer
     * @return Updated screening result
     */
    @PostMapping("/resolve/{checkId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('SENIOR_COMPLIANCE_OFFICER')")
    public ResponseEntity<SanctionsScreeningResult> resolveCheck(
            @PathVariable UUID checkId,
            @RequestParam @NotNull Resolution resolution,
            @RequestParam String reviewNotes,
            @RequestParam UUID reviewedBy) {

        // Rate limiting check
        if (!manualReviewRateLimiter.tryConsume(1)) {
            log.warn("SECURITY: Rate limit exceeded for manual review - CheckId: {}", checkId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        log.info("COMPLIANCE: Manual resolution requested - CheckId: {}, Resolution: {}, ReviewedBy: {}",
            checkId, resolution, reviewedBy);

        try {
            SanctionsScreeningResult result = sanctionsScreeningService.resolveManualReview(
                checkId, resolution, reviewNotes, reviewedBy);

            log.info("COMPLIANCE: Check resolved - CheckId: {}, Resolution: {}", checkId, resolution);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("COMPLIANCE ERROR: Invalid resolution request - CheckId: {}", checkId, e);
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to resolve check - CheckId: {}", checkId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get sanctions screening history for a specific user.
     *
     * Returns all historical sanctions checks including:
     * - Check timestamps
     * - Match results
     * - Risk assessments
     * - Resolution status
     *
     * @param userId User ID
     * @return List of historical sanctions checks
     */
    @GetMapping("/checks/{userId}")
    @PreAuthorize("hasAuthority('SCOPE_sanctions:read') or hasRole('COMPLIANCE_OFFICER') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<List<SanctionsCheckRecord>> getUserScreeningHistory(@PathVariable UUID userId) {
        log.info("COMPLIANCE: Retrieving screening history - UserId: {}", userId);

        try {
            List<SanctionsCheckRecord> history = sanctionsScreeningService.getScreeningHistory(userId);
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to retrieve screening history - UserId: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all sanctions checks pending manual review.
     *
     * Returns checks with:
     * - MANUAL_REVIEW status
     * - No resolution yet
     * - Ordered by risk level (highest first) and check date
     *
     * @return List of checks pending manual review
     */
    @GetMapping("/pending-review")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('SENIOR_COMPLIANCE_OFFICER')")
    public ResponseEntity<List<SanctionsCheckRecord>> getPendingManualReviews() {
        log.info("COMPLIANCE: Retrieving pending manual reviews");

        try {
            List<SanctionsCheckRecord> pending = sanctionsScreeningService.getPendingManualReviews();
            log.info("COMPLIANCE: Found {} checks pending manual review", pending.size());
            return ResponseEntity.ok(pending);

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to retrieve pending reviews", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get high-risk unresolved sanctions checks.
     *
     * Returns checks with:
     * - Risk level HIGH or CRITICAL
     * - No resolution yet
     * - Active (not deleted)
     *
     * @return List of high-risk unresolved checks
     */
    @GetMapping("/high-risk-unresolved")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('SENIOR_COMPLIANCE_OFFICER')")
    public ResponseEntity<List<SanctionsCheckRecord>> getHighRiskUnresolved() {
        log.info("COMPLIANCE: Retrieving high-risk unresolved checks");

        try {
            List<SanctionsCheckRecord> highRisk = sanctionsScreeningService.getHighRiskUnresolved();
            log.warn("COMPLIANCE ALERT: Found {} high-risk unresolved sanctions checks", highRisk.size());
            return ResponseEntity.ok(highRisk);

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to retrieve high-risk checks", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Check if an entity (user or business) is currently blocked.
     *
     * Quick check for real-time transaction blocking.
     *
     * @param entityId Entity ID (user or business)
     * @return Blocked status
     */
    @GetMapping("/blocked-status/{entityId}")
    @PreAuthorize("hasAuthority('SCOPE_sanctions:read')")
    public ResponseEntity<Map<String, Object>> getBlockedStatus(@PathVariable UUID entityId) {
        log.debug("COMPLIANCE: Checking blocked status - EntityId: {}", entityId);

        try {
            boolean isBlocked = sanctionsScreeningService.isEntityBlocked(entityId);

            if (isBlocked) {
                log.warn("COMPLIANCE ALERT: Entity is blocked - EntityId: {}", entityId);
            }

            return ResponseEntity.ok(Map.of(
                "entityId", entityId,
                "blocked", isBlocked,
                "timestamp", java.time.Instant.now()
            ));

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to check blocked status - EntityId: {}", entityId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get daily sanctions check statistics.
     *
     * Compliance reporting endpoint for daily check metrics:
     * - Total checks performed
     * - Number of matches found
     * - Risk level distribution
     * - Resolution statistics
     *
     * @param date Date for statistics (defaults to today)
     * @return Daily statistics
     */
    @GetMapping("/statistics/daily")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('COMPLIANCE_MANAGER')")
    public ResponseEntity<Map<String, Object>> getDailyStatistics(
            @RequestParam(required = false) java.time.LocalDate date) {

        java.time.LocalDate targetDate = date != null ? date : java.time.LocalDate.now();
        log.info("COMPLIANCE: Retrieving daily statistics - Date: {}", targetDate);

        try {
            Map<String, Object> stats = sanctionsScreeningService.getDailyStatistics(targetDate);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to retrieve daily statistics - Date: {}", targetDate, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get sanctions check statistics by risk level.
     *
     * @param riskLevel Risk level filter
     * @param startDate Start date
     * @param endDate End date
     * @return Statistics for the specified risk level
     */
    @GetMapping("/statistics/risk-level")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('COMPLIANCE_MANAGER')")
    public ResponseEntity<Map<String, Object>> getRiskLevelStatistics(
            @RequestParam SanctionsCheckRecord.RiskLevel riskLevel,
            @RequestParam java.time.LocalDate startDate,
            @RequestParam java.time.LocalDate endDate) {

        log.info("COMPLIANCE: Retrieving risk level statistics - Level: {}, Period: {} to {}",
            riskLevel, startDate, endDate);

        try {
            Map<String, Object> stats = sanctionsScreeningService.getRiskLevelStatistics(
                riskLevel, startDate, endDate);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to retrieve risk level statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Force refresh of all sanctions lists.
     *
     * Triggers immediate refresh of:
     * - OFAC SDN List
     * - EU Sanctions List
     * - UN Sanctions List
     *
     * @return Refresh status
     */
    @PostMapping("/admin/refresh-lists")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_MANAGER')")
    public ResponseEntity<Map<String, Object>> forceRefreshSanctionsLists() {
        log.info("COMPLIANCE ADMIN: Force refresh of sanctions lists requested");

        try {
            sanctionsListCacheService.forceRefresh();

            String currentVersion = sanctionsListCacheService.getCurrentListVersion();
            java.time.LocalDateTime lastUpdate = sanctionsListCacheService.getLastUpdateTime();

            log.info("COMPLIANCE: Sanctions lists refreshed - Version: {}", currentVersion);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Sanctions lists refreshed successfully",
                "version", currentVersion,
                "lastUpdate", lastUpdate
            ));

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to refresh sanctions lists", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to refresh sanctions lists: " + e.getMessage()
                ));
        }
    }

    /**
     * Get sanctions list cache statistics.
     *
     * Returns:
     * - Current list version
     * - Last update timestamp
     * - Number of entries per list (OFAC, EU, UN)
     *
     * @return Cache statistics
     */
    @GetMapping("/admin/cache-stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<SanctionsListCacheService.SanctionsListCacheStats> getCacheStats() {
        log.info("COMPLIANCE ADMIN: Retrieving sanctions list cache statistics");

        try {
            SanctionsListCacheService.SanctionsListCacheStats stats =
                sanctionsListCacheService.getCacheStats();
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to retrieve cache statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint for sanctions screening service.
     *
     * Verifies:
     * - Sanctions lists are loaded
     * - Lists are recent (updated within last 24 hours)
     * - Service is operational
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            SanctionsListCacheService.SanctionsListCacheStats stats =
                sanctionsListCacheService.getCacheStats();

            boolean isHealthy = stats.getOfacListSize() > 0
                && stats.getEuListSize() > 0
                && stats.getUnListSize() > 0
                && stats.getLastUpdateTime().isAfter(
                    java.time.LocalDateTime.now().minusHours(24));

            HttpStatus status = isHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

            return ResponseEntity.status(status)
                .body(Map.of(
                    "status", isHealthy ? "healthy" : "unhealthy",
                    "ofacListSize", stats.getOfacListSize(),
                    "euListSize", stats.getEuListSize(),
                    "unListSize", stats.getUnListSize(),
                    "lastUpdate", stats.getLastUpdateTime(),
                    "version", stats.getCurrentVersion()
                ));

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Health check failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "status", "unhealthy",
                    "error", e.getMessage()
                ));
        }
    }

    /**
     * Mask name for logging (GDPR/privacy compliance).
     *
     * Shows first 2 and last 2 characters only.
     *
     * @param name Full name
     * @return Masked name
     */
    private String maskName(String name) {
        if (name == null || name.length() <= 4) {
            return "****";
        }
        return name.substring(0, 2) + "****" + name.substring(name.length() - 2);
    }
}
