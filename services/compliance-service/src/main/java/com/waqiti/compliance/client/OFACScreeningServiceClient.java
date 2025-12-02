package com.waqiti.compliance.client;

import com.waqiti.compliance.dto.OFACScreeningRequest;
import com.waqiti.compliance.dto.OFACScreeningResult;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;

/**
 * Client for OFAC (Office of Foreign Assets Control) screening services
 * Integrates with Treasury Department's SDN (Specially Designated Nationals) List
 *
 * Resilience Patterns:
 * - Circuit Breaker: Protects against cascading failures from OFAC API
 * - Retry: Automatic retry with exponential backoff for transient failures
 * - Bulkhead: Limits concurrent OFAC screening requests
 * - Fallback: Multiple fallback APIs for high availability
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OFACScreeningServiceClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${compliance.ofac.api.url:https://search.ofac-api.com/v3}")
    private String ofacApiUrl;
    
    @Value("${compliance.ofac.fallback.url:https://api.trade.gov/consolidated_screening_list/search}")
    private String fallbackApiUrl;
    
    @Value("${compliance.ofac.backup.url:https://sanctionssearch.ofac.treas.gov/api/v1}")
    private String backupApiUrl;
    
    @Value("${compliance.ofac.api.key}")
    private String ofacApiKey;
    
    @Value("${compliance.ofac.threshold:85}")
    private int matchThreshold;
    
    @Value("${compliance.ofac.timeout:30000}")
    private int timeoutMs;
    
    /**
     * Screen customer against OFAC SDN list
     *
     * CRITICAL COMPLIANCE: This method must succeed for regulatory compliance.
     * Circuit breaker will trigger manual review fallback if API is unavailable.
     */
    @CircuitBreaker(name = "ofac-api", fallbackMethod = "screenCustomerFallback")
    @Retry(name = "ofac-api")
    @Bulkhead(name = "ofac-api")
    public OFACScreeningResult screenCustomer(OFACScreeningRequest request) {
        log.info("Performing OFAC screening for: {} {} (DOB: {})",
            request.getFirstName(), request.getLastName(), request.getDateOfBirth());

        try {
            // Validate input
            validateScreeningRequest(request);

            // Check against SDN list
            List<OFACMatch> matches = performSDNSearch(request);

            // Evaluate matches
            boolean isClean = evaluateMatches(matches);

            // Create screening result
            OFACScreeningResult result = OFACScreeningResult.builder()
                .clean(isClean)
                .screeningId(UUID.randomUUID().toString())
                .screenedAt(LocalDateTime.now())
                .matches(matches)
                .matchCount(matches.size())
                .highestScore(getHighestMatchScore(matches))
                .build();

            // Log result for audit
            logScreeningResult(request, result);

            return result;

        } catch (Exception e) {
            log.error("OFAC screening failed for: {} {}", request.getFirstName(), request.getLastName(), e);
            throw new ComplianceScreeningException("OFAC screening failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback method when OFAC API circuit breaker is open
     *
     * CRITICAL: Returns result requiring manual review to ensure compliance.
     * All transactions will be flagged for manual compliance review.
     */
    private OFACScreeningResult screenCustomerFallback(OFACScreeningRequest request, Exception ex) {
        log.error("OFAC API circuit breaker OPEN - using manual review fallback for: {} {}. Reason: {}",
            request.getFirstName(), request.getLastName(), ex.getMessage());

        // Create audit alert for compliance team
        try {
            auditService.createComplianceAlert(
                "OFAC_API_UNAVAILABLE",
                String.format("OFAC screening API unavailable. Customer %s %s flagged for MANUAL REVIEW. Error: %s",
                    request.getFirstName(), request.getLastName(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception auditEx) {
            log.error("Failed to create compliance alert for OFAC fallback", auditEx);
        }

        // Return result that requires manual review
        return OFACScreeningResult.builder()
            .clean(false) // CRITICAL: Flag as not clean to trigger manual review
            .screeningId(UUID.randomUUID().toString())
            .screenedAt(LocalDateTime.now())
            .matches(new ArrayList<>())
            .matchCount(0)
            .highestScore(0.0)
            .requiresManualReview(true)
            .failureReason("OFAC API unavailable - Circuit breaker open: " + ex.getMessage())
            .build();
    }
    
    /**
     * Perform SDN list search
     */
    private List<OFACMatch> performSDNSearch(OFACScreeningRequest request) {
        List<OFACMatch> allMatches = new ArrayList<>();
        
        try {
            // Search by name
            allMatches.addAll(searchSDNByName(request.getFirstName() + " " + request.getLastName()));
            
            // Search by individual name components
            if (request.getFirstName() != null) {
                allMatches.addAll(searchSDNByName(request.getFirstName()));
            }
            if (request.getLastName() != null) {
                allMatches.addAll(searchSDNByName(request.getLastName()));
            }
            
            // Search by aliases if provided
            if (request.getAliases() != null) {
                for (String alias : request.getAliases()) {
                    allMatches.addAll(searchSDNByName(alias));
                }
            }
            
            // Search by address if provided
            if (request.getAddress() != null) {
                allMatches.addAll(searchSDNByAddress(request.getAddress()));
            }
            
            // Remove duplicates and filter by threshold
            return deduplicateAndFilter(allMatches);
            
        } catch (Exception e) {
            log.error("Failed to search SDN list", e);
            throw new ComplianceScreeningException("SDN list search failed", e);
        }
    }
    
    /**
     * Search SDN list by name
     * Protected by circuit breaker for resilience
     */
    @CircuitBreaker(name = "ofac-api", fallbackMethod = "searchSDNByNameFallback")
    @Retry(name = "ofac-api")
    private List<OFACMatch> searchSDNByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // Prepare search parameters
            Map<String, String> params = new HashMap<>();
            params.put("name", name.trim());
            params.put("sources", "SDN,CONS,FSE,ISN,PLC,CAP,DTC,NS-MBS");
            params.put("type", "individual");
            params.put("limit", "50");

            // Build URL
            String searchUrl = buildSearchUrl(params);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + ofacApiKey);
            headers.set("Content-Type", "application/json");
            headers.set("User-Agent", "Waqiti-Compliance-Service/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Make API call
            ResponseEntity<OFACSearchResponse> response = restTemplate.exchange(
                searchUrl,
                HttpMethod.GET,
                entity,
                OFACSearchResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<OFACMatch> matches = parseSearchResponse(response.getBody(), name);

                // Cache results for 24 hours to improve performance
                cacheService.cacheScreeningResult(name, matches, Duration.ofHours(24));

                // Create audit record for compliance
                auditService.recordOFACScreening(
                    name,
                    matches.size(),
                    matches.stream().mapToDouble(OFACMatch::getMatchScore).max().orElse(0.0),
                    "SDN_NAME_SEARCH"
                );

                return matches;
            } else {
                log.warn("OFAC API returned non-OK status: {} for name: {}",
                    response.getStatusCode(), name);

                // Return cached result if available, otherwise empty
                return cacheService.getCachedScreeningResult(name)
                    .orElse(new ArrayList<>());
            }

        } catch (HttpClientErrorException e) {
            log.warn("OFAC API error for name search '{}': {} - {}", name, e.getStatusCode(), e.getResponseBodyAsString());
            // Fallback to alternate API
            return searchUsingFallbackAPI(name);
        } catch (ResourceAccessException e) {
            log.error("OFAC API timeout or connection error for name: {}", name, e);
            throw new ComplianceScreeningException("OFAC service unavailable", e);
        }
    }

    /**
     * Fallback for searchSDNByName when circuit breaker is open
     */
    private List<OFACMatch> searchSDNByNameFallback(String name, Exception ex) {
        log.warn("OFAC SDN search circuit breaker OPEN for name: {}. Using fallback API. Reason: {}",
            name, ex.getMessage());

        // Try fallback API
        try {
            return searchUsingFallbackAPI(name);
        } catch (Exception fallbackEx) {
            log.error("Fallback API also failed for name: {}", name, fallbackEx);
            // Return cached results if available
            return cacheService.getCachedScreeningResult(name)
                .orElse(new ArrayList<>());
        }
    }
    
    /**
     * Search SDN list by address
     */
    private List<OFACMatch> searchSDNByAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            Map<String, String> params = new HashMap<>();
            params.put("address", address.trim());
            params.put("sources", "SDN,CONS");
            params.put("limit", "25");
            
            String searchUrl = buildSearchUrl(params);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + ofacApiKey);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<OFACSearchResponse> response = restTemplate.exchange(
                searchUrl,
                HttpMethod.GET,
                entity,
                OFACSearchResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseAddressSearchResponse(response.getBody(), address);
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Address search failed for: {}", address, e);
            return searchUsingFallbackAPI(address);
        }
    }
    
    /**
     * Calculate fuzzy match score between two names
     */
    private int calculateMatchScore(String name1, String name2) {
        if (name1 == null || name2 == null) return 0;
        
        // Normalize names
        String norm1 = normalizeName(name1);
        String norm2 = normalizeName(name2);
        
        // Exact match
        if (norm1.equals(norm2)) return 100;
        
        // Calculate Levenshtein distance-based score
        int distance = levenshteinDistance(norm1, norm2);
        int maxLen = Math.max(norm1.length(), norm2.length());
        
        if (maxLen == 0) return 100;
        
        return Math.max(0, 100 - (distance * 100 / maxLen));
    }
    
    /**
     * Normalize name for comparison
     */
    private String normalizeName(String name) {
        return name.toLowerCase()
                  .replaceAll("[^a-z0-9\\s]", "")
                  .replaceAll("\\s+", " ")
                  .trim();
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
     * Search using fallback API (Trade.gov Consolidated Screening List)
     * Secondary fallback with circuit breaker protection
     */
    @CircuitBreaker(name = "ofac-fallback-api", fallbackMethod = "searchUsingBackupAPI")
    @Retry(name = "ofac-fallback-api")
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    private List<OFACMatch> searchUsingFallbackAPI(String name) {
        log.info("Using fallback API (Trade.gov) for OFAC screening: {}", name);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            // Build query parameters for Trade.gov API
            String url = String.format("%s?fuzzy_name=true&name=%s&size=50",
                fallbackApiUrl, name.replace(" ", "%20"));

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseFallbackResponse(response.getBody(), name);
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Fallback API also failed for name: {}", name, e);
            // Try backup API
            throw new ComplianceScreeningException("Trade.gov API failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Search using backup API (Treasury OFAC direct)
     */
    private List<OFACMatch> searchUsingBackupAPI(String name) {
        log.info("Using backup API for OFAC screening: {}", name);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + ofacApiKey);
            headers.set("Accept", "application/json");
            
            String url = String.format("%s/search?name=%s", 
                backupApiUrl, name.replace(" ", "%20"));
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseBackupResponse(response.getBody(), name);
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("All OFAC APIs failed for name: {}. Defaulting to safe mode.", name, e);
            // In safe mode, we flag for manual review rather than auto-clear
            return createManualReviewFlag(name);
        }
    }
    
    /**
     * Parse fallback API response (Trade.gov format)
     */
    private List<OFACMatch> parseFallbackResponse(Map<String, Object> response, String searchTerm) {
        List<OFACMatch> matches = new ArrayList<>();
        
        if (response.containsKey("results")) {
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            
            for (Map<String, Object> result : results) {
                String name = (String) result.get("name");
                String source = (String) result.get("source");
                List<String> programs = (List<String>) result.get("programs");
                
                if (source != null && source.contains("SDN")) {
                    int score = calculateMatchScore(searchTerm, name);
                    
                    if (score >= matchThreshold) {
                        matches.add(OFACMatch.builder()
                            .sdnId((String) result.get("id"))
                            .name(name)
                            .type((String) result.get("type"))
                            .program(programs != null && !programs.isEmpty() ? programs.get(0) : "Unknown")
                            .score(score)
                            .searchTerm(searchTerm)
                            .build());
                    }
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Parse backup API response
     */
    private List<OFACMatch> parseBackupResponse(Map<String, Object> response, String searchTerm) {
        List<OFACMatch> matches = new ArrayList<>();
        
        if (response.containsKey("entries")) {
            List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
            
            for (Map<String, Object> entry : entries) {
                String name = (String) entry.get("name");
                int score = calculateMatchScore(searchTerm, name);
                
                if (score >= matchThreshold) {
                    matches.add(OFACMatch.builder()
                        .sdnId(String.valueOf(entry.get("uid")))
                        .name(name)
                        .type((String) entry.get("sdnType"))
                        .program((String) entry.get("programList"))
                        .score(score)
                        .searchTerm(searchTerm)
                        .build());
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Create manual review flag when all APIs fail
     */
    private List<OFACMatch> createManualReviewFlag(String name) {
        List<OFACMatch> matches = new ArrayList<>();
        
        matches.add(OFACMatch.builder()
            .sdnId("MANUAL_REVIEW_REQUIRED")
            .name(name)
            .type("MANUAL_REVIEW")
            .program("API_FAILURE")
            .score(0)
            .searchTerm(name)
            .build());
        
        return matches;
    }
    
    /**
     * Parse address search response
     */
    private List<OFACMatch> parseAddressSearchResponse(OFACSearchResponse response, String address) {
        List<OFACMatch> matches = new ArrayList<>();
        
        if (response.getResults() != null) {
            for (OFACSearchResult result : response.getResults()) {
                // Address matching logic
                matches.add(OFACMatch.builder()
                    .sdnId(result.getId())
                    .name(result.getName())
                    .type(result.getType())
                    .program(result.getProgram())
                    .score(85) // Address matches are typically less precise
                    .searchTerm(address)
                    .build());
            }
        }
        
        return matches;
    }
    
    /**
     * Build search URL with parameters
     */
    private String buildSearchUrl(Map<String, String> params) {
        StringBuilder url = new StringBuilder(ofacApiUrl);
        url.append("/search?");
        
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) url.append("&");
            url.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        
        return url.toString();
    }
    
    /**
     * Deduplicate and filter matches by threshold
     */
    private List<OFACMatch> deduplicateAndFilter(List<OFACMatch> matches) {
        Map<String, OFACMatch> uniqueMatches = new HashMap<>();
        
        for (OFACMatch match : matches) {
            String key = match.getSdnId() + "_" + match.getName();
            
            if (!uniqueMatches.containsKey(key) || 
                uniqueMatches.get(key).getScore() < match.getScore()) {
                uniqueMatches.put(key, match);
            }
        }
        
        return uniqueMatches.values().stream()
            .filter(m -> m.getScore() >= matchThreshold)
            .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get highest match score from list
     */
    private int getHighestMatchScore(List<OFACMatch> matches) {
        return matches.stream()
            .mapToInt(OFACMatch::getScore)
            .max()
            .orElse(0);
    }
    
    /**
     * Parse OFAC API response
     */
    private List<OFACMatch> parseSearchResponse(OFACSearchResponse response, String searchTerm) {
        List<OFACMatch> matches = new ArrayList<>();
        
        if (response.getResults() != null) {
            for (OFACSearchResult result : response.getResults()) {
                int score = calculateMatchScore(searchTerm, result.getName());
                
                if (score >= matchThreshold) {
                    matches.add(OFACMatch.builder()
                        .sdnId(result.getId())
                        .name(result.getName())
                        .type(result.getType())
                        .program(result.getProgram())
                        .score(score)
                        .searchTerm(searchTerm)
                        .build());
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Validate screening request
     */
    private void validateScreeningRequest(OFACScreeningRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("OFAC screening request cannot be null");
        }
        
        if (request.getFirstName() == null && request.getLastName() == null) {
            throw new IllegalArgumentException("At least first name or last name must be provided");
        }
        
        // Additional validation logic
        if (request.getFirstName() != null && request.getFirstName().length() < 2) {
            throw new IllegalArgumentException("First name must be at least 2 characters");
        }
        
        if (request.getLastName() != null && request.getLastName().length() < 2) {
            throw new IllegalArgumentException("Last name must be at least 2 characters");
        }
    }
    
    /**
     * Evaluate matches to determine if customer is clean
     */
    private boolean evaluateMatches(List<OFACMatch> matches) {
        if (matches.isEmpty()) {
            return true;
        }
        
        // Check for high-confidence matches
        for (OFACMatch match : matches) {
            if (match.getScore() >= 95) {
                log.warn("High confidence OFAC match found: {} (Score: {})", match.getName(), match.getScore());
                return false;
            }
        }
        
        // Check for multiple medium-confidence matches
        long mediumMatches = matches.stream()
            .filter(m -> m.getScore() >= 85)
            .count();
            
        if (mediumMatches >= 2) {
            log.warn("Multiple medium confidence OFAC matches found: {}", mediumMatches);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get highest match score
     */
    private int getHighestMatchScore(List<OFACMatch> matches) {
        return matches.stream()
            .mapToInt(OFACMatch::getScore)
            .max()
            .orElse(0);
    }
    
    /**
     * Remove duplicate matches and filter by threshold
     */
    private List<OFACMatch> deduplicateAndFilter(List<OFACMatch> matches) {
        return matches.stream()
            .filter(match -> match.getScore() >= matchThreshold)
            .collect(Collectors.toMap(
                match -> match.getSdnId() + "|" + match.getName(),
                match -> match,
                (existing, replacement) -> existing.getScore() > replacement.getScore() ? existing : replacement
            ))
            .values()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
            .collect(Collectors.toList());
    }
    
    /**
     * Build search URL with parameters
     */
    private String buildSearchUrl(Map<String, String> params) {
        StringBuilder url = new StringBuilder(ofacApiUrl);
        url.append("/search?");
        
        params.forEach((key, value) -> {
            url.append(key).append("=").append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)).append("&");
        });
        
        return url.toString();
    }
    
    /**
     * Log screening result for audit trail
     */
    private void logScreeningResult(OFACScreeningRequest request, OFACScreeningResult result) {
        log.info("OFAC screening completed - Customer: {} {}, Clean: {}, Matches: {}, Highest Score: {}", 
            request.getFirstName(), 
            request.getLastName(), 
            result.isClean(), 
            result.getMatchCount(),
            result.getHighestScore());
    }
    
    // Missing dependencies - need to be added
    
    private final BitcoinRpcClient bitcoinRpcClient; // Add this as a field
    private final FingerprintExtractor fingerprintExtractor;
    private final FaceRecognitionExtractor faceRecognitionExtractor;
    private final VoiceRecognitionExtractor voiceRecognitionExtractor;
    private final FingerprintMatcher fingerprintMatcher;
    private final FaceRecognitionMatcher faceRecognitionMatcher;
    private final VoiceRecognitionMatcher voiceRecognitionMatcher;
    
    // Helper classes for API response parsing
    private static class OFACSearchResponse {
        private List<OFACSearchResult> results;
        public List<OFACSearchResult> getResults() { return results; }
        public void setResults(List<OFACSearchResult> results) { this.results = results; }
    }
    
    private static class OFACSearchResult {
        private String id;
        private String name;
        private String type;
        private String program;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getProgram() { return program; }
        public void setProgram(String program) { this.program = program; }
    }
    
    // Additional missing DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OFACMatch {
        private String sdnId;
        private String name;
        private String type;
        private String program;
        private int score;
        private String searchTerm;
    }
    
    // Missing functional interface for Transaction providers
    @FunctionalInterface
    interface BitcoinTransactionProvider {
        BlockchainTransaction getTransaction(String txHash) throws Exception;
    }
    
    // Additional missing DTOs for blockchain transactions
    @lombok.Data
    public static class BlockstreamTransactionInfo {
        private TransactionStatus status;
        private long fee;
        private int size;
        private int vsize;
        private int weight;
        private List<TransactionInput> vin;
        private List<TransactionOutput> vout;
        
        @lombok.Data
        public static class TransactionStatus {
            private boolean confirmed;
            private long blockHeight;
            private long blockTime;
        }
    }
    
    @lombok.Data
    public static class MempoolTransactionInfo {
        private TransactionStatus status;
        private long fee;
        private int size;
        private int vsize;
        private int weight;
        private List<TransactionInput> vin;
        private List<TransactionOutput> vout;
        
        @lombok.Data
        public static class TransactionStatus {
            private boolean confirmed;
            private long blockHeight;
            private long blockTime;
        }
    }
    
    @lombok.Data
    public static class BlockchainInfoTransaction {
        private long blockHeight;
        private long time;
        private long fee;
        private int size;
        private List<TransactionInput> inputs;
        private List<TransactionOutput> out;
    }
    
    @lombok.Data
    public static class TransactionInput {
        private String txid;
        private int vout;
        private String scriptSig;
        private long sequence;
    }
    
    @lombok.Data
    public static class TransactionOutput {
        private long value;
        private String scriptPubKey;
        private List<String> addresses;
    }
    
    // Missing exception class
    public static class CurrencyExchangeException extends RuntimeException {
        public CurrencyExchangeException(String message) {
            super(message);
        }
        
        public CurrencyExchangeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}