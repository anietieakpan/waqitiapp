package com.waqiti.common.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Rate Limiting Configuration
 *
 * Registers the authentication rate limit filter to protect against
 * brute-force attacks on authentication endpoints.
 *
 * Configuration:
 * - rate-limit.enabled=true (default: true)
 * - rate-limit.fail-open=true (allow requests if Redis is down)
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfiguration {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Register authentication rate limit filter
     *
     * Order: HIGHEST_PRECEDENCE + 10 (after security context, before auth)
     */
    @Bean
    public FilterRegistrationBean<AuthenticationRateLimitFilter> authenticationRateLimitFilter() {
        log.info("SECURITY: Registering authentication rate limit filter");

        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(redisTemplate, objectMapper);

        FilterRegistrationBean<AuthenticationRateLimitFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);

        // Apply to authentication endpoints
        registrationBean.addUrlPatterns(
                "/api/v1/auth/*",
                "/api/v1/users/register",
                "/api/v1/users/verify/*",
                "/api/v1/users/password/reset/*"
        );

        // High priority - execute early in filter chain
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);

        log.info("SECURITY: Authentication rate limiting enabled on auth endpoints");

        return registrationBean;
    }

    /**
     * Rate limit metrics bean for monitoring
     */
    @Bean
    public RateLimitMetrics rateLimitMetrics() {
        return new RateLimitMetrics(redisTemplate);
    }
}
