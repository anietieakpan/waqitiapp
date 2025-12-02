package com.waqiti.common.idempotency;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Idempotency Response Caching Filter
 *
 * This filter works in conjunction with IdempotencyInterceptor to cache HTTP responses
 * for idempotent requests. It wraps the response to capture the response body and status,
 * then stores it in Redis for replay on subsequent identical requests.
 *
 * Order: HIGHEST_PRECEDENCE + 10 (runs after security filter but before most others)
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-02
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class IdempotencyResponseCachingFilter implements Filter {

    private final IdempotencyInterceptor idempotencyInterceptor;

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Check if this request has an idempotency key
        String idempotencyKey = httpRequest.getHeader(IDEMPOTENCY_KEY_HEADER);
        String cacheKey = (String) httpRequest.getAttribute("idempotencyCacheKey");

        // If no idempotency key or cache key, skip caching
        if (idempotencyKey == null || cacheKey == null) {
            chain.doFilter(request, response);
            return;
        }

        // Wrap response to capture output
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);

        try {
            // Process request
            chain.doFilter(request, responseWrapper);

            // Cache successful responses (2xx status codes)
            int status = responseWrapper.getStatus();
            if (status >= 200 && status < 300) {
                cacheResponse(cacheKey, responseWrapper);
            }

        } finally {
            // Copy cached content to original response
            responseWrapper.copyBodyToResponse();
        }
    }

    /**
     * Cache response for future idempotency checks
     */
    private void cacheResponse(String cacheKey, ContentCachingResponseWrapper responseWrapper) {
        try {
            int status = responseWrapper.getStatus();
            String contentType = responseWrapper.getContentType();
            byte[] contentBytes = responseWrapper.getContentAsByteArray();
            String responseBody = new String(contentBytes, StandardCharsets.UTF_8);

            // Cache in Redis via interceptor
            idempotencyInterceptor.cacheResponse(cacheKey, status, contentType, responseBody, DEFAULT_TTL);

            log.debug("Cached response for idempotency key - Status: {}, ContentType: {}, BodySize: {} bytes",
                    status, contentType, contentBytes.length);

        } catch (Exception e) {
            log.error("Failed to cache response for idempotency", e);
        }
    }
}
