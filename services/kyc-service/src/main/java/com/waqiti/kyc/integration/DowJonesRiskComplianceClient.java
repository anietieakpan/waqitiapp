package com.waqiti.kyc.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

/**
 * P0-002 CRITICAL FIX: Real Dow Jones Risk & Compliance API Integration
 *
 * Replaces stub sanctions screening with production-grade Dow Jones API.
 *
 * Dow Jones Risk & Compliance provides:
 * - Real-time sanctions screening (OFAC, EU, UN, etc.)
 * - PEP (Politically Exposed Persons) screening
 * - Adverse media screening
 * - Global watchlist coverage (200+ countries)
 * - Fuzzy name matching
 * - Continuous monitoring
 *
 * API Documentation: https://developer.dowjones.com/factiva-risk-compliance-api/
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-10-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DowJonesRiskComplianceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kyc.dowjones.api-key}")
    private String apiKey;

    @Value("${kyc.dowjones.api-url:https://api.dowjones.com/risk}")
    private String apiUrl;

    @Value("${kyc.dowjones.customer-id}")
    private String customerId;

    @Value("${kyc.dowjones.timeout-ms:10000}")
    private int timeoutMs;

    /**
     * Screen entity against Dow Jones sanctions and watchlists
     */
    public DowJonesScreeningResponse screenEntity(DowJonesScreeningRequest request) {
        try {
            log.debug("Screening entity with Dow Jones: {}", request.getName());

            // Build screening request payload
            Map<String, Object> payload = buildScreeningPayload(request);

            // Send to Dow Jones Risk API
            String url = apiUrl + "/entities/v1/screen";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", apiKey);
            headers.set("X-Customer-Id", customerId);

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, httpRequest, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> body = response.getBody();
                return parseScreeningResponse(body, request);
            } else {
                log.error("Dow Jones API returned non-200 status: {}", response.getStatusCode());
                return createFallbackResponse(request, "API error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to screen entity with Dow Jones: {}", request.getName(), e);
            return createFallbackResponse(request, "Exception: " + e.getMessage());
        }
    }

    /**
     * Screen individual person (basic screening)
     */
    public DowJonesScreeningResponse screenPerson(String name, LocalDate dateOfBirth, String nationality) {
        DowJonesScreeningRequest request = DowJonesScreeningRequest.builder()
            .name(name)
            .dateOfBirth(dateOfBirth)
            .nationality(nationality)
            .entityType("PERSON")
            .build();

        return screenEntity(request);
    }

    /**
     * Enhanced screening with addresses and associates
     */
    public DowJonesScreeningResponse screenEnhanced(String name, List<String> addresses, List<String> associates) {
        DowJonesScreeningRequest request = DowJonesScreeningRequest.builder()
            .name(name)
            .addresses(addresses)
            .associates(associates)
            .entityType("PERSON")
            .screeningType("ENHANCED")
            .build();

        return screenEntity(request);
    }

    /**
     * Monitor entity for ongoing changes (continuous monitoring)
     */
    public void monitorEntity(String entityId, String name) {
        try {
            Map<String, Object> monitorRequest = new HashMap<>();
            monitorRequest.put("entity_id", entityId);
            monitorRequest.put("name", name);
            monitorRequest.put("monitoring_type", "CONTINUOUS");
            monitorRequest.put("alert_on_change", true);

            String url = apiUrl + "/entities/v1/monitor";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", apiKey);
            headers.set("X-Customer-Id", customerId);

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(monitorRequest, headers);
            restTemplate.postForEntity(url, httpRequest, Map.class);

            log.info("Enabled continuous monitoring for entity: {} ({})", name, entityId);

        } catch (Exception e) {
            log.error("Failed to enable monitoring for entity: {}", entityId, e);
        }
    }

    /**
     * Get detailed profile from screening hit
     */
    public DowJonesProfileResponse getEntityProfile(String entityId) {
        try {
            String url = apiUrl + "/entities/v1/" + entityId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", apiKey);
            headers.set("X-Customer-Id", customerId);

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseProfileResponse(response.getBody());
            }

            return DowJonesProfileResponse.builder()
                .success(false)
                .errorMessage("Failed to retrieve profile")
                .build();

        } catch (Exception e) {
            log.error("Failed to get entity profile: {}", entityId, e);
            return DowJonesProfileResponse.builder()
                .success(false)
                .errorMessage("Exception: " + e.getMessage())
                .build();
        }
    }

    // ========================================
    // PRIVATE HELPER METHODS
    // ========================================

    private Map<String, Object> buildScreeningPayload(DowJonesScreeningRequest request) {
        Map<String, Object> payload = new HashMap<>();

        // Entity details
        payload.put("entity_type", request.getEntityType());
        payload.put("name", request.getName());

        // Optional fields
        if (request.getDateOfBirth() != null) {
            payload.put("date_of_birth", request.getDateOfBirth().toString());
        }

        if (request.getNationality() != null) {
            payload.put("nationality", request.getNationality());
        }

        if (request.getAddresses() != null && !request.getAddresses().isEmpty()) {
            payload.put("addresses", request.getAddresses());
        }

        if (request.getAssociates() != null && !request.getAssociates().isEmpty()) {
            payload.put("associates", request.getAssociates());
        }

        // Screening configuration
        Map<String, Object> screeningConfig = new HashMap<>();
        screeningConfig.put("sanctions", true);
        screeningConfig.put("pep", true);
        screeningConfig.put("adverse_media", true);
        screeningConfig.put("fuzzy_matching", true);
        screeningConfig.put("match_threshold", 0.85); // 85% similarity threshold

        payload.put("screening_config", screeningConfig);

        // Include watchlists
        payload.put("watchlists", Arrays.asList(
            "OFAC_SDN",           // US OFAC Specially Designated Nationals
            "EU_SANCTIONS",       // EU Sanctions
            "UN_SANCTIONS",       // UN Consolidated List
            "UK_HMT",             // UK HM Treasury
            "DFAT_AUSTRALIA",     // Australian Sanctions
            "CANADA_OSFI",        // Canadian Sanctions
            "PEP_WORLD",          // Global PEP Database
            "ADVERSE_MEDIA"       // Negative news screening
        ));

        return payload;
    }

    private DowJonesScreeningResponse parseScreeningResponse(Map<String, Object> body, DowJonesScreeningRequest request) {
        try {
            // Parse Dow Jones response structure
            Map<String, Object> result = (Map<String, Object>) body.get("result");
            List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");

            boolean isHit = matches != null && !matches.isEmpty();
            double riskScore = isHit ? calculateRiskScore(matches) : 0.0;

            List<MatchedEntity> matchedEntities = new ArrayList<>();
            List<String> matchedLists = new ArrayList<>();

            if (matches != null) {
                for (Map<String, Object> match : matches) {
                    String entityId = (String) match.get("entity_id");
                    String matchName = (String) match.get("name");
                    Number matchScore = (Number) match.get("match_score");
                    String watchlist = (String) match.get("watchlist");
                    List<String> reasons = (List<String>) match.get("reasons");

                    matchedEntities.add(MatchedEntity.builder()
                        .entityId(entityId)
                        .name(matchName)
                        .matchScore(matchScore.doubleValue())
                        .watchlist(watchlist)
                        .reasons(reasons != null ? reasons : List.of())
                        .build());

                    if (watchlist != null && !matchedLists.contains(watchlist)) {
                        matchedLists.add(watchlist);
                    }
                }
            }

            String screeningId = (String) result.get("screening_id");

            log.info("Dow Jones screening completed - name: {}, hit: {}, score: {}, matches: {}",
                request.getName(), isHit, riskScore, matchedEntities.size());

            return DowJonesScreeningResponse.builder()
                .hit(isHit)
                .riskScore(riskScore)
                .screeningId(screeningId)
                .matchedEntities(matchedEntities)
                .matchedLists(matchedLists)
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Failed to parse Dow Jones response", e);
            return createFallbackResponse(request, "Parse error: " + e.getMessage());
        }
    }

    private DowJonesProfileResponse parseProfileResponse(Map<String, Object> body) {
        try {
            Map<String, Object> profile = (Map<String, Object>) body.get("profile");

            return DowJonesProfileResponse.builder()
                .entityId((String) profile.get("entity_id"))
                .name((String) profile.get("name"))
                .sanctionsDetails((Map<String, Object>) profile.get("sanctions"))
                .pepDetails((Map<String, Object>) profile.get("pep_info"))
                .adverseMedia((List<Map<String, Object>>) profile.get("adverse_media"))
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Failed to parse profile response", e);
            return DowJonesProfileResponse.builder()
                .success(false)
                .errorMessage("Parse error: " + e.getMessage())
                .build();
        }
    }

    private double calculateRiskScore(List<Map<String, Object>> matches) {
        if (matches == null || matches.isEmpty()) {
            return 0.0;
        }

        // Calculate risk based on highest match score and watchlist severity
        double maxScore = 0.0;

        for (Map<String, Object> match : matches) {
            Number matchScore = (Number) match.get("match_score");
            String watchlist = (String) match.get("watchlist");

            double score = matchScore != null ? matchScore.doubleValue() : 0.0;

            // Apply severity multiplier based on watchlist type
            if (watchlist != null) {
                if (watchlist.contains("OFAC") || watchlist.contains("UN_SANCTIONS")) {
                    score *= 1.5; // Critical watchlists
                } else if (watchlist.contains("PEP")) {
                    score *= 1.2; // High risk
                }
            }

            maxScore = Math.max(maxScore, score);
        }

        return Math.min(1.0, maxScore); // Cap at 1.0
    }

    private DowJonesScreeningResponse createFallbackResponse(DowJonesScreeningRequest request, String reason) {
        log.warn("Creating fallback screening response for {}: {}", request.getName(), reason);

        return DowJonesScreeningResponse.builder()
            .hit(false)
            .riskScore(0.0)
            .matchedEntities(List.of())
            .matchedLists(List.of())
            .success(false)
            .errorMessage(reason)
            .build();
    }

    // ========================================
    // DATA CLASSES
    // ========================================

    @Data
    @Builder
    public static class DowJonesScreeningRequest {
        private String name;
        private LocalDate dateOfBirth;
        private String nationality;
        private String entityType;
        private String screeningType;
        private List<String> addresses;
        private List<String> associates;
    }

    @Data
    @Builder
    public static class DowJonesScreeningResponse {
        private boolean hit;
        private double riskScore;
        private String screeningId;
        private List<MatchedEntity> matchedEntities;
        private List<String> matchedLists;
        private boolean success;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class MatchedEntity {
        private String entityId;
        private String name;
        private double matchScore;
        private String watchlist;
        private List<String> reasons;
    }

    @Data
    @Builder
    public static class DowJonesProfileResponse {
        private String entityId;
        private String name;
        private Map<String, Object> sanctionsDetails;
        private Map<String, Object> pepDetails;
        private List<Map<String, Object>> adverseMedia;
        private boolean success;
        private String errorMessage;
    }
}
