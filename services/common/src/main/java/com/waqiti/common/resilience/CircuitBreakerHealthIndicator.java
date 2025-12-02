package com.waqiti.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Circuit Breaker Health Indicator for monitoring service health
 * Provides comprehensive health status for all circuit breakers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerHealthIndicator implements HealthIndicator {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean allHealthy = true;
        int totalCircuitBreakers = 0;
        int openCircuitBreakers = 0;
        int halfOpenCircuitBreakers = 0;
        
        // Check all circuit breakers
        for (CircuitBreaker circuitBreaker : circuitBreakerRegistry.getAllCircuitBreakers()) {
            totalCircuitBreakers++;
            String name = circuitBreaker.getName();
            CircuitBreaker.State state = circuitBreaker.getState();
            
            Map<String, Object> circuitBreakerDetails = new HashMap<>();
            circuitBreakerDetails.put("state", state.name());
            circuitBreakerDetails.put("failureRate", circuitBreaker.getMetrics().getFailureRate());
            circuitBreakerDetails.put("numberOfSuccessfulCalls", circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
            circuitBreakerDetails.put("numberOfFailedCalls", circuitBreaker.getMetrics().getNumberOfFailedCalls());
            circuitBreakerDetails.put("numberOfNotPermittedCalls", circuitBreaker.getMetrics().getNumberOfNotPermittedCalls());
            circuitBreakerDetails.put("slowCallRate", circuitBreaker.getMetrics().getSlowCallRate());
            
            details.put(name, circuitBreakerDetails);
            
            // Track circuit breaker states
            if (state == CircuitBreaker.State.OPEN) {
                openCircuitBreakers++;
                allHealthy = false;
            } else if (state == CircuitBreaker.State.HALF_OPEN) {
                halfOpenCircuitBreakers++;
            }
        }
        
        // Add summary
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", totalCircuitBreakers);
        summary.put("open", openCircuitBreakers);
        summary.put("halfOpen", halfOpenCircuitBreakers);
        summary.put("closed", totalCircuitBreakers - openCircuitBreakers - halfOpenCircuitBreakers);
        summary.put("lastChecked", LocalDateTime.now());
        
        details.put("summary", summary);
        
        // Determine overall health status
        Status status;
        if (!allHealthy) {
            if (openCircuitBreakers > totalCircuitBreakers * 0.5) {
                status = Status.DOWN; // More than 50% of circuit breakers are open
            } else {
                status = new Status("DEGRADED"); // Some circuit breakers are open
            }
        } else if (halfOpenCircuitBreakers > 0) {
            status = new Status("RECOVERING"); // Some are recovering
        } else {
            status = Status.UP; // All good
        }
        
        return Health.status(status)
                .withDetails(details)
                .build();
    }
    
    /**
     * Get critical services health
     */
    public Health getCriticalServicesHealth() {
        Map<String, Object> details = new HashMap<>();
        String[] criticalServices = {"wallet-service", "payment-gateway", "fraud-detection", "kyc-service"};
        
        boolean allCriticalHealthy = true;
        
        for (String serviceName : criticalServices) {
            try {
                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
                CircuitBreaker.State state = circuitBreaker.getState();
                
                Map<String, Object> serviceDetails = new HashMap<>();
                serviceDetails.put("state", state.name());
                serviceDetails.put("failureRate", circuitBreaker.getMetrics().getFailureRate());
                serviceDetails.put("critical", true);
                
                details.put(serviceName, serviceDetails);
                
                if (state == CircuitBreaker.State.OPEN) {
                    allCriticalHealthy = false;
                }
                
            } catch (Exception e) {
                details.put(serviceName, Map.of("error", "Circuit breaker not found", "critical", true));
                allCriticalHealthy = false;
            }
        }
        
        Status status = allCriticalHealthy ? Status.UP : Status.DOWN;
        
        return Health.status(status)
                .withDetail("criticalServices", details)
                .withDetail("lastChecked", LocalDateTime.now())
                .build();
    }
}