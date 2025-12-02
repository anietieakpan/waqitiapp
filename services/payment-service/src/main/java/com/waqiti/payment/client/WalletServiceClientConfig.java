package com.waqiti.payment.client.config;

import com.waqiti.common.tracing.CorrelationId;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.codec.ErrorDecoder;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Configuration class for WalletServiceClient providing comprehensive
 * Feign client configuration optimized for wallet operations including
 * authentication, timeouts, logging, error handling, and financial
 * transaction-specific requirements.
 *
 * This configuration ensures:
 * - Secure authentication token propagation
 * - Appropriate timeouts for financial operations
 * - Comprehensive audit logging for compliance
 * - Robust error handling for wallet operations
 * - Request/response transformation with proper serialization
 * - Idempotency key management for financial safety
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Configuration
@Slf4j
public class WalletServiceClientConfig {

    @Value("${waqiti.clients.wallet.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${waqiti.clients.wallet.read-timeout:15000}")
    private int readTimeout;

    @Value("${waqiti.clients.wallet.follow-redirects:false}")
    private boolean followRedirects;

    /**
     * Configures request timeout options for wallet service calls.
     * Wallet operations require fast response times for optimal user experience.
     */
    @Bean
    public Request.Options walletRequestOptions() {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                followRedirects
        );
    }

    /**
     * Request interceptor to add authentication, correlation, and wallet-specific headers.
     */
    @Bean
    public RequestInterceptor walletRequestInterceptor() {
        return new WalletRequestInterceptor();
    }

    /**
     * Custom error decoder for handling wallet service specific errors.
     */
    @Bean
    public ErrorDecoder walletErrorDecoder() {
        return new WalletErrorDecoder();
    }

    /**
     * Feign logger configuration for wallet operations monitoring.
     */
    @Bean
    public Logger.Level walletFeignLoggerLevel() {
        return Logger.Level.BASIC; // Basic level to reduce noise but maintain visibility
    }

    /**
     * Custom encoder for wallet request serialization.
     */
    @Bean
    public Encoder walletEncoder() {
        HttpMessageConverter<Object> jacksonConverter = new MappingJackson2HttpMessageConverter();
        return new SpringEncoder(() -> new HttpMessageConverters(jacksonConverter));
    }

    /**
     * Custom decoder for wallet response deserialization with ResponseEntity support.
     */
    @Bean
    public Decoder walletDecoder() {
        HttpMessageConverter<Object> jacksonConverter = new MappingJackson2HttpMessageConverter();
        return new ResponseEntityDecoder(new SpringDecoder(() -> new HttpMessageConverters(jacksonConverter)));
    }

    /**
     * Request interceptor implementation for wallet service
     */
    @Slf4j
    static class WalletRequestInterceptor implements RequestInterceptor {

        @Override
        public void apply(RequestTemplate template) {
            // Add correlation ID for distributed tracing
            String correlationId = MDC.get(CorrelationId.CORRELATION_ID_HEADER);
            if (correlationId == null) {
                correlationId = CorrelationId.generate();
                MDC.put(CorrelationId.CORRELATION_ID_HEADER, correlationId);
            }
            template.header(CorrelationId.CORRELATION_ID_HEADER, correlationId);

            // Add trace ID if available
            String traceId = MDC.get(CorrelationId.TRACE_ID_HEADER);
            if (traceId != null) {
                template.header(CorrelationId.TRACE_ID_HEADER, traceId);
            }

            // Add authentication token
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() instanceof AbstractOAuth2Token) {
                AbstractOAuth2Token token = (AbstractOAuth2Token) authentication.getCredentials();
                template.header("Authorization", "Bearer " + token.getTokenValue());
            }

            // Add service identification headers
            template.header("X-Service-Name", "payment-service");
            template.header("X-Service-Version", "1.0");
            template.header("X-Client-Type", "wallet-client");

            // Add wallet-specific headers
            template.header("X-Wallet-Client-Version", "1.0");
            template.header("X-Operation-Source", "payment-service");

            // Add content type and accept headers
            template.header("Content-Type", "application/json");
            template.header("Accept", "application/json");

            // Add request timestamp for audit trails
            template.header("X-Request-Timestamp", String.valueOf(System.currentTimeMillis()));

            // Add user agent for tracking
            template.header("User-Agent", "Waqiti-Payment-Service/1.0 (Wallet-Client)");

            // Generate idempotency key if not present for state-changing operations
            if (isStateChangingOperation(template) && !hasIdempotencyKey(template)) {
                String idempotencyKey = generateIdempotencyKey(correlationId);
                template.header("Idempotency-Key", idempotencyKey);
                log.debug("Generated idempotency key for wallet operation: {}", idempotencyKey);
            }

            // Log request details for auditing (exclude sensitive data)
            log.debug("Wallet service request: {} {} with correlation ID: {}",
                    template.method(),
                    template.path(),
                    correlationId);
        }

        private boolean isStateChangingOperation(RequestTemplate template) {
            String method = template.method();
            String path = template.path();

            // POST, PUT, DELETE operations that change wallet state
            return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) ||
                    ("GET".equals(method) && path.contains("/validate-balance")); // Balance validation affects reserves
        }

        private boolean hasIdempotencyKey(RequestTemplate template) {
            return template.headers().containsKey("Idempotency-Key");
        }

        private String generateIdempotencyKey(String correlationId) {
            return "wqt-wallet-" + correlationId + "-" + System.currentTimeMillis();
        }
    }

    /**
     * Custom error decoder for wallet service specific error handling
     */
    @Slf4j
    static class WalletErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            String correlationId = getCorrelationId(response);

            log.error("Wallet service error - Method: {}, Status: {}, Correlation ID: {}",
                    methodKey, response.status(), correlationId);

            switch (response.status()) {
                case 400:
                    return new WalletValidationException(
                            "Invalid wallet request: " + response.reason(),
                            correlationId
                    );
                case 401:
                    return new WalletAuthenticationException(
                            "Authentication failed for wallet service",
                            correlationId
                    );
                case 403:
                    return new WalletAuthorizationException(
                            "Insufficient permissions for wallet operation",
                            correlationId
                    );
                case 404:
                    return new WalletNotFoundException(
                            "Wallet or resource not found: " + response.reason(),
                            correlationId
                    );
                case 409:
                    return new WalletConflictException(
                            "Wallet operation conflict: " + response.reason(),
                            correlationId
                    );
                case 422:
                    return new InsufficientFundsException(
                            "Insufficient funds for operation: " + response.reason(),
                            correlationId
                    );
                case 429:
                    return new WalletRateLimitException(
                            "Wallet service rate limit exceeded",
                            correlationId,
                            getRetryAfter(response)
                    );
                case 500:
                    return new WalletServiceException(
                            "Internal wallet service error",
                            correlationId
                    );
                case 502:
                case 503:
                case 504:
                    return new WalletServiceUnavailableException(
                            "Wallet service temporarily unavailable: " + response.reason(),
                            correlationId
                    );
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        }

        private String getCorrelationId(feign.Response response) {
            return response.headers().getOrDefault(CorrelationId.CORRELATION_ID_HEADER, List.of("unknown"))
                    .stream().findFirst().orElse("unknown");
        }

        private Long getRetryAfter(feign.Response response) {
            return response.headers().getOrDefault("Retry-After", List.of("30"))
                    .stream().findFirst().map(Long::parseLong).orElse(30L);
        }
    }

    // ==================== Custom Exception Classes ====================

    /**
     * Base exception for all wallet service related errors
     */
    public static abstract class WalletException extends RuntimeException {
        private final String correlationId;

        protected WalletException(String message, String correlationId) {
            super(message);
            this.correlationId = correlationId;
        }

        protected WalletException(String message, String correlationId, Throwable cause) {
            super(message, cause);
            this.correlationId = correlationId;
        }

        public String getCorrelationId() {
            return correlationId;
        }
    }

    /**
     * Exception for wallet validation errors (400)
     */
    public static class WalletValidationException extends WalletException {
        public WalletValidationException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for wallet authentication failures (401)
     */
    public static class WalletAuthenticationException extends WalletException {
        public WalletAuthenticationException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for wallet authorization failures (403)
     */
    public static class WalletAuthorizationException extends WalletException {
        public WalletAuthorizationException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for wallet not found errors (404)
     */
    public static class WalletNotFoundException extends WalletException {
        public WalletNotFoundException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for wallet operation conflicts (409)
     */
    public static class WalletConflictException extends WalletException {
        public WalletConflictException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for insufficient funds (422)
     */
    public static class InsufficientFundsException extends WalletException {
        public InsufficientFundsException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for wallet rate limiting (429)
     */
    public static class WalletRateLimitException extends WalletException {
        private final Long retryAfterSeconds;

        public WalletRateLimitException(String message, String correlationId, Long retryAfterSeconds) {
            super(message, correlationId);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public Long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /**
     * Exception for internal wallet service errors (500)
     */
    public static class WalletServiceException extends WalletException {
        public WalletServiceException(String message, String correlationId) {
            super(message, correlationId);
        }

        public WalletServiceException(String message, String correlationId, Throwable cause) {
            super(message, correlationId, cause);
        }
    }

    /**
     * Exception for wallet service unavailability (502, 503, 504)
     */
    public static class WalletServiceUnavailableException extends WalletException {
        public WalletServiceUnavailableException(String message, String correlationId) {
            super(message, correlationId);
        }
    }
}