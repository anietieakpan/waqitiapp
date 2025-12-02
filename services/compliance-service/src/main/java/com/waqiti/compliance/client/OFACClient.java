package com.waqiti.compliance.client;

import com.waqiti.compliance.dto.ofac.*;
import com.waqiti.common.compliance.MultiProviderOFACScreeningService;
import com.waqiti.common.compliance.model.OFACScreeningRequest;
import com.waqiti.common.compliance.model.OFACScreeningResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client for OFAC (Office of Foreign Assets Control) sanctions screening.
 * Integrates with the OFAC API to check entities against sanctions lists.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OFACClient {
    
    private final MultiProviderOFACScreeningService multiProviderScreeningService;
    
    @Value("${ofac.api.key}")
    private String apiKey;
    
    @Value("${ofac.api.url:https://api.ofac-api.com/v3}")
    private String apiUrl;
    
    @Value("${ofac.api.timeout:30000}")
    private int timeout;
    
    private final RestTemplate restTemplate;
    
    public OFACClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Search the SDN (Specially Designated Nationals) list
     */
    @CircuitBreaker(name = "ofac-api", fallbackMethod = "fallbackSDNSearch")
    @Retry(name = "ofac-api")
    @TimeLimiter(name = "ofac-api")
    public CompletableFuture<OFACSearchResponse> searchSDNList(String name, String address) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Searching OFAC SDN list for name: {}, address: {}", name, address);
                
                URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                    .path("/sdn/search")
                    .queryParam("name", name)
                    .queryParam("address", address)
                    .queryParam("minScore", "85")
                    .build()
                    .toUri();
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                
                ResponseEntity<OFACSearchResponse> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    entity,
                    OFACSearchResponse.class
                );
                
                log.info("OFAC SDN search completed with {} matches", 
                        response.getBody() != null ? response.getBody().getMatchCount() : 0);
                
                return response.getBody();
                
            } catch (Exception e) {
                log.error("Error searching OFAC SDN list", e);
                throw new OFACServiceException("Failed to search SDN list", e);
            }
        });
    }
    
    /**
     * Search the Consolidated Sanctions List
     */
    @CircuitBreaker(name = "ofac-api", fallbackMethod = "fallbackConsolidatedSearch")
    @Retry(name = "ofac-api")
    public OFACSearchResponse searchConsolidatedList(String name, String address) {
        try {
            log.info("Searching OFAC Consolidated list for name: {}", name);
            
            URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .path("/consolidated/search")
                .queryParam("name", name)
                .queryParam("address", address)
                .queryParam("threshold", "0.85")
                .build()
                .toUri();
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<OFACSearchResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                OFACSearchResponse.class
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Error searching Consolidated list", e);
            throw new OFACServiceException("Failed to search Consolidated list", e);
        }
    }
    
    /**
     * Batch screening for multiple entities
     */
    @CircuitBreaker(name = "ofac-api")
    @Retry(name = "ofac-api")
    public List<OFACSearchResponse> batchScreening(List<OFACBatchRequest> entities) {
        try {
            log.info("Performing batch OFAC screening for {} entities", entities.size());
            
            URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .path("/batch/screen")
                .build()
                .toUri();
            
            HttpHeaders headers = createHeaders();
            HttpEntity<List<OFACBatchRequest>> entity = new HttpEntity<>(entities, headers);
            
            ResponseEntity<OFACBatchResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                OFACBatchResponse.class
            );
            
            return response.getBody() != null ? response.getBody().getResults() : new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Error in batch screening", e);
            throw new OFACServiceException("Failed to perform batch screening", e);
        }
    }
    
    /**
     * Download the latest SDN list for offline screening
     */
    public void downloadSDNList() {
        try {
            log.info("Downloading latest OFAC SDN list");
            
            URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .path("/sdn/download")
                .queryParam("format", "json")
                .build()
                .toUri();
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<SDNListData> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                SDNListData.class
            );
            
            // Store the SDN list in local database for offline screening
            if (response.getBody() != null) {
                storeSANListLocally(response.getBody());
            }
            
            log.info("SDN list download completed");
            
        } catch (Exception e) {
            log.error("Error downloading SDN list", e);
            throw new OFACServiceException("Failed to download SDN list", e);
        }
    }
    
    /**
     * Get details of a specific SDN entry
     */
    public SDNEntry getSDNEntry(String sdnId) {
        try {
            log.info("Getting SDN entry details for ID: {}", sdnId);
            
            URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .path("/sdn/{id}")
                .buildAndExpand(sdnId)
                .toUri();
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<SDNEntry> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                SDNEntry.class
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Error getting SDN entry", e);
            throw new OFACServiceException("Failed to get SDN entry", e);
        }
    }
    
    /**
     * Create headers for API requests
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
    
    /**
     * Store SDN list locally for offline screening
     */
    private void storeSANListLocally(SDNListData sdnList) {
        // Implementation to store SDN list in local database
        log.info("Storing {} SDN entries locally", sdnList.getEntries().size());
        // This would typically save to a database for offline screening capability
    }
    
    /**
     * ENHANCED FALLBACK METHOD WITH MULTI-PROVIDER REDUNDANCY
     * 
     * This fallback method now uses the multi-provider OFAC screening service
     * instead of returning "manual review required", eliminating the critical
     * security vulnerability where sanctions screening could be bypassed.
     */
    public CompletableFuture<OFACSearchResponse> fallbackSDNSearch(String name, String address, Exception ex) {
        log.warn("Primary OFAC SDN search failed, activating multi-provider fallback. Error: {}", ex.getMessage());
        
        try {
            // Use multi-provider screening service as fallback
            OFACScreeningRequest screeningRequest = OFACScreeningRequest.builder()
                .entityName(name)
                .address(address)
                .screeningType("SDN_SEARCH")
                .requestedAt(java.time.LocalDateTime.now())
                .build();
            
            OFACScreeningResult screeningResult = multiProviderScreeningService.screenEntity(screeningRequest);
            
            // Convert multi-provider result to legacy format
            return CompletableFuture.completedFuture(
                OFACSearchResponse.builder()
                    .hasMatches(screeningResult.isMatch())
                    .matchCount(screeningResult.getMatches() != null ? screeningResult.getMatches().size() : 0)
                    .matches(convertToLegacyMatches(screeningResult.getMatches()))
                    .confidenceScore(screeningResult.getConfidenceScore())
                    .requiresManualReview(screeningResult.isRequiresInvestigation())
                    .providersChecked(screeningResult.getProvidersChecked())
                    .riskLevel(screeningResult.getRiskLevel().toString())
                    .screeningStatus("MULTI_PROVIDER_FALLBACK_SUCCESS")
                    .errorMessage(null) // No error - fallback succeeded
                    .build()
            );
            
        } catch (Exception fallbackError) {
            log.error("CRITICAL: Multi-provider OFAC fallback also failed for entity: {}", name, fallbackError);
            
            // Only now return manual review as last resort, but with clear indication this is a system failure
            return CompletableFuture.completedFuture(
                OFACSearchResponse.builder()
                    .hasMatches(true) // FAIL SECURE - assume match until proven otherwise
                    .matchCount(0)
                    .requiresManualReview(true)
                    .screeningStatus("CRITICAL_SYSTEM_FAILURE")
                    .errorMessage("CRITICAL: All OFAC providers failed - immediate manual review required. " +
                                "Primary error: " + ex.getMessage() + 
                                ". Fallback error: " + fallbackError.getMessage())
                    .build()
            );
        }
    }
    
    /**
     * Fallback method for Consolidated search
     */
    public OFACSearchResponse fallbackConsolidatedSearch(String name, String address, Exception ex) {
        log.warn("OFAC Consolidated search failed, using fallback. Error: {}", ex.getMessage());
        
        return OFACSearchResponse.builder()
            .hasMatches(true) // Fail secure
            .matchCount(0)
            .requiresManualReview(true)
            .errorMessage("OFAC service unavailable - manual review required")
            .build();
    }
    
    /**
     * Custom exception for OFAC service errors
     */
    public static class OFACServiceException extends RuntimeException {
        public OFACServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}