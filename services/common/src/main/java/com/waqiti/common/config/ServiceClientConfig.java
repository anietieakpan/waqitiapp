package com.waqiti.common.config;

import com.waqiti.common.client.*;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Service Client Configuration
 * 
 * Configures RestTemplate, circuit breakers, and service clients
 * for inter-service communication
 */
@Configuration
@Slf4j
public class ServiceClientConfig {

    @Value("${services.rest-template.connection-timeout:5000}")
    private int connectionTimeout;

    @Value("${services.rest-template.read-timeout:10000}")
    private int readTimeout;

    @Value("${services.circuit-breaker.failure-rate-threshold:50}")
    private float failureRateThreshold;

    @Value("${services.circuit-breaker.slow-call-rate-threshold:100}")
    private float slowCallRateThreshold;

    @Value("${services.circuit-breaker.slow-call-duration-threshold:60000}")
    private long slowCallDurationThreshold;

    @Value("${services.circuit-breaker.wait-duration-in-open-state:60000}")
    private long waitDurationInOpenState;

    @Value("${services.circuit-breaker.sliding-window-size:100}")
    private int slidingWindowSize;

    @Value("${services.circuit-breaker.minimum-number-of-calls:10}")
    private int minimumNumberOfCalls;

    @Value("${services.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${services.retry.wait-duration:1000}")
    private long retryWaitDuration;

    @Value("${services.time-limiter.timeout-duration:30000}")
    private long timeLimiterTimeout;

    /**
     * Configure RestTemplate for service communication
     */
    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectionTimeout));
        factory.setConnectionRequestTimeout(Duration.ofMillis(readTimeout));
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // Add interceptors for correlation ID, authentication, etc.
        restTemplate.getInterceptors().add(new ServiceRequestInterceptor());
        
        log.info("RestTemplate configured with connection timeout: {}ms, read timeout: {}ms", 
            connectionTimeout, readTimeout);
        
        return restTemplate;
    }

    /**
     * Circuit Breaker Configuration
     */
    @Bean
    public CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(failureRateThreshold)
            .slowCallRateThreshold(slowCallRateThreshold)
            .slowCallDurationThreshold(Duration.ofMillis(slowCallDurationThreshold))
            .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenState))
            .slidingWindowSize(slidingWindowSize)
            .minimumNumberOfCalls(minimumNumberOfCalls)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    }

    /**
     * Retry Configuration
     */
    @Bean
    public RetryConfig retryConfig() {
        return RetryConfig.custom()
            .maxAttempts(maxRetryAttempts)
            .waitDuration(Duration.ofMillis(retryWaitDuration))
            .retryExceptions(
                java.net.ConnectException.class,
                java.net.SocketTimeoutException.class,
                org.springframework.web.client.ResourceAccessException.class
            )
            .ignoreExceptions(
                org.springframework.web.client.HttpClientErrorException.BadRequest.class,
                org.springframework.web.client.HttpClientErrorException.Unauthorized.class,
                org.springframework.web.client.HttpClientErrorException.Forbidden.class,
                org.springframework.web.client.HttpClientErrorException.NotFound.class
            )
            .build();
    }

    /**
     * Time Limiter Configuration
     */
    @Bean
    public TimeLimiterConfig timeLimiterConfig() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(timeLimiterTimeout))
            .cancelRunningFuture(true)
            .build();
    }

    /**
     * Service Request Interceptor
     */
    private static class ServiceRequestInterceptor implements org.springframework.http.client.ClientHttpRequestInterceptor {
        
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request, 
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException {
            
            // Add correlation ID
            String correlationId = org.slf4j.MDC.get("correlationId");
            if (correlationId != null) {
                request.getHeaders().add("X-Correlation-ID", correlationId);
            }
            
            // Add request ID
            request.getHeaders().add("X-Request-ID", java.util.UUID.randomUUID().toString());
            
            // Add timestamp
            request.getHeaders().add("X-Request-Timestamp", java.time.Instant.now().toString());
            
            // Add service name
            request.getHeaders().add("X-Source-Service", getServiceName());
            
            log.debug("Outgoing request to: {} with correlation ID: {}", request.getURI(), correlationId);
            
            // Execute the request
            return execution.execute(request, body);
        }
        
        private String getServiceName() {
            // Get service name from application properties or environment
            return System.getProperty("spring.application.name", "unknown-service");
        }
    }

    /**
     * User Service Client Bean
     */
    @Bean
    public UserServiceClient userServiceClient(RestTemplate restTemplate,
                                             @Value("${services.user-service.url}") String userServiceUrl) {
        log.info("Configuring UserServiceClient with URL: {}", userServiceUrl);
        return new UserServiceClient(restTemplate, userServiceUrl);
    }

    /**
     * Wallet Service Client Bean
     */
    @Bean
    public WalletServiceClient walletServiceClient(RestTemplate restTemplate,
                                                 @Value("${services.wallet-service.url}") String walletServiceUrl) {
        log.info("Configuring WalletServiceClient with URL: {}", walletServiceUrl);
        return new WalletServiceClient(restTemplate, walletServiceUrl);
    }

    /**
     * Payment Service Client Bean
     */
    @Bean
    public PaymentServiceClient paymentServiceClient(RestTemplate restTemplate,
                                                   @Value("${services.payment-service.url}") String paymentServiceUrl) {
        log.info("Configuring PaymentServiceClient with URL: {}", paymentServiceUrl);
        return new PaymentServiceClient(restTemplate, paymentServiceUrl);
    }

    /**
     * Security Service Client Bean
     */
    @Bean
    public SecurityServiceClient securityServiceClient(RestTemplate restTemplate,
                                                     @Value("${services.security-service.url}") String securityServiceUrl) {
        log.info("Configuring SecurityServiceClient with URL: {}", securityServiceUrl);
        return new SecurityServiceClient(restTemplate, securityServiceUrl);
    }

    /**
     * Analytics Service Client Bean
     */
    @Bean
    public AnalyticsServiceClient analyticsServiceClient(RestTemplate restTemplate,
                                                       @Value("${services.analytics-service.url}") String analyticsServiceUrl) {
        log.info("Configuring AnalyticsServiceClient with URL: {}", analyticsServiceUrl);
        return new AnalyticsServiceClient(restTemplate, analyticsServiceUrl);
    }

    /**
     * Health check configuration for all service clients
     */
    @Bean
    public ServiceHealthChecker serviceHealthChecker() {
        return new ServiceHealthChecker();
    }

    /**
     * Service Health Checker
     */
    public static class ServiceHealthChecker {
        
        private final java.util.concurrent.ScheduledExecutorService scheduler = 
            java.util.concurrent.Executors.newScheduledThreadPool(5);
        
        @jakarta.annotation.PostConstruct
        public void startHealthChecks() {
            log.info("Starting service health checks");
            
            // Schedule periodic health checks for all services
            scheduler.scheduleWithFixedDelay(this::performHealthChecks, 30, 60, 
                java.util.concurrent.TimeUnit.SECONDS);
        }
        
        private void performHealthChecks() {
            try {
                // Implementation would check health of all registered services
                log.debug("Performing periodic health checks for all services");
                
                // Check each service health endpoint
                // Update service status in registry
                // Send alerts for unhealthy services
                
            } catch (Exception e) {
                log.error("Error during health checks", e);
            }
        }
        
        @jakarta.annotation.PreDestroy
        public void shutdown() {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}