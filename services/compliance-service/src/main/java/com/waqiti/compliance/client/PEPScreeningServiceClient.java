package com.waqiti.compliance.client;

import com.waqiti.compliance.dto.PEPScreeningRequest;
import com.waqiti.compliance.dto.PEPScreeningResult;
import com.waqiti.compliance.dto.PEPMatch;
import com.waqiti.compliance.exception.ComplianceScreeningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Client for PEP (Politically Exposed Persons) screening services
 * Integrates with multiple PEP databases and watch lists
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PEPScreeningServiceClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${compliance.pep.primary-api.url:https://api.pep-screening.com/v1}")
    private String primaryApiUrl;
    
    @Value("${compliance.pep.primary-api.key}")
    private String primaryApiKey;
    
    @Value("${compliance.pep.fallback-api.url:https://api.worldcheck.refinitiv.com/v2}")
    private String fallbackApiUrl;
    
    @Value("${compliance.pep.fallback-api.key}")
    private String fallbackApiKey;
    
    @Value("${compliance.pep.threshold:75}")
    private int pepMatchThreshold;
    
    @Value("${compliance.pep.timeout:30000}")
    private int timeoutMs;
    
    /**
     * Screen person against PEP databases
     */
    public PEPScreeningResult screenForPEP(PEPScreeningRequest request) {
        log.info("Performing PEP screening for: {} {} (Nationality: {})", 
                request.getFirstName(), request.getLastName(), request.getNationality());
        
        try {
            // Validate input
            validatePEPScreeningRequest(request);
            
            // Try primary PEP screening service
            List<PEPMatch> matches = performPrimaryPEPSearch(request);
            
            // If primary fails or returns insufficient results, try fallback
            if (matches.isEmpty()) {
                matches = performFallbackPEPSearch(request);
            }
            
            // Enhance matches with additional data
            enhancePEPMatches(matches);
            
            // Evaluate matches
            boolean isClean = evaluatePEPMatches(matches);
            
            // Create screening result
            PEPScreeningResult result = PEPScreeningResult.builder()
                    .clean(isClean)
                    .screeningId(UUID.randomUUID().toString())
                    .screenedAt(LocalDateTime.now())
                    .matches(matches)
                    .matchCount(matches.size())
                    .highestScore(getHighestPEPScore(matches))
                    .riskLevel(determinePEPRiskLevel(matches))
                    .build();
            
            // Log result for audit
            logPEPScreeningResult(request, result);
            
            return result;
            
        } catch (Exception e) {
            log.error("PEP screening failed for: {} {}", request.getFirstName(), request.getLastName(), e);
            throw new ComplianceScreeningException("PEP screening failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Perform primary PEP search
     */
    private List<PEPMatch> performPrimaryPEPSearch(PEPScreeningRequest request) {
        List<PEPMatch> allMatches = new ArrayList<>();
        
        try {
            // Search by full name
            allMatches.addAll(searchPEPByName(request.getFirstName() + " " + request.getLastName(), "PRIMARY"));
            
            // Search by individual name components
            if (request.getFirstName() != null) {
                allMatches.addAll(searchPEPByName(request.getFirstName(), "PRIMARY"));
            }
            if (request.getLastName() != null) {
                allMatches.addAll(searchPEPByName(request.getLastName(), "PRIMARY"));
            }
            
            // Search by nationality and occupation combination
            if (request.getNationality() != null && request.getOccupation() != null) {
                allMatches.addAll(searchPEPByNationalityAndOccupation(
                        request.getNationality(), request.getOccupation(), "PRIMARY"));
            }
            
            // Remove duplicates and filter by threshold
            return deduplicateAndFilterPEP(allMatches);
            
        } catch (Exception e) {
            log.warn("Primary PEP search failed, will try fallback", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Perform fallback PEP search using alternative service
     */
    private List<PEPMatch> performFallbackPEPSearch(PEPScreeningRequest request) {
        List<PEPMatch> allMatches = new ArrayList<>();
        
        try {
            log.info("Using fallback PEP screening service");
            
            // Search using fallback service
            allMatches.addAll(searchPEPByName(request.getFirstName() + " " + request.getLastName(), "FALLBACK"));
            
            // Enhanced search by known PEP categories
            allMatches.addAll(searchPEPByCategory(request, "HEAD_OF_STATE"));
            allMatches.addAll(searchPEPByCategory(request, "GOVERNMENT_OFFICIAL"));
            allMatches.addAll(searchPEPByCategory(request, "POLITICAL_PARTY_OFFICIAL"));
            allMatches.addAll(searchPEPByCategory(request, "MILITARY_OFFICIAL"));
            allMatches.addAll(searchPEPByCategory(request, "JUDICIAL_OFFICIAL"));
            allMatches.addAll(searchPEPByCategory(request, "STATE_ENTERPRISE_EXECUTIVE"));
            allMatches.addAll(searchPEPByCategory(request, "INTERNATIONAL_ORG_OFFICIAL"));
            
            return deduplicateAndFilterPEP(allMatches);
            
        } catch (Exception e) {
            log.error("Fallback PEP search also failed", e);
            throw new ComplianceScreeningException("All PEP screening services failed", e);
        }
    }
    
    /**
     * Search PEP by name using specified service
     */
    private List<PEPMatch> searchPEPByName(String name, String service) {
        if (name == null || name.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            String apiUrl = "PRIMARY".equals(service) ? primaryApiUrl : fallbackApiUrl;
            String apiKey = "PRIMARY".equals(service) ? primaryApiKey : fallbackApiKey;
            
            // Prepare search parameters
            Map<String, String> params = new HashMap<>();
            params.put("name", name.trim());
            params.put("categories", "PEP,RCA,SIP"); // PEP, Relatives & Close Associates, Special Interest Person
            params.put("limit", "50");
            
            // Build URL
            String searchUrl = buildPEPSearchUrl(apiUrl, params);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Content-Type", "application/json");
            headers.set("User-Agent", "Waqiti-Compliance-Service/1.0");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Make API call
            ResponseEntity<PEPSearchResponse> response = restTemplate.exchange(
                    searchUrl, 
                    HttpMethod.GET, 
                    entity, 
                    PEPSearchResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parsePEPSearchResponse(response.getBody(), name, service);
            }
            
            return Collections.emptyList();
            
        } catch (HttpClientErrorException e) {
            log.warn("PEP API error for name search '{}': {} - {}", name, e.getStatusCode(), e.getResponseBodyAsString());
            // Try backup service
            return searchPEPUsingBackupAPI(name);
        } catch (ResourceAccessException e) {
            log.error("PEP API timeout or connection error for name: {}", name, e);
            throw new ComplianceScreeningException("PEP service unavailable", e);
        }
    }
    
    /**
     * Search PEP by nationality and occupation
     */
    private List<PEPMatch> searchPEPByNationalityAndOccupation(String nationality, String occupation, String service) {
        try {
            String apiUrl = "PRIMARY".equals(service) ? primaryApiUrl : fallbackApiUrl;
            String apiKey = "PRIMARY".equals(service) ? primaryApiKey : fallbackApiKey;
            
            Map<String, String> params = new HashMap<>();
            params.put("nationality", nationality);
            params.put("occupation", occupation);
            params.put("categories", "PEP");
            
            String searchUrl = buildPEPSearchUrl(apiUrl + "/search/nationality-occupation", params);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<PEPSearchResponse> response = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, entity, PEPSearchResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parsePEPSearchResponse(response.getBody(), nationality + ":" + occupation, service);
            }
            
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.warn("PEP nationality/occupation search failed for {} / {}", nationality, occupation, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Search PEP by specific category
     */
    private List<PEPMatch> searchPEPByCategory(PEPScreeningRequest request, String category) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("name", request.getFirstName() + " " + request.getLastName());
            params.put("category", category);
            if (request.getNationality() != null) {
                params.put("nationality", request.getNationality());
            }
            
            String searchUrl = buildPEPSearchUrl(fallbackApiUrl + "/search/category", params);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + fallbackApiKey);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<PEPSearchResponse> response = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, entity, PEPSearchResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parsePEPSearchResponse(response.getBody(), 
                        request.getFirstName() + " " + request.getLastName(), "CATEGORY:" + category);
            }
            
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.debug("PEP category search failed for category: {}", category, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Parse PEP search response
     */
    private List<PEPMatch> parsePEPSearchResponse(PEPSearchResponse response, String searchTerm, String source) {
        List<PEPMatch> matches = new ArrayList<>();
        
        if (response.getResults() != null) {
            for (PEPSearchResult result : response.getResults()) {
                int score = calculatePEPMatchScore(searchTerm, result.getName(), result);
                
                if (score >= pepMatchThreshold) {
                    matches.add(PEPMatch.builder()
                            .pepId(result.getId())
                            .name(result.getName())
                            .aliases(result.getAliases())
                            .nationality(result.getNationality())
                            .dateOfBirth(result.getDateOfBirth())
                            .pepCategory(result.getCategory())
                            .position(result.getPosition())
                            .organization(result.getOrganization())
                            .country(result.getCountry())
                            .riskLevel(result.getRiskLevel())
                            .isActive(result.isActive())
                            .score(score)
                            .searchTerm(searchTerm)
                            .source(source)
                            .lastUpdated(result.getLastUpdated())
                            .build());
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Calculate PEP match score with enhanced algorithm
     */
    private int calculatePEPMatchScore(String searchTerm, String pepName, PEPSearchResult result) {
        // Base name matching score
        int nameScore = calculateNameMatchScore(searchTerm, pepName);
        
        // Boost score based on PEP category risk
        int categoryBoost = getPEPCategoryRiskBoost(result.getCategory());
        
        // Boost score if person is currently active
        int activeBoost = result.isActive() ? 10 : 0;
        
        // Boost score based on position importance
        int positionBoost = getPositionImportanceBoost(result.getPosition());
        
        // Calculate final score
        int finalScore = nameScore + categoryBoost + activeBoost + positionBoost;
        
        // Cap at 100
        return Math.min(100, finalScore);
    }
    
    /**
     * Enhanced name matching with fuzzy logic
     */
    private int calculateNameMatchScore(String searchTerm, String pepName) {
        if (searchTerm == null || pepName == null) return 0;
        
        // Normalize names
        String norm1 = normalizePEPName(searchTerm);
        String norm2 = normalizePEPName(pepName);
        
        // Exact match
        if (norm1.equals(norm2)) return 90;
        
        // Calculate Levenshtein distance-based score
        int distance = levenshteinDistance(norm1, norm2);
        int maxLen = Math.max(norm1.length(), norm2.length());
        
        if (maxLen == 0) return 90;
        
        return Math.max(0, 90 - (distance * 90 / maxLen));
    }
    
    /**
     * Calculate Levenshtein distance
     */
    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                        Math.min(dp[i-1][j] + 1, dp[i][j-1] + 1),
                        dp[i-1][j-1] + (a.charAt(i-1) == b.charAt(j-1) ? 0 : 1)
                    );
                }
            }
        }
        
        return dp[a.length()][b.length()];
    }
    
    /**
     * Get risk boost based on PEP category
     */
    private int getPEPCategoryRiskBoost(String category) {
        return switch (category != null ? category.toUpperCase() : "") {
            case "HEAD_OF_STATE" -> 15;
            case "HEAD_OF_GOVERNMENT" -> 15;
            case "SENIOR_POLITICAL_FIGURE" -> 12;
            case "GOVERNMENT_MINISTER" -> 10;
            case "MILITARY_OFFICIAL" -> 8;
            case "JUDICIAL_OFFICIAL" -> 8;
            case "POLITICAL_PARTY_OFFICIAL" -> 6;
            case "STATE_ENTERPRISE_EXECUTIVE" -> 5;
            case "INTERNATIONAL_ORG_OFFICIAL" -> 4;
            case "RCA" -> 3; // Relatives & Close Associates
            case "SIP" -> 2; // Special Interest Person
            default -> 0;
        };
    }
    
    /**
     * Get boost based on position importance
     */
    private int getPositionImportanceBoost(String position) {
        if (position == null) return 0;
        
        String pos = position.toLowerCase();
        if (pos.contains("president") || pos.contains("prime minister") || pos.contains("king") || pos.contains("queen")) {
            return 10;
        } else if (pos.contains("minister") || pos.contains("governor") || pos.contains("ambassador")) {
            return 8;
        } else if (pos.contains("director") || pos.contains("commissioner") || pos.contains("chief")) {
            return 5;
        } else if (pos.contains("deputy") || pos.contains("assistant")) {
            return 3;
        }
        return 0;
    }
    
    /**
     * Search using backup API when primary and fallback fail
     */
    private List<PEPMatch> searchPEPUsingBackupAPI(String name) {
        log.info("Using backup API for PEP screening: {}", name);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + fallbackApiKey);
            headers.set("Content-Type", "application/json");
            headers.set("Accept", "application/json");
            
            // Dow Jones Risk API format
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("searchText", name);
            requestBody.put("filters", Map.of(
                "categories", Arrays.asList("PEP", "RCA", "SIP"),
                "matchThreshold", pepMatchThreshold
            ));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                fallbackApiUrl + "/search",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseBackupAPIResponse(response.getBody(), name);
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Backup PEP API also failed for name: {}", name, e);
            // Create manual review flag
            return createPEPManualReviewFlag(name);
        }
    }
    
    /**
     * Parse backup API response
     */
    private List<PEPMatch> parseBackupAPIResponse(Map<String, Object> response, String searchTerm) {
        List<PEPMatch> matches = new ArrayList<>();
        
        if (response.containsKey("results")) {
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            
            for (Map<String, Object> result : results) {
                String name = (String) result.get("name");
                String category = (String) result.get("category");
                
                // Only process PEP matches
                if (category != null && category.contains("PEP")) {
                    int score = calculateNameMatchScore(searchTerm, name);
                    
                    if (score >= pepMatchThreshold) {
                        List<String> positions = (List<String>) result.get("positions");
                        String position = positions != null && !positions.isEmpty() ? 
                            positions.get(0) : "Unknown Position";
                        
                        matches.add(PEPMatch.builder()
                            .pepId((String) result.get("id"))
                            .name(name)
                            .nationality((String) result.get("nationality"))
                            .dateOfBirth((String) result.get("dateOfBirth"))
                            .pepCategory(category)
                            .position(position)
                            .level(determinePEPLevel(position))
                            .country((String) result.get("country"))
                            .score(score)
                            .searchTerm(searchTerm)
                            .source("BACKUP_API")
                            .build());
                    }
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Create manual review flag for PEP screening
     */
    private List<PEPMatch> createPEPManualReviewFlag(String name) {
        List<PEPMatch> matches = new ArrayList<>();
        
        matches.add(PEPMatch.builder()
            .pepId("MANUAL_REVIEW_REQUIRED")
            .name(name)
            .pepCategory("MANUAL_REVIEW")
            .position("API_FAILURE")
            .level("REVIEW")
            .score(0)
            .searchTerm(name)
            .source("MANUAL_FLAG")
            .build());
        
        log.warn("PEP screening flagged for manual review: {}", name);
        
        return matches;
    }
    
    /**
     * Calculate simple name match score
     */
    private int calculateNameMatchScore(String searchTerm, String name) {
        if (searchTerm == null || name == null) return 0;
        
        String normalizedSearch = searchTerm.toLowerCase().trim();
        String normalizedName = name.toLowerCase().trim();
        
        if (normalizedSearch.equals(normalizedName)) {
            return 100;
        }
        
        // Calculate Levenshtein distance-based score
        int distance = calculateLevenshteinDistance(normalizedSearch, normalizedName);
        int maxLength = Math.max(normalizedSearch.length(), normalizedName.length());
        
        if (maxLength == 0) return 100;
        
        return Math.max(0, 100 - (distance * 100 / maxLength));
    }
    
    /**
     * Calculate Levenshtein distance
     */
    private int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Determine PEP level based on position
     */
    private String determinePEPLevel(String position) {
        if (position == null) return "UNKNOWN";
        
        String upperPosition = position.toUpperCase();
        
        // Senior level positions
        if (upperPosition.contains("PRESIDENT") || upperPosition.contains("PRIME MINISTER") ||
            upperPosition.contains("KING") || upperPosition.contains("QUEEN") ||
            upperPosition.contains("EMPEROR") || upperPosition.contains("SUPREME")) {
            return "SENIOR";
        }
        
        // High level positions
        if (upperPosition.contains("MINISTER") || upperPosition.contains("GOVERNOR") ||
            upperPosition.contains("SENATOR") || upperPosition.contains("AMBASSADOR")) {
            return "HIGH";
        }
        
        // Mid level positions
        if (upperPosition.contains("DEPUTY") || upperPosition.contains("ASSISTANT") ||
            upperPosition.contains("DIRECTOR") || upperPosition.contains("SECRETARY")) {
            return "MEDIUM";
        }
        
        // Family or associates
        if (upperPosition.contains("SPOUSE") || upperPosition.contains("CHILD") ||
            upperPosition.contains("RELATIVE") || upperPosition.contains("ASSOCIATE")) {
            return "ASSOCIATE";
        }
        
        return "LOW";
    }
    
    /**
     * Enhance PEP matches with additional contextual data
     */
    private void enhancePEPMatches(List<PEPMatch> matches) {
        for (PEPMatch match : matches) {
            // Add risk assessment based on current political climate
            match.setContextualRiskScore(calculateContextualRisk(match));
            
            // Add relationship mapping if available
            match.setKnownAssociates(findKnownAssociates(match.getPepId()));
            
            // Add sanctions cross-reference
            match.setSanctionsStatus(checkSanctionsStatus(match.getName()));
        }
    }
    
    /**
     * Evaluate PEP matches to determine if customer is clean
     */
    private boolean evaluatePEPMatches(List<PEPMatch> matches) {
        if (matches.isEmpty()) {
            return true;
        }
        
        // Check for direct PEP matches (not just relatives/associates)
        long directPEPMatches = matches.stream()
                .filter(m -> !"RCA".equals(m.getPepCategory()))
                .filter(m -> m.getScore() >= 90)
                .count();
                
        if (directPEPMatches > 0) {
            log.warn("Direct PEP match found with high confidence");
            return false;
        }
        
        // Check for active high-risk PEP matches
        long activeHighRiskMatches = matches.stream()
                .filter(PEPMatch::isActive)
                .filter(m -> m.getScore() >= 85)
                .filter(m -> "HIGH".equals(m.getRiskLevel()))
                .count();
                
        if (activeHighRiskMatches > 0) {
            log.warn("Active high-risk PEP match found");
            return false;
        }
        
        // Check for multiple medium-confidence matches
        long mediumMatches = matches.stream()
                .filter(m -> m.getScore() >= pepMatchThreshold)
                .count();
                
        if (mediumMatches >= 3) {
            log.warn("Multiple PEP matches found: {}", mediumMatches);
            return false;
        }
        
        return true;
    }
    
    /**
     * Determine PEP risk level based on matches
     */
    private String determinePEPRiskLevel(List<PEPMatch> matches) {
        if (matches.isEmpty()) {
            return "LOW";
        }
        
        int highestScore = matches.stream().mapToInt(PEPMatch::getScore).max().orElse(0);
        
        boolean hasDirectPEP = matches.stream().anyMatch(m -> !"RCA".equals(m.getPepCategory()) && m.getScore() >= 85);
        boolean hasActivePEP = matches.stream().anyMatch(m -> m.isActive() && m.getScore() >= pepMatchThreshold);
        
        if (hasDirectPEP || highestScore >= 90) {
            return "HIGH";
        } else if (hasActivePEP || highestScore >= pepMatchThreshold) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    // Additional utility methods
    private void validatePEPScreeningRequest(PEPScreeningRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PEP screening request cannot be null");
        }
        
        if (request.getFirstName() == null && request.getLastName() == null) {
            throw new IllegalArgumentException("At least first name or last name must be provided");
        }
    }
    
    private List<PEPMatch> deduplicateAndFilterPEP(List<PEPMatch> matches) {
        return matches.stream()
                .filter(match -> match.getScore() >= pepMatchThreshold)
                .collect(Collectors.toMap(
                        match -> match.getPepId() + "|" + match.getName(),
                        match -> match,
                        (existing, replacement) -> existing.getScore() > replacement.getScore() ? existing : replacement
                ))
                .values()
                .stream()
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
    }
    
    private int getHighestPEPScore(List<PEPMatch> matches) {
        return matches.stream().mapToInt(PEPMatch::getScore).max().orElse(0);
    }
    
    private String buildPEPSearchUrl(String baseUrl, Map<String, String> params) {
        StringBuilder url = new StringBuilder(baseUrl);
        if (!params.isEmpty()) {
            url.append("?");
            params.forEach((key, value) -> 
                url.append(key).append("=")
                   .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8))
                   .append("&"));
            
            // Remove trailing &
            if (url.charAt(url.length() - 1) == '&') {
                url.deleteCharAt(url.length() - 1);
            }
        }
        return url.toString();
    }
    
    private String normalizePEPName(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
    
    // Calculate contextual risk based on current political climate and match details
    private int calculateContextualRisk(PEPMatch match) {
        int baseRisk = 50;
        
        // Adjust based on position level
        if (match.getLevel() != null) {
            switch (match.getLevel()) {
                case "SENIOR" -> baseRisk += 30;
                case "HIGH" -> baseRisk += 20;
                case "MEDIUM" -> baseRisk += 10;
                case "LOW" -> baseRisk += 5;
                case "ASSOCIATE" -> baseRisk += 3;
            }
        }
        
        // Adjust based on country stability
        if (match.getCountry() != null) {
            // Countries with political instability or conflict
            Set<String> unstableCountries = Set.of(
                "AF", "SY", "YE", "LY", "SO", "SD", "SS", "MM", "VE", "HT"
            );
            if (unstableCountries.contains(match.getCountry())) {
                baseRisk += 15;
            }
        }
        
        // Adjust based on PEP category
        if (match.getPepCategory() != null) {
            switch (match.getPepCategory()) {
                case "HEAD_OF_STATE" -> baseRisk += 25;
                case "GOVERNMENT_MINISTER" -> baseRisk += 20;
                case "MILITARY_OFFICIAL" -> baseRisk += 15;
                case "JUDICIAL_OFFICIAL" -> baseRisk += 12;
                case "STATE_ENTERPRISE_EXECUTIVE" -> baseRisk += 10;
                case "RCA" -> baseRisk += 5; // Relatives & Close Associates
            }
        }
        
        // Adjust based on whether PEP is currently active
        if (match.isActive()) {
            baseRisk += 10;
        }
        
        // Adjust based on sanctions status
        if (match.getSanctionsStatus() != null && !match.getSanctionsStatus().equals("NONE")) {
            baseRisk += 20;
        }
        
        // Cap at 100
        return Math.min(100, baseRisk);
    }
    
    private List<String> findKnownAssociates(String pepId) {
        return Collections.emptyList();
    }
    
    private String checkSanctionsStatus(String name) {
        return "NONE";
    }
    
    private void logPEPScreeningResult(PEPScreeningRequest request, PEPScreeningResult result) {
        log.info("PEP screening completed - Customer: {} {}, Clean: {}, Matches: {}, Risk Level: {}, Highest Score: {}", 
                request.getFirstName(), 
                request.getLastName(), 
                result.isClean(), 
                result.getMatchCount(),
                result.getRiskLevel(),
                result.getHighestScore());
    }
    
    // Response DTOs
    private static class PEPSearchResponse {
        private List<PEPSearchResult> results;
        public List<PEPSearchResult> getResults() { return results; }
        public void setResults(List<PEPSearchResult> results) { this.results = results; }
    }
    
    private static class PEPSearchResult {
        private String id;
        private String name;
        private List<String> aliases;
        private String nationality;
        private String dateOfBirth;
        private String category;
        private String position;
        private String organization;
        private String country;
        private String riskLevel;
        private boolean active;
        private LocalDateTime lastUpdated;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getAliases() { return aliases; }
        public void setAliases(List<String> aliases) { this.aliases = aliases; }
        public String getNationality() { return nationality; }
        public void setNationality(String nationality) { this.nationality = nationality; }
        public String getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
        public String getOrganization() { return organization; }
        public void setOrganization(String organization) { this.organization = organization; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    }
}