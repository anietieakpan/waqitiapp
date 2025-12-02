package com.waqiti.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Failure Analysis Service for analyzing and tracking failures
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FailureAnalysisService {
    
    private final AuditService auditService;
    
    public void analyzePaymentFailure(String paymentId, String failureReason, Object failureData) {
        log.info("Analyzing payment failure for paymentId: {}, reason: {}", paymentId, failureReason);
        
        // Perform failure analysis
        String pattern = identifyFailurePattern(failureReason);
        
        // Log the analysis
        auditService.logEvent("PAYMENT_FAILURE_ANALYSIS", 
            java.util.Map.of(
                "paymentId", paymentId,
                "failureReason", failureReason,
                "failurePattern", pattern,
                "analysisTimestamp", java.time.LocalDateTime.now()
            ));
    }
    
    public void analyzeSystemFailure(String componentId, String failureType, Object failureData) {
        log.info("Analyzing system failure for component: {}, type: {}", componentId, failureType);
        
        String severity = determineSeverity(failureType);
        
        auditService.logEvent("SYSTEM_FAILURE_ANALYSIS",
            java.util.Map.of(
                "componentId", componentId,
                "failureType", failureType,
                "severity", severity,
                "analysisTimestamp", java.time.LocalDateTime.now()
            ));
    }
    
    private String identifyFailurePattern(String failureReason) {
        if (failureReason.contains("timeout")) return "TIMEOUT_PATTERN";
        if (failureReason.contains("network")) return "NETWORK_PATTERN";
        if (failureReason.contains("auth")) return "AUTHENTICATION_PATTERN";
        if (failureReason.contains("validation")) return "VALIDATION_PATTERN";
        return "UNKNOWN_PATTERN";
    }
    
    private String determineSeverity(String failureType) {
        if (failureType.contains("critical") || failureType.contains("security")) return "HIGH";
        if (failureType.contains("warning") || failureType.contains("timeout")) return "MEDIUM";
        return "LOW";
    }
}