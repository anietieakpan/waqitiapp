package com.waqiti.payment.service;

import com.waqiti.payment.core.provider.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayHealthService implements HealthIndicator {

    private final StripePaymentProvider stripeProvider;
    private final PayPalPaymentProvider paypalProvider;
    private final SquarePaymentProvider squareProvider;
    private final BraintreePaymentProvider braintreeProvider;
    private final AdyenPaymentProvider adyenProvider;
    private final DwollaPaymentProvider dwollaProvider;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private final Map<String, Boolean> providerHealthStatus = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastHealthCheck = new ConcurrentHashMap<>();

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean allHealthy = true;
        
        details.put("stripe", getProviderHealth("stripe"));
        details.put("paypal", getProviderHealth("paypal"));
        details.put("square", getProviderHealth("square"));
        details.put("braintree", getProviderHealth("braintree"));
        details.put("adyen", getProviderHealth("adyen"));
        details.put("dwolla", getProviderHealth("dwolla"));
        
        // Check if at least one critical provider is healthy
        boolean hasCriticalProvider = providerHealthStatus.getOrDefault("stripe", false) ||
                                    providerHealthStatus.getOrDefault("paypal", false) ||
                                    providerHealthStatus.getOrDefault("adyen", false);
        
        if (!hasCriticalProvider) {
            allHealthy = false;
            details.put("critical_issue", "No critical payment providers are healthy");
        }
        
        details.put("last_check", LocalDateTime.now());
        details.put("total_providers", providerHealthStatus.size());
        details.put("healthy_providers", providerHealthStatus.values().stream().mapToInt(h -> h ? 1 : 0).sum());
        
        return allHealthy ? Health.up().withDetails(details).build() : 
                          Health.down().withDetails(details).build();
    }

    @Scheduled(fixedRate = 30000) // Check every 30 seconds
    public void performHealthChecks() {
        log.debug("Performing payment gateway health checks");
        
        // Perform async health checks for all providers
        CompletableFuture<Void> allChecks = CompletableFuture.allOf(
            checkProviderHealthAsync("stripe", stripeProvider::isHealthy),
            checkProviderHealthAsync("paypal", paypalProvider::isHealthy),
            checkProviderHealthAsync("square", squareProvider::isHealthy),
            checkProviderHealthAsync("braintree", braintreeProvider::isHealthy),
            checkProviderHealthAsync("adyen", adyenProvider::isHealthy),
            checkProviderHealthAsync("dwolla", dwollaProvider::isHealthy)
        );
        
        try {
            // Wait for all health checks to complete (max 10 seconds)
            allChecks.get(10, java.util.concurrent.TimeUnit.SECONDS);
            
            publishHealthMetrics();
            
        } catch (Exception e) {
            log.error("Health check batch failed: ", e);
        }
    }
    
    public boolean isProviderHealthy(String providerName) {
        return providerHealthStatus.getOrDefault(providerName.toLowerCase(), false);
    }
    
    public Map<String, Boolean> getAllProviderHealth() {
        return new HashMap<>(providerHealthStatus);
    }
    
    public int getHealthyProviderCount() {
        return (int) providerHealthStatus.values().stream().filter(Boolean::booleanValue).count();
    }
    
    public boolean hasHealthyCriticalProvider() {
        return isProviderHealthy("stripe") || 
               isProviderHealthy("paypal") || 
               isProviderHealthy("adyen");
    }
    
    private CompletableFuture<Void> checkProviderHealthAsync(String providerName, java.util.function.Supplier<Boolean> healthCheck) {
        return CompletableFuture.runAsync(() -> {
            try {
                boolean isHealthy = healthCheck.get();
                boolean wasHealthy = providerHealthStatus.getOrDefault(providerName, true);
                
                providerHealthStatus.put(providerName, isHealthy);
                lastHealthCheck.put(providerName, LocalDateTime.now());
                
                // Log status changes
                if (isHealthy != wasHealthy) {
                    if (isHealthy) {
                        log.info("✅ Payment provider {} is now HEALTHY", providerName);
                    } else {
                        log.error("❌ Payment provider {} is now UNHEALTHY", providerName);
                    }
                    
                    // Publish health change event
                    publishHealthChangeEvent(providerName, isHealthy);
                }
                
            } catch (Exception e) {
                log.error("Health check failed for provider {}: ", providerName, e);
                providerHealthStatus.put(providerName, false);
                lastHealthCheck.put(providerName, LocalDateTime.now());
                publishHealthChangeEvent(providerName, false);
            }
        });
    }
    
    private void publishHealthMetrics() {
        try {
            Map<String, Object> healthMetrics = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "providers", getAllProviderHealth(),
                "healthy_count", getHealthyProviderCount(),
                "total_count", providerHealthStatus.size(),
                "has_critical_provider", hasHealthyCriticalProvider(),
                "critical_providers", Map.of(
                    "stripe", isProviderHealthy("stripe"),
                    "paypal", isProviderHealthy("paypal"),
                    "adyen", isProviderHealthy("adyen")
                )
            );
            
            kafkaTemplate.send("payment-gateway-health", healthMetrics);
            
        } catch (Exception e) {
            log.error("Failed to publish health metrics: ", e);
        }
    }
    
    private void publishHealthChangeEvent(String providerName, boolean isHealthy) {
        try {
            Map<String, Object> event = Map.of(
                "provider", providerName,
                "healthy", isHealthy,
                "timestamp", LocalDateTime.now().toString(),
                "event_type", isHealthy ? "PROVIDER_RECOVERED" : "PROVIDER_DOWN"
            );
            
            kafkaTemplate.send("payment-provider-status-changes", event);
            
        } catch (Exception e) {
            log.error("Failed to publish health change event for {}: ", providerName, e);
        }
    }
    
    private Map<String, Object> getProviderHealth(String providerName) {
        boolean healthy = providerHealthStatus.getOrDefault(providerName, false);
        LocalDateTime lastCheck = lastHealthCheck.get(providerName);
        
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("healthy", healthy);
        healthInfo.put("last_check", lastCheck != null ? lastCheck.toString() : "never");
        healthInfo.put("status", healthy ? "UP" : "DOWN");
        
        return healthInfo;
    }
}