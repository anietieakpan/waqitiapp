package com.waqiti.payment.feature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature Flag Service for payment-service module
 * Simplified version for local feature flag management
 */
@Slf4j
@Service
public class FeatureFlagService {
    
    @Value("${feature.fraud-detection.new:true}")
    private boolean useNewFraudDetection;
    
    @Value("${feature.compliance-check.new:true}")
    private boolean useNewComplianceCheck;
    
    @Value("${feature.shadow-mode.enabled:false}")
    private boolean shadowModeEnabled;
    
    private final Map<String, Boolean> featureFlags = new ConcurrentHashMap<>();
    
    public enum Feature {
        USE_NEW_FRAUD_DETECTION,
        USE_NEW_COMPLIANCE_CHECK,
        ENABLE_SHADOW_MODE,
        ENABLE_CACHING,
        ENABLE_ASYNC_PROCESSING;
    }
    
    public boolean isEnabled(Feature feature) {
        switch (feature) {
            case USE_NEW_FRAUD_DETECTION:
                return useNewFraudDetection;
            case USE_NEW_COMPLIANCE_CHECK:
                return useNewComplianceCheck;
            case ENABLE_SHADOW_MODE:
                return shadowModeEnabled;
            case ENABLE_CACHING:
            case ENABLE_ASYNC_PROCESSING:
                return featureFlags.getOrDefault(feature.name(), true);
            default:
                return false;
        }
    }
    
    public boolean isEnabled(Feature feature, String userId) {
        return isEnabled(feature);
    }
    
    public void enable(Feature feature) {
        featureFlags.put(feature.name(), true);
        log.info("Feature enabled: {}", feature);
    }
    
    public void disable(Feature feature) {
        featureFlags.put(feature.name(), false);
        log.info("Feature disabled: {}", feature);
    }
}