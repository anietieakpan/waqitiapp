package com.waqiti.payment.client;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.feign.Resilience4jFeign;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class TaxServiceClientConfig {

    @Value("${services.tax-service.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${services.tax-service.timeout.read:10000}")
    private int readTimeout;

    @Bean
    public Request.Options taxServiceRequestOptions() {
        return new Request.Options(connectTimeout, TimeUnit.MILLISECONDS,
                                 readTimeout, TimeUnit.MILLISECONDS,
                                 true);
    }

    @Bean
    public Logger.Level taxServiceLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Retryer taxServiceRetryer() {
        return new Retryer.Default(1000, 3000, 3);
    }

    @Bean
    public ErrorDecoder taxServiceErrorDecoder() {
        return new TaxServiceErrorDecoder();
    }

    @Bean
    public CircuitBreaker taxServiceCircuitBreaker() {
        return CircuitBreaker.ofDefaults("tax-service");
    }

    @Bean
    public FeignDecorators taxServiceFeignDecorators(CircuitBreaker taxServiceCircuitBreaker) {
        return FeignDecorators.builder()
                .withCircuitBreaker(taxServiceCircuitBreaker)
                .build();
    }

    private static class TaxServiceErrorDecoder implements ErrorDecoder {
        private final Default defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            switch (response.status()) {
                case 400:
                    return new TaxServiceException("Bad request to tax service: " + methodKey);
                case 404:
                    return new TaxServiceException("Tax service endpoint not found: " + methodKey);
                case 500:
                    return new TaxServiceException("Tax service internal error: " + methodKey);
                case 503:
                    return new TaxServiceException("Tax service unavailable: " + methodKey);
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        }
    }

    public static class TaxServiceException extends RuntimeException {
        public TaxServiceException(String message) {
            super(message);
        }
        
        public TaxServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}