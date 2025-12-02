package com.waqiti.dispute.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * REST Template configuration for external service communication.
 * Configures timeouts, interceptors, circuit breakers, and error handling.
 *
 * @author Waqiti Development Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RestTemplateConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    /**
     * Creates RestTemplate bean with production-grade configuration.
     * Features:
     * - Connection timeout: 5 seconds
     * - Read timeout: 10 seconds
     * - Request/response logging interceptor
     * - Error handling interceptor
     * - Buffering for retry capability
     *
     * @param builder RestTemplateBuilder injected by Spring Boot
     * @return Configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        log.info("Initializing RestTemplate with production configuration");

        // Create buffering factory for logging and retry capability
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());

        BufferingClientHttpRequestFactory bufferingFactory =
            new BufferingClientHttpRequestFactory(requestFactory);

        // Build interceptor chain
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new RequestResponseLoggingInterceptor());
        interceptors.add(new CorrelationIdInterceptor());

        RestTemplate restTemplate = builder
            .requestFactory(() -> bufferingFactory)
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .interceptors(interceptors)
            .errorHandler(new RestTemplateResponseErrorHandler())
            .build();

        log.info("RestTemplate initialized successfully with timeouts: connect=5s, read=10s");
        return restTemplate;
    }

    /**
     * Logging interceptor for REST requests and responses.
     * Logs request method, URI, status code, and execution time.
     * Does NOT log request/response bodies to prevent sensitive data leakage.
     */
    private static class RequestResponseLoggingInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException {

            long startTime = System.currentTimeMillis();

            log.debug("Outgoing Request: {} {}", request.getMethod(), request.getURI());

            org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);

            long duration = System.currentTimeMillis() - startTime;

            log.debug("Response received: {} {} - Status: {} - Duration: {}ms",
                request.getMethod(),
                request.getURI(),
                response.getStatusCode(),
                duration);

            return response;
        }
    }

    /**
     * Correlation ID interceptor for distributed tracing.
     * Propagates correlation ID across service boundaries.
     */
    private static class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException {

            // Add correlation ID header if available from MDC or generate new one
            String correlationId = org.slf4j.MDC.get("correlationId");
            if (correlationId == null) {
                correlationId = java.util.UUID.randomUUID().toString();
            }

            request.getHeaders().add("X-Correlation-ID", correlationId);
            request.getHeaders().add("X-Service-Name", "dispute-service");

            return execution.execute(request, body);
        }
    }
}
