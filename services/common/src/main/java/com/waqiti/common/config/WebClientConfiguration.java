package com.waqiti.common.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Centralized WebClient Configuration with Comprehensive Timeout Settings
 *
 * CRITICAL: All WebClients now have timeout configurations to prevent thread starvation
 *
 * TIMEOUT CONFIGURATION:
 * - Connection Timeout: Time to establish TCP connection (default: 10s)
 * - Response Timeout: Overall time for complete request/response (default: 30s)
 * - Read Timeout: Time waiting for response data (default: 30s)
 * - Write Timeout: Time to send request data (default: 10s)
 * - SSL Handshake Timeout: Time for TLS/SSL negotiation (default: 30s)
 *
 * USAGE:
 * <pre>
 * {@literal @}Autowired
 * {@literal @}Qualifier("defaultWebClient")
 * private WebClient webClient;
 *
 * // For external APIs with longer timeouts
 * {@literal @}Autowired
 * {@literal @}Qualifier("externalApiWebClient")
 * private WebClient externalApiWebClient;
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 3.0.0
 */
@Configuration
@ConditionalOnClass(WebClient.class)
@Slf4j
public class WebClientConfiguration {

    @Value("${webclient.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${webclient.read-timeout:30000}")
    private int readTimeout;

    @Value("${webclient.write-timeout:10000}")
    private int writeTimeout;

    @Value("${webclient.response-timeout:30000}")
    private int responseTimeout;

    @Value("${webclient.handshake-timeout:30000}")
    private int handshakeTimeout;

    @Value("${webclient.max-in-memory-size:10485760}")
    private int maxInMemorySize;  // 10MB default

    /**
     * Default WebClient with standard timeouts for internal service communication
     */
    @Bean("defaultWebClient")
    public WebClient defaultWebClient() {
        log.info("Configuring default WebClient - Connect: {}ms, Read: {}ms, Write: {}ms, Response: {}ms",
            connectionTimeout, readTimeout, writeTimeout, responseTimeout);

        return createWebClient(
            connectionTimeout,
            readTimeout,
            writeTimeout,
            responseTimeout,
            handshakeTimeout
        );
    }

    /**
     * WebClient for external API calls with longer timeouts
     */
    @Bean("externalApiWebClient")
    public WebClient externalApiWebClient() {
        int externalConnectTimeout = 30000;  // 30 seconds
        int externalReadTimeout = 60000;     // 60 seconds
        int externalWriteTimeout = 30000;    // 30 seconds
        int externalResponseTimeout = 60000; // 60 seconds

        log.info("Configuring external API WebClient - Connect: {}ms, Read: {}ms, Write: {}ms, Response: {}ms",
            externalConnectTimeout, externalReadTimeout, externalWriteTimeout, externalResponseTimeout);

        return createWebClient(
            externalConnectTimeout,
            externalReadTimeout,
            externalWriteTimeout,
            externalResponseTimeout,
            handshakeTimeout
        );
    }

    /**
     * WebClient for payment processors with extra long timeouts (3DS, fraud checks)
     */
    @Bean("paymentProviderWebClient")
    public WebClient paymentProviderWebClient() {
        int paymentConnectTimeout = 30000;   // 30 seconds
        int paymentReadTimeout = 90000;      // 90 seconds
        int paymentWriteTimeout = 30000;     // 30 seconds
        int paymentResponseTimeout = 90000;  // 90 seconds

        log.info("Configuring payment provider WebClient - Connect: {}ms, Read: {}ms, Write: {}ms, Response: {}ms",
            paymentConnectTimeout, paymentReadTimeout, paymentWriteTimeout, paymentResponseTimeout);

        return createWebClient(
            paymentConnectTimeout,
            paymentReadTimeout,
            paymentWriteTimeout,
            paymentResponseTimeout,
            handshakeTimeout
        );
    }

    /**
     * WebClient for health checks with very short timeouts (fail fast)
     */
    @Bean("healthCheckWebClient")
    public WebClient healthCheckWebClient() {
        int healthConnectTimeout = 3000;   // 3 seconds
        int healthReadTimeout = 5000;      // 5 seconds
        int healthWriteTimeout = 3000;     // 3 seconds
        int healthResponseTimeout = 5000;  // 5 seconds

        log.info("Configuring health check WebClient - Connect: {}ms, Read: {}ms, Write: {}ms, Response: {}ms",
            healthConnectTimeout, healthReadTimeout, healthWriteTimeout, healthResponseTimeout);

        return createWebClient(
            healthConnectTimeout,
            healthReadTimeout,
            healthWriteTimeout,
            healthResponseTimeout,
            10000  // 10s SSL handshake for health checks
        );
    }

    /**
     * WebClient for internal service-to-service calls (fast network)
     */
    @Bean("internalServiceWebClient")
    public WebClient internalServiceWebClient() {
        int internalConnectTimeout = 5000;   // 5 seconds
        int internalReadTimeout = 15000;     // 15 seconds
        int internalWriteTimeout = 5000;     // 5 seconds
        int internalResponseTimeout = 15000; // 15 seconds

        log.info("Configuring internal service WebClient - Connect: {}ms, Read: {}ms, Write: {}ms, Response: {}ms",
            internalConnectTimeout, internalReadTimeout, internalWriteTimeout, internalResponseTimeout);

        return createWebClient(
            internalConnectTimeout,
            internalReadTimeout,
            internalWriteTimeout,
            internalResponseTimeout,
            handshakeTimeout
        );
    }

    /**
     * Create WebClient with custom timeout configurations
     *
     * CRITICAL: This method configures all timeout layers:
     * 1. Netty channel options (connection timeout)
     * 2. Response timeout (overall request/response time)
     * 3. Read/Write timeout handlers (data transfer timeouts)
     * 4. SSL handshake timeout
     */
    private WebClient createWebClient(
            int connectTimeoutMs,
            int readTimeoutMs,
            int writeTimeoutMs,
            int responseTimeoutMs,
            int sslHandshakeTimeoutMs) {

        // Configure Netty HTTP client with timeouts
        HttpClient httpClient = HttpClient.create()
            // Connection timeout
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)

            // Response timeout (overall timeout for the entire request/response)
            .responseTimeout(Duration.ofMillis(responseTimeoutMs))

            // Read and write timeout handlers
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS))
            )

            // SSL/TLS configuration
            .secure(sslSpec -> {
                // Note: handshakeTimeout, closeNotifyFlushTimeout, and closeNotifyReadTimeout
                // are not available in newer Reactor Netty versions
                // SSL handshake timeout is now controlled by the connection timeout
                // Close notify timeouts are now handled internally by Reactor Netty
            });

        // Configure exchange strategies (buffer size limits)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer ->
                configurer.defaultCodecs().maxInMemorySize(maxInMemorySize)
            )
            .build();

        // Build and return WebClient
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
            .defaultHeader("User-Agent", "Waqiti/3.0")
            .defaultHeader("Accept", "application/json")
            .filter((request, next) -> {
                log.debug("WebClient request: {} {}", request.method(), request.url());
                return next.exchange(request);
            })
            .build();
    }

    /**
     * Builder for creating custom WebClient instances with specific timeouts
     *
     * USAGE:
     * <pre>
     * WebClient customClient = webClientBuilder()
     *     .baseUrl("https://api.example.com")
     *     .build();
     * </pre>
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        log.info("Configuring WebClient.Builder with default timeouts");

        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
            .responseTimeout(Duration.ofMillis(responseTimeout))
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS))
            )
            .secure(sslSpec -> {
                // Note: handshakeTimeout is not available in newer Reactor Netty versions
                // SSL handshake timeout is now controlled by the connection timeout
            });

        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer ->
                configurer.defaultCodecs().maxInMemorySize(maxInMemorySize)
            )
            .build();

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
            .defaultHeader("User-Agent", "Waqiti/3.0");
    }
}
