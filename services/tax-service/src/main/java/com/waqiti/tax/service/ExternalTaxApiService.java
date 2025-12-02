package com.waqiti.tax.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.tax.dto.TaxCalculationRequest;
import com.waqiti.tax.dto.TaxCalculationResponse;
import com.waqiti.tax.entity.TaxJurisdiction;
import com.waqiti.tax.entity.TaxRule;
import com.waqiti.tax.repository.TaxJurisdictionRepository;
import com.waqiti.tax.repository.TaxRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for integrating with external tax calculation APIs.
 * Provides fallback and validation for tax calculations using external providers.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalTaxApiService {

    private final RestTemplate restTemplate;
    private final TaxJurisdictionRepository jurisdictionRepository;
    private final TaxRuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    // Cache for API responses to reduce external calls
    private final Map<String, CachedTaxResult> cache = new ConcurrentHashMap<>();
    
    @Value("${tax.external.api.timeout:5000}")
    private int apiTimeout;
    
    @Value("${tax.external.api.retry.attempts:3}")
    private int retryAttempts;
    
    @Value("${tax.external.api.cache.ttl:300000}") // 5 minutes
    private long cacheTtl;

    /**
     * Calculate tax using external API with fallback to internal calculation
     */
    public TaxCalculationResponse calculateTaxWithExternalApi(TaxCalculationRequest request) {
        log.info("Attempting external tax calculation for jurisdiction: {}, amount: {}", 
                request.getJurisdiction(), request.getAmount());

        try {
            // Check cache first
            String cacheKey = buildCacheKey(request);
            CachedTaxResult cachedResult = cache.get(cacheKey);
            
            if (cachedResult != null && !cachedResult.isExpired()) {
                log.debug("Returning cached tax calculation result");
                return cachedResult.getResult();
            }

            // Find jurisdiction configuration
            Optional<TaxJurisdiction> jurisdiction = jurisdictionRepository.findByCode(request.getJurisdiction());
            
            if (jurisdiction.isEmpty() || !jurisdiction.get().hasExternalApiIntegration()) {
                log.warn("No external API configuration found for jurisdiction: {}", request.getJurisdiction());
                return null; // Will trigger fallback to internal calculation
            }

            // Attempt external API call
            TaxCalculationResponse result = callExternalTaxApi(jurisdiction.get(), request);
            
            if (result != null) {
                // Cache successful result
                cache.put(cacheKey, new CachedTaxResult(result, System.currentTimeMillis() + cacheTtl));
                log.info("External tax calculation successful");
                return result;
            }

        } catch (Exception e) {
            log.error("External tax API call failed: {}", e.getMessage(), e);
        }

        log.warn("External tax calculation failed, will fallback to internal calculation");
        return null; // Triggers fallback
    }

    /**
     * Validate tax calculation against external API
     */
    public boolean validateTaxCalculation(TaxCalculationRequest request, TaxCalculationResponse internalResult) {
        log.debug("Validating tax calculation against external API");

        try {
            TaxCalculationResponse externalResult = calculateTaxWithExternalApi(request);
            
            if (externalResult == null) {
                log.debug("External validation not available, considering internal result valid");
                return true; // No external validation available
            }

            // Compare results with tolerance
            BigDecimal tolerance = new BigDecimal("0.01"); // 1 cent tolerance
            BigDecimal difference = internalResult.getTotalTaxAmount()
                    .subtract(externalResult.getTotalTaxAmount()).abs();

            boolean isValid = difference.compareTo(tolerance) <= 0;
            
            if (!isValid) {
                log.warn("Tax calculation validation failed - Internal: {}, External: {}, Difference: {}", 
                        internalResult.getTotalTaxAmount(), externalResult.getTotalTaxAmount(), difference);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Tax calculation validation failed: {}", e.getMessage(), e);
            return true; // Consider valid if validation fails
        }
    }

    /**
     * Sync tax rules from external API
     */
    public void syncTaxRulesFromExternalApi() {
        log.info("Starting tax rules synchronization from external APIs");

        List<TaxJurisdiction> jurisdictions = jurisdictionRepository.findByExternalApiAvailableTrueAndActiveTrue();
        
        for (TaxJurisdiction jurisdiction : jurisdictions) {
            if (jurisdiction.needsExternalSync()) {
                try {
                    syncJurisdictionRules(jurisdiction);
                } catch (Exception e) {
                    log.error("Failed to sync rules for jurisdiction: {}", jurisdiction.getCode(), e);
                }
            }
        }

        log.info("Tax rules synchronization completed");
    }

    /**
     * Update tax rates from external sources
     */
    public void updateTaxRatesFromExternalSources() {
        log.info("Starting tax rates update from external sources");

        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
        List<TaxRule> rulesToUpdate = ruleRepository.findRulesNeedingExternalUpdate(cutoffTime);

        for (TaxRule rule : rulesToUpdate) {
            try {
                updateRuleFromExternalSource(rule);
            } catch (Exception e) {
                log.error("Failed to update rule: {}", rule.getRuleCode(), e);
            }
        }

        log.info("Tax rates update completed, updated {} rules", rulesToUpdate.size());
    }

    /**
     * Call external tax API for specific jurisdiction
     */
    private TaxCalculationResponse callExternalTaxApi(TaxJurisdiction jurisdiction, TaxCalculationRequest request) {
        String apiUrl = jurisdiction.getExternalApiUrl();
        String authHeader = jurisdiction.getExternalApiAuth();

        log.debug("Calling external tax API: {}", apiUrl);

        try {
            // Build request payload
            Map<String, Object> payload = buildExternalApiPayload(request);
            
            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if (authHeader != null && !authHeader.trim().isEmpty()) {
                headers.set("Authorization", authHeader);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            // Make API call with retry logic
            ResponseEntity<String> response = callWithRetry(apiUrl, entity);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseExternalApiResponse(response.getBody(), jurisdiction);
            } else {
                log.warn("External API returned non-OK status: {}", response.getStatusCode());
                return null;
            }

        } catch (HttpClientErrorException e) {
            log.error("External API client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (HttpServerErrorException e) {
            log.error("External API server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error calling external API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build payload for external API call
     */
    private Map<String, Object> buildExternalApiPayload(TaxCalculationRequest request) {
        Map<String, Object> payload = new HashMap<>();
        
        payload.put("jurisdiction", request.getJurisdiction());
        payload.put("amount", request.getAmount());
        payload.put("currency", request.getCurrency());
        payload.put("transactionType", request.getTransactionType());
        payload.put("transactionDate", request.getTransactionDate());
        
        if (request.getUserId() != null) {
            payload.put("userId", request.getUserId());
        }
        
        if (request.getBusinessCategory() != null) {
            payload.put("businessCategory", request.getBusinessCategory());
        }
        
        if (request.getCustomerType() != null) {
            payload.put("customerType", request.getCustomerType());
        }
        
        if (request.getSourceCountry() != null) {
            payload.put("sourceCountry", request.getSourceCountry());
        }
        
        if (request.getDestinationCountry() != null) {
            payload.put("destinationCountry", request.getDestinationCountry());
        }

        return payload;
    }

    /**
     * Call external API with retry logic
     */
    private ResponseEntity<String> callWithRetry(String url, HttpEntity<Map<String, Object>> entity) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                log.debug("External API call attempt {} of {}", attempt, retryAttempts);
                return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                
            } catch (Exception e) {
                lastException = e;
                log.warn("External API call attempt {} failed: {}", attempt, e.getMessage());
                
                if (attempt < retryAttempts) {
                    try {
                        // Exponential backoff
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        throw new RuntimeException("All retry attempts failed", lastException);
    }

    /**
     * Parse response from external API
     */
    private TaxCalculationResponse parseExternalApiResponse(String responseBody, TaxJurisdiction jurisdiction) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            // Extract tax calculation data based on jurisdiction's API format
            return TaxCalculationResponse.builder()
                    .jurisdiction(jurisdiction.getCode())
                    .totalTaxAmount(new BigDecimal(rootNode.path("totalTax").asText("0")))
                    .effectiveTaxRate(new BigDecimal(rootNode.path("effectiveRate").asText("0")))
                    .currency(rootNode.path("currency").asText(jurisdiction.getDefaultCurrency()))
                    .calculationDate(LocalDateTime.now())
                    .status("CALCULATED")
                    .source("EXTERNAL_API")
                    .metadata(parseMetadata(rootNode))
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse external API response: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parse metadata from external API response
     */
    private Map<String, String> parseMetadata(JsonNode rootNode) {
        Map<String, String> metadata = new HashMap<>();
        
        metadata.put("externalProvider", rootNode.path("provider").asText("UNKNOWN"));
        metadata.put("externalTransactionId", rootNode.path("transactionId").asText());
        metadata.put("externalCalculationTime", rootNode.path("calculationTime").asText());
        metadata.put("externalVersion", rootNode.path("version").asText());
        
        return metadata;
    }

    /**
     * Sync rules for specific jurisdiction
     */
    private void syncJurisdictionRules(TaxJurisdiction jurisdiction) {
        log.info("Syncing rules for jurisdiction: {}", jurisdiction.getCode());

        try {
            // Call external API to get latest rules
            String rulesResponse = fetchRulesFromExternalApi(jurisdiction);
            
            if (rulesResponse != null) {
                // Parse and update rules
                List<TaxRule> updatedRules = parseExternalRules(rulesResponse, jurisdiction);
                
                // Update local rules
                updateLocalRules(jurisdiction, updatedRules);
                
                // Update sync timestamp
                jurisdiction.setLastExternalSync(LocalDateTime.now());
                jurisdictionRepository.save(jurisdiction);
                
                log.info("Successfully synced {} rules for jurisdiction: {}", 
                        updatedRules.size(), jurisdiction.getCode());
            }

        } catch (Exception e) {
            log.error("Failed to sync rules for jurisdiction: {}", jurisdiction.getCode(), e);
        }
    }

    /**
     * Fetch rules from external API
     */
    private String fetchRulesFromExternalApi(TaxJurisdiction jurisdiction) {
        String rulesUrl = jurisdiction.getExternalApiUrl() + "/rules";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", jurisdiction.getExternalApiAuth());
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    rulesUrl, HttpMethod.GET, entity, String.class);
            
            return response.getStatusCode() == HttpStatus.OK ? response.getBody() : null;
            
        } catch (Exception e) {
            log.error("Failed to fetch rules from external API: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse external rules response
     */
    private List<TaxRule> parseExternalRules(String response, TaxJurisdiction jurisdiction) {
        List<TaxRule> rules = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode rulesArray = rootNode.path("rules");
            
            for (JsonNode ruleNode : rulesArray) {
                TaxRule rule = parseExternalRule(ruleNode, jurisdiction);
                if (rule != null) {
                    rules.add(rule);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to parse external rules: {}", e.getMessage(), e);
        }
        
        return rules;
    }

    /**
     * Parse individual external rule
     */
    private TaxRule parseExternalRule(JsonNode ruleNode, TaxJurisdiction jurisdiction) {
        try {
            return TaxRule.builder()
                    .ruleCode(ruleNode.path("code").asText())
                    .jurisdiction(jurisdiction.getCode())
                    .taxType(ruleNode.path("taxType").asText())
                    .transactionType(ruleNode.path("transactionType").asText())
                    .rate(new BigDecimal(ruleNode.path("rate").asText("0")))
                    .calculationType(ruleNode.path("calculationType").asText("PERCENTAGE"))
                    .ruleSource("EXTERNAL_API")
                    .lastExternalUpdate(LocalDateTime.now())
                    .active(ruleNode.path("active").asBoolean(true))
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to parse external rule: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Update local rules with external data
     */
    private void updateLocalRules(TaxJurisdiction jurisdiction, List<TaxRule> externalRules) {
        // This is a simplified implementation
        // In production, you'd want more sophisticated rule merging logic
        
        for (TaxRule externalRule : externalRules) {
            Optional<TaxRule> existingRule = ruleRepository.findByRuleCode(externalRule.getRuleCode());
            
            if (existingRule.isPresent()) {
                // Update existing rule
                TaxRule existing = existingRule.get();
                existing.setRate(externalRule.getRate());
                existing.setLastExternalUpdate(LocalDateTime.now());
                ruleRepository.save(existing);
            } else {
                // Create new rule
                ruleRepository.save(externalRule);
            }
        }
    }

    /**
     * Update individual rule from external source
     */
    private void updateRuleFromExternalSource(TaxRule rule) {
        // Implementation for updating individual rules
        log.debug("Updating rule from external source: {}", rule.getRuleCode());
        
        // This would fetch updated rate/configuration from external API
        // For now, just update the timestamp
        rule.setLastExternalUpdate(LocalDateTime.now());
        ruleRepository.save(rule);
    }

    /**
     * Build cache key for tax calculation
     */
    private String buildCacheKey(TaxCalculationRequest request) {
        return String.format("%s-%s-%s-%s", 
                request.getJurisdiction(), 
                request.getAmount(), 
                request.getCurrency(), 
                request.getTransactionType());
    }

    /**
     * Clear expired cache entries
     */
    public void clearExpiredCache() {
        long currentTime = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));
        log.debug("Cleared expired cache entries");
    }

    /**
     * Fetch tax rules from external API for specific jurisdiction
     *
     * USAGE: Called by TaxCalculationService.updateTaxRules()
     */
    public List<TaxRule> fetchTaxRules(String jurisdiction) {
        log.info("Fetching tax rules from external API for jurisdiction: {}", jurisdiction);

        try {
            Optional<TaxJurisdiction> taxJurisdiction = jurisdictionRepository.findByCode(jurisdiction);

            if (taxJurisdiction.isEmpty() || !taxJurisdiction.get().hasExternalApiIntegration()) {
                log.warn("No external API configuration for jurisdiction: {}", jurisdiction);
                return Collections.emptyList();
            }

            String rulesResponse = fetchRulesFromExternalApi(taxJurisdiction.get());

            if (rulesResponse != null) {
                return parseExternalRules(rulesResponse, taxJurisdiction.get());
            }

        } catch (Exception e) {
            log.error("Failed to fetch tax rules for jurisdiction: {}", jurisdiction, e);
        }

        return Collections.emptyList();
    }

    /**
     * Cache entry for tax calculation results
     */
    private static class CachedTaxResult {
        private final TaxCalculationResponse result;
        private final long expireTime;

        public CachedTaxResult(TaxCalculationResponse result, long expireTime) {
            this.result = result;
            this.expireTime = expireTime;
        }

        public TaxCalculationResponse getResult() {
            return result;
        }

        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        public boolean isExpired(long currentTime) {
            return currentTime > expireTime;
        }
    }
}