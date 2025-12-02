package com.waqiti.frauddetection.integration;

import com.waqiti.frauddetection.config.FraudDetectionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production Threat Intelligence Integration Client
 * 
 * Integrates with multiple threat intelligence providers:
 * - VirusTotal API for IP reputation
 * - AbuseIPDB for abuse reports
 * - IBM X-Force for threat data
 * - Custom threat feeds
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ThreatIntelligenceClient {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FraudDetectionProperties properties;
    
    // API endpoints
    private static final String VIRUSTOTAL_API = "https://www.virustotal.com/vtapi/v2/ip-address/report";
    private static final String ABUSEIPDB_API = "https://api.abuseipdb.com/api/v2/check";
    private static final String XFORCE_API = "https://api.xforce.ibmcloud.com/ipr";
    
    /**
     * Check IP against multiple threat intelligence sources
     */
    public ThreatIntelligenceResult checkThreat(String ipAddress) {
        try {
            log.debug("Checking threat intelligence for IP: {}", maskIpAddress(ipAddress));
            
            // Parallel queries to multiple providers for speed
            CompletableFuture<VirusTotalResult> vtFuture = CompletableFuture
                .supplyAsync(() -> queryVirusTotal(ipAddress));
            
            CompletableFuture<AbuseIPDBResult> abuseDbFuture = CompletableFuture
                .supplyAsync(() -> queryAbuseIPDB(ipAddress));
            
            CompletableFuture<XForceResult> xForceFuture = CompletableFuture
                .supplyAsync(() -> queryXForce(ipAddress));
            
            // Wait for all queries to complete (with timeout)
            CompletableFuture.allOf(vtFuture, abuseDbFuture, xForceFuture)
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
            
            // Aggregate results
            return aggregateThreatResults(
                ipAddress,
                vtFuture.get(),
                abuseDbFuture.get(),
                xForceFuture.get()
            );
            
        } catch (Exception e) {
            log.error("Error checking threat intelligence for IP {}: {}", 
                maskIpAddress(ipAddress), e.getMessage(), e);
            
            // Return minimal result with error indicator
            return ThreatIntelligenceResult.builder()
                .ipAddress(maskIpAddress(ipAddress))
                .riskScore(0.3) // Moderate risk when threat intel unavailable
                .categories(List.of("UNKNOWN"))
                .providers(List.of("ERROR"))
                .timestamp(LocalDateTime.now())
                .error("Threat intelligence unavailable")
                .build();
        }
    }
    
    /**
     * Query VirusTotal API for IP reputation
     */
    private VirusTotalResult queryVirusTotal(String ipAddress) {
        try {
            if (!properties.getThreatIntel().getVirusTotal().isEnabled()) {
                return VirusTotalResult.disabled();
            }
            
            String apiKey = properties.getThreatIntel().getVirusTotal().getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("VirusTotal API key not configured");
                return VirusTotalResult.disabled();
            }
            
            String url = UriComponentsBuilder.fromHttpUrl(VIRUSTOTAL_API)
                .queryParam("apikey", apiKey)
                .queryParam("ip", ipAddress)
                .toUriString();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "WaqitiFraudDetection/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseVirusTotalResponse(response.getBody());
            }
            
            return VirusTotalResult.empty();
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return VirusTotalResult.notFound(); // IP not in VirusTotal database
            }
            log.warn("VirusTotal API error for IP {}: {}", maskIpAddress(ipAddress), e.getMessage());
            return VirusTotalResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("VirusTotal query failed for IP {}: {}", maskIpAddress(ipAddress), e.getMessage());
            return VirusTotalResult.error(e.getMessage());
        }
    }
    
    /**
     * Query AbuseIPDB for abuse reports
     */
    private AbuseIPDBResult queryAbuseIPDB(String ipAddress) {
        try {
            if (!properties.getThreatIntel().getAbuseIpDb().isEnabled()) {
                return AbuseIPDBResult.disabled();
            }
            
            String apiKey = properties.getThreatIntel().getAbuseIpDb().getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("AbuseIPDB API key not configured");
                return AbuseIPDBResult.disabled();
            }
            
            String url = UriComponentsBuilder.fromHttpUrl(ABUSEIPDB_API)
                .queryParam("ipAddress", ipAddress)
                .queryParam("maxAgeInDays", "90")
                .queryParam("verbose", "")
                .toUriString();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Key", apiKey);
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseAbuseIPDBResponse(response.getBody());
            }
            
            return AbuseIPDBResult.empty();
            
        } catch (HttpClientErrorException e) {
            log.warn("AbuseIPDB API error for IP {}: {}", maskIpAddress(ipAddress), e.getMessage());
            return AbuseIPDBResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("AbuseIPDB query failed for IP {}: {}", maskIpAddress(ipAddress), e.getMessage());
            return AbuseIPDBResult.error(e.getMessage());
        }
    }
    
    /**
     * Query IBM X-Force for threat data
     */
    private XForceResult queryXForce(String ipAddress) {
        try {
            if (!properties.getThreatIntel().getXForce().isEnabled()) {
                return XForceResult.disabled();
            }
            
            String apiKey = properties.getThreatIntel().getXForce().getApiKey();
            String password = properties.getThreatIntel().getXForce().getPassword();
            
            if (apiKey == null || password == null) {
                log.warn("X-Force credentials not configured");
                return XForceResult.disabled();
            }
            
            String url = XFORCE_API + "/" + ipAddress;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(apiKey, password);
            headers.set("Accept", "application/json");
            headers.set("User-Agent", "WaqitiFraudDetection/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseXForceResponse(response.getBody());
            }
            
            return XForceResult.empty();
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return XForceResult.notFound();
            }
            log.warn("X-Force API error for IP {}: {}", maskIpAddress(ipAddress), e.getMessage());
            return XForceResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("X-Force query failed for IP {}: {}", maskIpAddress(ipAddress), e.getMessage());
            return XForceResult.error(e.getMessage());
        }
    }
    
    /**
     * Parse VirusTotal API response
     */
    private VirusTotalResult parseVirusTotalResponse(Map<String, Object> response) {
        try {
            Integer responseCode = (Integer) response.get("response_code");
            if (responseCode == null || responseCode == 0) {
                return VirusTotalResult.notFound();
            }
            
            Integer positives = (Integer) response.getOrDefault("detected_urls", 0);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> detectedUrls = (List<Map<String, Object>>) response.get("detected_urls");
            
            List<String> categories = new ArrayList<>();
            double riskScore = 0.0;
            
            if (positives != null && positives > 0) {
                categories.add("MALICIOUS_URLS");
                riskScore = Math.min(1.0, positives / 10.0); // Scale based on detections
            }
            
            // Check for additional threat indicators
            @SuppressWarnings("unchecked")
            Map<String, Object> resolutions = (Map<String, Object>) response.get("resolutions");
            if (resolutions != null && !resolutions.isEmpty()) {
                categories.add("DNS_RESOLUTIONS");
                riskScore += 0.2;
            }
            
            return VirusTotalResult.builder()
                .available(true)
                .positiveDetections(positives != null ? positives : 0)
                .totalScans(detectedUrls != null ? detectedUrls.size() : 0)
                .riskScore(Math.min(1.0, riskScore))
                .categories(categories)
                .rawResponse(response)
                .build();
            
        } catch (Exception e) {
            log.warn("Error parsing VirusTotal response: {}", e.getMessage());
            return VirusTotalResult.error("Parse error: " + e.getMessage());
        }
    }
    
    /**
     * Parse AbuseIPDB API response
     */
    private AbuseIPDBResult parseAbuseIPDBResponse(Map<String, Object> response) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                return AbuseIPDBResult.empty();
            }
            
            Integer abuseConfidence = (Integer) data.getOrDefault("abuseConfidencePercentage", 0);
            Integer totalReports = (Integer) data.getOrDefault("totalReports", 0);
            Boolean whitelisted = (Boolean) data.getOrDefault("whitelisted", false);
            
            List<String> categories = new ArrayList<>();
            double riskScore = 0.0;
            
            if (Boolean.TRUE.equals(whitelisted)) {
                categories.add("WHITELISTED");
                riskScore = 0.0;
            } else if (abuseConfidence != null && abuseConfidence > 0) {
                categories.add("ABUSE_REPORTS");
                riskScore = abuseConfidence / 100.0; // Convert percentage to decimal
                
                if (abuseConfidence > 75) categories.add("HIGH_ABUSE");
                if (totalReports != null && totalReports > 10) categories.add("MULTIPLE_REPORTS");
            }
            
            return AbuseIPDBResult.builder()
                .available(true)
                .abuseConfidence(abuseConfidence != null ? abuseConfidence : 0)
                .totalReports(totalReports != null ? totalReports : 0)
                .whitelisted(Boolean.TRUE.equals(whitelisted))
                .riskScore(riskScore)
                .categories(categories)
                .rawResponse(data)
                .build();
            
        } catch (Exception e) {
            log.warn("Error parsing AbuseIPDB response: {}", e.getMessage());
            return AbuseIPDBResult.error("Parse error: " + e.getMessage());
        }
    }
    
    /**
     * Parse IBM X-Force API response
     */
    private XForceResult parseXForceResponse(Map<String, Object> response) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> history = (Map<String, Object>) response.get("history");
            
            List<String> categories = new ArrayList<>();
            double riskScore = 0.0;
            
            if (history != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> historyList = (List<Map<String, Object>>) history.get("history");
                
                if (historyList != null && !historyList.isEmpty()) {
                    for (Map<String, Object> entry : historyList) {
                        String categoryName = (String) entry.get("categoryDescriptions");
                        if (categoryName != null) {
                            categories.add(categoryName.toUpperCase());
                            riskScore += 0.3; // Each malicious category adds risk
                        }
                    }
                }
            }
            
            // Check reputation score
            Object score = response.get("score");
            if (score instanceof Number) {
                double xforceScore = ((Number) score).doubleValue();
                if (xforceScore < 3) { // X-Force scale: 1-10 (lower is worse)
                    riskScore += 0.5;
                    categories.add("LOW_REPUTATION");
                }
            }
            
            return XForceResult.builder()
                .available(true)
                .reputationScore(score instanceof Number ? ((Number) score).doubleValue() : 5.0)
                .riskScore(Math.min(1.0, riskScore))
                .categories(categories)
                .rawResponse(response)
                .build();
            
        } catch (Exception e) {
            log.warn("Error parsing X-Force response: {}", e.getMessage());
            return XForceResult.error("Parse error: " + e.getMessage());
        }
    }
    
    /**
     * Aggregate results from all threat intelligence sources
     */
    private ThreatIntelligenceResult aggregateThreatResults(
            String ipAddress,
            VirusTotalResult vtResult,
            AbuseIPDBResult abuseResult,
            XForceResult xForceResult) {
        
        Set<String> allCategories = new HashSet<>();
        List<String> providers = new ArrayList<>();
        double totalRisk = 0.0;
        int providerCount = 0;
        
        // Aggregate VirusTotal results
        if (vtResult.isAvailable()) {
            providers.add("VirusTotal");
            allCategories.addAll(vtResult.getCategories());
            totalRisk += vtResult.getRiskScore();
            providerCount++;
        }
        
        // Aggregate AbuseIPDB results
        if (abuseResult.isAvailable()) {
            providers.add("AbuseIPDB");
            allCategories.addAll(abuseResult.getCategories());
            totalRisk += abuseResult.getRiskScore();
            providerCount++;
        }
        
        // Aggregate X-Force results
        if (xForceResult.isAvailable()) {
            providers.add("X-Force");
            allCategories.addAll(xForceResult.getCategories());
            totalRisk += xForceResult.getRiskScore();
            providerCount++;
        }
        
        // Calculate average risk score
        double avgRiskScore = providerCount > 0 ? totalRisk / providerCount : 0.0;
        
        // Apply risk amplification if multiple sources agree
        if (providerCount > 1 && avgRiskScore > 0.5) {
            avgRiskScore = Math.min(1.0, avgRiskScore * 1.2); // 20% amplification
        }
        
        return ThreatIntelligenceResult.builder()
            .ipAddress(maskIpAddress(ipAddress))
            .riskScore(avgRiskScore)
            .categories(new ArrayList<>(allCategories))
            .providers(providers)
            .providerCount(providerCount)
            .virusTotalResult(vtResult)
            .abuseIpDbResult(abuseResult)
            .xForceResult(xForceResult)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.length() < 8) {
            return "***";
        }
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.**";
        }
        return ipAddress.substring(0, Math.min(8, ipAddress.length())) + "***";
    }
    
    // Result classes would be defined here or in separate files
    // (VirusTotalResult, AbuseIPDBResult, XForceResult, ThreatIntelligenceResult)
}