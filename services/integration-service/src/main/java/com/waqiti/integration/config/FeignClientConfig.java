package com.waqiti.integration.config;

import com.waqiti.common.exception.BusinessException;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class FeignClientConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("X-Trace-ID", java.util.UUID.randomUUID().toString());
            log.debug("Adding trace ID to request: {}", requestTemplate.url());
        };
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            log.error("Error Response: status={}, body={}",
                    response.status(), getResponseBody(response));

            if (response.status() >= 400 && response.status() <= 499) {
                return new BusinessException(
                        "Client error in integration service: " + response.reason());
            } else if (response.status() >= 500 && response.status() <= 599) {
                return new com.waqiti.common.exception.ServiceUnavailableException(
                        "Server error in integration service: " + response.reason());
            }

            return new Exception("Unexpected error: " + response.reason());
        };
    }

    private String getResponseBody(feign.Response response) {
        try {
            if (response.body() != null) {
                return new String(response.body().asInputStream().readAllBytes());
            }
        } catch (Exception e) {
            log.warn("Could not read response body", e);
        }
        return "[empty]";
    }
}