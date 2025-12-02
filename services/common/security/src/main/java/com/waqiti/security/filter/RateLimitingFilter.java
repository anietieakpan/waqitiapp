package com.waqiti.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate Limiting Filter using Redis
 *
 * Implements sliding window rate limiting to protect against:
 * - Brute force attacks
 * - API abuse
 * - DDoS attempts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;

    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        String clientId = getClientIdentifier(request);
        String key = "rate_limit:" + clientId;

        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);

            if (currentCount == null) {
                currentCount = 1L;
            }

            if (currentCount == 1) {
                redisTemplate.expire(key, WINDOW_DURATION);
            }

            if (currentCount > MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for client: {}", clientId);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Rate limit exceeded. Please try again later.");
                return;
            }

            response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
            response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, MAX_REQUESTS_PER_MINUTE - currentCount)));

        } catch (Exception e) {
            log.error("Error in rate limiting filter", e);
            // Allow request to proceed on Redis errors (fail open for availability)
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIdentifier(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            return "user:" + userId;
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) {
            return "apikey:" + apiKey;
        }

        return "ip:" + request.getRemoteAddr();
    }
}
