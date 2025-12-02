package com.waqiti.bankintegration.health;

import com.plaid.client.PlaidApi;
import com.plaid.client.request.CategoriesGetRequest;
import com.plaid.client.response.CategoriesGetResponse;
import com.waqiti.bankintegration.domain.PaymentProvider;
import com.waqiti.bankintegration.repository.PaymentProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import retrofit2.Response;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Plaid Integration Health Indicator
 * 
 * Monitors Plaid API health and connectivity for production reliability.
 * Integrates with Spring Boot Actuator for comprehensive health monitoring.
 */
@Slf4j
@Component("plaidHealth")
@RequiredArgsConstructor
public class PlaidHealthIndicator implements HealthIndicator {
    
    private final PaymentProviderRepository providerRepository;
    private final ExecutorService healthCheckExecutor = Executors.newSingleThreadExecutor();
    
    // Health check cache to avoid excessive API calls
    private volatile Health cachedHealth;
    private volatile Instant lastHealthCheck;
    private static final Duration CACHE_DURATION = Duration.ofMinutes(2);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(10);
    
    @Override
    public Health health() {
        // Return cached health if still valid
        if (cachedHealth != null && lastHealthCheck != null) {
            Duration timeSinceLastCheck = Duration.between(lastHealthCheck, Instant.now());
            if (timeSinceLastCheck.compareTo(CACHE_DURATION) < 0) {
                log.debug("Returning cached Plaid health status (age: {}s)", timeSinceLastCheck.getSeconds());
                return cachedHealth;
            }
        }
        
        // Perform new health check
        Health newHealth = performHealthCheck();
        
        // Update cache
        cachedHealth = newHealth;
        lastHealthCheck = Instant.now();
        
        return newHealth;
    }
    
    private Health performHealthCheck() {
        try {
            log.debug("Performing Plaid health check");
            
            // Find active Plaid provider
            PaymentProvider plaidProvider = providerRepository
                .findByProviderTypeAndEnabled("PLAID", true)
                .stream()
                .findFirst()
                .orElse(null);
            
            if (plaidProvider == null) {
                return Health.down()
                    .withDetail("status", "NOT_CONFIGURED")
                    .withDetail("message", "No active Plaid provider configured")
                    .build();
            }
            
            // Execute health check with timeout
            Future<HealthCheckResult> futureResult = healthCheckExecutor.submit(
                () -> checkPlaidConnectivity(plaidProvider)
            );
            
            try {
                HealthCheckResult result = futureResult.get(
                    HEALTH_CHECK_TIMEOUT.toMillis(), 
                    TimeUnit.MILLISECONDS
                );
                
                Map<String, Object> details = new HashMap<>();
                details.put("providerId", plaidProvider.getId());
                details.put("providerName", plaidProvider.getName());
                details.put("environment", plaidProvider.isSandboxMode() ? "SANDBOX" : "PRODUCTION");
                details.put("responseTime", result.responseTimeMs + "ms");
                details.put("lastChecked", Instant.now().toString());
                
                if (result.success) {
                    details.put("status", "OPERATIONAL");
                    return Health.up().withDetails(details).build();
                } else {
                    details.put("status", "DEGRADED");
                    details.put("error", result.errorMessage);
                    return Health.down().withDetails(details).build();
                }
                
            } catch (TimeoutException e) {
                log.warn("Plaid health check timed out after {}s", HEALTH_CHECK_TIMEOUT.getSeconds());
                return Health.down()
                    .withDetail("status", "TIMEOUT")
                    .withDetail("message", "Health check timed out")
                    .withDetail("timeout", HEALTH_CHECK_TIMEOUT.getSeconds() + "s")
                    .build();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Health.down()
                    .withDetail("status", "INTERRUPTED")
                    .withDetail("message", "Health check interrupted")
                    .build();
            } catch (ExecutionException e) {
                log.error("Plaid health check execution failed", e);
                return Health.down()
                    .withDetail("status", "ERROR")
                    .withDetail("message", "Health check execution failed: " + e.getMessage())
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Unexpected error during Plaid health check", e);
            return Health.down()
                .withDetail("status", "ERROR")
                .withDetail("message", "Unexpected error: " + e.getMessage())
                .build();
        }
    }
    
    private HealthCheckResult checkPlaidConnectivity(PaymentProvider provider) {
        Instant start = Instant.now();
        
        try {
            PlaidApi plaidClient = createPlaidClient(provider);
            
            // Use lightweight Categories API call for health check
            CategoriesGetRequest request = new CategoriesGetRequest();
            Response<CategoriesGetResponse> response = plaidClient.categoriesGet(request).execute();
            
            long responseTimeMs = Duration.between(start, Instant.now()).toMillis();
            
            if (response.isSuccessful()) {
                log.debug("Plaid health check successful ({}ms)", responseTimeMs);
                return new HealthCheckResult(true, responseTimeMs, null);
            } else {
                String errorMsg = response.errorBody() != null ? 
                    response.errorBody().string() : "Unknown error";
                log.warn("Plaid health check failed: HTTP {}, {}", response.code(), errorMsg);
                return new HealthCheckResult(false, responseTimeMs, 
                    "HTTP " + response.code() + ": " + errorMsg);
            }
            
        } catch (Exception e) {
            long responseTimeMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Plaid connectivity check failed", e);
            return new HealthCheckResult(false, responseTimeMs, 
                e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
    
    private PlaidApi createPlaidClient(PaymentProvider provider) {
        Map<String, String> apiKeys = new HashMap<>();
        apiKeys.put("client_id", provider.getApiKey());
        apiKeys.put("secret", provider.getApiSecret());
        
        PlaidApi.Builder builder = PlaidApi.builder();
        
        if (provider.isSandboxMode()) {
            builder.sandboxBaseUrl();
        } else {
            builder.productionBaseUrl();
        }
        
        return builder.apiKeys(apiKeys).build();
    }
    
    /**
     * Result of Plaid health check
     */
    private static class HealthCheckResult {
        final boolean success;
        final long responseTimeMs;
        final String errorMessage;
        
        HealthCheckResult(boolean success, long responseTimeMs, String errorMessage) {
            this.success = success;
            this.responseTimeMs = responseTimeMs;
            this.errorMessage = errorMessage;
        }
    }
}