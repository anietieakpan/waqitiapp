package com.waqiti.bankintegration.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load balancer for payment providers.
 * 
 * Implements multiple load balancing strategies including round-robin,
 * weighted selection, and performance-based routing for payment providers.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Component
@Slf4j
public class ProviderLoadBalancer {
    
    private final Map<String, AtomicInteger> roundRobinCounters = new HashMap<>();
    private final Random random = ThreadLocalRandom.current();
    
    /**
     * Load balancing strategies
     */
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,
        WEIGHTED_RANDOM,
        PERFORMANCE_BASED,
        LEAST_CONNECTIONS,
        RANDOM
    }
    
    /**
     * Select provider using default strategy (performance-based)
     */
    public PaymentProvider selectProvider(List<PaymentProvider> providers) {
        return selectProvider(providers, LoadBalancingStrategy.PERFORMANCE_BASED);
    }
    
    /**
     * Select provider using specified strategy
     */
    public PaymentProvider selectProvider(List<PaymentProvider> providers, 
                                        LoadBalancingStrategy strategy) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Provider list cannot be null or empty");
        }
        
        if (providers.size() == 1) {
            return providers.get(0);
        }
        
        return switch (strategy) {
            case ROUND_ROBIN -> selectRoundRobin(providers);
            case WEIGHTED_RANDOM -> selectWeightedRandom(providers);
            case PERFORMANCE_BASED -> selectPerformanceBased(providers);
            case LEAST_CONNECTIONS -> selectLeastConnections(providers);
            case RANDOM -> selectRandom(providers);
        };
    }
    
    /**
     * Round-robin selection
     */
    private PaymentProvider selectRoundRobin(List<PaymentProvider> providers) {
        String key = createProviderListKey(providers);
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        
        int index = counter.getAndIncrement() % providers.size();
        PaymentProvider selected = providers.get(index);
        
        log.debug("Round-robin selected provider: {} (index: {})", selected.getName(), index);
        return selected;
    }
    
    /**
     * Weighted random selection based on provider priority/weight
     */
    private PaymentProvider selectWeightedRandom(List<PaymentProvider> providers) {
        // Calculate total weight
        int totalWeight = providers.stream()
            .mapToInt(this::getProviderWeight)
            .sum();
        
        if (totalWeight == 0) {
            return selectRandom(providers);
        }
        
        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;
        
        for (PaymentProvider provider : providers) {
            currentWeight += getProviderWeight(provider);
            if (randomWeight < currentWeight) {
                log.debug("Weighted random selected provider: {} (weight: {})", 
                        provider.getName(), getProviderWeight(provider));
                return provider;
            }
        }
        
        // Fallback to last provider
        return providers.get(providers.size() - 1);
    }
    
    /**
     * Performance-based selection (best success rate and response time)
     */
    private PaymentProvider selectPerformanceBased(List<PaymentProvider> providers) {
        PaymentProvider best = providers.stream()
            .max(Comparator
                .comparingDouble(this::getProviderSuccessRate)
                .thenComparing(provider -> -getProviderResponseTime(provider))) // Lower response time is better
            .orElse(providers.get(0));
        
        log.debug("Performance-based selected provider: {} (success rate: {}, response time: {}ms)", 
                best.getName(), getProviderSuccessRate(best), getProviderResponseTime(best));
        return best;
    }
    
    /**
     * Least connections selection
     */
    private PaymentProvider selectLeastConnections(List<PaymentProvider> providers) {
        PaymentProvider least = providers.stream()
            .min(Comparator.comparingInt(this::getProviderActiveConnections))
            .orElse(providers.get(0));
        
        log.debug("Least connections selected provider: {} (connections: {})", 
                least.getName(), getProviderActiveConnections(least));
        return least;
    }
    
    /**
     * Random selection
     */
    private PaymentProvider selectRandom(List<PaymentProvider> providers) {
        int index = random.nextInt(providers.size());
        PaymentProvider selected = providers.get(index);
        
        log.debug("Random selected provider: {} (index: {})", selected.getName(), index);
        return selected;
    }
    
    /**
     * Select provider for specific criteria
     */
    public PaymentProvider selectProviderForCriteria(List<PaymentProvider> providers,
                                                   SelectionCriteria criteria) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Provider list cannot be null or empty");
        }
        
        // Filter providers based on criteria
        List<PaymentProvider> filtered = providers.stream()
            .filter(provider -> matchesCriteria(provider, criteria))
            .toList();
        
        if (filtered.isEmpty()) {
            log.warn("No providers match the specified criteria, falling back to all providers");
            filtered = providers;
        }
        
        // Apply preferred strategy
        LoadBalancingStrategy strategy = criteria.getPreferredStrategy() != null ? 
            criteria.getPreferredStrategy() : LoadBalancingStrategy.PERFORMANCE_BASED;
        
        return selectProvider(filtered, strategy);
    }
    
    /**
     * Get provider weight for weighted selection
     */
    private int getProviderWeight(PaymentProvider provider) {
        // This would typically come from provider configuration
        // For now, use a simple heuristic based on success rate
        double successRate = getProviderSuccessRate(provider);
        return Math.max(1, (int) (successRate * 10));
    }
    
    /**
     * Get provider success rate
     */
    private double getProviderSuccessRate(PaymentProvider provider) {
        try {
            // This would typically come from metrics/monitoring
            // For now, return a default value
            return 0.95; // 95% success rate
        } catch (Exception e) {
            log.warn("Could not get success rate for provider: {}", provider.getName());
            return 0.5; // Default to moderate success rate
        }
    }
    
    /**
     * Get provider response time
     */
    private long getProviderResponseTime(PaymentProvider provider) {
        try {
            // This would typically come from metrics/monitoring
            // For now, return a default value
            return 1000; // 1 second default
        } catch (Exception e) {
            log.warn("Could not get response time for provider: {}", provider.getName());
            return 5000; // Default to high response time
        }
    }
    
    /**
     * Get provider active connections
     */
    private int getProviderActiveConnections(PaymentProvider provider) {
        try {
            // This would typically come from connection pool metrics
            // For now, return a random number
            return random.nextInt(100);
        } catch (Exception e) {
            log.warn("Could not get active connections for provider: {}", provider.getName());
            return 50; // Default to moderate load
        }
    }
    
    /**
     * Check if provider matches selection criteria
     */
    private boolean matchesCriteria(PaymentProvider provider, SelectionCriteria criteria) {
        if (criteria.getMinSuccessRate() != null && 
            getProviderSuccessRate(provider) < criteria.getMinSuccessRate()) {
            return false;
        }
        
        if (criteria.getMaxResponseTime() != null && 
            getProviderResponseTime(provider) > criteria.getMaxResponseTime()) {
            return false;
        }
        
        if (criteria.getRequiredCurrency() != null && 
            !provider.supportsCurrency(criteria.getRequiredCurrency())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Create unique key for provider list
     */
    private String createProviderListKey(List<PaymentProvider> providers) {
        return providers.stream()
            .map(PaymentProvider::getName)
            .sorted()
            .reduce("", (a, b) -> a + ":" + b);
    }
    
    /**
     * Clear round-robin counters
     */
    public void clearCounters() {
        roundRobinCounters.clear();
        log.info("Load balancer counters cleared");
    }
    
    /**
     * Get load balancer statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("roundRobinCounters", new HashMap<>(roundRobinCounters));
        stats.put("totalProviderLists", roundRobinCounters.size());
        
        return stats;
    }
    
    /**
     * Selection criteria for provider filtering
     */
    public static class SelectionCriteria {
        private Double minSuccessRate;
        private Long maxResponseTime;
        private String requiredCurrency;
        private LoadBalancingStrategy preferredStrategy;
        
        // Builder pattern
        public static SelectionCriteria builder() {
            return new SelectionCriteria();
        }
        
        public SelectionCriteria minSuccessRate(double minSuccessRate) {
            this.minSuccessRate = minSuccessRate;
            return this;
        }
        
        public SelectionCriteria maxResponseTime(long maxResponseTime) {
            this.maxResponseTime = maxResponseTime;
            return this;
        }
        
        public SelectionCriteria requiredCurrency(String currency) {
            this.requiredCurrency = currency;
            return this;
        }
        
        public SelectionCriteria preferredStrategy(LoadBalancingStrategy strategy) {
            this.preferredStrategy = strategy;
            return this;
        }
        
        // Getters
        public Double getMinSuccessRate() { return minSuccessRate; }
        public Long getMaxResponseTime() { return maxResponseTime; }
        public String getRequiredCurrency() { return requiredCurrency; }
        public LoadBalancingStrategy getPreferredStrategy() { return preferredStrategy; }
    }
}