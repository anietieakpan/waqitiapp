package com.waqiti.kyc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "kyc.features")
@Data
public class FeatureFlagConfiguration {

    private Map<String, FeatureFlag> flags = new HashMap<>();
    
    public FeatureFlagConfiguration() {
        // Default feature flags
        flags.put("USE_NEW_KYC_SERVICE", new FeatureFlag(false, 0));
        flags.put("ENABLE_KYC_MIGRATION", new FeatureFlag(false, 0));
        flags.put("DUAL_WRITE_MODE", new FeatureFlag(false, 0));
        flags.put("SHADOW_MODE", new FeatureFlag(true, 0));
        flags.put("AUTO_MIGRATION", new FeatureFlag(false, 0));
    }
    
    public boolean isEnabled(String featureName) {
        FeatureFlag flag = flags.get(featureName);
        if (flag == null) {
            return false;
        }
        
        // If percentage is set, use it for gradual rollout
        if (flag.getPercentage() > 0 && flag.getPercentage() < 100) {
            // Simple hash-based percentage rollout
            int hash = featureName.hashCode() + Thread.currentThread().getName().hashCode();
            return (Math.abs(hash) % 100) < flag.getPercentage();
        }
        
        return flag.isEnabled();
    }
    
    public void setFeature(String featureName, boolean enabled) {
        flags.computeIfAbsent(featureName, k -> new FeatureFlag()).setEnabled(enabled);
    }
    
    public void setFeaturePercentage(String featureName, int percentage) {
        flags.computeIfAbsent(featureName, k -> new FeatureFlag()).setPercentage(percentage);
    }
    
    @Data
    public static class FeatureFlag {
        private boolean enabled;
        private int percentage; // For gradual rollout (0-100)
        private String description;
        private Map<String, String> metadata;
        
        public FeatureFlag() {
            this.enabled = false;
            this.percentage = 0;
        }
        
        public FeatureFlag(boolean enabled, int percentage) {
            this.enabled = enabled;
            this.percentage = percentage;
        }
    }
}