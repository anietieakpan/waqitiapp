package com.waqiti.billpayment.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;

/**
 * Configuration for Notification Service Feign client
 */
@Configuration
@Slf4j
public class NotificationServiceClientConfig {

    @Bean
    public RequestInterceptor notificationServiceRequestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                    Jwt jwt = (Jwt) authentication.getPrincipal();
                    template.header("Authorization", "Bearer " + jwt.getTokenValue());
                }

                template.header("Content-Type", "application/json");
                template.header("X-Service-Name", "bill-payment-service");
            }
        };
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> notificationServiceCircuitBreakerCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(60)
                        .waitDurationInOpenState(Duration.ofSeconds(45))
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(10))
                        .build())
                .build(), "notification-service");
    }
}
