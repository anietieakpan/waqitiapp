package com.waqiti.audit.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feign client for Security Service integration
 */
@FeignClient(
    name = "security-service", 
    url = "${security-service.url:http://security-service:8080}",
    fallback = SecurityServiceClientFallback.class
)
public interface SecurityServiceClient {
    
    /**
     * Validate user permissions
     */
    @GetMapping("/api/v1/security/permissions/validate")
    PermissionValidationResponse validatePermissions(@RequestParam("userId") String userId,
                                                     @RequestParam("resource") String resource,
                                                     @RequestParam("action") String action);
    
    /**
     * Get user security profile
     */
    @GetMapping("/api/v1/security/users/{userId}/profile")
    UserSecurityProfile getUserSecurityProfile(@PathVariable("userId") String userId);
    
    /**
     * Report security incident
     */
    @PostMapping("/api/v1/security/incidents")
    IncidentResponse reportSecurityIncident(@RequestBody SecurityIncidentRequest request);
    
    /**
     * Get threat intelligence
     */
    @GetMapping("/api/v1/security/threat-intelligence")
    ThreatIntelligenceResponse getThreatIntelligence(@RequestParam("indicator") String indicator);
    
    /**
     * Analyze security risk
     */
    @PostMapping("/api/v1/security/risk/analyze")
    RiskAnalysisResponse analyzeSecurityRisk(@RequestBody RiskAnalysisRequest request);
    
    /**
     * Get security policies
     */
    @GetMapping("/api/v1/security/policies")
    List<SecurityPolicy> getSecurityPolicies(@RequestParam(value = "active", defaultValue = "true") boolean active);
}

// DTOs for Security Service Client

