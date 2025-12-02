package com.waqiti.kyc.integration.complyadvantage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.kyc.dto.*;
import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import com.waqiti.kyc.exception.KYCProviderException;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.provider.AbstractKYCProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ComplyAdvantage KYC provider implementation
 * Specializes in AML screening, PEP checks, and sanctions screening
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ComplyAdvantageKYCProvider extends AbstractKYCProvider implements KYCProvider {
    
    private final ObjectMapper objectMapper;
    private String apiKey;
    
    @Override
    protected void configureHeaders(HttpHeaders headers, Map<String, String> config) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        
        String apiKey = config.get("apiKey");
        if (apiKey != null) {
            headers.set("Authorization", "Bearer " + apiKey);
        }
    }
    
    @Override
    protected void doInitialize(Map<String, String> config) {
        validateConfiguration("apiKey", "apiUrl");
        this.apiKey = config.get("apiKey");
    }
    
    @Override
    protected boolean performHealthCheck() {
        try {
            // Check API connectivity by fetching tags
            Map<String, Object> response = get("/searches/tags", Map.class);
            return response != null;
        } catch (Exception e) {
            log.debug("ComplyAdvantage health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String createVerificationSession(String userId, KYCVerificationRequest request) {
        try {
            log.info("Creating ComplyAdvantage screening session for user: {}", userId);
            
            // Create a search request for the individual
            Map<String, Object> searchRequest = buildSearchRequest(userId, request);
            Map<String, Object> response = post("/searches", searchRequest, Map.class);
            
            String searchId = (String) response.get("id");
            log.info("Created ComplyAdvantage search: {}", searchId);
            
            // If monitoring is requested, create a monitored entity
            if (request.isEnableMonitoring()) {
                createMonitoredEntity(searchId, userId, request);
            }
            
            return searchId;
            
        } catch (Exception e) {
            throw new KYCProviderException("Failed to create ComplyAdvantage verification session", providerName, e);
        }
    }
    
    @Override
    public Map<String, Object> getVerificationStatus(String sessionId) {
        try {
            Map<String, Object> search = get("/searches/" + sessionId, Map.class);
            return convertSearchToStatus(search);
        } catch (Exception e) {
            log.debug("Search {} not found in ComplyAdvantage", sessionId);
            return Map.of();
        }
    }
    
    @Override
    public Map<String, Object> getVerificationResults(String sessionId) {
        try {
            Map<String, Object> search = get("/searches/" + sessionId, Map.class);
            List<Map<String, Object>> hits = (List<Map<String, Object>>) search.get("hits");
            
            return convertSearchToResults(search, hits);
        } catch (Exception e) {
            throw new KYCProviderException("Failed to get verification results", providerName, e);
        }
    }
    
    @Override
    public void cancelVerificationSession(String sessionId) {
        // ComplyAdvantage searches cannot be cancelled, but we can stop monitoring
        try {
            Map<String, Object> search = get("/searches/" + sessionId, Map.class);
            String entityId = (String) search.get("monitored_entity_id");
            
            if (entityId != null) {
                delete("/monitored_entities/" + entityId);
                log.info("Stopped monitoring for search: {}", sessionId);
            }
        } catch (Exception e) {
            log.warn("Failed to stop monitoring for session: {}", sessionId, e);
        }
    }
    
    @Override
    public String uploadDocument(String sessionId, byte[] documentData, String documentType) {
        // ComplyAdvantage doesn't support document upload - it's a screening service
        // Log the attempt and provide guidance
        log.warn("Document upload attempted on ComplyAdvantage provider for session: {}. " +
                "ComplyAdvantage is an AML screening service and does not support document verification. " +
                "Consider using Onfido or Jumio for document verification.", sessionId);
        
        // Instead of throwing exception, return a special indicator that document upload
        // should be delegated to another provider
        return "CA_DOCUMENT_NOT_SUPPORTED_" + sessionId;
    }
    
    @Override
    public Map<String, String> extractDocumentData(String documentId) {
        // ComplyAdvantage doesn't handle documents
        log.warn("Document extraction attempted on ComplyAdvantage provider for document: {}. " +
                "ComplyAdvantage is an AML screening service and does not support document processing.", documentId);
        
        // Return empty map with special indicator instead of throwing exception
        Map<String, String> result = new HashMap<>();
        result.put("error", "DOCUMENT_EXTRACTION_NOT_SUPPORTED");
        result.put("provider", "ComplyAdvantage");
        result.put("message", "ComplyAdvantage is an AML screening service and does not support document processing");
        result.put("recommendation", "Use Onfido or Jumio providers for document verification");
        return result;
    }
    
    @Override
    public void processWebhook(Map<String, Object> webhookData) {
        log.debug("Processing ComplyAdvantage webhook: {}", webhookData.get("type"));
        
        String type = (String) webhookData.get("type");
        switch (type) {
            case "search.created":
            case "search.updated":
                processSearchWebhook(webhookData);
                break;
            case "monitored_entity.updated":
                processMonitoringWebhook(webhookData);
                break;
            default:
                log.warn("Unknown webhook type: {}", type);
        }
    }
    
    @Override
    public List<String> getSupportedDocumentTypes() {
        // ComplyAdvantage doesn't verify documents
        return List.of();
    }
    
    @Override
    public List<String> getSupportedCountries() {
        // ComplyAdvantage supports global screening
        return List.of("GLOBAL");
    }
    
    @Override
    public Map<String, Boolean> getFeatures() {
        return Map.of(
            "aml_screening", true,
            "pep_screening", true,
            "sanctions_screening", true,
            "adverse_media", true,
            "ongoing_monitoring", true,
            "batch_screening", true,
            "risk_scoring", true,
            "watchlist_screening", true,
            "entity_resolution", true,
            "webhooks", true
        );
    }
    
    // Helper methods
    
    private Map<String, Object> buildSearchRequest(String userId, KYCVerificationRequest request) {
        Map<String, Object> searchRequest = new HashMap<>();
        
        // Search terms
        Map<String, Object> searchTerm = new HashMap<>();
        searchTerm.put("first_name", request.getFirstName());
        searchTerm.put("last_name", request.getLastName());
        
        if (request.getMiddleName() != null) {
            searchTerm.put("middle_name", request.getMiddleName());
        }
        
        if (request.getDateOfBirth() != null) {
            searchTerm.put("year", extractYear(request.getDateOfBirth()));
        }
        
        searchRequest.put("search_term", searchTerm);
        
        // Filters based on verification level
        List<String> filters = new ArrayList<>();
        switch (request.getVerificationLevel()) {
            case "BASIC":
                filters.add("sanctions");
                break;
            case "INTERMEDIATE":
                filters.add("sanctions");
                filters.add("pep");
                filters.add("pep-class-1");
                filters.add("pep-class-2");
                break;
            case "ADVANCED":
                filters.add("sanctions");
                filters.add("pep");
                filters.add("pep-class-1");
                filters.add("pep-class-2");
                filters.add("pep-class-3");
                filters.add("adverse-media");
                filters.add("adverse-media-financial-crime");
                filters.add("adverse-media-violent-crime");
                filters.add("adverse-media-sexual-crime");
                filters.add("adverse-media-terrorism");
                filters.add("adverse-media-fraud");
                filters.add("adverse-media-narcotics");
                filters.add("adverse-media-general");
                break;
        }
        
        searchRequest.put("filters", Map.of("types", filters));
        
        // Additional parameters
        searchRequest.put("share_url", 1); // Generate shareable URL
        searchRequest.put("client_ref", userId);
        
        // Tags for organization
        List<String> tags = new ArrayList<>();
        if (request.getTags() != null) {
            tags.addAll(request.getTags());
        }
        tags.add("verification_level:" + request.getVerificationLevel());
        searchRequest.put("tags", tags);
        
        return searchRequest;
    }
    
    private void createMonitoredEntity(String searchId, String userId, KYCVerificationRequest request) {
        try {
            Map<String, Object> monitoringRequest = Map.of(
                "search_id", searchId,
                "client_ref", userId,
                "is_monitored", true,
                "tags", List.of("user:" + userId, "auto_monitored")
            );
            
            post("/monitored_entities", monitoringRequest, Map.class);
            log.info("Created monitored entity for search: {}", searchId);
            
        } catch (Exception e) {
            log.error("Failed to create monitored entity", e);
        }
    }
    
    private Map<String, Object> convertSearchToStatus(Map<String, Object> search) {
        Map<String, Object> status = new HashMap<>();
        status.put("sessionId", search.get("id"));
        status.put("status", determineSearchStatus(search));
        status.put("createdAt", search.get("created_at"));
        status.put("updatedAt", search.get("updated_at"));
        status.put("totalHits", search.get("total_hits"));
        status.put("shareUrl", search.get("share_url"));
        
        return status;
    }
    
    private String determineSearchStatus(Map<String, Object> search) {
        Integer totalHits = (Integer) search.get("total_hits");
        
        if (totalHits == null) {
            return "PENDING";
        } else if (totalHits == 0) {
            return "CLEAR";
        } else {
            // Check if any hits are confirmed matches
            List<Map<String, Object>> hits = (List<Map<String, Object>>) search.get("hits");
            if (hits != null) {
                boolean hasConfirmedMatch = hits.stream()
                    .anyMatch(hit -> "true_positive".equals(hit.get("match_status")));
                
                if (hasConfirmedMatch) {
                    return "MATCH_FOUND";
                } else {
                    return "POTENTIAL_MATCH";
                }
            }
            return "REVIEW_REQUIRED";
        }
    }
    
    private Map<String, Object> convertSearchToResults(Map<String, Object> search, 
                                                      List<Map<String, Object>> hits) {
        Map<String, Object> results = new HashMap<>();
        results.put("searchId", search.get("id"));
        results.put("status", determineSearchStatus(search));
        results.put("totalHits", search.get("total_hits"));
        results.put("shareUrl", search.get("share_url"));
        
        // Process hits
        if (hits != null && !hits.isEmpty()) {
            List<Map<String, Object>> processedHits = hits.stream()
                .map(this::processHit)
                .collect(Collectors.toList());
            
            results.put("matches", processedHits);
            
            // Calculate risk score
            int riskScore = calculateRiskScore(hits);
            results.put("riskScore", riskScore);
            results.put("riskLevel", determineRiskLevel(riskScore));
        } else {
            results.put("matches", List.of());
            results.put("riskScore", 0);
            results.put("riskLevel", "LOW");
        }
        
        return results;
    }
    
    private Map<String, Object> processHit(Map<String, Object> hit) {
        Map<String, Object> processedHit = new HashMap<>();
        
        processedHit.put("id", hit.get("id"));
        processedHit.put("name", hit.get("name"));
        processedHit.put("matchStrength", hit.get("match_strength"));
        processedHit.put("matchStatus", hit.get("match_status"));
        processedHit.put("isPep", hit.get("is_pep"));
        processedHit.put("isSanction", hit.get("is_sanction"));
        
        // Extract entity details
        Map<String, Object> entity = (Map<String, Object>) hit.get("doc");
        if (entity != null) {
            processedHit.put("entityType", entity.get("entity_type"));
            processedHit.put("nationality", entity.get("nationality"));
            processedHit.put("dateOfBirth", entity.get("date_of_birth"));
            
            // Sources
            List<Map<String, Object>> sources = (List<Map<String, Object>>) entity.get("sources");
            if (sources != null) {
                processedHit.put("sources", sources.stream()
                    .map(s -> (String) s.get("name"))
                    .collect(Collectors.toList()));
            }
            
            // Types (sanctions, PEP, etc.)
            List<Map<String, Object>> types = (List<Map<String, Object>>) entity.get("types");
            if (types != null) {
                processedHit.put("types", types.stream()
                    .map(t -> (String) t.get("name"))
                    .collect(Collectors.toList()));
            }
        }
        
        return processedHit;
    }
    
    private int calculateRiskScore(List<Map<String, Object>> hits) {
        int score = 0;
        
        for (Map<String, Object> hit : hits) {
            String matchStatus = (String) hit.get("match_status");
            if ("true_positive".equals(matchStatus)) {
                score += 50;
            } else if ("possible_match".equals(matchStatus)) {
                score += 20;
            }
            
            Boolean isPep = (Boolean) hit.get("is_pep");
            if (Boolean.TRUE.equals(isPep)) {
                score += 30;
            }
            
            Boolean isSanction = (Boolean) hit.get("is_sanction");
            if (Boolean.TRUE.equals(isSanction)) {
                score += 40;
            }
            
            // Check for adverse media
            Map<String, Object> entity = (Map<String, Object>) hit.get("doc");
            if (entity != null) {
                Map<String, Object> media = (Map<String, Object>) entity.get("media");
                if (media != null && !media.isEmpty()) {
                    score += 25;
                }
            }
        }
        
        return Math.min(score, 100); // Cap at 100
    }
    
    private String determineRiskLevel(int riskScore) {
        if (riskScore >= 70) {
            return "HIGH";
        } else if (riskScore >= 40) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    private void processSearchWebhook(Map<String, Object> webhookData) {
        Map<String, Object> search = (Map<String, Object>) webhookData.get("data");
        if (search != null) {
            String searchId = (String) search.get("id");
            log.info("Search {} updated via webhook", searchId);
        }
    }
    
    private void processMonitoringWebhook(Map<String, Object> webhookData) {
        Map<String, Object> entity = (Map<String, Object>) webhookData.get("data");
        if (entity != null) {
            String entityId = (String) entity.get("id");
            log.info("Monitored entity {} updated via webhook", entityId);
        }
    }
    
    private String extractYear(String dateOfBirth) {
        try {
            return dateOfBirth.substring(0, 4);
        } catch (Exception e) {
            return null;
        }
    }
    
    // KYCProvider interface implementations
    
    @Override
    public VerificationResult verifyIdentity(String userId, Map<String, Object> identityData) {
        log.warn("Identity verification not supported by ComplyAdvantage provider");
        return VerificationResult.builder()
                .success(false)
                .score(0)
                .status("NOT_SUPPORTED")
                .provider(getProviderName())
                .errorMessage("Identity verification not supported by ComplyAdvantage")
                .build();
    }

    @Override
    public VerificationResult verifyDocument(DocumentVerificationRequest request) {
        log.warn("Document verification not supported by ComplyAdvantage provider");
        return VerificationResult.builder()
                .success(false)
                .score(0)
                .status("NOT_SUPPORTED")
                .provider(getProviderName())
                .errorMessage("Document verification not supported by ComplyAdvantage")
                .build();
    }

    @Override
    public VerificationResult verifySelfie(Map<String, Object> selfieData) {
        log.warn("Selfie verification not supported by ComplyAdvantage provider");
        return VerificationResult.builder()
                .success(false)
                .score(0)
                .status("NOT_SUPPORTED")
                .provider(getProviderName())
                .errorMessage("Selfie verification not supported by ComplyAdvantage")
                .build();
    }

    @Override
    public VerificationResult verifyAddress(AddressVerificationRequest request) {
        log.warn("Address verification not supported by ComplyAdvantage provider");
        return VerificationResult.builder()
                .success(false)
                .score(0)
                .status("NOT_SUPPORTED")
                .provider(getProviderName())
                .errorMessage("Address verification not supported by ComplyAdvantage")
                .build();
    }

    @Override
    public VerificationResult performAMLCheck(AMLScreeningRequest request) {
        try {
            log.info("ComplyAdvantage AML screening for user: {}", request.getUserId());
            
            // Build search request
            Map<String, Object> searchRequest = new HashMap<>();
            
            Map<String, Object> searchTerm = new HashMap<>();
            searchTerm.put("first_name", request.getFirstName());
            searchTerm.put("last_name", request.getLastName());
            
            if (request.getDateOfBirth() != null) {
                searchTerm.put("year", extractYear(request.getDateOfBirth()));
            }
            
            searchRequest.put("search_term", searchTerm);
            
            // Configure filters
            List<String> filters = new ArrayList<>();
            if (request.isIncludeSanctions()) filters.add("sanctions");
            if (request.isIncludePEP()) {
                filters.add("pep");
                filters.add("pep-class-1");
                filters.add("pep-class-2");
                filters.add("pep-class-3");
            }
            if (request.isIncludeAdverseMedia()) {
                filters.add("adverse-media");
                filters.add("adverse-media-financial-crime");
                filters.add("adverse-media-violent-crime");
            }
            
            searchRequest.put("filters", Map.of("types", filters));
            searchRequest.put("client_ref", request.getUserId());
            
            // Perform search
            Map<String, Object> response = post("/searches", searchRequest, Map.class);
            
            // Process results
            Integer totalHits = (Integer) response.get("total_hits");
            List<Map<String, Object>> hits = (List<Map<String, Object>>) response.get("hits");
            
            boolean success = totalHits != null && totalHits == 0;
            int score = success ? 95 : calculateRiskScore(hits != null ? hits : List.of());
            String status = totalHits == null ? "PENDING" : 
                           totalHits == 0 ? "CLEAR" : "POTENTIAL_MATCH";
            
            return VerificationResult.builder()
                    .verificationId((String) response.get("id"))
                    .success(success)
                    .score(score)
                    .status(status)
                    .provider(getProviderName())
                    .verifiedAt(LocalDateTime.now())
                    .details(response)
                    .build();
                    
        } catch (Exception e) {
            log.error("ComplyAdvantage AML check failed", e);
            return VerificationResult.builder()
                    .success(false)
                    .score(0)
                    .status("ERROR")
                    .provider(getProviderName())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public String getProviderName() {
        return "complyadvantage";
    }

    @Override
    public boolean isAvailable() {
        return performHealthCheck();
    }
}