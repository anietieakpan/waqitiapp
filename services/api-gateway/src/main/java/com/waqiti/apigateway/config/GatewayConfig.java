package com.waqiti.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import reactor.core.publisher.Mono;

/**
 * API Gateway Route Configuration
 * Defines routes to backend microservices with load balancing, circuit breakers, and rate limiting
 */
@Configuration
public class GatewayConfig {

    private final RedisRateLimiter redisRateLimiter;
    private final KeyResolver userKeyResolver;

    public GatewayConfig(
            @Qualifier("redisRateLimiter") RedisRateLimiter redisRateLimiter,
            @Qualifier("userKeyResolver") KeyResolver userKeyResolver) {
        this.redisRateLimiter = redisRateLimiter;
        this.userKeyResolver = userKeyResolver;
    }

    @Bean
    public RouteLocator gatewayRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // User Service Routes
            .route("user-service", r -> r
                .path("/api/v1/users/**", "/api/v1/auth/**")
                .filters(f -> f
                    .stripPrefix(2) // Remove /api/v1
                    .circuitBreaker(config -> config
                        .setName("userServiceCircuitBreaker")
                        .setFallbackUri("forward:/fallback/user-service"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter)
                        .setKeyResolver(userKeyResolver))
                    .retry(config -> config
                        .setRetries(3)
                        .setMethods(org.springframework.http.HttpMethod.GET)
                        .setSeries(org.springframework.http.HttpStatus.Series.SERVER_ERROR)
                        .setBackoff(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(1000), 2, false)))
                .uri("lb://user-service"))
                
            // Payment Service Routes
            .route("payment-service", r -> r
                .path("/api/v1/payments/**", "/api/v1/transactions/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .circuitBreaker(config -> config
                        .setName("paymentServiceCircuitBreaker")
                        .setFallbackUri("forward:/fallback/payment-service"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter)
                        .setKeyResolver(userKeyResolver))
                    .retry(config -> config
                        .setRetries(2) // Fewer retries for payments
                        .setMethods(org.springframework.http.HttpMethod.GET)
                        .setSeries(org.springframework.http.HttpStatus.Series.SERVER_ERROR)))
                .uri("lb://payment-service"))
                
            // Wallet Service Routes
            .route("wallet-service", r -> r
                .path("/api/v1/wallets/**", "/api/v1/balance/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .circuitBreaker(config -> config
                        .setName("walletServiceCircuitBreaker")
                        .setFallbackUri("forward:/fallback/wallet-service"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter)
                        .setKeyResolver(userKeyResolver))
                    .retry(config -> config
                        .setRetries(3)
                        .setMethods(org.springframework.http.HttpMethod.GET)
                        .setSeries(org.springframework.http.HttpStatus.Series.SERVER_ERROR)))
                .uri("lb://wallet-service"))
                
            // Notification Service Routes
            .route("notification-service", r -> r
                .path("/api/v1/notifications/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .circuitBreaker(config -> config
                        .setName("notificationServiceCircuitBreaker")
                        .setFallbackUri("forward:/fallback/notification-service"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("lb://notification-service"))
                
            // Integration Service Routes
            .route("integration-service", r -> r
                .path("/api/v1/integration/**", "/api/v1/banking/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .circuitBreaker(config -> config
                        .setName("integrationServiceCircuitBreaker")
                        .setFallbackUri("forward:/fallback/integration-service"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("lb://integration-service"))
                
            // Analytics Service Routes
            .route("analytics-service", r -> r
                .path("/api/v1/analytics/**", "/api/v1/reports/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .circuitBreaker(config -> config
                        .setName("analyticsServiceCircuitBreaker")
                        .setFallbackUri("forward:/fallback/analytics-service"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("lb://analytics-service"))
                
            // Security Service Routes
            .route("security-service", r -> r
                .path("/api/v1/security/**", "/api/v1/kyc/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .circuitBreaker(config -> config
                        .setName("securityServiceCircuitBreaker")
                        .setFallbackUri("forward:/fallback/security-service"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("lb://security-service"))
                
            // Admin Service Routes (Higher security)
            .route("admin-service", r -> r
                .path("/api/v1/admin/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .circuitBreaker(config -> config
                        .setName("adminServiceCircuitBreaker")
                        .setFallbackUri("forward:/fallback/admin-service"))
                    .requestRateLimiter(config -> config
                        .setRateLimiter(adminRateLimiter())
                        .setKeyResolver(adminKeyResolver())))
                .uri("lb://admin-service"))
                
            // Health Check Routes (No auth required)
            .route("health-check", r -> r
                .path("/health/**", "/actuator/health/**")
                .filters(f -> f.stripPrefix(0))
                .uri("lb://discovery-service"))
                
            .build();
    }

    // Rate limiting beans are now defined in RateLimiterConfig to avoid conflicts
    // @Bean
    // public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter redisRateLimiter() {
    //     return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(50, 100, 1);
    // }

    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter adminRateLimiter() {
        return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(10, 20, 1);
    }

    // Key resolvers are now defined in RateLimiterConfig to avoid conflicts
    // @Bean
    // public KeyResolver userKeyResolver() {
    //     return exchange -> {
    //         String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
    //         return Mono.just(userId != null ? userId : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    //     };
    // }

    @Bean
    public KeyResolver adminKeyResolver() {
        return exchange -> {
            String adminId = exchange.getRequest().getHeaders().getFirst("X-Admin-Id");
            return Mono.just(adminId != null ? adminId : "admin");
        };
    }

    // @Bean
    // public KeyResolver ipKeyResolver() {
    //     return exchange -> Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    // }
}