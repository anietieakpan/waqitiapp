package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Summary of configuration for monitoring and troubleshooting
 */
@Data
@Builder
public class ConfigurationSummary {
    
    private String[] activeProfiles;
    private Map<String, String> criticalProperties;
    private String javaVersion;
    private String springBootVersion;
    
    public boolean hasProfile(String profile) {
        if (activeProfiles == null) return false;
        for (String activeProfile : activeProfiles) {
            if (profile.equals(activeProfile)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isProductionConfiguration() {
        return hasProfile("prod") || hasProfile("production");
    }
    
    public boolean isDevelopmentConfiguration() {
        return hasProfile("dev") || hasProfile("development") || hasProfile("local");
    }
    
    public int getCriticalPropertiesCount() {
        return criticalProperties != null ? criticalProperties.size() : 0;
    }
}