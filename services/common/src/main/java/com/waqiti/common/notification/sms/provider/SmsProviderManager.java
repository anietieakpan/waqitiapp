package com.waqiti.common.notification.sms.provider;

import com.waqiti.common.notification.sms.dto.SmsMessage;
import com.waqiti.common.notification.sms.dto.SmsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages SMS providers and routes messages to appropriate providers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsProviderManager {
    
    private final List<SmsProvider> providers;
    
    public SmsProvider getProvider(String providerId) {
        return providers.stream()
            .filter(provider -> provider.getProviderId().equals(providerId))
            .findFirst()
            .orElse(getDefaultProvider());
    }
    
    public SmsProvider getDefaultProvider() {
        return providers.stream()
            .filter(SmsProvider::isActive)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No active SMS providers available"));
    }
    
    /**
     * Get primary SMS provider
     */
    public SmsProvider getPrimaryProvider() {
        return providers.stream()
            .filter(SmsProvider::isActive)
            .filter(SmsProvider::isPrimary)
            .findFirst()
            .orElse(getDefaultProvider());
    }
    
    /**
     * Get failover SMS provider
     */
    public SmsProvider getFailoverProvider() {
        return providers.stream()
            .filter(SmsProvider::isActive)
            .filter(provider -> !provider.isPrimary())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if failover provider is available
     */
    public boolean hasFailoverProvider() {
        return getFailoverProvider() != null;
    }
    
    /**
     * Validate all providers
     */
    public boolean validateProviders() {
        try {
            for (SmsProvider provider : providers) {
                if (!provider.validateConfiguration()) {
                    log.error("Provider {} failed validation", provider.getProviderId());
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Provider validation failed", e);
            return false;
        }
    }
    
    /**
     * Get all active providers
     */
    public List<SmsProvider> getActiveProviders() {
        return providers.stream()
            .filter(SmsProvider::isActive)
            .toList();
    }
    
    public interface SmsProvider {
        String getProviderId();
        boolean isActive();
        boolean isPrimary();
        boolean validateConfiguration();
        CompletableFuture<SmsResult> sendSms(SmsMessage message);
        boolean sendSms(String to, String message, Map<String, Object> options);
    }
}