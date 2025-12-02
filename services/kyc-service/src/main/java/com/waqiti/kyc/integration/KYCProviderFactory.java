package com.waqiti.kyc.integration;

import com.waqiti.kyc.integration.complyadvantage.ComplyAdvantageKYCProvider;
import com.waqiti.kyc.integration.jumio.JumioKYCProvider;
import com.waqiti.kyc.integration.onfido.OnfidoKYCProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KYCProviderFactory {
    
    private final OnfidoKYCProvider onfidoProvider;
    private final JumioKYCProvider jumioProvider;
    private final ComplyAdvantageKYCProvider complyAdvantageProvider;
    
    private final Map<String, KYCProvider> providers = new HashMap<>();
    
    @PostConstruct
    public void init() {
        // Register all available providers
        registerProvider(onfidoProvider);
        registerProvider(jumioProvider);
        registerProvider(complyAdvantageProvider);
        
        log.info("Registered {} KYC providers", providers.size());
    }
    
    private void registerProvider(KYCProvider provider) {
        if (provider.isAvailable()) {
            providers.put(provider.getProviderName().toLowerCase(), provider);
            log.info("Registered KYC provider: {}", provider.getProviderName());
        } else {
            log.warn("KYC provider {} is not available", provider.getProviderName());
        }
    }
    
    public KYCProvider getProvider(String providerName) {
        if (providerName == null) {
            return getDefaultProvider();
        }
        
        KYCProvider provider = providers.get(providerName.toLowerCase());
        if (provider == null) {
            log.warn("Provider {} not found, using default provider", providerName);
            return getDefaultProvider();
        }
        
        return provider;
    }
    
    private KYCProvider getDefaultProvider() {
        // Priority order: Onfido > Jumio > ComplyAdvantage
        if (providers.containsKey("onfido")) {
            return providers.get("onfido");
        } else if (providers.containsKey("jumio")) {
            return providers.get("jumio");
        } else if (providers.containsKey("complyadvantage")) {
            return providers.get("complyadvantage");
        }
        
        throw new IllegalStateException("No KYC providers available");
    }
    
    public Map<String, KYCProvider> getAllProviders() {
        return new HashMap<>(providers);
    }
    
    public boolean hasProvider(String providerName) {
        return providers.containsKey(providerName.toLowerCase());
    }
}