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
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Configuration class for ComplianceServiceClient providing comprehensive
 * Feign client configuration including authentication, timeouts, logging,
 * error handling, and request/response processing.
 * 
 * This configuration ensures:
 * - Proper authentication token propagation
 * - Correlation ID and tracing headers
 * - Appropriate timeouts for financial services
 * - Comprehensive error handling and logging
 * - Request/response transformation
 * 
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Configuration
@Slf4j
public class ComplianceServiceClientConfig {

    @Value("${waqiti.clients.compliance.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${waqiti.clients.compliance.read-timeout:30000}")
    private int readTimeout;

    @Value("${waqiti.clients.compliance.follow-redirects:false}")
    private boolean followRedirects;

    /**
     * Configures request timeout options for compliance service calls.
     * Compliance operations may require longer timeouts due to complex processing.
     */
    @Bean
    public Request.Options complianceRequestOptions() {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                followRedirects
        );
    }

    /**
     * Request interceptor to add authentication, correlation, and tracing headers.
     */
    @Bean
    public RequestInterceptor complianceRequestInterceptor() {
        return new ComplianceRequestInterceptor();
    }

    /**
     * Custom error decoder for handling compliance service specific errors.
     */
    @Bean
    public ErrorDecoder complianceErrorDecoder() {
        return new ComplianceErrorDecoder();
    }

    /**
     * Feign logger configuration for debugging and monitoring.
     */
    @Bean
    public Logger.Level complianceFeignLoggerLevel() {
        return Logger.Level.HEADERS; // Log headers for security auditing
    }

    /**
     * Custom encoder for request serialization.
     */
    @Bean
    public Encoder complianceEncoder() {
        HttpMessageConverter<Object> jacksonConverter = new MappingJackson2HttpMessageConverter();
        return new SpringEncoder(() -> new HttpMessageConverters(jacksonConverter));
    }

    /**
     * Custom decoder for response deserialization with ResponseEntity support.
     */
    @Bean
    public Decoder complianceDecoder() {
        HttpMessageConverter<Object> jacksonConverter = new MappingJackson2HttpMessageConverter();
        return new ResponseEntityDecoder(new SpringDecoder(() -> new HttpMessageConverters(jacksonConverter)));
    }

    /**
     * Request interceptor implementation for compliance service
     */
    @Slf4j
    static class ComplianceRequestInterceptor implements RequestInterceptor {

        @Override
        public void apply(RequestTemplate template) {
            // Add correlation ID
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
                template.header(\"Authorization\", \"Bearer \" + token.getTokenValue());
            }

            // Add service identification headers
            template.header(\"X-Service-Name\", \"payment-service\");
            template.header(\"X-Service-Version\", \"1.0\");
            template.header(\"X-Client-Type\", \"compliance-client\");

            // Add content type and accept headers
            template.header(\"Content-Type\", \"application/json\");
            template.header(\"Accept\", \"application/json\");

            // Add request timestamp
            template.header(\"X-Request-Timestamp\", String.valueOf(System.currentTimeMillis()));

            // Add user agent for tracking
            template.header(\"User-Agent\", \"Waqiti-Payment-Service/1.0 (Compliance-Client)\");

            // Log request details for auditing (without sensitive data)
            log.debug(\"Compliance service request: {} {} with correlation ID: {}\", 
                    template.method(), 
                    template.path(), 
                    correlationId);
        }
    }

    /**
     * Custom error decoder for compliance service specific error handling
     */
    @Slf4j
    static class ComplianceErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            String correlationId = getCorrelationId(response);
            
            log.error(\"Compliance service error - Method: {}, Status: {}, Correlation ID: {}\", 
                    methodKey, response.status(), correlationId);

            switch (response.status()) {
                case 400:
                    return new ComplianceValidationException(
                            \"Invalid compliance request: \" + response.reason(), 
                            correlationId
                    );
                case 401:
                    return new ComplianceAuthenticationException(
                            \"Authentication failed for compliance service\", 
                            correlationId
                    );
                case 403:
                    return new ComplianceAuthorizationException(
                            \"Insufficient permissions for compliance operation\", 
                            correlationId
                    );
                case 404:
                    return new ComplianceResourceNotFoundException(
                            \"Compliance resource not found: \" + response.reason(), 
                            correlationId
                    );
                case 409:
                    return new ComplianceConflictException(
                            \"Compliance check conflict: \" + response.reason(), 
                            correlationId
                    );
                case 422:
                    return new ComplianceBusinessException(
                            \"Compliance business rule violation: \" + response.reason(), 
                            correlationId
                    );
                case 429:
                    return new ComplianceRateLimitException(
                            \"Compliance service rate limit exceeded\", 
                            correlationId,
                            getRetryAfter(response)
                    );
                case 500:
                    return new ComplianceServiceException(
                            \"Internal compliance service error\", 
                            correlationId
                    );
                case 502:
                case 503:
                case 504:
                    return new ComplianceServiceUnavailableException(
                            \"Compliance service temporarily unavailable: \" + response.reason(), 
                            correlationId
                    );
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        }

        private String getCorrelationId(feign.Response response) {
            return response.headers().getOrDefault(CorrelationId.CORRELATION_ID_HEADER, List.of(\"unknown\"))
                    .stream().findFirst().orElse(\"unknown\");
        }

        private Long getRetryAfter(feign.Response response) {
            return response.headers().getOrDefault(\"Retry-After\", List.of(\"60\"))
                    .stream().findFirst().map(Long::parseLong).orElse(60L);
        }
    }

    // ==================== Custom Exception Classes ====================

    /**
     * Base exception for all compliance service related errors
     */
    public static abstract class ComplianceException extends RuntimeException {
        private final String correlationId;

        protected ComplianceException(String message, String correlationId) {
            super(message);
            this.correlationId = correlationId;
        }

        protected ComplianceException(String message, String correlationId, Throwable cause) {
            super(message, cause);
            this.correlationId = correlationId;
        }

        public String getCorrelationId() {
            return correlationId;
        }
    }

    /**
     * Exception for compliance validation errors (400)
     */
    public static class ComplianceValidationException extends ComplianceException {
        public ComplianceValidationException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for compliance authentication failures (401)
     */
    public static class ComplianceAuthenticationException extends ComplianceException {
        public ComplianceAuthenticationException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for compliance authorization failures (403)
     */
    public static class ComplianceAuthorizationException extends ComplianceException {
        public ComplianceAuthorizationException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for compliance resource not found errors (404)
     */
    public static class ComplianceResourceNotFoundException extends ComplianceException {
        public ComplianceResourceNotFoundException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for compliance conflict errors (409)
     */
    public static class ComplianceConflictException extends ComplianceException {
        public ComplianceConflictException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for compliance business rule violations (422)
     */
    public static class ComplianceBusinessException extends ComplianceException {
        public ComplianceBusinessException(String message, String correlationId) {
            super(message, correlationId);
        }
    }

    /**
     * Exception for compliance rate limiting (429)
     */
    public static class ComplianceRateLimitException extends ComplianceException {
        private final Long retryAfterSeconds;

        public ComplianceRateLimitException(String message, String correlationId, Long retryAfterSeconds) {
            super(message, correlationId);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public Long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /**
     * Exception for internal compliance service errors (500)
     */
    public static class ComplianceServiceException extends ComplianceException {
        public ComplianceServiceException(String message, String correlationId) {
            super(message, correlationId);
        }

        public ComplianceServiceException(String message, String correlationId, Throwable cause) {
            super(message, correlationId, cause);
        }
    }

    /**
     * Exception for compliance service unavailability (502, 503, 504)
     */
    public static class ComplianceServiceUnavailableException extends ComplianceException {
        public ComplianceServiceUnavailableException(String message, String correlationId) {
            super(message, correlationId);
        }
    }
}