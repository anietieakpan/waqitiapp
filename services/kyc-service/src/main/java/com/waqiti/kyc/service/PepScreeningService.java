package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.InternationalKycModels.*;
import com.waqiti.kyc.integration.pep.DowJonesPepScreeningClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Politically Exposed Person (PEP) Screening Service
 *
 * Production-grade service that screens customers against global PEP databases
 * using Dow Jones Risk & Compliance API.
 *
 * Features:
 * - Real-time PEP screening against global watchlists
 * - Sanctions list checking (OFAC, UN, EU, UK)
 * - Adverse media monitoring
 * - Family members and close associates (RCA) detection
 * - Caching for performance optimization
 *
 * @author Waqiti Compliance Team
 * @version 4.0.0-PRODUCTION
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PepScreeningService {

    private final DowJonesPepScreeningClient dowJonesClient;

    /**
     * Perform basic PEP screening (lightweight, cached)
     *
     * Uses cached results for 24 hours to optimize performance for repeated checks.
     *
     * @param name Full legal name
     * @param dateOfBirth Date of birth for matching accuracy
     * @param nationality Country code (ISO 3166-1 alpha-2)
     * @return PepResult with match details
     */
    @Cacheable(value = "pep-basic-screening", key = "#name + '_' + #dateOfBirth + '_' + #nationality",
               unless = "#result == null || #result.hit == true")
    public PepResult screenBasic(String name, LocalDate dateOfBirth, String nationality) {
        log.info("COMPLIANCE: Initiating basic PEP screening for: {} (DOB: {}, Nationality: {})",
            name, dateOfBirth, nationality);

        try {
            // Delegate to Dow Jones client for real screening
            PepResult result = dowJonesClient.screenBasic(name, dateOfBirth, nationality);

            log.info("COMPLIANCE: Basic PEP screening completed - Hit: {}, Score: {}",
                result.isHit(), result.getScore());

            return result;

        } catch (Exception e) {
            log.error("COMPLIANCE: Basic PEP screening failed for: {}", name, e);

            // Fail-closed: Return potential hit requiring manual review
            return PepResult.builder()
                .hit(true)
                .score(50.0)
                .matchDetails("PEP screening service error - manual review required: " + e.getMessage())
                .category(null)
                .build();
        }
    }

    /**
     * Perform detailed PEP screening with comprehensive risk assessment
     *
     * This method:
     * 1. Screens against global PEP databases (Dow Jones Watchlist)
     * 2. Checks sanctions lists (OFAC, UN, EU, UK)
     * 3. Screens family members and close associates
     * 4. Checks adverse media
     * 5. Calculates composite risk score
     *
     * Results are NOT cached due to dynamic risk nature.
     *
     * @param name Full legal name
     * @param dateOfBirth Date of birth for matching accuracy
     * @param nationality Country code (ISO 3166-1 alpha-2)
     * @return CompletableFuture<PepScreeningResult> with detailed risk assessment
     */
    public CompletableFuture<PepScreeningResult> screenPoliticallyExposedPerson(
            String name, LocalDate dateOfBirth, String nationality) {

        log.info("COMPLIANCE: Initiating comprehensive PEP screening for: {} (DOB: {}, Nationality: {})",
            name, dateOfBirth, nationality);

        try {
            // Asynchronous comprehensive screening via Dow Jones
            CompletableFuture<PepScreeningResult> screeningFuture =
                dowJonesClient.screenPoliticallyExposedPerson(name, dateOfBirth, nationality);

            // Add timeout protection (30 seconds)
            return screeningFuture
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.error("COMPLIANCE: Comprehensive PEP screening failed or timed out for: {}", name, ex);

                    // Fail-closed: Return UNKNOWN status requiring manual review
                    return PepScreeningResult.builder()
                        .status(PepStatus.UNKNOWN)
                        .matchDetails("PEP screening timeout or error - manual review required: " + ex.getMessage())
                        .category(null)
                        .riskScore(50.0)
                        .build();
                })
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        log.info("COMPLIANCE: Comprehensive PEP screening completed - Status: {}, Risk Score: {}",
                            result.getStatus(), result.getRiskScore());

                        // Log high-risk findings for immediate review
                        if (result.getRiskScore() >= 70.0 || result.getStatus() == PepStatus.PEP_DIRECT) {
                            log.warn("COMPLIANCE ALERT: High-risk PEP detected - Name: {}, Status: {}, Score: {}, Details: {}",
                                name, result.getStatus(), result.getRiskScore(), result.getMatchDetails());
                        }
                    }
                });

        } catch (Exception e) {
            log.error("COMPLIANCE: PEP screening initiation failed for: {}", name, e);

            // Return immediately with safe default
            return CompletableFuture.completedFuture(
                PepScreeningResult.builder()
                    .status(PepStatus.UNKNOWN)
                    .matchDetails("PEP screening service unavailable - manual review required")
                    .category(null)
                    .riskScore(50.0)
                    .build()
            );
        }
    }

    /**
     * Synchronous version of comprehensive PEP screening
     *
     * Use this for blocking workflows that require immediate results.
     *
     * @param name Full legal name
     * @param dateOfBirth Date of birth
     * @param nationality Country code
     * @return PepScreeningResult with detailed risk assessment
     */
    public PepScreeningResult screenPoliticallyExposedPersonSync(
            String name, LocalDate dateOfBirth, String nationality) {

        try {
            CompletableFuture<PepScreeningResult> future =
                screenPoliticallyExposedPerson(name, dateOfBirth, nationality);

            // Block and wait for result (max 30 seconds)
            return future.get(30, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("COMPLIANCE: Synchronous PEP screening failed for: {}", name, e);

            return PepScreeningResult.builder()
                .status(PepStatus.UNKNOWN)
                .matchDetails("PEP screening failed - manual review required: " + e.getMessage())
                .category(null)
                .riskScore(50.0)
                .build();
        }
    }
}