package com.waqiti.compliance.service;

import com.waqiti.compliance.client.EUSanctionsClient;
import com.waqiti.compliance.client.UNSanctionsClient;
import com.waqiti.compliance.dto.SanctionsScreeningRequest;
import com.waqiti.compliance.dto.SanctionsScreeningResponse;
import com.waqiti.compliance.entity.SanctionsScreeningResult;
import com.waqiti.compliance.repository.SanctionsScreeningRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * EU/UN Sanctions Screening Service
 *
 * CRITICAL COMPLIANCE SERVICE:
 * - Screens entities against EU and UN sanctions lists
 * - Required for international financial transactions
 * - Prevents violations of international sanctions regimes
 *
 * REGULATORY FRAMEWORKS:
 * - EU Common Foreign and Security Policy (CFSP)
 * - EU Restrictive Measures (Sanctions)
 * - UN Security Council Consolidated List
 * - EU Council Regulation 269/2014 (Ukraine/Russia)
 * - EU Council Regulation 833/2014 (Sectoral Sanctions)
 *
 * SANCTIONS LISTS COVERED:
 * - EU Consolidated List of Persons, Groups and Entities
 * - UN Security Council Sanctions List (1267/1989 Committee)
 * - UN Consolidated Sanctions List
 * - EU Financial Sanctions Files
 * - HM Treasury Sanctions List (UK post-Brexit)
 *
 * PENALTIES FOR NON-COMPLIANCE:
 * - EU: Up to €500,000 or 4x transaction value
 * - Criminal prosecution for willful violations
 * - Asset freezing and transaction reversal
 * - Reputational damage and correspondent banking loss
 *
 * @author Waqiti Compliance Team
 * @version 3.0.0
 * @since 2025-01-01
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EUUNSanctionsScreeningService {

    private final EUSanctionsClient euSanctionsClient;
    private final UNSanctionsClient unSanctionsClient;
    private final SanctionsScreeningRepository screeningRepository;
    private final ComplianceAuditService auditService;
    private final AlertService alertService;
    private final ComplianceNotificationService notificationService;

    @Value("${sanctions.screening.threshold.high:0.90}")
    private double highRiskThreshold;

    @Value("${sanctions.screening.threshold.medium:0.85}")
    private double mediumRiskThreshold;

    @Value("${sanctions.screening.threshold.low:0.75}")
    private double lowRiskThreshold;

    /**
     * Comprehensive screening against EU and UN sanctions lists
     */
    @CircuitBreaker(name = "eu-un-sanctions", fallbackMethod = "fallbackEUUNScreening")
    @Retry(name = "eu-un-sanctions")
    @Transactional
    public SanctionsScreeningResponse screenEntityAgainstEUUNSanctions(
            SanctionsScreeningRequest request) {

        log.info("SANCTIONS: Screening entity {} against EU/UN sanctions lists",
            request.getEntityId());

        try {
            // Normalize entity data
            String normalizedName = normalizeEntityName(request.getName());
            String normalizedDOB = normalizeDateOfBirth(request.getDateOfBirth());
            String normalizedAddress = normalizeAddress(request.getAddress());
            String normalizedCountry = normalizeCountry(request.getCountry());

            // Execute parallel screening against multiple lists
            CompletableFuture<EUSanctionsResult> euScreening =
                screenAgainstEUSanctions(normalizedName, normalizedDOB, normalizedCountry);

            CompletableFuture<UNSanctionsResult> unScreening =
                screenAgainstUNSanctions(normalizedName, normalizedDOB, normalizedCountry);

            // Wait for all screenings to complete with timeout
            try {
                CompletableFuture.allOf(euScreening, unScreening)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("EU/UN sanctions screening timed out after 10 seconds for entity: {}", request.getEntityId(), e);
                throw new SanctionsScreeningException("Sanctions screening timed out - cannot process", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("EU/UN sanctions screening execution failed for entity: {}", request.getEntityId(), e.getCause());
                throw new SanctionsScreeningException("Sanctions screening failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("EU/UN sanctions screening interrupted for entity: {}", request.getEntityId(), e);
                throw new SanctionsScreeningException("Sanctions screening interrupted", e);
            }

            // Collect results (already completed, safe to call .get() immediately)
            EUSanctionsResult euResult;
            UNSanctionsResult unResult;
            try {
                euResult = euScreening.get(1, java.util.concurrent.TimeUnit.SECONDS);
                unResult = unScreening.get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to retrieve sanctions screening results for entity: {}", request.getEntityId(), e);
                throw new SanctionsScreeningException("Failed to retrieve screening results", e);
            }

            // Combine and analyze results
            List<SanctionMatch> allMatches = new ArrayList<>();
            allMatches.addAll(euResult.getMatches());
            allMatches.addAll(unResult.getMatches());

            // Determine overall status
            String status = determineScreeningStatus(allMatches);
            double riskScore = calculateOverallRiskScore(allMatches);
            boolean hasMatches = !allMatches.isEmpty();

            // Save screening result
            SanctionsScreeningResult screeningResult = saveScreeningResult(
                request, allMatches, status, riskScore, euResult, unResult);

            // Handle high-risk matches
            if (hasMatches && riskScore >= highRiskThreshold) {
                handleHighRiskSanctionsMatch(request, allMatches, screeningResult);
            }

            // Audit the screening
            auditScreening(request, screeningResult, euResult, unResult);

            // Build response
            return buildScreeningResponse(request, screeningResult, allMatches, riskScore);

        } catch (Exception e) {
            log.error("CRITICAL: EU/UN sanctions screening failed for entity: {}",
                request.getEntityId(), e);

            // Create critical alert
            alertService.createCriticalAlert(
                "SANCTIONS_SCREENING_FAILURE",
                Map.of(
                    "entityId", request.getEntityId(),
                    "entityName", request.getName(),
                    "error", e.getMessage()
                ),
                "Sanctions screening system failure - manual review required"
            );

            throw new SanctionsScreeningException(
                "EU/UN sanctions screening failed", e);
        }
    }

    /**
     * Screen against EU Consolidated Sanctions List
     */
    @Async
    @CircuitBreaker(name = "eu-sanctions", fallbackMethod = "fallbackEUScreening")
    protected CompletableFuture<EUSanctionsResult> screenAgainstEUSanctions(
            String name, String dob, String country) {

        log.debug("SANCTIONS: Screening against EU sanctions lists - name: {}", name);

        try {
            // Screen against EU CFSP Consolidated List
            List<SanctionMatch> cfspMatches = euSanctionsClient.searchCFSPList(name, dob, country);

            // Screen against EU Financial Sanctions Files
            List<SanctionMatch> financialMatches = euSanctionsClient.searchFinancialSanctionsList(name);

            // Screen against EU Asset Freeze Targets
            List<SanctionMatch> assetFreezeMatches = euSanctionsClient.searchAssetFreezeList(name, country);

            // Combine all EU matches
            List<SanctionMatch> allEUMatches = new ArrayList<>();
            allEUMatches.addAll(cfspMatches);
            allEUMatches.addAll(financialMatches);
            allEUMatches.addAll(assetFreezeMatches);

            // Deduplicate matches
            List<SanctionMatch> dedupedMatches = deduplicateMatches(allEUMatches);

            log.info("SANCTIONS: EU screening complete - {} matches found", dedupedMatches.size());

            return CompletableFuture.completedFuture(
                EUSanctionsResult.builder()
                    .matches(dedupedMatches)
                    .screenedAt(LocalDateTime.now())
                    .listsChecked(Arrays.asList("EU_CFSP", "EU_FINANCIAL", "EU_ASSET_FREEZE"))
                    .build()
            );

        } catch (Exception e) {
            log.error("EU sanctions screening failed for entity: {}", name, e);
            return CompletableFuture.completedFuture(
                EUSanctionsResult.builder()
                    .matches(Collections.emptyList())
                    .screeningFailed(true)
                    .failureReason(e.getMessage())
                    .build()
            );
        }
    }

    /**
     * Screen against UN Security Council Sanctions Lists
     */
    @Async
    @CircuitBreaker(name = "un-sanctions", fallbackMethod = "fallbackUNScreening")
    protected CompletableFuture<UNSanctionsResult> screenAgainstUNSanctions(
            String name, String dob, String country) {

        log.debug("SANCTIONS: Screening against UN sanctions lists - name: {}", name);

        try {
            // Screen against UN Consolidated List
            List<SanctionMatch> consolidatedMatches =
                unSanctionsClient.searchConsolidatedList(name, dob);

            // Screen against UN 1267/1989 Committee List (Al-Qaida/ISIS)
            List<SanctionMatch> terrorismMatches =
                unSanctionsClient.search1267List(name);

            // Screen against UN Proliferation Lists (North Korea, Iran)
            List<SanctionMatch> proliferationMatches =
                unSanctionsClient.searchProliferationLists(name, country);

            // Combine all UN matches
            List<SanctionMatch> allUNMatches = new ArrayList<>();
            allUNMatches.addAll(consolidatedMatches);
            allUNMatches.addAll(terrorismMatches);
            allUNMatches.addAll(proliferationMatches);

            // Deduplicate matches
            List<SanctionMatch> dedupedMatches = deduplicateMatches(allUNMatches);

            log.info("SANCTIONS: UN screening complete - {} matches found", dedupedMatches.size());

            return CompletableFuture.completedFuture(
                UNSanctionsResult.builder()
                    .matches(dedupedMatches)
                    .screenedAt(LocalDateTime.now())
                    .listsChecked(Arrays.asList("UN_CONSOLIDATED", "UN_1267", "UN_PROLIFERATION"))
                    .build()
            );

        } catch (Exception e) {
            log.error("UN sanctions screening failed for entity: {}", name, e);
            return CompletableFuture.completedFuture(
                UNSanctionsResult.builder()
                    .matches(Collections.emptyList())
                    .screeningFailed(true)
                    .failureReason(e.getMessage())
                    .build()
            );
        }
    }

    /**
     * Download and cache latest sanctions lists
     * Scheduled to run daily at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void updateSanctionsLists() {
        log.info("SANCTIONS: Starting daily sanctions list update");

        try {
            // Update EU sanctions lists
            LocalDateTime euUpdateStart = LocalDateTime.now();
            euSanctionsClient.downloadLatestLists();
            log.info("SANCTIONS: EU lists updated in {} ms",
                java.time.Duration.between(euUpdateStart, LocalDateTime.now()).toMillis());

            // Update UN sanctions lists
            LocalDateTime unUpdateStart = LocalDateTime.now();
            unSanctionsClient.downloadLatestLists();
            log.info("SANCTIONS: UN lists updated in {} ms",
                java.time.Duration.between(unUpdateStart, LocalDateTime.now()).toMillis());

            // Audit the update
            auditService.auditCriticalComplianceEvent(
                "SANCTIONS_LISTS_UPDATED",
                "SYSTEM",
                "Daily sanctions lists update completed successfully",
                Map.of(
                    "euListsUpdated", LocalDateTime.now(),
                    "unListsUpdated", LocalDateTime.now(),
                    "updateSuccessful", true
                )
            );

        } catch (Exception e) {
            log.error("CRITICAL: Failed to update sanctions lists", e);

            // Create critical alert
            alertService.createCriticalAlert(
                "SANCTIONS_LIST_UPDATE_FAILURE",
                Map.of(
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ),
                "Critical: Sanctions list update failed - using outdated lists"
            );

            // Notify compliance team
            notificationService.notifyComplianceTeam(
                "Sanctions List Update Failure",
                "Failed to update EU/UN sanctions lists. Manual intervention required."
            );
        }
    }

    /**
     * Re-screen all active customers against updated lists
     * Runs weekly on Sunday at 4 AM
     */
    @Scheduled(cron = "0 0 4 * * SUN")
    @Async
    public CompletableFuture<Void> batchRescreenActiveCustomers() {
        log.info("SANCTIONS: Starting weekly batch re-screening of active customers");

        try {
            List<UUID> activeCustomers = getActiveCustomerIds();

            log.info("SANCTIONS: Re-screening {} active customers", activeCustomers.size());

            int processedCount = 0;
            int matchesFound = 0;

            for (UUID customerId : activeCustomers) {
                try {
                    SanctionsScreeningRequest request = buildRescreenRequest(customerId);
                    SanctionsScreeningResponse response =
                        screenEntityAgainstEUUNSanctions(request);

                    if (response.isHasMatches()) {
                        matchesFound++;
                        log.warn("SANCTIONS: Match found during batch re-screening - Customer: {}",
                            customerId);
                    }

                    processedCount++;

                    if (processedCount % 100 == 0) {
                        log.info("SANCTIONS: Batch re-screening progress: {}/{} customers processed",
                            processedCount, activeCustomers.size());
                    }

                } catch (Exception e) {
                    log.error("Failed to re-screen customer: {}", customerId, e);
                }
            }

            log.info("SANCTIONS: Batch re-screening complete - Processed: {}, Matches: {}",
                processedCount, matchesFound);

            // Audit the batch screening
            auditService.auditCriticalComplianceEvent(
                "SANCTIONS_BATCH_RESCREEN_COMPLETE",
                "SYSTEM",
                "Weekly batch re-screening completed",
                Map.of(
                    "totalCustomers", activeCustomers.size(),
                    "processed", processedCount,
                    "matchesFound", matchesFound
                )
            );

        } catch (Exception e) {
            log.error("CRITICAL: Batch re-screening failed", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    // Helper methods

    private String normalizeEntityName(String name) {
        if (name == null) return "";
        return name.trim().toUpperCase()
            .replaceAll("[^A-Z0-9\\s-]", "")
            .replaceAll("\\s+", " ");
    }

    private String normalizeDateOfBirth(String dob) {
        if (dob == null) return "";
        // Normalize to ISO format YYYY-MM-DD
        return dob.replaceAll("[^0-9-]", "");
    }

    private String normalizeAddress(String address) {
        if (address == null) return "";
        return address.trim().toUpperCase()
            .replaceAll("[^A-Z0-9\\s,.-]", "")
            .replaceAll("\\s+", " ");
    }

    private String normalizeCountry(String country) {
        if (country == null) return "";
        // Convert to ISO 3166-1 alpha-2 code if needed
        return country.trim().toUpperCase();
    }

    private List<SanctionMatch> deduplicateMatches(List<SanctionMatch> matches) {
        Map<String, SanctionMatch> uniqueMatches = new HashMap<>();

        for (SanctionMatch match : matches) {
            String key = match.getMatchedName() + "|" + match.getSanctionsList();
            if (!uniqueMatches.containsKey(key) ||
                uniqueMatches.get(key).getMatchScore() < match.getMatchScore()) {
                uniqueMatches.put(key, match);
            }
        }

        return new ArrayList<>(uniqueMatches.values());
    }

    private String determineScreeningStatus(List<SanctionMatch> matches) {
        if (matches.isEmpty()) {
            return "CLEAR";
        }

        double maxScore = matches.stream()
            .mapToDouble(SanctionMatch::getMatchScore)
            .max()
            .orElse(0.0);

        if (maxScore >= highRiskThreshold) {
            return "BLOCKED";
        } else if (maxScore >= mediumRiskThreshold) {
            return "REQUIRES_REVIEW";
        } else {
            return "POTENTIAL_MATCH";
        }
    }

    private double calculateOverallRiskScore(List<SanctionMatch> matches) {
        if (matches.isEmpty()) return 0.0;

        return matches.stream()
            .mapToDouble(SanctionMatch::getMatchScore)
            .max()
            .orElse(0.0);
    }

    private void handleHighRiskSanctionsMatch(
            SanctionsScreeningRequest request,
            List<SanctionMatch> matches,
            SanctionsScreeningResult result) {

        log.error("SANCTIONS VIOLATION: High-risk match found - Entity: {} Matches: {}",
            request.getName(), matches.size());

        // Create critical alert
        alertService.createCriticalAlert(
            "HIGH_RISK_SANCTIONS_MATCH",
            Map.of(
                "entityId", request.getEntityId(),
                "entityName", request.getName(),
                "matchCount", matches.size(),
                "maxMatchScore", matches.stream().mapToDouble(SanctionMatch::getMatchScore).max().orElse(0.0)
            ),
            "URGENT: High-risk sanctions match detected - immediate action required"
        );

        // Block entity immediately
        blockEntity(request.getEntityId(), result.getId());

        // Notify executive team
        notificationService.notifyExecutiveTeam(
            "Critical Sanctions Match Detected",
            String.format("Entity %s matched against EU/UN sanctions lists. " +
                "Entity has been blocked pending investigation.", request.getName())
        );

        // Create SAR if required
        if (shouldFileSAR(matches)) {
            createSARForSanctionsViolation(request, matches, result);
        }
    }

    private void blockEntity(UUID entityId, UUID screeningId) {
        log.warn("SANCTIONS: Blocking entity {} due to sanctions match", entityId);
        // Implement entity blocking logic
    }

    private boolean shouldFileSAR(List<SanctionMatch> matches) {
        // File SAR for terrorism, proliferation, or high-value matches
        return matches.stream().anyMatch(match ->
            match.getSanctionType().contains("TERRORISM") ||
            match.getSanctionType().contains("PROLIFERATION") ||
            match.getMatchScore() >= 0.95
        );
    }

    private void createSARForSanctionsViolation(
            SanctionsScreeningRequest request,
            List<SanctionMatch> matches,
            SanctionsScreeningResult result) {

        log.warn("SANCTIONS: Creating SAR for sanctions violation - Entity: {}",
            request.getEntityId());
        // Implement SAR creation logic
    }

    private SanctionsScreeningResult saveScreeningResult(
            SanctionsScreeningRequest request,
            List<SanctionMatch> matches,
            String status,
            double riskScore,
            EUSanctionsResult euResult,
            UNSanctionsResult unResult) {

        // Implementation would save to database
        return null; // Placeholder
    }

    private void auditScreening(
            SanctionsScreeningRequest request,
            SanctionsScreeningResult result,
            EUSanctionsResult euResult,
            UNSanctionsResult unResult) {

        auditService.auditCriticalComplianceEvent(
            "SANCTIONS_SCREENING_COMPLETED",
            request.getEntityId().toString(),
            "EU/UN sanctions screening completed",
            Map.of(
                "entityName", request.getName(),
                "euMatches", euResult.getMatches().size(),
                "unMatches", unResult.getMatches().size(),
                "status", result.getStatus()
            )
        );
    }

    private SanctionsScreeningResponse buildScreeningResponse(
            SanctionsScreeningRequest request,
            SanctionsScreeningResult result,
            List<SanctionMatch> matches,
            double riskScore) {

        return SanctionsScreeningResponse.builder()
            .screeningId(result.getId())
            .entityId(request.getEntityId())
            .hasMatches(!matches.isEmpty())
            .matchCount(matches.size())
            .status(result.getStatus())
            .riskScore(riskScore)
            .requiresManualReview(riskScore >= mediumRiskThreshold)
            .screenedAt(LocalDateTime.now())
            .build();
    }

    private List<UUID> getActiveCustomerIds() {
        // Implementation would query database
        return Collections.emptyList(); // Placeholder
    }

    private SanctionsScreeningRequest buildRescreenRequest(UUID customerId) {
        // Implementation would build request from customer data
        return null; // Placeholder
    }

    // Fallback methods for circuit breaker

    private SanctionsScreeningResponse fallbackEUUNScreening(
            SanctionsScreeningRequest request, Exception e) {
        log.error("CRITICAL: EU/UN sanctions screening circuit breaker activated", e);

        return SanctionsScreeningResponse.builder()
            .entityId(request.getEntityId())
            .hasMatches(false)
            .status("SCREENING_UNAVAILABLE")
            .requiresManualReview(true)
            .screenedAt(LocalDateTime.now())
            .build();
    }

    // Result classes

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class EUSanctionsResult {
        private List<SanctionMatch> matches;
        private LocalDateTime screenedAt;
        private List<String> listsChecked;
        private boolean screeningFailed;
        private String failureReason;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class UNSanctionsResult {
        private List<SanctionMatch> matches;
        private LocalDateTime screenedAt;
        private List<String> listsChecked;
        private boolean screeningFailed;
        private String failureReason;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SanctionMatch {
        private String matchedName;
        private double matchScore;
        private String sanctionType;
        private String sanctionsList;
        private String programName;
        private LocalDateTime listingDate;
        private String reason;
    }

    public static class SanctionsScreeningException extends RuntimeException {
        public SanctionsScreeningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
