package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.InternationalKycModels.*;
import com.waqiti.kyc.integration.DowJonesRiskComplianceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sanctions Screening Service
 * 
 * Performs sanctions screening against various watchlists including OFAC, EU, UN, etc.
 * 
 * @author Waqiti Compliance Team
 * @version 3.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionsScreeningService {

    private final DowJonesRiskComplianceClient dowJonesClient;

    /**
     * P0-002 CRITICAL FIX: Perform basic sanctions screening using real Dow Jones API
     *
     * BEFORE: Stub implementation always returning isHit=false ❌
     * AFTER: Real Dow Jones Risk & Compliance API screening ✅
     */
    public SanctionsResult screenBasic(String name, LocalDate dateOfBirth, String nationality) {
        log.info("Performing Dow Jones sanctions screening for: {}", name);

        try {
            // Use real Dow Jones API for sanctions screening
            DowJonesRiskComplianceClient.DowJonesScreeningResponse response =
                dowJonesClient.screenPerson(name, dateOfBirth, nationality);

            if (response.isSuccess()) {
                // Map Dow Jones response to internal format
                String matchDetails = response.isHit()
                    ? formatMatchDetails(response.getMatchedEntities())
                    : "Clear - No matches found";

                log.info("Dow Jones screening completed - name: {}, hit: {}, score: {}",
                    name, response.isHit(), response.getRiskScore());

                return SanctionsResult.builder()
                    .hit(response.isHit())
                    .score(response.getRiskScore())
                    .matchDetails(matchDetails)
                    .matchedLists(response.getMatchedLists())
                    .build();
            } else {
                log.warn("Dow Jones API failed: {} - returning safe default", response.getErrorMessage());
                // Conservative approach: flag for manual review when API fails
                return createFallbackResult(name, true);
            }

        } catch (Exception e) {
            log.error("Exception during Dow Jones screening for: {}", name, e);
            // Conservative approach: flag for manual review on exception
            return createFallbackResult(name, true);
        }
    }

    /**
     * P0-002 CRITICAL FIX: Perform enhanced sanctions screening with real Dow Jones API
     *
     * BEFORE: Stub implementation always returning isHit=false ❌
     * AFTER: Real Dow Jones enhanced screening with addresses and associates ✅
     */
    public EnhancedSanctionsResult performEnhancedScreening(
            String name, List<Address> addresses, List<String> associates) {

        log.info("Performing enhanced Dow Jones sanctions screening for: {}", name);

        try {
            // Convert addresses to string list for Dow Jones API
            List<String> addressStrings = addresses != null
                ? addresses.stream()
                    .map(addr -> String.format("%s, %s, %s",
                        addr.getCity(), addr.getState(), addr.getCountry()))
                    .collect(Collectors.toList())
                : List.of();

            // Use real Dow Jones API for enhanced screening
            DowJonesRiskComplianceClient.DowJonesScreeningResponse response =
                dowJonesClient.screenEnhanced(name, addressStrings, associates);

            if (response.isSuccess()) {
                log.info("Dow Jones enhanced screening completed - name: {}, hit: {}, score: {}, matches: {}",
                    name, response.isHit(), response.getRiskScore(), response.getMatchedEntities().size());

                return EnhancedSanctionsResult.builder()
                    .hit(response.isHit())
                    .riskScore(response.getRiskScore())
                    .matchedEntities(mapMatchedEntities(response.getMatchedEntities()))
                    .sanctions(extractSanctions(response.getMatchedLists()))
                    .build();
            } else {
                log.warn("Dow Jones enhanced screening failed: {} - flagging for review", response.getErrorMessage());
                return createEnhancedFallbackResult(name, true);
            }

        } catch (Exception e) {
            log.error("Exception during enhanced Dow Jones screening for: {}", name, e);
            return createEnhancedFallbackResult(name, true);
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private String formatMatchDetails(List<DowJonesRiskComplianceClient.MatchedEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return "No matches found";
        }

        return entities.stream()
            .map(entity -> String.format("%s (Score: %.2f, List: %s)",
                entity.getName(), entity.getMatchScore(), entity.getWatchlist()))
            .collect(Collectors.joining("; "));
    }

    private List<MatchedEntity> mapMatchedEntities(List<DowJonesRiskComplianceClient.MatchedEntity> djEntities) {
        return djEntities.stream()
            .map(entity -> MatchedEntity.builder()
                .name(entity.getName())
                .matchScore(entity.getMatchScore())
                .listName(entity.getWatchlist())
                .reasons(entity.getReasons())
                .build())
            .collect(Collectors.toList());
    }

    private List<String> extractSanctions(List<String> matchedLists) {
        return matchedLists != null ? matchedLists : List.of();
    }

    private SanctionsResult createFallbackResult(String name, boolean requiresReview) {
        return SanctionsResult.builder()
            .hit(requiresReview)
            .score(requiresReview ? 0.5 : 0.0)
            .matchDetails(requiresReview
                ? "API unavailable - requires manual review"
                : "Clear - No matches found")
            .matchedLists(List.of())
            .build();
    }

    private EnhancedSanctionsResult createEnhancedFallbackResult(String name, boolean requiresReview) {
        return EnhancedSanctionsResult.builder()
            .hit(requiresReview)
            .riskScore(requiresReview ? 0.5 : 0.0)
            .matchedEntities(List.of())
            .sanctions(List.of())
            .build();
    }
}