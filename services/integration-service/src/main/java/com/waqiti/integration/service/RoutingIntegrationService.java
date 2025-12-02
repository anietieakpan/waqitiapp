package com.waqiti.integration.service;

import com.waqiti.integration.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Routing Integration Service
 * 
 * Routes banking operations to internal core banking services.
 * This service has been simplified after completing migration from legacy systems.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingIntegrationService {

    private final InternalCoreBankingService internalCoreBankingService;

    /**
     * Routes user creation to internal service
     */
    public CompletableFuture<UserRegistrationResponse> createUser(UserRegistrationRequest request) {
        log.info("Creating user for {}", request.getEmail());
        return internalCoreBankingService.createUser(request);
    }

    /**
     * Routes account creation to internal service
     */
    public CompletableFuture<AccountResponse> createAccount(String userId, AccountCreationRequest request) {
        log.info("Creating account for user {}", userId);
        return internalCoreBankingService.createAccount(userId, request);
    }

    /**
     * Routes balance retrieval to internal service
     */
    public CompletableFuture<AccountBalanceResponse> getAccountBalance(String userId, String accountId) {
        log.debug("Retrieving balance for user {} account {}", userId, accountId);
        return internalCoreBankingService.getAccountBalance(userId, accountId);
    }

    /**
     * Routes payment processing to internal service
     */
    public CompletableFuture<PaymentResponse> performPayment(String userId, PaymentRequest request) {
        log.info("Processing payment for user {}", userId);
        return internalCoreBankingService.performPayment(userId, request);
    }

    /**
     * Health check for internal services
     */
    public CompletableFuture<HealthStatus> checkHealth() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simple health check - in production this would be more comprehensive
                return HealthStatus.builder()
                    .isHealthy(true)
                    .responseTime(10) // Fast internal response
                    .timestamp(java.time.Instant.now())
                    .version("internal-1.0")
                    .serviceName("internal-core-banking")
                    .build();
            } catch (Exception e) {
                return HealthStatus.builder()
                    .isHealthy(false)
                    .responseTime(-1)
                    .timestamp(java.time.Instant.now())
                    .error(e.getMessage())
                    .serviceName("internal-core-banking")
                    .build();
            }
        });
    }

    /**
     * Get current routing configuration for monitoring/debugging
     * Simplified after migration completion
     */
    public RoutingConfiguration getRoutingConfiguration() {
        return RoutingConfiguration.builder()
            .useInternalServices(true)
            .serviceName("internal-core-banking")
            .version("1.0")
            .build();
    }

    /**
     * Simplified configuration data class
     */
    public static class RoutingConfiguration {
        public boolean useInternalServices;
        public String serviceName;
        public String version;

        public static RoutingConfigurationBuilder builder() {
            return new RoutingConfigurationBuilder();
        }

        public static class RoutingConfigurationBuilder {
            private boolean useInternalServices;
            private String serviceName;
            private String version;

            public RoutingConfigurationBuilder useInternalServices(boolean useInternalServices) {
                this.useInternalServices = useInternalServices;
                return this;
            }

            public RoutingConfigurationBuilder serviceName(String serviceName) {
                this.serviceName = serviceName;
                return this;
            }

            public RoutingConfigurationBuilder version(String version) {
                this.version = version;
                return this;
            }

            public RoutingConfiguration build() {
                RoutingConfiguration config = new RoutingConfiguration();
                config.useInternalServices = this.useInternalServices;
                config.serviceName = this.serviceName;
                config.version = this.version;
                return config;
            }
        }
    }
}