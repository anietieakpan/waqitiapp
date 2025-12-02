package com.waqiti.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for HTTP client beans with comprehensive timeout settings
 *
 * CRITICAL: All HTTP clients now have timeout configurations to prevent thread starvation
 *
 * TIMEOUT CONFIGURATION:
 * - Connect Timeout: Time to establish TCP connection (default: 10s)
 * - Read Timeout: Time waiting for response data (default: 30s)
 * - Connection Request Timeout: Time to get connection from pool (default: 5s)
 *
 * @version 3.0.0
 */
@Configuration
@Slf4j
public class HttpClientConfiguration {

    @Value("${http.client.connection.timeout:10000}")
    private int connectionTimeout;

    @Value("${http.client.read.timeout:30000}")
    private int readTimeout;

    @Value("${http.client.connection-request.timeout:5000}")
    private int connectionRequestTimeout;

    @Value("${http.client.max.connections:200}")
    private int maxConnections;

    @Value("${http.client.max.per-route:50}")
    private int maxConnectionsPerRoute;

    /**
     * Provides a RestTemplate bean with standard configuration and timeouts
     *
     * CRITICAL: Connection pooling + timeouts prevent resource exhaustion
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        log.info("Configuring RestTemplate with timeouts - Connect: {}ms, Read: {}ms, ConnectionRequest: {}ms",
            connectionTimeout, readTimeout, connectionRequestTimeout);

        // Connection pool configuration
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);

        // Timeout configuration - HttpClient 5.x uses Timeout objects
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeout))
            .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
            .build();

        // Build HTTP client with pooling and timeouts
        CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();

        // Create RestTemplate with configured HTTP client
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(factory);

        log.info("RestTemplate configured successfully with connection pool (max: {}, per-route: {})",
            maxConnections, maxConnectionsPerRoute);

        return restTemplate;
    }
}