package com.waqiti.customer.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Feign Client Configuration for Customer Service.
 * Configures Feign clients with retry policy, timeout settings,
 * error handling, and request interceptors for correlation ID propagation.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@Slf4j
public class FeignClientConfig {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Configures Feign client retry behavior.
     * Retries up to 3 times with exponential backoff.
     * - Initial period: 1000ms
     * - Max period: 3000ms
     * - Max attempts: 3
     *
     * @return Configured retryer
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(1000L, TimeUnit.SECONDS.toMillis(3L), 3);
    }

    /**
     * Configures request timeout options.
     * - Connect timeout: 5 seconds
     * - Read timeout: 10 seconds
     * - Follow redirects: enabled
     *
     * @return Request options
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(5000, TimeUnit.MILLISECONDS, 10000, TimeUnit.MILLISECONDS, true);
    }

    /**
     * Configures Feign logging level.
     * Uses FULL level for development to log request and response details.
     * Consider using BASIC or NONE in production for performance.
     *
     * @return Logger level
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * Custom error decoder for Feign clients.
     * Provides detailed error logging and exception handling.
     * Decodes HTTP error responses into appropriate exceptions.
     *
     * @return Error decoder
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            log.error("Feign client error: method={}, status={}, reason={}",
                    methodKey, response.status(), response.reason());
            return new ErrorDecoder.Default().decode(methodKey, response);
        };
    }

    /**
     * Request interceptor for adding correlation ID headers.
     * Propagates correlation ID across service calls for distributed tracing.
     * Creates new correlation ID if not present in MDC.
     *
     * @return RequestInterceptor for correlation ID
     */
    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return requestTemplate -> {
            String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            requestTemplate.header(CORRELATION_ID_HEADER, correlationId);
            requestTemplate.header(REQUEST_ID_HEADER, UUID.randomUUID().toString());

            log.debug("Adding correlation ID to Feign request: correlationId={}, url={}",
                correlationId, requestTemplate.url());
        };
    }
}

