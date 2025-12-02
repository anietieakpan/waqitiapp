package com.waqiti.common.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production-Ready Tracing Filter for HTTP Requests
 *
 * This filter provides comprehensive distributed tracing for all HTTP requests with:
 * - Automatic span creation for incoming requests
 * - Trace context extraction and propagation (W3C Trace Context)
 * - Baggage propagation for cross-cutting concerns
 * - Custom correlation ID generation and management
 * - MDC integration for logging correlation
 * - Performance metrics collection per request
 * - Error tracking and exception recording
 * - Request/response metadata capture
 * - Service mesh header support
 * - Sampling decision enforcement
 *
 * The filter integrates seamlessly with OpenTelemetry and supports:
 * - HTTP method, URL, headers, query parameters
 * - Client IP address (with proxy support)
 * - User agent and authentication context
 * - Response status codes and error details
 * - Request duration and performance metrics
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 1.0
 */
@Slf4j
public class TracingFilter extends OncePerRequestFilter {

    private static final String TRACE_CONTEXT_ATTRIBUTE = "otel.trace.context";
    private static final String SPAN_ATTRIBUTE = "otel.span";
    private static final String START_TIME_ATTRIBUTE = "trace.start.time";
    private static final String CORRELATION_ID_ATTRIBUTE = "trace.correlation.id";

    // Standard HTTP headers for trace context
    private static final String TRACE_PARENT_HEADER = "traceparent";
    private static final String TRACE_STATE_HEADER = "tracestate";
    private static final String BAGGAGE_HEADER = "baggage";

    // Waqiti custom headers
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String USER_ID_HEADER = "X-User-ID";
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String SESSION_ID_HEADER = "X-Session-ID";

    // OpenTelemetry components
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final TraceIdGenerator traceIdGenerator;

    // Feature flags
    private final boolean mdcEnabled;
    private final boolean metricsEnabled;

    // Metrics
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final Map<String, AtomicLong> endpointMetrics = new ConcurrentHashMap<>();

    // TextMapGetter for extracting trace context from HTTP headers
    private static final TextMapGetter<HttpServletRequest> REQUEST_TEXT_MAP_GETTER =
            new TextMapGetter<HttpServletRequest>() {
                @Override
                public Iterable<String> keys(HttpServletRequest request) {
                    List<String> keys = new ArrayList<>();
                    Enumeration<String> headerNames = request.getHeaderNames();
                    if (headerNames != null) {
                        while (headerNames.hasMoreElements()) {
                            keys.add(headerNames.nextElement());
                        }
                    }
                    return keys;
                }

                @Override
                public String get(HttpServletRequest request, String key) {
                    return request.getHeader(key);
                }
            };

    /**
     * Constructor
     *
     * @param openTelemetry OpenTelemetry instance
     * @param traceIdGenerator Custom trace ID generator
     * @param mdcEnabled Enable MDC logging integration
     * @param metricsEnabled Enable metrics collection
     */
    public TracingFilter(OpenTelemetry openTelemetry,
                        TraceIdGenerator traceIdGenerator,
                        boolean mdcEnabled,
                        boolean metricsEnabled) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("waqiti-http-server");
        this.traceIdGenerator = traceIdGenerator;
        this.mdcEnabled = mdcEnabled;
        this.metricsEnabled = metricsEnabled;

        log.info("TracingFilter initialized - MDC: {}, Metrics: {}", mdcEnabled, metricsEnabled);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                   @NonNull HttpServletResponse response,
                                   @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Skip tracing for health check and metrics endpoints
        String requestUri = request.getRequestURI();
        if (shouldSkipTracing(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Record start time
        Instant startTime = Instant.now();
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);

        // Extract or generate correlation ID
        String correlationId = extractOrGenerateCorrelationId(request);
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);

        // Extract trace context from incoming request
        Context extractedContext = openTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), request, REQUEST_TEXT_MAP_GETTER);

        // Create span for this HTTP request
        String operationName = buildOperationName(request);
        Span span = tracer.spanBuilder(operationName)
                .setParent(extractedContext)
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        // Store span in request attributes for potential access by application
        request.setAttribute(SPAN_ATTRIBUTE, span);

        // Make span current and add to context
        Context context = extractedContext.with(span);

        // Extract and set baggage
        Baggage baggage = extractBaggage(request, extractedContext);
        if (baggage != null) {
            context = context.with(baggage);
        }

        request.setAttribute(TRACE_CONTEXT_ATTRIBUTE, context);

        try (Scope scope = context.makeCurrent()) {
            // Add request attributes to span
            addRequestAttributesToSpan(span, request, correlationId);

            // Add baggage items
            addBaggageItems(baggage, request);

            // Set up MDC for logging
            if (mdcEnabled) {
                setupMDC(span, correlationId);
            }

            // Update metrics
            if (metricsEnabled) {
                requestCount.incrementAndGet();
                updateEndpointMetrics(operationName);
            }

            log.debug("Started tracing request: {} {} - TraceId: {}, SpanId: {}, CorrelationId: {}",
                    request.getMethod(),
                    requestUri,
                    span.getSpanContext().getTraceId(),
                    span.getSpanContext().getSpanId(),
                    correlationId);

            // Add correlation ID to response headers
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            response.setHeader("X-Trace-ID", span.getSpanContext().getTraceId());
            response.setHeader("X-Span-ID", span.getSpanContext().getSpanId());

            // Execute the request
            try {
                filterChain.doFilter(request, response);

                // Record successful response
                recordSuccessfulResponse(span, request, response, startTime);

            } catch (Exception e) {
                // Record error
                recordError(span, request, response, e, startTime);
                throw e;
            }

        } finally {
            // Calculate and record duration
            Duration duration = Duration.between(startTime, Instant.now());
            span.setAttribute("http.duration_ms", duration.toMillis());

            // End span
            span.end();

            // Clear MDC
            if (mdcEnabled) {
                clearMDC();
            }

            log.debug("Completed tracing request: {} {} - Duration: {}ms, Status: {}",
                    request.getMethod(),
                    requestUri,
                    duration.toMillis(),
                    response.getStatus());
        }
    }

    /**
     * Check if tracing should be skipped for this request
     */
    private boolean shouldSkipTracing(String requestUri) {
        return requestUri != null && (
                requestUri.contains("/actuator/") ||
                requestUri.contains("/health") ||
                requestUri.contains("/metrics") ||
                requestUri.contains("/prometheus") ||
                requestUri.endsWith("/favicon.ico")
        );
    }

    /**
     * Extract correlation ID from request headers or generate new one
     */
    private String extractOrGenerateCorrelationId(HttpServletRequest request) {
        // Try standard Waqiti header
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        // Try alternative headers
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = request.getHeader(REQUEST_ID_HEADER);
        }

        // Validate and return, or generate new one
        if (correlationId != null && traceIdGenerator.isValidCorrelationId(correlationId)) {
            return correlationId;
        }

        // Generate new correlation ID
        return traceIdGenerator.generateCorrelationId();
    }

    /**
     * Extract baggage from request
     */
    private Baggage extractBaggage(HttpServletRequest request, Context context) {
        try {
            String baggageHeader = request.getHeader(BAGGAGE_HEADER);
            if (baggageHeader != null && !baggageHeader.isEmpty()) {
                // Baggage is extracted by the propagator
                return Baggage.fromContext(context);
            }
        } catch (Exception e) {
            log.warn("Failed to extract baggage from request: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Add request attributes to span
     */
    private void addRequestAttributesToSpan(Span span, HttpServletRequest request, String correlationId) {
        try {
            // Standard HTTP semantic conventions
            span.setAttribute(SemanticAttributes.HTTP_METHOD, request.getMethod());
            span.setAttribute(SemanticAttributes.HTTP_URL, getFullURL(request));
            span.setAttribute(SemanticAttributes.HTTP_TARGET, request.getRequestURI());
            span.setAttribute(SemanticAttributes.HTTP_SCHEME, request.getScheme());
            span.setAttribute(SemanticAttributes.HTTP_HOST, request.getServerName());

            if (request.getQueryString() != null) {
                span.setAttribute("http.query", sanitizeQueryString(request.getQueryString()));
            }

            // Client information
            String clientIp = getClientIpAddress(request);
            if (clientIp != null) {
                span.setAttribute(SemanticAttributes.HTTP_CLIENT_IP, clientIp);
            }

            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null) {
                span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, userAgent);
            }

            // Waqiti-specific attributes
            span.setAttribute("waqiti.correlation_id", correlationId);

            String userId = request.getHeader(USER_ID_HEADER);
            if (userId != null) {
                span.setAttribute("waqiti.user_id", userId);
                span.setAttribute(SemanticAttributes.ENDUSER_ID, userId);
            }

            String tenantId = request.getHeader(TENANT_ID_HEADER);
            if (tenantId != null) {
                span.setAttribute("waqiti.tenant_id", tenantId);
            }

            String sessionId = request.getHeader(SESSION_ID_HEADER);
            if (sessionId != null) {
                span.setAttribute("waqiti.session_id", sessionId);
            }

            // Request metadata
            span.setAttribute("http.request.content_length", request.getContentLengthLong());
            if (request.getContentType() != null) {
                span.setAttribute("http.request.content_type", request.getContentType());
            }

        } catch (Exception e) {
            log.warn("Failed to add request attributes to span: {}", e.getMessage());
        }
    }

    /**
     * Add baggage items from custom headers
     */
    private void addBaggageItems(Baggage baggage, HttpServletRequest request) {
        if (baggage == null) {
            return;
        }

        try {
            // Build new baggage from current with additional entries
            var baggageBuilder = baggage.toBuilder();

            // Add user context as baggage
            String userId = request.getHeader(USER_ID_HEADER);
            if (userId != null) {
                baggageBuilder.put("user.id", userId);
            }

            String tenantId = request.getHeader(TENANT_ID_HEADER);
            if (tenantId != null) {
                baggageBuilder.put("tenant.id", tenantId);
            }

            baggageBuilder.build().storeInContext(Context.current());

        } catch (Exception e) {
            log.warn("Failed to add baggage items: {}", e.getMessage());
        }
    }

    /**
     * Set up MDC for logging correlation
     */
    private void setupMDC(Span span, String correlationId) {
        try {
            MDC.put("traceId", span.getSpanContext().getTraceId());
            MDC.put("spanId", span.getSpanContext().getSpanId());
            MDC.put("correlationId", correlationId);
            MDC.put("sampled", String.valueOf(span.getSpanContext().isSampled()));

            log.trace("MDC setup complete - TraceId: {}, SpanId: {}, CorrelationId: {}",
                    span.getSpanContext().getTraceId(),
                    span.getSpanContext().getSpanId(),
                    correlationId);

        } catch (Exception e) {
            log.warn("Failed to setup MDC: {}", e.getMessage());
        }
    }

    /**
     * Clear MDC
     */
    private void clearMDC() {
        MDC.remove("traceId");
        MDC.remove("spanId");
        MDC.remove("correlationId");
        MDC.remove("sampled");
    }

    /**
     * Record successful response
     */
    private void recordSuccessfulResponse(Span span, HttpServletRequest request,
                                         HttpServletResponse response, Instant startTime) {
        try {
            int statusCode = response.getStatus();

            span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, (long) statusCode);

            // Determine status based on HTTP status code
            if (statusCode >= 400) {
                span.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
                span.setAttribute("error", true);

                if (metricsEnabled) {
                    errorCount.incrementAndGet();
                }

                // Add error event
                span.addEvent("http.error", io.opentelemetry.api.common.Attributes.builder()
                        .put("http.status_code", (long) statusCode)
                        .put("http.method", request.getMethod())
                        .put("http.url", request.getRequestURI())
                        .build());

            } else {
                span.setStatus(StatusCode.OK);
            }

            // Add response headers
            String contentType = response.getContentType();
            if (contentType != null) {
                span.setAttribute("http.response.content_type", contentType);
            }

        } catch (Exception e) {
            log.warn("Failed to record successful response: {}", e.getMessage());
        }
    }

    /**
     * Record error
     */
    private void recordError(Span span, HttpServletRequest request,
                           HttpServletResponse response, Exception exception, Instant startTime) {
        try {
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            span.recordException(exception);
            span.setAttribute("error", true);
            span.setAttribute("error.type", exception.getClass().getName());
            span.setAttribute("error.message", exception.getMessage() != null ? exception.getMessage() : "");

            if (metricsEnabled) {
                errorCount.incrementAndGet();
            }

            int statusCode = response.getStatus();
            if (statusCode == 200) {
                // Status might not be set yet, default to 500
                statusCode = 500;
            }

            span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, (long) statusCode);

            log.error("Error in traced request: {} {} - {}: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage());

        } catch (Exception e) {
            log.warn("Failed to record error: {}", e.getMessage());
        }
    }

    /**
     * Build operation name for span
     */
    private String buildOperationName(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        // Normalize path by removing IDs for better grouping
        String normalizedPath = normalizePath(path);

        return method + " " + normalizedPath;
    }

    /**
     * Normalize path for operation name (replace IDs with placeholders)
     */
    private String normalizePath(String path) {
        if (path == null) {
            return "/";
        }

        return path
                // UUID pattern
                .replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "/{id}")
                // Numeric IDs
                .replaceAll("/\\d+", "/{id}")
                // Correlation IDs
                .replaceAll("/wqt-[0-9a-f]{32}", "/{correlationId}")
                // Generic alphanumeric IDs (long strings)
                .replaceAll("/[a-zA-Z0-9]{20,}", "/{id}");
    }

    /**
     * Get full URL from request
     */
    private String getFullURL(HttpServletRequest request) {
        StringBuffer requestURL = request.getRequestURL();
        String queryString = request.getQueryString();

        if (queryString == null) {
            return requestURL.toString();
        } else {
            return requestURL.append('?').append(queryString).toString();
        }
    }

    /**
     * Sanitize query string (mask sensitive parameters)
     */
    private String sanitizeQueryString(String queryString) {
        if (queryString == null) {
            return "";
        }

        // Mask sensitive parameters
        return queryString
                .replaceAll("(?i)(password|secret|token|key|apikey)=[^&]*", "$1=***")
                .replaceAll("(?i)(credit_card|card_number|cvv)=[^&]*", "$1=***");
    }

    /**
     * Get client IP address (supports proxy headers)
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] ipHeaderCandidates = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED"
        };

        for (String header : ipHeaderCandidates) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Update endpoint metrics
     */
    private void updateEndpointMetrics(String endpoint) {
        endpointMetrics.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Get current metrics (for monitoring)
     */
    public Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("total_requests", requestCount.get());
        metrics.put("total_errors", errorCount.get());

        endpointMetrics.forEach((endpoint, count) ->
                metrics.put("endpoint." + endpoint, count.get()));

        return metrics;
    }

    /**
     * Reset metrics (for testing)
     */
    public void resetMetrics() {
        requestCount.set(0);
        errorCount.set(0);
        endpointMetrics.clear();
        log.info("Tracing filter metrics reset");
    }
}
