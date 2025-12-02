package com.waqiti.audit.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SecurityServiceClientFallback implements SecurityServiceClient {

    @Override
    public PermissionValidationResponse validatePermissions(String userId, String resource, String action) {
        log.error("FALLBACK ACTIVATED: BLOCKING permission validation - Security Service unavailable. " +
                "User: {}, Resource: {}, Action: {}", userId, resource, action);
        
        // DENY by default when security service unavailable
        return new PermissionValidationResponse(
                false,
                "Security service temporarily unavailable - access denied for safety",
                Collections.emptyList(),
                Map.of("fallback", true, "reason", "SECURITY_SERVICE_UNAVAILABLE")
        );
    }

    @Override
    public UserSecurityProfile getUserSecurityProfile(String userId) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve security profile - Security Service unavailable. User: {}", userId);
        
        // Return minimal profile with locked status
        return new UserSecurityProfile(
                userId,
                Collections.emptyList(),
                Collections.emptyList(),
                "UNKNOWN",
                false,
                null,
                null,
                0,
                true, // Account locked for safety
                Map.of("fallback", true, "status", "UNAVAILABLE")
        );
    }

    @Override
    public IncidentResponse reportSecurityIncident(SecurityIncidentRequest request) {
        log.error("FALLBACK ACTIVATED: CRITICAL - Security incident cannot be reported! " +
                "Type: {}, Severity: {}, Resource: {}, User: {}", 
                request.incidentType(), request.severity(), request.affectedResource(), request.userId());
        
        // Log critical incident details for manual review
        log.error("====== SECURITY INCIDENT (FALLBACK MODE) ======");
        log.error("Incident Type: {}", request.incidentType());
        log.error("Severity: {}", request.severity());
        log.error("Description: {}", request.description());
        log.error("Affected Resource: {}", request.affectedResource());
        log.error("User ID: {}", request.userId());
        log.error("Source IP: {}", request.sourceIp());
        log.error("Details: {}", request.details());
        log.error("=============================================");
        
        return new IncidentResponse(
                "FALLBACK-" + System.currentTimeMillis(),
                "QUEUED_FOR_MANUAL_REVIEW",
                "security-team",
                "CRITICAL",
                List.of("MANUAL_REVIEW_REQUIRED", "SERVICE_UNAVAILABLE")
        );
    }

    @Override
    public ThreatIntelligenceResponse getThreatIntelligence(String indicator) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve threat intelligence - Security Service unavailable. Indicator: {}", 
                indicator);
        
        // Return unknown threat level for safety
        return new ThreatIntelligenceResponse(
                indicator,
                "UNKNOWN",
                "UNAVAILABLE",
                Collections.emptyList(),
                Map.of("fallback", true, "status", "SERVICE_UNAVAILABLE")
        );
    }

    @Override
    public RiskAnalysisResponse analyzeSecurityRisk(RiskAnalysisRequest request) {
        log.error("FALLBACK ACTIVATED: Cannot analyze security risk - defaulting to HIGH risk. " +
                "User: {}, Activity: {}, Resource: {}", request.userId(), request.activityType(), request.resource());
        
        // Default to HIGH risk when unable to analyze
        return new RiskAnalysisResponse(
                100.0, // Maximum risk score
                "HIGH",
                List.of("SECURITY_SERVICE_UNAVAILABLE", "UNABLE_TO_ANALYZE"),
                Map.of("action", "BLOCK", "reason", "Cannot verify security risk")
        );
    }

    @Override
    public List<SecurityPolicy> getSecurityPolicies(boolean active) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve security policies - Security Service unavailable. Active: {}", active);
        
        // Return empty list - no policies available
        return Collections.emptyList();
    }
}