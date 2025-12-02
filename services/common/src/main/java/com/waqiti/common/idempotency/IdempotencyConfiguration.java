package com.waqiti.common.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Idempotency Configuration
 *
 * Registers idempotency interceptor for all financial endpoints.
 *
 * Configuration Properties:
 * - idempotency.enabled: Enable/disable idempotency (default: true)
 * - idempotency.default-ttl: Response cache TTL (default: PT24H)
 * - idempotency.redis-enabled: Use Redis for distributed caching (default: true)
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-02
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "idempotency.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class IdempotencyConfiguration implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns(
                        "/api/**/payments/**",
                        "/api/**/wallets/**",
                        "/api/**/transfers/**",
                        "/api/**/transactions/**",
                        "/api/**/deposits/**",
                        "/api/**/withdrawals/**"
                )
                .excludePathPatterns(
                        // Exclude GET requests (handled by interceptor logic)
                        "/api/**/health",
                        "/api/**/metrics",
                        "/api/**/actuator/**"
                )
                .order(10); // Run after security but before business logic
    }
}
