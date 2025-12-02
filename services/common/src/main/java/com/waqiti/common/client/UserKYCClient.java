package com.waqiti.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class UserKYCClient {
    
    private final RestTemplate restTemplate;
    private final String kycServiceUrl;
    
    public UserKYCClient(
            RestTemplate restTemplate,
            @Value("${services.kyc-service.url:http://kyc-service:8080}") String kycServiceUrl) {
        this.restTemplate = restTemplate;
        this.kycServiceUrl = kycServiceUrl;
    }
    
    @Cacheable(value = "userKYCLevel", key = "#userId")
    public String getUserKYCLevel(String userId) {
        try {
            log.debug("Fetching KYC level for user: {}", userId);
            
            String url = kycServiceUrl + "/api/internal/kyc/users/" + userId + "/status";
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object currentLevel = response.getBody().get("currentLevel");
                if (currentLevel != null) {
                    return currentLevel.toString();
                }
            }
            
            log.warn("Failed to fetch KYC level for user: {}, returning NONE", userId);
            return "NONE";
            
        } catch (Exception e) {
            log.error("Error fetching KYC level for user: {}", userId, e);
            return "NONE";
        }
    }
    
    @Cacheable(value = "userKYCStatus", key = "#userId")
    public boolean isUserVerified(String userId, String requiredLevel) {
        try {
            log.debug("Checking KYC verification for user: {} with required level: {}", userId, requiredLevel);
            
            String url = kycServiceUrl + "/api/internal/kyc/users/" + userId + "/verified?level=" + requiredLevel;
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object verified = response.getBody().get("verified");
                if (verified instanceof Boolean) {
                    return (Boolean) verified;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking KYC verification for user: {}", userId, e);
            return false;
        }
    }
}