package com.waqiti.common.tracing;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter to add correlation IDs to all HTTP requests.
 *
 * Correlation IDs enable end-to-end request tracing across microservices.
 * The ID is:
 * - Extracted from X-Correlation-ID header if present
 * - Generated if not present
 * - Added to MDC for logging
 * - Propagated to downstream services
 * - Returned in response header
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Slf4j
@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // Get or generate correlation ID
            String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);

            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = generateCorrelationId();
                log.debug("Generated new correlation ID: {}", correlationId);
            } else {
                log.debug("Using existing correlation ID: {}", correlationId);
            }

            // Add to MDC for logging
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

            // Add to response header
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

            // Store in request attribute for downstream use
            httpRequest.setAttribute(CORRELATION_ID_MDC_KEY, correlationId);

            // Continue filter chain
            chain.doFilter(request, response);

        } finally {
            // Clean up MDC
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    /**
     * Generate a unique correlation ID.
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get correlation ID from current request context.
     */
    public static String getCurrentCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        }
        return correlationId;
    }
}
