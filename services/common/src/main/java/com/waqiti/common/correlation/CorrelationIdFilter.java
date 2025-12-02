package com.waqiti.common.correlation;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filter to manage correlation IDs for all HTTP requests
 * Ensures every request has a correlation ID for distributed tracing
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CorrelationIdFilter implements Filter {

    private final CorrelationIdService correlationIdService;
    
    // Header names for correlation IDs
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    
    // Alternative header names for compatibility
    private static final List<String> CORRELATION_ID_HEADERS = Arrays.asList(
        "X-Correlation-Id",
        "X-Correlation-ID",
        "X-Request-Id",
        "X-Request-ID",
        "Correlation-Id",
        "Request-Id"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            // Extract or generate correlation ID
            String correlationId = extractCorrelationId(httpRequest);
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = correlationIdService.generateCorrelationId();
                log.debug("Generated new correlation ID: {} for request: {} {}",
                        correlationId, httpRequest.getMethod(), httpRequest.getRequestURI());
            } else {
                correlationIdService.setCorrelationId(correlationId);
                log.debug("Using existing correlation ID: {} for request: {} {}",
                        correlationId, httpRequest.getMethod(), httpRequest.getRequestURI());
            }
            
            // Extract or generate trace context
            String traceId = extractHeader(httpRequest, TRACE_ID_HEADER);
            if (traceId == null) {
                traceId = correlationIdService.generateTraceId();
            }
            
            String parentSpanId = extractHeader(httpRequest, PARENT_SPAN_ID_HEADER);
            if (parentSpanId == null) {
                parentSpanId = "ROOT";
            }
            
            correlationIdService.setTraceContext(traceId, parentSpanId);
            
            // Add correlation context to response headers
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            httpResponse.setHeader(SPAN_ID_HEADER, correlationIdService.getCurrentContext().getSpanId());
            
            // Log request with correlation ID
            logRequest(httpRequest, correlationId);
            
            // Continue with the filter chain
            chain.doFilter(request, response);
            
            // Log response
            logResponse(httpRequest, httpResponse, correlationId);
            
        } finally {
            // Clear context after request processing
            correlationIdService.clearContext();
        }
    }

    /**
     * Extract correlation ID from request headers
     */
    private String extractCorrelationId(HttpServletRequest request) {
        // Try standard headers
        for (String headerName : CORRELATION_ID_HEADERS) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        
        // Try request parameter as fallback
        String paramValue = request.getParameter("correlationId");
        if (paramValue != null && !paramValue.isEmpty()) {
            return paramValue;
        }
        
        return null;
    }

    /**
     * Extract header value
     */
    private String extractHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        return (value != null && !value.isEmpty()) ? value : null;
    }

    /**
     * Log incoming request
     */
    private void logRequest(HttpServletRequest request, String correlationId) {
        if (log.isDebugEnabled()) {
            log.debug("Incoming request - CorrelationId: {}, Method: {}, URI: {}, RemoteAddr: {}",
                    correlationId,
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr());
        }
    }

    /**
     * Log outgoing response
     */
    private void logResponse(HttpServletRequest request, HttpServletResponse response, String correlationId) {
        if (log.isDebugEnabled()) {
            log.debug("Outgoing response - CorrelationId: {}, Status: {}, Method: {}, URI: {}",
                    correlationId,
                    response.getStatus(),
                    request.getMethod(),
                    request.getRequestURI());
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("CorrelationIdFilter initialized");
    }

    @Override
    public void destroy() {
        log.info("CorrelationIdFilter destroyed");
    }
}