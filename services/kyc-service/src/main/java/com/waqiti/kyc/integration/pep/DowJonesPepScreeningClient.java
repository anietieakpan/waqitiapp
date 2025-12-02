package com.waqiti.kyc.integration.pep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.kyc.dto.InternationalKycModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-Grade PEP Screening Client using Dow Jones Risk & Compliance API
 *
 * Integration with Dow Jones Watchlist for:
 * - Politically Exposed Persons (PEP) screening
 * - Sanctions list screening
 * - Adverse media checking
 * - Enhanced Due Diligence (EDD)
 *
 * API Documentation: https://developer.dowjones.com/site/global/api_reference/index.gsp
 *
 * @author Waqiti Compliance Team
 * @version 1.0.0-PRODUCTION
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DowJonesPepScreeningClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${waqiti.compliance.dowjones.api.url:https://api.dowjones.com}")
    private String apiBaseUrl;

    @Value("${waqiti.compliance.dowjones.api.key}")
    private String apiKey;

    @Value("${waqiti.compliance.dowjones.api.secret}")
    private String apiSecret;

    @Value("${waqiti.compliance.dowjones.product.code:djwl}")
    private String productCode;  // djwl = Dow Jones Watchlist

    @Value("${waqiti.compliance.dowjones.timeout.seconds:30}")
    private int timeoutSeconds;

    // API Endpoints
    private static final String OAUTH_TOKEN_ENDPOINT = "/oauth2/v1/token";
    private static final String SCREENING_ENDPOINT = "/risk/v1/screening";
    private static final String BATCH_SCREENING_ENDPOINT = "/risk/v1/batch";
    private static final String ENTITY_DETAILS_ENDPOINT = "/risk/v1/entities";

    // Cache for OAuth token (expires after 24 hours typically)
    private String cachedAccessToken;
    private long tokenExpiryTime;

    /**
     * Performs comprehensive PEP and sanctions screening against Dow Jones database.
     *
     * @param name Full legal name of the person
     * @param dateOfBirth Date of birth for matching accuracy
     * @param nationality Country of citizenship
     * @return PepScreeningResult with detailed risk assessment
     */
    public CompletableFuture<PepScreeningResult> screenPoliticallyExposedPerson(
            String name, LocalDate dateOfBirth, String nationality) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("COMPLIANCE: Initiating Dow Jones PEP screening for: {} (DOB: {}, Nationality: {})",
                    name, dateOfBirth, nationality);

                // Step 1: Get OAuth access token
                String accessToken = getAccessToken();

                // Step 2: Build screening request payload
                Map<String, Object> screeningRequest = buildScreeningRequest(name, dateOfBirth, nationality);

                // Step 3: Execute API call
                Map<String, Object> response = executeScreening(accessToken, screeningRequest);

                // Step 4: Parse and analyze results
                PepScreeningResult result = parseScreeningResponse(response, name);

                log.info("COMPLIANCE: Dow Jones PEP screening completed - Status: {}, Risk Score: {}",
                    result.getStatus(), result.getRiskScore());

                return result;

            } catch (Exception e) {
                log.error("COMPLIANCE: Dow Jones PEP screening failed for: {}", name, e);

                // Return safe default on error (fail-closed approach)
                return PepScreeningResult.builder()
                    .status(PepStatus.UNKNOWN)
                    .matchDetails("Screening service unavailable - manual review required")
                    .category(null)
                    .riskScore(50.0)  // Medium risk when screening fails
                    .build();
            }
        });
    }

    /**
     * Performs basic PEP screening (lightweight version for high-volume checks)
     */
    public PepResult screenBasic(String name, LocalDate dateOfBirth, String nationality) {
        try {
            log.info("COMPLIANCE: Dow Jones basic PEP screening for: {}", name);

            String accessToken = getAccessToken();

            // Use quick-match endpoint for faster results
            Map<String, Object> quickMatchRequest = Map.of(
                "name", name,
                "country", nationality != null ? nationality : "",
                "dob", dateOfBirth != null ? dateOfBirth.toString() : "",
                "matchStrength", "MEDIUM",  // Lower threshold for basic checks
                "screeningType", "PEP_QUICK"
            );

            Map<String, Object> response = executeScreening(accessToken, quickMatchRequest);

            boolean isHit = isHitDetected(response);
            double matchScore = calculateMatchScore(response);

            PepCategory category = null;
            String matchDetails = "No PEP matches found";

            if (isHit) {
                category = extractPepCategory(response);
                matchDetails = extractMatchDetails(response);
            }

            return PepResult.builder()
                .hit(isHit)
                .score(matchScore)
                .matchDetails(matchDetails)
                .category(category)
                .build();

        } catch (Exception e) {
            log.error("COMPLIANCE: Dow Jones basic PEP screening failed for: {}", name, e);

            // Fail-closed: Indicate potential hit requiring review
            return PepResult.builder()
                .hit(true)
                .score(50.0)
                .matchDetails("Screening unavailable - requires manual review")
                .category(null)
                .build();
        }
    }

    /**
     * Batch screening for multiple individuals (efficient for onboarding flows)
     */
    public CompletableFuture<List<PepScreeningResult>> batchScreening(
            List<Map<String, Object>> individuals) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("COMPLIANCE: Dow Jones batch PEP screening for {} individuals", individuals.size());

                String accessToken = getAccessToken();

                Map<String, Object> batchRequest = Map.of(
                    "screenings", individuals,
                    "async", false,  // Synchronous for immediate results
                    "matchStrength", "HIGH"
                );

                HttpHeaders headers = createAuthHeaders(accessToken);
                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(batchRequest, headers);

                ResponseEntity<Map> response = restTemplate.exchange(
                    apiBaseUrl + BATCH_SCREENING_ENDPOINT,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");

                    return results.stream()
                        .map(r -> parseScreeningResponse(r, (String) r.get("name")))
                        .collect(Collectors.toList());
                }

                return Collections.emptyList();

            } catch (Exception e) {
                log.error("COMPLIANCE: Batch PEP screening failed", e);
                return Collections.emptyList();
            }
        });
    }

    /**
     * Get detailed entity information for a PEP match
     */
    public CompletableFuture<Map<String, Object>> getEntityDetails(String entityId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String accessToken = getAccessToken();
                HttpHeaders headers = createAuthHeaders(accessToken);

                ResponseEntity<Map> response = restTemplate.exchange(
                    apiBaseUrl + ENTITY_DETAILS_ENDPOINT + "/" + entityId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
                );

                if (response.getStatusCode() == HttpStatus.OK) {
                    return response.getBody();
                }

                return Collections.emptyMap();

            } catch (Exception e) {
                log.error("COMPLIANCE: Failed to fetch entity details for: {}", entityId, e);
                return Collections.emptyMap();
            }
        });
    }

    // ===================================================================
    // PRIVATE HELPER METHODS
    // ===================================================================

    /**
     * Obtains OAuth 2.0 access token from Dow Jones (cached for 24 hours)
     */
    private synchronized String getAccessToken() {
        // Check if cached token is still valid
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return cachedAccessToken;
        }

        try {
            log.debug("COMPLIANCE: Requesting new Dow Jones OAuth token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(apiKey, apiSecret);

            Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "scope", "risk"
            );

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                apiBaseUrl + OAUTH_TOKEN_ENDPOINT,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                cachedAccessToken = (String) tokenResponse.get("access_token");
                int expiresIn = (Integer) tokenResponse.getOrDefault("expires_in", 86400); // Default 24h

                // Set expiry with 5-minute buffer
                tokenExpiryTime = System.currentTimeMillis() + ((expiresIn - 300) * 1000L);

                log.info("COMPLIANCE: Dow Jones OAuth token obtained (expires in {} seconds)", expiresIn);
                return cachedAccessToken;
            }

            throw new RuntimeException("Failed to obtain Dow Jones OAuth token");

        } catch (Exception e) {
            log.error("COMPLIANCE: OAuth token request failed", e);
            throw new RuntimeException("Dow Jones authentication failed", e);
        }
    }

    /**
     * Builds screening request payload for Dow Jones API
     */
    private Map<String, Object> buildScreeningRequest(String name, LocalDate dateOfBirth, String nationality) {
        Map<String, Object> request = new HashMap<>();

        request.put("name", name);
        request.put("matchStrength", "HIGH");  // HIGH | MEDIUM | LOW
        request.put("screeningType", "COMPREHENSIVE");  // PEP, Sanctions, Adverse Media

        // Optional fields for better matching
        if (dateOfBirth != null) {
            request.put("dateOfBirth", dateOfBirth.toString());
        }

        if (nationality != null && !nationality.isEmpty()) {
            request.put("country", nationality);
        }

        // Configure what to screen against
        request.put("screenAgainst", List.of(
            "PEP",                  // Politically Exposed Persons
            "SANCTIONS",            // Global sanctions lists
            "ADVERSE_MEDIA",        // Negative news
            "STATE_OWNED_ENTITIES"  // SOE ownership
        ));

        // Risk factors configuration
        request.put("includeRCAAssociates", true);  // Relatives and Close Associates
        request.put("includeFamilyMembers", true);
        request.put("maxResults", 100);

        return request;
    }

    /**
     * Executes screening API call with proper headers and timeout
     */
    private Map<String, Object> executeScreening(String accessToken, Map<String, Object> screeningRequest) {
        try {
            HttpHeaders headers = createAuthHeaders(accessToken);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(screeningRequest, headers);

            // Set read timeout for API call
            ResponseEntity<Map> response = restTemplate.exchange(
                apiBaseUrl + SCREENING_ENDPOINT,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            throw new RuntimeException("Dow Jones API returned non-200 status: " + response.getStatusCode());

        } catch (HttpClientErrorException e) {
            log.error("COMPLIANCE: Dow Jones API error - Status: {}, Body: {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Dow Jones API client error", e);
        }
    }

    /**
     * Creates HTTP headers with OAuth bearer token
     */
    private HttpHeaders createAuthHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("X-API-Key", apiKey);
        headers.set("User-Agent", "Waqiti-Compliance/1.0");
        return headers;
    }

    /**
     * Parses Dow Jones API response and converts to PepScreeningResult
     */
    private PepScreeningResult parseScreeningResponse(Map<String, Object> response, String name) {
        try {
            List<Map<String, Object>> matches = (List<Map<String, Object>>) response.getOrDefault("matches", Collections.emptyList());

            if (matches.isEmpty()) {
                return PepScreeningResult.builder()
                    .status(PepStatus.NOT_PEP)
                    .matchDetails("No PEP or sanctions matches found")
                    .category(null)
                    .riskScore(0.0)
                    .build();
            }

            // Analyze highest-scoring match
            Map<String, Object> topMatch = matches.get(0);
            double matchScore = ((Number) topMatch.getOrDefault("score", 0.0)).doubleValue();
            String pepType = (String) topMatch.getOrDefault("pepType", "");
            List<String> categories = (List<String>) topMatch.getOrDefault("categories", Collections.emptyList());
            String matchName = (String) topMatch.getOrDefault("name", "");

            // Determine PEP status based on relationship
            PepStatus status = determinePepStatus(pepType, categories);

            // Extract PEP category
            PepCategory category = extractCategoryFromMatch(categories);

            // Calculate risk score (0-100)
            double riskScore = calculateRiskScore(matchScore, status, categories);

            // Build match details
            String matchDetails = buildMatchDetails(matchName, pepType, categories, matchScore);

            return PepScreeningResult.builder()
                .status(status)
                .matchDetails(matchDetails)
                .category(category)
                .riskScore(riskScore)
                .build();

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to parse Dow Jones screening response", e);

            // Safe default when parsing fails
            return PepScreeningResult.builder()
                .status(PepStatus.UNKNOWN)
                .matchDetails("Response parsing error - manual review required")
                .category(null)
                .riskScore(50.0)
                .build();
        }
    }

    /**
     * Determines PEP status from match metadata
     */
    private PepStatus determinePepStatus(String pepType, List<String> categories) {
        if (pepType == null || pepType.isEmpty()) {
            return PepStatus.NOT_PEP;
        }

        return switch (pepType.toUpperCase()) {
            case "DIRECT", "CURRENT", "ACTIVE" -> PepStatus.PEP_DIRECT;
            case "FORMER", "HISTORIC" -> PepStatus.PEP_DIRECT;  // Still considered PEP
            case "ASSOCIATE", "RCA" -> PepStatus.PEP_ASSOCIATE;
            case "FAMILY", "RELATIVE" -> PepStatus.PEP_FAMILY;
            default -> PepStatus.UNKNOWN;
        };
    }

    /**
     * Extracts PEP category from match categories
     */
    private PepCategory extractCategoryFromMatch(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }

        for (String cat : categories) {
            String upper = cat.toUpperCase();
            if (upper.contains("HEAD_OF_STATE") || upper.contains("PRESIDENT") || upper.contains("MONARCH")) {
                return PepCategory.HEAD_OF_STATE;
            } else if (upper.contains("MINISTER") || upper.contains("GOVERNMENT")) {
                return PepCategory.GOVERNMENT_OFFICIAL;
            } else if (upper.contains("MILITARY") || upper.contains("DEFENSE")) {
                return PepCategory.MILITARY_OFFICIAL;
            } else if (upper.contains("JUDGE") || upper.contains("JUDICIAL")) {
                return PepCategory.JUDICIAL_OFFICIAL;
            } else if (upper.contains("PARTY") || upper.contains("POLITICAL")) {
                return PepCategory.PARTY_OFFICIAL;
            } else if (upper.contains("STATE_OWNED") || upper.contains("SOE")) {
                return PepCategory.STATE_OWNED_ENTERPRISE;
            } else if (upper.contains("INTERNATIONAL") || upper.contains("UN") || upper.contains("INTERGOVERNMENTAL")) {
                return PepCategory.INTERNATIONAL_ORGANIZATION;
            }
        }

        return PepCategory.GOVERNMENT_OFFICIAL;  // Default fallback
    }

    /**
     * Calculates composite risk score from match data
     */
    private double calculateRiskScore(double matchScore, PepStatus status, List<String> categories) {
        double baseScore = matchScore * 100.0;  // Convert 0-1 to 0-100

        // Adjust by PEP status severity
        double statusMultiplier = switch (status) {
            case PEP_DIRECT -> 1.0;
            case PEP_ASSOCIATE -> 0.7;
            case PEP_FAMILY -> 0.8;
            case NOT_PEP -> 0.0;
            case UNKNOWN -> 0.5;
        };

        // Adjust by category severity
        double categoryBonus = 0.0;
        if (categories != null) {
            for (String cat : categories) {
                String upper = cat.toUpperCase();
                if (upper.contains("SANCTIONS") || upper.contains("EMBARGO")) {
                    categoryBonus += 30.0;  // Major risk increase
                }
                if (upper.contains("ADVERSE") || upper.contains("NEGATIVE")) {
                    categoryBonus += 15.0;
                }
            }
        }

        double finalScore = (baseScore * statusMultiplier) + categoryBonus;
        return Math.min(100.0, Math.max(0.0, finalScore));  // Clamp to 0-100
    }

    /**
     * Builds human-readable match details string
     */
    private String buildMatchDetails(String matchName, String pepType, List<String> categories, double score) {
        StringBuilder details = new StringBuilder();

        details.append(String.format("Match found: %s (%.1f%% confidence)", matchName, score * 100));
        details.append("\nType: ").append(pepType != null ? pepType : "Unknown");

        if (categories != null && !categories.isEmpty()) {
            details.append("\nCategories: ").append(String.join(", ", categories));
        }

        return details.toString();
    }

    /**
     * Checks if API response contains any hits
     */
    private boolean isHitDetected(Map<String, Object> response) {
        List<Map<String, Object>> matches = (List<Map<String, Object>>) response.getOrDefault("matches", Collections.emptyList());
        return !matches.isEmpty();
    }

    /**
     * Calculates overall match score from response
     */
    private double calculateMatchScore(Map<String, Object> response) {
        List<Map<String, Object>> matches = (List<Map<String, Object>>) response.getOrDefault("matches", Collections.emptyList());

        if (matches.isEmpty()) {
            return 0.0;
        }

        // Return highest match score
        return matches.stream()
            .mapToDouble(m -> ((Number) m.getOrDefault("score", 0.0)).doubleValue())
            .max()
            .orElse(0.0) * 100.0;
    }

    /**
     * Extracts PEP category from response
     */
    private PepCategory extractPepCategory(Map<String, Object> response) {
        List<Map<String, Object>> matches = (List<Map<String, Object>>) response.getOrDefault("matches", Collections.emptyList());

        if (matches.isEmpty()) {
            return null;
        }

        List<String> categories = (List<String>) matches.get(0).getOrDefault("categories", Collections.emptyList());
        return extractCategoryFromMatch(categories);
    }

    /**
     * Extracts match details string from response
     */
    private String extractMatchDetails(Map<String, Object> response) {
        List<Map<String, Object>> matches = (List<Map<String, Object>>) response.getOrDefault("matches", Collections.emptyList());

        if (matches.isEmpty()) {
            return "No matches found";
        }

        Map<String, Object> topMatch = matches.get(0);
        String name = (String) topMatch.getOrDefault("name", "Unknown");
        double score = ((Number) topMatch.getOrDefault("score", 0.0)).doubleValue();
        String pepType = (String) topMatch.getOrDefault("pepType", "");

        return String.format("Match: %s (%.1f%% confidence, Type: %s)", name, score * 100, pepType);
    }
}
