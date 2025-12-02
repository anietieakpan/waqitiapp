package com.waqiti.payment.core.provider;

import com.waqiti.payment.core.model.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing payment provider instances
 * Industrial-grade provider management with caching and lifecycle control
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderFactory {
    
    private final ApplicationContext applicationContext;
    
    private final Map<String, PaymentProvider> providerCache = new ConcurrentHashMap<>();
    private final Map<ProviderType, String> providerMappings = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initializeProviders() {
        log.info("Initializing payment provider factory");
        
        // Discover all payment provider beans
        Map<String, PaymentProvider> providers = applicationContext.getBeansOfType(PaymentProvider.class);
        
        for (Map.Entry<String, PaymentProvider> entry : providers.entrySet()) {
            String beanName = entry.getKey();
            PaymentProvider provider = entry.getValue();
            
            // Cache provider
            providerCache.put(provider.getProviderName(), provider);
            
            // Map provider type
            if (provider.getProviderType() != null) {
                providerMappings.put(provider.getProviderType(), provider.getProviderName());
            }
            
            log.info("Registered payment provider: {} ({})", provider.getProviderName(), beanName);
        }
        
        log.info("Payment provider factory initialized with {} providers", providerCache.size());
    }
    
    /**
     * Get provider by name
     */
    public PaymentProvider getProvider(String providerName) {
        if (providerName == null) {
            throw new IllegalArgumentException("Provider name cannot be null");
        }
        
        PaymentProvider provider = providerCache.get(providerName.toUpperCase());
        
        if (provider == null) {
            // Try to load dynamically
            provider = loadProviderDynamically(providerName);
            
            if (provider == null) {
                throw new ProviderNotFoundException("Payment provider not found: " + providerName);
            }
        }
        
        if (!provider.isAvailable()) {
            log.warn("Provider {} is not currently available", providerName);
        }
        
        return provider;
    }
    
    /**
     * Get provider by type
     */
    public PaymentProvider getProviderByType(ProviderType providerType) {
        String providerName = providerMappings.get(providerType);
        
        if (providerName == null) {
            throw new ProviderNotFoundException("No provider found for type: " + providerType);
        }
        
        return getProvider(providerName);
    }
    
    /**
     * Get all available providers
     */
    public List<PaymentProvider> getAvailableProviders() {
        return providerCache.values().stream()
            .filter(PaymentProvider::isAvailable)
            .toList();
    }
    
    /**
     * Get providers supporting specific payment type
     */
    public List<PaymentProvider> getProvidersByPaymentType(com.waqiti.payment.core.model.PaymentType paymentType) {
        return providerCache.values().stream()
            .filter(provider -> provider.supportsPaymentType(paymentType))
            .filter(PaymentProvider::isAvailable)
            .toList();
    }
    
    /**
     * Get providers supporting specific currency
     */
    public List<PaymentProvider> getProvidersByCurrency(String currency) {
        return providerCache.values().stream()
            .filter(provider -> provider.supportsCurrency(currency))
            .filter(PaymentProvider::isAvailable)
            .toList();
    }
    
    /**
     * Register a new provider at runtime
     */
    public void registerProvider(PaymentProvider provider) {
        if (provider == null || provider.getProviderName() == null) {
            throw new IllegalArgumentException("Invalid provider");
        }
        
        providerCache.put(provider.getProviderName().toUpperCase(), provider);
        
        if (provider.getProviderType() != null) {
            providerMappings.put(provider.getProviderType(), provider.getProviderName());
        }
        
        log.info("Dynamically registered provider: {}", provider.getProviderName());
    }
    
    /**
     * Unregister a provider
     */
    public void unregisterProvider(String providerName) {
        PaymentProvider removed = providerCache.remove(providerName.toUpperCase());
        
        if (removed != null && removed.getProviderType() != null) {
            providerMappings.remove(removed.getProviderType());
            log.info("Unregistered provider: {}", providerName);
        }
    }
    
    /**
     * Check if provider exists
     */
    public boolean hasProvider(String providerName) {
        return providerCache.containsKey(providerName.toUpperCase());
    }
    
    /**
     * Get all registered provider names
     */
    public Set<String> getProviderNames() {
        return new HashSet<>(providerCache.keySet());
    }
    
    /**
     * Get provider health status
     */
    public Map<String, ProviderHealthStatus> getProviderHealthStatus() {
        Map<String, ProviderHealthStatus> healthMap = new HashMap<>();
        
        for (Map.Entry<String, PaymentProvider> entry : providerCache.entrySet()) {
            PaymentProvider provider = entry.getValue();
            
            ProviderHealthStatus health = ProviderHealthStatus.builder()
                .providerName(entry.getKey())
                .available(provider.isAvailable())
                .healthScore(provider.getHealthScore())
                .lastChecked(new Date())
                .build();
            
            healthMap.put(entry.getKey(), health);
        }
        
        return healthMap;
    }
    
    /**
     * Refresh provider configurations
     */
    public void refreshProviders() {
        log.info("Refreshing payment providers");
        
        for (PaymentProvider provider : providerCache.values()) {
            try {
                provider.refreshConfiguration();
                log.debug("Refreshed provider: {}", provider.getProviderName());
            } catch (Exception e) {
                log.error("Failed to refresh provider: {}", provider.getProviderName(), e);
            }
        }
    }
    
    /**
     * Load provider dynamically if not in cache
     */
    private PaymentProvider loadProviderDynamically(String providerName) {
        try {
            // Try to get from Spring context by name
            String beanName = providerName.toLowerCase() + "PaymentProvider";
            
            if (applicationContext.containsBean(beanName)) {
                PaymentProvider provider = applicationContext.getBean(beanName, PaymentProvider.class);
                providerCache.put(providerName.toUpperCase(), provider);
                log.info("Dynamically loaded provider from Spring context: {}", providerName);
                return provider;
            }
            
            // Try to load by class name
            String className = "com.waqiti.payment.provider." + 
                             providerName.substring(0, 1).toUpperCase() + 
                             providerName.substring(1).toLowerCase() + 
                             "PaymentProvider";
            
            Class<?> providerClass = Class.forName(className);
            if (PaymentProvider.class.isAssignableFrom(providerClass)) {
                PaymentProvider provider = (PaymentProvider) applicationContext.getBean(providerClass);
                providerCache.put(providerName.toUpperCase(), provider);
                log.info("Dynamically loaded provider from class: {}", providerName);
                return provider;
            }
            
            // Try alternative package paths
            String[] alternativePackages = {
                "com.waqiti.payment.core.provider.",
                "com.waqiti.payment.integration.",
                "com.waqiti.payment.providers."
            };
            
            for (String packagePath : alternativePackages) {
                try {
                    String altClassName = packagePath + 
                                         providerName.substring(0, 1).toUpperCase() + 
                                         providerName.substring(1).toLowerCase() + 
                                         "PaymentProvider";
                    
                    Class<?> altProviderClass = Class.forName(altClassName);
                    if (PaymentProvider.class.isAssignableFrom(altProviderClass)) {
                        PaymentProvider provider = (PaymentProvider) applicationContext.getBean(altProviderClass);
                        providerCache.put(providerName.toUpperCase(), provider);
                        log.info("Dynamically loaded provider from alternative package: {} -> {}", 
                                providerName, altClassName);
                        return provider;
                    }
                } catch (ClassNotFoundException ignored) {
                    // Continue trying other packages
                }
            }
            
        } catch (Exception e) {
            log.debug("Could not dynamically load provider: {}", providerName, e);
        }
        
        // Return a fallback provider instead of null
        log.warn("Provider {} not found, returning fallback provider", providerName);
        return createFallbackProvider(providerName);
    }
    
    /**
     * Create a fallback provider when requested provider is not available
     */
    private PaymentProvider createFallbackProvider(String providerName) {
        return new PaymentProvider() {
            @Override
            public String getProviderName() {
                return providerName + "_FALLBACK";
            }
            
            @Override
            public ProviderType getProviderType() {
                return ProviderType.FALLBACK;
            }
            
            @Override
            public boolean isAvailable() {
                return false;
            }
            
            @Override
            public Double getHealthScore() {
                return 0.0;
            }
            
            @Override
            public boolean supportsPaymentType(com.waqiti.payment.core.model.PaymentType paymentType) {
                return false;
            }
            
            @Override
            public boolean supportsCurrency(String currency) {
                return false;
            }
            
            @Override
            public void refreshConfiguration() {
                log.warn("Refresh requested on fallback provider: {}", providerName);
            }
            
            @Override
            public com.waqiti.payment.core.model.PaymentResult processPayment(
                    com.waqiti.payment.core.model.PaymentRequest request) {
                // Return failed result instead of throwing exception
                return com.waqiti.payment.core.model.PaymentResult.builder()
                    .success(false)
                    .errorCode("PROVIDER_UNAVAILABLE")
                    .errorMessage("Payment provider not available: " + providerName)
                    .transactionId(request.getTransactionId())
                    .build();
            }
        };
    }
    
    /**
     * Provider health status model
     */
    @lombok.Data
    @lombok.Builder
    public static class ProviderHealthStatus {
        private String providerName;
        private boolean available;
        private Double healthScore;
        private Date lastChecked;
        private Map<String, Object> metrics;
    }
    
    /**
     * Exception for provider not found
     */
    public static class ProviderNotFoundException extends RuntimeException {
        public ProviderNotFoundException(String message) {
            super(message);
        }
    }
}