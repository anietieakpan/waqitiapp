package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Provider configuration model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderConfiguration {
    
    private String providerId;
    private String providerName;
    private boolean enabled;
    private boolean sandbox;
    
    private String apiBaseUrl;
    private String webhookUrl;
    private String returnUrl;
    private String cancelUrl;
    
    private int timeoutSeconds;
    private int maxRetries;
    private int rateLimitPerMinute;
    
    private boolean autoRetryEnabled;
    private boolean webhooksEnabled;
    private boolean notificationsEnabled;
    
    private Map<String, String> credentials;
    private Map<String, Object> settings;
    private Map<String, String> headers;
    
    public String getCredential(String key) {
        return credentials != null ? credentials.get(key) : null;
    }
    
    public Object getSetting(String key) {
        return settings != null ? settings.get(key) : null;
    }
    
    public String getHeader(String key) {
        return headers != null ? headers.get(key) : null;
    }
    
    public boolean isValid() {
        return providerId != null && !providerId.isEmpty() && 
               providerName != null && !providerName.isEmpty() &&
               apiBaseUrl != null && !apiBaseUrl.isEmpty();
    }
}