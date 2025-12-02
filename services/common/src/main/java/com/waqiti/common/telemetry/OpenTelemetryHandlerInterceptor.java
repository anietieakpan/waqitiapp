package com.waqiti.common.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * OpenTelemetry Handler Interceptor for HTTP request tracing
 * 
 * This interceptor provides comprehensive HTTP request/response tracing:
 * - Automatic span creation for all HTTP requests
 * - Standard HTTP semantic attributes
 * - Custom Waqiti-specific attributes
 * - Error tracking and status code handling
 * - Performance metrics collection
 * - User and session correlation
 * 
 * @author Waqiti Platform Team
 * @since Phase 3 - OpenTelemetry Implementation
 */
@Slf4j
@RequiredArgsConstructor
public class OpenTelemetryHandlerInterceptor implements HandlerInterceptor {
    
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    
    private static final String SPAN_ATTRIBUTE_KEY = "opentelemetry.span";
    private static final String SCOPE_ATTRIBUTE_KEY = "opentelemetry.scope";
    
    public OpenTelemetryHandlerInterceptor(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("waqiti-http-interceptor", "1.0.0");
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        // Create span for HTTP request
        String spanName = createSpanName(request);
        
        SpanBuilder spanBuilder = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.SERVER)
                .setStartTimestamp(Instant.now());
        
        // Extract parent context from headers
        Context parentContext = openTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), request, new HttpRequestGetter());
        
        Span span = spanBuilder.setParent(parentContext).startSpan();
        
        // Add HTTP attributes
        addHttpAttributes(span, request);
        
        // Add custom Waqiti attributes
        addWaqitiAttributes(span, request);
        
        // Make span current
        Scope scope = span.makeCurrent();
        
        // Store span and scope in request attributes
        request.setAttribute(SPAN_ATTRIBUTE_KEY, span);
        request.setAttribute(SCOPE_ATTRIBUTE_KEY, scope);
        
        log.trace("Started span for request: {} {}", request.getMethod(), request.getRequestURI());
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, 
                              HttpServletResponse response, 
                              Object handler, 
                              Exception ex) throws Exception {
        
        Span span = (Span) request.getAttribute(SPAN_ATTRIBUTE_KEY);
        Scope scope = (Scope) request.getAttribute(SCOPE_ATTRIBUTE_KEY);
        
        if (span != null) {
            try {
                // Add response attributes
                addResponseAttributes(span, response);
                
                // Handle exceptions
                if (ex != null) {
                    addErrorAttributes(span, ex);
                }
                
                // Set span status based on HTTP status
                setSpanStatus(span, response, ex);
                
                log.trace("Completed span for request: {} {} with status: {}", 
                    request.getMethod(), request.getRequestURI(), response.getStatus());
                
            } finally {
                // End span
                span.end(Instant.now());
                
                // Close scope
                if (scope != null) {
                    scope.close();
                }
            }
        }
    }
    
    /**
     * Create span name from HTTP request
     */
    private String createSpanName(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        
        // Normalize path to avoid high cardinality
        String normalizedPath = normalizePath(path);
        
        return method + " " + normalizedPath;
    }
    
    /**
     * Normalize path to reduce cardinality
     */
    private String normalizePath(String path) {
        if (path == null) {
            return "/";
        }
        
        // Replace UUID patterns with placeholder
        path = path.replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/{uuid}");
        
        // Replace numeric IDs with placeholder
        path = path.replaceAll("/\\d+", "/{id}");
        
        // Replace hash patterns
        path = path.replaceAll("/[a-fA-F0-9]{32,64}", "/{hash}");
        
        return path;
    }
    
    /**
     * Add standard HTTP attributes to span
     */
    private void addHttpAttributes(Span span, HttpServletRequest request) {
        // HTTP method
        span.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.getMethod());
        
        // URL attributes
        span.setAttribute(UrlAttributes.URL_FULL, getFullUrl(request));
        span.setAttribute(UrlAttributes.URL_PATH, request.getRequestURI());
        span.setAttribute(UrlAttributes.URL_QUERY, request.getQueryString());
        span.setAttribute(UrlAttributes.URL_SCHEME, request.getScheme());
        
        // Server attributes
        span.setAttribute(SemanticAttributes.SERVER_ADDRESS, request.getServerName());
        span.setAttribute(SemanticAttributes.SERVER_PORT, (long) request.getServerPort());
        
        // Client attributes
        String clientIp = getClientIpAddress(request);
        if (clientIp != null) {
            span.setAttribute(SemanticAttributes.CLIENT_ADDRESS, clientIp);
        }
        
        // User agent
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            span.setAttribute(SemanticAttributes.USER_AGENT_ORIGINAL, userAgent);
        }
        
        // Content length
        int contentLength = request.getContentLength();
        if (contentLength > 0) {
            span.setAttribute(HttpAttributes.HTTP_REQUEST_BODY_SIZE, (long) contentLength);
        }
    }
    
    /**
     * Add Waqiti-specific attributes to span
     */
    private void addWaqitiAttributes(Span span, HttpServletRequest request) {
        // User ID from JWT token or header
        String userId = extractUserId(request);
        if (userId != null) {
            span.setAttribute("waqiti.user.id", userId);
        }
        
        // Session ID
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId == null && request.getSession(false) != null) {
            sessionId = request.getSession().getId();
        }
        if (sessionId != null) {
            span.setAttribute("waqiti.session.id", sessionId);
        }
        
        // Transaction ID
        String transactionId = request.getHeader("X-Transaction-Id");
        if (transactionId != null) {
            span.setAttribute("waqiti.transaction.id", transactionId);
        }
        
        // API version
        String apiVersion = extractApiVersion(request);
        if (apiVersion != null) {
            span.setAttribute("waqiti.api.version", apiVersion);
        }
        
        // Service tier
        String serviceTier = determineServiceTier(request);
        span.setAttribute("waqiti.service.tier", serviceTier);
        
        // Request correlation ID
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId != null) {
            span.setAttribute("waqiti.correlation.id", correlationId);
        }
        
        // Authentication method
        String authMethod = determineAuthMethod(request);
        if (authMethod != null) {
            span.setAttribute("waqiti.auth.method", authMethod);
        }
    }
    
    /**
     * Add response attributes to span
     */
    private void addResponseAttributes(Span span, HttpServletResponse response) {
        // HTTP status code
        span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus());
        
        // Response size
        String contentLength = response.getHeader("Content-Length");
        if (contentLength != null) {
            try {
                span.setAttribute(HttpAttributes.HTTP_RESPONSE_BODY_SIZE, Long.parseLong(contentLength));
            } catch (NumberFormatException e) {
                // Ignore invalid content length
            }
        }
        
        // Response content type
        String contentType = response.getContentType();
        if (contentType != null) {
            span.setAttribute("http.response.content_type", contentType);
        }
    }
    
    /**
     * Add error attributes to span
     */
    private void addErrorAttributes(Span span, Exception ex) {
        span.setAttribute("error", true);
        span.setAttribute("error.type", ex.getClass().getSimpleName());
        span.setAttribute("error.message", ex.getMessage() != null ? ex.getMessage() : "");
        
        // Record exception
        span.recordException(ex);
    }
    
    /**
     * Set span status based on HTTP response and exceptions
     */
    private void setSpanStatus(Span span, HttpServletResponse response, Exception ex) {
        if (ex != null) {
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, ex.getMessage());
        } else {
            int statusCode = response.getStatus();
            if (statusCode >= 400) {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, 
                    "HTTP " + statusCode);
            } else {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            }
        }
    }
    
    /**
     * Get full URL from request
     */
    private String getFullUrl(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        String queryString = request.getQueryString();
        if (queryString != null) {
            url.append('?').append(queryString);
        }
        return url.toString();
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Extract user ID from request
     */
    private String extractUserId(HttpServletRequest request) {
        // Try header first
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            return userId;
        }
        
        // Try to extract from JWT token (simplified)
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            // In a real implementation, you would decode the JWT and extract the user ID
            return "jwt-user"; // Placeholder
        }
        
        return null;
    }
    
    /**
     * Extract API version from request path
     */
    private String extractApiVersion(String requestURI) {
        if (requestURI.contains("/api/v1/")) {
            return "v1";
        } else if (requestURI.contains("/api/v2/")) {
            return "v2";
        }
        return null;
    }
    
    private String extractApiVersion(HttpServletRequest request) {
        return extractApiVersion(request.getRequestURI());
    }
    
    /**
     * Determine service tier based on request path
     */
    private String determineServiceTier(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        if (path.contains("/payment") || path.contains("/transfer")) {
            return "financial";
        } else if (path.contains("/compliance") || path.contains("/kyc")) {
            return "regulatory";
        } else if (path.contains("/user") || path.contains("/auth")) {
            return "identity";
        } else if (path.contains("/notification")) {
            return "messaging";
        }
        
        return "general";
    }
    
    /**
     * Determine authentication method
     */
    private String determineAuthMethod(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            if (authorization.startsWith("Bearer ")) {
                return "jwt";
            } else if (authorization.startsWith("Basic ")) {
                return "basic";
            }
        }
        
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) {
            return "api-key";
        }
        
        return "none";
    }
    
    /**
     * HTTP request getter for context extraction
     */
    private static class HttpRequestGetter implements io.opentelemetry.context.propagation.TextMapGetter<HttpServletRequest> {
        @Override
        public Iterable<String> keys(HttpServletRequest request) {
            return java.util.Collections.list(request.getHeaderNames());
        }
        
        @Override
        public String get(HttpServletRequest request, String key) {
            return request.getHeader(key);
        }
    }
}