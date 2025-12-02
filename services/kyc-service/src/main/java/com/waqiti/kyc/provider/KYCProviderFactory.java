package com.waqiti.kyc.provider;

import com.waqiti.kyc.config.KYCProperties;
import com.waqiti.kyc.exception.KYCProviderException;
import com.waqiti.kyc.integration.complyadvantage.ComplyAdvantageKYCProvider;
import com.waqiti.kyc.integration.jumio.JumioKYCProvider;
import com.waqiti.kyc.integration.onfido.OnfidoKYCProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing KYC provider instances.
 * Supports dynamic provider registration and configuration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KYCProviderFactory {
    
    private final ApplicationContext applicationContext;
    private final KYCProperties kycProperties;
    private final Map<String, KYCProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends KYCProvider>> providerClasses = new HashMap<>();
    
    @PostConstruct
    public void initialize() {
        // Register known provider classes
        registerProviderClass("ONFIDO", OnfidoKYCProvider.class);
        registerProviderClass("JUMIO", JumioKYCProvider.class);
        registerProviderClass("COMPLY_ADVANTAGE", ComplyAdvantageKYCProvider.class);
        
        // Initialize enabled providers
        initializeProviders();
    }
    
    /**
     * Register a provider class for dynamic instantiation
     * @param providerName The provider identifier
     * @param providerClass The provider implementation class
     */
    public void registerProviderClass(String providerName, Class<? extends KYCProvider> providerClass) {
        providerClasses.put(providerName.toUpperCase(), providerClass);
        log.info("Registered provider class: {} -> {}", providerName, providerClass.getSimpleName());
    }
    
    /**
     * Initialize all enabled providers based on configuration
     */
    private void initializeProviders() {
        // Initialize Onfido if enabled
        if (kycProperties.getProviders().getOnfido().isEnabled()) {
            initializeProvider("ONFIDO");
        }
        
        // Initialize Jumio if enabled
        if (kycProperties.getProviders().getJumio().isEnabled()) {
            initializeProvider("JUMIO");
        }
        
        // Initialize ComplyAdvantage if enabled
        if (kycProperties.getProviders().getComplyAdvantage().isEnabled()) {
            initializeProvider("COMPLY_ADVANTAGE");
        }
        
        log.info("Initialized {} KYC providers", providers.size());
    }
    
    /**
     * Initialize a specific provider
     * @param providerName The provider to initialize
     */
    private void initializeProvider(String providerName) {
        try {
            Class<? extends KYCProvider> providerClass = providerClasses.get(providerName);
            if (providerClass == null) {
                log.error("No provider class registered for: {}", providerName);
                return;
            }
            
            // Get or create the provider bean from Spring context
            KYCProvider provider;
            try {
                provider = applicationContext.getBean(providerClass);
            } catch (Exception e) {
                log.debug("Provider not found in context, creating new instance: {}", providerName);
                provider = applicationContext.getAutowireCapableBeanFactory()
                        .createBean(providerClass);
            }
            
            // Initialize the provider with configuration
            Map<String, String> config = getProviderConfig(providerName);
            provider.initialize(config);
            
            // Register the provider
            providers.put(providerName, provider);
            log.info("Initialized provider: {} ({})", providerName, provider.getClass().getSimpleName());
            
        } catch (Exception e) {
            log.error("Failed to initialize provider: {}", providerName, e);
        }
    }
    
    /**
     * Get a provider by name
     * @param providerName The provider identifier
     * @return The provider instance
     * @throws KYCProviderException if provider not found or not available
     */
    public KYCProvider getProvider(String providerName) {
        String normalizedName = providerName.toUpperCase();
        KYCProvider provider = providers.get(normalizedName);
        
        if (provider == null) {
            // Try to initialize on-demand
            initializeProvider(normalizedName);
            provider = providers.get(normalizedName);
            
            if (provider == null) {
                throw new KYCProviderException("Provider not found: " + providerName, providerName);
            }
        }
        
        if (!provider.isAvailable()) {
            throw new KYCProviderException("Provider not available: " + providerName, providerName);
        }
        
        return provider;
    }
    
    /**
     * Get the default provider
     * @return The default provider instance
     */
    public KYCProvider getDefaultProvider() {
        String defaultProviderName = kycProperties.getProviders().getDefaultProvider();
        return getProvider(defaultProviderName);
    }
    
    /**
     * Get all available provider names
     * @return Set of provider names
     */
    public Set<String> getAvailableProviders() {
        return new HashSet<>(providers.keySet());
    }
    
    /**
     * Check if a provider is registered and available
     * @param providerName The provider identifier
     * @return true if the provider is available
     */
    public boolean isProviderAvailable(String providerName) {
        KYCProvider provider = providers.get(providerName.toUpperCase());
        return provider != null && provider.isAvailable();
    }
    
    /**
     * Reload a provider with new configuration
     * @param providerName The provider to reload
     */
    public void reloadProvider(String providerName) {
        String normalizedName = providerName.toUpperCase();
        
        // Shutdown existing provider
        KYCProvider existingProvider = providers.get(normalizedName);
        if (existingProvider != null) {
            try {
                existingProvider.shutdown();
            } catch (Exception e) {
                log.error("Error shutting down provider: {}", providerName, e);
            }
            providers.remove(normalizedName);
        }
        
        // Reinitialize the provider
        initializeProvider(normalizedName);
    }
    
    /**
     * Shutdown all providers
     */
    public void shutdown() {
        log.info("Shutting down all KYC providers");
        
        providers.values().forEach(provider -> {
            try {
                provider.shutdown();
            } catch (Exception e) {
                log.error("Error shutting down provider: {}", provider.getProviderName(), e);
            }
        });
        
        providers.clear();
    }
    
    /**
     * Get provider-specific configuration
     * @param providerName The provider name
     * @return Configuration map
     */
    private Map<String, String> getProviderConfig(String providerName) {
        Map<String, String> config = new HashMap<>();
        
        switch (providerName) {
            case "ONFIDO":
                KYCProperties.OnfidoProperties onfido = kycProperties.getProviders().getOnfido();
                config.put("apiKey", onfido.getApiKey());
                config.put("apiUrl", onfido.getApiUrl());
                config.put("webhookSecret", onfido.getWebhookSecret());
                config.put("timeoutSeconds", String.valueOf(onfido.getTimeoutSeconds()));
                break;
                
            case "JUMIO":
                KYCProperties.JumioProperties jumio = kycProperties.getProviders().getJumio();
                config.put("apiKey", jumio.getApiKey());
                config.put("apiSecret", jumio.getApiSecret());
                config.put("apiUrl", jumio.getApiUrl());
                config.put("webhookSecret", jumio.getWebhookSecret());
                config.put("timeoutSeconds", String.valueOf(jumio.getTimeoutSeconds()));
                break;
                
            case "COMPLY_ADVANTAGE":
                KYCProperties.ComplyAdvantageProperties comply = kycProperties.getProviders().getComplyAdvantage();
                config.put("apiKey", comply.getApiKey());
                config.put("apiUrl", comply.getApiUrl());
                config.put("timeoutSeconds", String.valueOf(comply.getTimeoutSeconds()));
                break;
        }
        
        return config;
    }
    
    /**
     * Get provider metrics for monitoring
     * @return Map of provider name to metrics
     */
    public Map<String, Map<String, Object>> getProviderMetrics() {
        Map<String, Map<String, Object>> metrics = new HashMap<>();
        
        providers.forEach((name, provider) -> {
            Map<String, Object> providerMetrics = new HashMap<>();
            providerMetrics.put("available", provider.isAvailable());
            providerMetrics.put("features", provider.getFeatures());
            providerMetrics.put("supportedCountries", provider.getSupportedCountries().size());
            providerMetrics.put("supportedDocuments", provider.getSupportedDocumentTypes().size());
            metrics.put(name, providerMetrics);
        });
        
        return metrics;
    }
}