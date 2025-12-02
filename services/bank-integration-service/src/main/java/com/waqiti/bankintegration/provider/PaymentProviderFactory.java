package com.waqiti.bankintegration.provider;

import com.waqiti.bankintegration.domain.ProviderType;
import com.waqiti.bankintegration.exception.ProviderNotFoundException;
import com.waqiti.bankintegration.exception.ProviderUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing payment provider instances.
 * 
 * Provides centralized management of payment providers with features including:
 * - Provider lifecycle management
 * - Health monitoring and failover
 * - Load balancing and routing
 * - Circuit breaker patterns
 * - Provider-specific configuration
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderFactory {
    
    private final List<PaymentProvider> paymentProviders;
    private final ProviderHealthMonitor healthMonitor;
    private final ProviderLoadBalancer loadBalancer;
    
    private final Map<String, PaymentProvider> providerRegistry = new ConcurrentHashMap<>();
    private final Map<ProviderType, List<PaymentProvider>> providersByType = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initializeProviders() {
        log.info("Initializing payment provider factory with {} providers", paymentProviders.size());
        
        for (PaymentProvider provider : paymentProviders) {
            registerProvider(provider);
        }
        
        log.info("Payment provider factory initialized successfully");
    }
    
    /**
     * Get payment provider by name
     */
    public PaymentProvider getProvider(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }
        
        PaymentProvider provider = providerRegistry.get(providerName.toLowerCase());
        if (provider == null) {
            throw new ProviderNotFoundException("No provider found with name: " + providerName);
        }
        
        // Check provider health
        if (!healthMonitor.isProviderHealthy(provider)) {
            throw new ProviderUnavailableException("Provider is currently unavailable: " + providerName);
        }
        
        log.debug("Retrieved provider: {}", providerName);
        return provider;
    }
    
    /**
     * Get best available provider for given type
     */
    public PaymentProvider getBestProvider(ProviderType providerType) {
        List<PaymentProvider> providers = providersByType.get(providerType);
        if (providers == null || providers.isEmpty()) {
            throw new ProviderNotFoundException("No providers found for type: " + providerType);
        }
        
        // Filter healthy providers
        List<PaymentProvider> healthyProviders = providers.stream()
            .filter(healthMonitor::isProviderHealthy)
            .toList();
        
        if (healthyProviders.isEmpty()) {
            throw new ProviderUnavailableException("No healthy providers available for type: " + providerType);
        }
        
        // Use load balancer to select best provider
        PaymentProvider selected = loadBalancer.selectProvider(healthyProviders);
        
        log.debug("Selected provider {} for type {}", selected.getName(), providerType);
        return selected;
    }
    
    /**
     * Get provider for specific currency and amount
     */
    public PaymentProvider getProviderForTransaction(ProviderType providerType, 
                                                   String currency, 
                                                   java.math.BigDecimal amount) {
        List<PaymentProvider> providers = providersByType.get(providerType);
        if (providers == null || providers.isEmpty()) {
            throw new ProviderNotFoundException("No providers found for type: " + providerType);
        }
        
        // Filter providers that support the currency and amount
        List<PaymentProvider> eligibleProviders = providers.stream()
            .filter(healthMonitor::isProviderHealthy)
            .filter(provider -> provider.supportsCurrency(currency))
            .filter(provider -> provider.supportsAmount(amount))
            .toList();
        
        if (eligibleProviders.isEmpty()) {
            throw new ProviderUnavailableException(
                String.format("No providers available for type: %s, currency: %s, amount: %s", 
                            providerType, currency, amount));
        }
        
        PaymentProvider selected = loadBalancer.selectProvider(eligibleProviders);
        
        log.debug("Selected provider {} for transaction - type: {}, currency: {}, amount: {}", 
                selected.getName(), providerType, currency, amount);
        return selected;
    }
    
    /**
     * Get all healthy providers for a type
     */
    public List<PaymentProvider> getHealthyProviders(ProviderType providerType) {
        List<PaymentProvider> providers = providersByType.get(providerType);
        if (providers == null) {
            return Collections.emptyList();
        }
        
        return providers.stream()
            .filter(healthMonitor::isProviderHealthy)
            .toList();
    }
    
    /**
     * Get provider with fallback options
     */
    public PaymentProvider getProviderWithFallback(String primaryProviderName, 
                                                  ProviderType fallbackType) {
        try {
            return getProvider(primaryProviderName);
        } catch (ProviderNotFoundException | ProviderUnavailableException e) {
            log.warn("Primary provider {} unavailable, falling back to type {}", 
                    primaryProviderName, fallbackType);
            return getBestProvider(fallbackType);
        }
    }
    
    /**
     * Register a new provider
     */
    public void registerProvider(PaymentProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }
        
        String providerName = provider.getName().toLowerCase();
        providerRegistry.put(providerName, provider);
        
        ProviderType type = provider.getProviderType();
        providersByType.computeIfAbsent(type, k -> new ArrayList<>()).add(provider);
        
        // Initialize provider
        try {
            provider.initialize();
            log.info("Registered and initialized provider: {} (type: {})", 
                    provider.getName(), type);
        } catch (Exception e) {
            log.error("Failed to initialize provider: {}", provider.getName(), e);
            // Remove from registry if initialization fails
            unregisterProvider(provider.getName());
            throw new RuntimeException("Provider initialization failed: " + provider.getName(), e);
        }
    }
    
    /**
     * Unregister a provider
     */
    public void unregisterProvider(String providerName) {
        PaymentProvider provider = providerRegistry.remove(providerName.toLowerCase());
        if (provider != null) {
            ProviderType type = provider.getProviderType();
            List<PaymentProvider> typeProviders = providersByType.get(type);
            if (typeProviders != null) {
                typeProviders.remove(provider);
                if (typeProviders.isEmpty()) {
                    providersByType.remove(type);
                }
            }
            
            try {
                provider.shutdown();
                log.info("Unregistered provider: {}", providerName);
            } catch (Exception e) {
                log.warn("Error shutting down provider: {}", providerName, e);
            }
        }
    }
    
    /**
     * Get all registered provider names
     */
    public Set<String> getAllProviderNames() {
        return new HashSet<>(providerRegistry.keySet());
    }
    
    /**
     * Get providers by type
     */
    public List<PaymentProvider> getProvidersByType(ProviderType providerType) {
        return providersByType.getOrDefault(providerType, Collections.emptyList());
    }
    
    /**
     * Check if provider is registered
     */
    public boolean isProviderRegistered(String providerName) {
        return providerRegistry.containsKey(providerName.toLowerCase());
    }
    
    /**
     * Get provider health status
     */
    public boolean isProviderHealthy(String providerName) {
        PaymentProvider provider = providerRegistry.get(providerName.toLowerCase());
        return provider != null && healthMonitor.isProviderHealthy(provider);
    }
    
    /**
     * Get provider statistics
     */
    public Map<String, Object> getProviderStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalProviders", providerRegistry.size());
        stats.put("providersByType", providersByType.entrySet().stream()
            .collect(HashMap::new, 
                    (map, entry) -> map.put(entry.getKey().toString(), entry.getValue().size()),
                    HashMap::putAll));
        
        long healthyCount = providerRegistry.values().stream()
            .mapToLong(provider -> healthMonitor.isProviderHealthy(provider) ? 1 : 0)
            .sum();
        
        stats.put("healthyProviders", healthyCount);
        stats.put("unhealthyProviders", providerRegistry.size() - healthyCount);
        
        return stats;
    }
    
    /**
     * Refresh provider health status
     */
    public void refreshProviderHealth() {
        log.debug("Refreshing provider health status for {} providers", providerRegistry.size());
        
        for (PaymentProvider provider : providerRegistry.values()) {
            try {
                healthMonitor.checkProviderHealth(provider);
            } catch (Exception e) {
                log.warn("Error checking health for provider: {}", provider.getName(), e);
            }
        }
    }
    
    /**
     * Emergency shutdown of all providers
     */
    public void emergencyShutdown() {
        log.warn("Emergency shutdown initiated for all providers");
        
        for (PaymentProvider provider : providerRegistry.values()) {
            try {
                provider.emergencyShutdown();
            } catch (Exception e) {
                log.error("Error during emergency shutdown of provider: {}", provider.getName(), e);
            }
        }
        
        providerRegistry.clear();
        providersByType.clear();
        
        log.warn("Emergency shutdown completed");
    }
}