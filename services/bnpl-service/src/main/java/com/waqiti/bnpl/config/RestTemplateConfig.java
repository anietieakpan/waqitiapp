/**
 * RestTemplate Configuration
 * Production-ready HTTP client configuration with:
 * - Connection pooling
 * - Timeout configuration
 * - Error handling
 * - Interceptors for logging and metrics
 */
package com.waqiti.bnpl.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Configures RestTemplate with production-grade settings
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RestTemplateConfig {

    private final MeterRegistry meterRegistry;

    @Value("${http.client.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${http.client.read-timeout:30000}")
    private int readTimeout;

    @Value("${http.client.max-connections:100}")
    private int maxConnections;

    @Value("${http.client.max-connections-per-route:20}")
    private int maxConnectionsPerRoute;

    /**
     * Primary RestTemplate bean with connection pooling and timeouts
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Configure connection pool
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);

        // Configure timeouts
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
                .build();

        // Build HttpClient with connection pooling
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        // Create request factory
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        // Build RestTemplate with interceptors
        RestTemplate restTemplate = builder
                .requestFactory(() -> requestFactory)
                .setConnectTimeout(Duration.ofMillis(connectionTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .additionalInterceptors(loggingInterceptor())
                .additionalInterceptors(metricsInterceptor())
                .additionalInterceptors(requestIdInterceptor())
                .build();

        log.info("RestTemplate configured with connection timeout: {}ms, read timeout: {}ms, max connections: {}",
                connectionTimeout, readTimeout, maxConnections);

        return restTemplate;
    }

    /**
     * Logging interceptor for debugging and monitoring
     */
    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            long startTime = System.currentTimeMillis();

            log.debug("HTTP Request: {} {}", request.getMethod(), request.getURI());

            ClientHttpResponse response = execution.execute(request, body);

            long duration = System.currentTimeMillis() - startTime;

            log.debug("HTTP Response: {} {} - Status: {} - Duration: {}ms",
                    request.getMethod(),
                    request.getURI(),
                    response.getStatusCode(),
                    duration);

            return response;
        };
    }

    /**
     * Metrics interceptor for monitoring HTTP calls
     */
    private ClientHttpRequestInterceptor metricsInterceptor() {
        return (request, body, execution) -> {
            long startTime = System.currentTimeMillis();
            String uri = request.getURI().getHost();

            try {
                ClientHttpResponse response = execution.execute(request, body);
                long duration = System.currentTimeMillis() - startTime;

                // Record success metrics
                meterRegistry.counter("http.client.requests.total",
                        "method", request.getMethod().name(),
                        "uri", uri,
                        "status", String.valueOf(response.getStatusCode().value())
                ).increment();

                meterRegistry.timer("http.client.request.duration",
                        "method", request.getMethod().name(),
                        "uri", uri
                ).record(Duration.ofMillis(duration));

                return response;

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;

                // Record error metrics
                meterRegistry.counter("http.client.requests.errors",
                        "method", request.getMethod().name(),
                        "uri", uri,
                        "exception", e.getClass().getSimpleName()
                ).increment();

                meterRegistry.timer("http.client.request.duration",
                        "method", request.getMethod().name(),
                        "uri", uri
                ).record(Duration.ofMillis(duration));

                throw e;
            }
        };
    }

    /**
     * Adds X-Request-ID header for distributed tracing
     */
    private ClientHttpRequestInterceptor requestIdInterceptor() {
        return (request, body, execution) -> {
            // Add request ID if not present
            if (!request.getHeaders().containsKey("X-Request-ID")) {
                request.getHeaders().add("X-Request-ID", UUID.randomUUID().toString());
            }

            // Add service identifier
            request.getHeaders().add("X-Service-Name", "bnpl-service");

            return execution.execute(request, body);
        };
    }
}
