package com.waqiti.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Configuration for RestTemplate beans with proper connection pooling and security
 */
@Configuration
@Slf4j
public class RestTemplateConfig {

    // PRODUCTION-OPTIMIZED TIMEOUTS
    // Connection timeout: Time to establish TCP connection (5s prevents slow server attacks)
    @Value("${http.client.connection.timeout:5000}")
    private int connectionTimeout;

    // Read timeout: Time to read response after connection (30s for normal operations)
    @Value("${http.client.read.timeout:30000}")
    private int readTimeout;

    // Connection request timeout: Time to get connection from pool (3s prevents pool exhaustion)
    @Value("${http.client.connection.request.timeout:3000}")
    private int connectionRequestTimeout;

    // PRODUCTION-OPTIMIZED CONNECTION POOLING
    // Max total connections across all routes (increased for high throughput)
    @Value("${http.client.max.connections:200}")
    private int maxConnections;

    // Max connections per route (host:port) - prevents single-host saturation
    @Value("${http.client.max.per.route:50}")
    private int maxConnectionsPerRoute;

    // Validate idle connections after inactivity (2s catches stale connections)
    @Value("${http.client.validate.after.inactivity:2000}")
    private int validateAfterInactivity;

    // Evict idle connections after timeout (60s frees resources)
    @Value("${http.client.evict.idle.timeout:60000}")
    private int evictIdleTimeout;

    /**
     * SECURITY FIX: Removed verifySsl flag - SSL verification is now ALWAYS enabled
     * Trust-all SSL is a critical security vulnerability (CVSS 9.3)
     * For testing with self-signed certificates, use proper certificate import
     */

    /**
     * Primary RestTemplate with connection pooling and ENFORCED SSL verification
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return restTemplateBuilder().build();
    }

    /**
     * Secure RestTemplate with MANDATORY SSL verification and enhanced security
     * SECURITY: SSL verification is ALWAYS enabled - no bypass option
     */
    @Bean("secureRestTemplate")
    public RestTemplate secureRestTemplate() {
        log.info("Creating secure RestTemplate with ENFORCED SSL verification");
        
        try {
            // Create HTTP client with connection pooling
            // PRODUCTION-GRADE CONNECTION POOLING
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setMaxTotal(maxConnections);
            connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
            connectionManager.setValidateAfterInactivity(Timeout.ofMilliseconds(validateAfterInactivity));

            // PRODUCTION-GRADE TIMEOUT CONFIGURATION
            RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
                .build();

            log.info("RestTemplate pool configured: maxTotal={}, maxPerRoute={}, connectTimeout={}ms, readTimeout={}ms",
                maxConnections, maxConnectionsPerRoute, connectionTimeout, readTimeout);

            HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(config);

            // SECURITY FIX: SSL verification is ALWAYS enabled
            // For development with self-signed certs, import them into Java truststore:
            // keytool -import -alias mycert -file cert.pem -keystore $JAVA_HOME/lib/security/cacerts

            HttpClient httpClient = clientBuilder.build();
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            
            RestTemplate restTemplate = new RestTemplate(factory);
            
            // Add error handler for better error management
            restTemplate.setErrorHandler(new CustomRestTemplateErrorHandler());
            
            return restTemplate;
            
        } catch (Exception e) {
            log.error("Failed to create secure RestTemplate", e);
            throw new IllegalStateException("Cannot create RestTemplate without proper SSL configuration", e);
        }
    }

    /**
     * RestTemplate specifically for external API calls with longer timeouts
     */
    @Bean("externalApiRestTemplate")
    public RestTemplate externalApiRestTemplate() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(60))
            .setReadTimeout(Duration.ofSeconds(60))
            .build();
    }

    /**
     * RestTemplate for internal service-to-service communication
     */
    @Bean("internalServiceRestTemplate")
    public RestTemplate internalServiceRestTemplate() {
        return restTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Lightweight RestTemplate for health checks and monitoring
     */
    @Bean("healthCheckRestTemplate")
    public RestTemplate healthCheckRestTemplate() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
    }

    private RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(connectionTimeout))
            .setReadTimeout(Duration.ofMillis(readTimeout))
            .errorHandler(new CustomRestTemplateErrorHandler());
    }

    /**
     * SECURITY FIX: Removed createTrustAllSSLContext() method entirely
     * Trust-all SSL is a CRITICAL security vulnerability enabling MITM attacks
     * 
     * For development/testing with self-signed certificates, use proper approach:
     * 1. Generate proper certificates
     * 2. Import into Java truststore
     * 3. Use certificate pinning for production
     * 
     * DO NOT re-enable trust-all SSL under any circumstances
     */

    /**
     * Custom error handler for RestTemplate operations
     */
    private static class CustomRestTemplateErrorHandler implements org.springframework.web.client.ResponseErrorHandler {
        
        @Override
        public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
            try {
                return response.getStatusCode().is4xxClientError() || 
                       response.getStatusCode().is5xxServerError();
            } catch (Exception e) {
                return true;
            }
        }

        @Override
        public void handleError(org.springframework.http.client.ClientHttpResponse response) {
            try {
                log.error("RestTemplate error: {} {}", response.getStatusCode(), response.getStatusText());
                
                // Let Spring's default error handler manage the exception throwing
                new org.springframework.web.client.DefaultResponseErrorHandler().handleError(response);
                
            } catch (Exception e) {
                log.error("Error handling RestTemplate response", e);
                throw new RuntimeException("RestTemplate operation failed: " + e.getMessage(), e);
            }
        }
    }
}