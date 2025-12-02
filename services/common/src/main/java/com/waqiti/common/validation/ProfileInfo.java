package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

/**
 * Information about Spring profiles
 */
@Data
@Builder
public class ProfileInfo {
    
    private String[] activeProfiles;
    private String[] defaultProfiles;
    private boolean hasActiveProfiles;
    
    public boolean isProductionMode() {
        if (activeProfiles == null) return false;
        for (String profile : activeProfiles) {
            if ("prod".equals(profile) || "production".equals(profile)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isDevelopmentMode() {
        if (activeProfiles == null) return false;
        for (String profile : activeProfiles) {
            if ("dev".equals(profile) || "development".equals(profile) || "local".equals(profile)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isTestMode() {
        if (activeProfiles == null) return false;
        for (String profile : activeProfiles) {
            if ("test".equals(profile) || "testing".equals(profile)) {
                return true;
            }
        }
        return false;
    }
}