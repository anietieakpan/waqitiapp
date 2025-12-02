package com.waqiti.common.tracing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP Interceptors for automatic distributed tracing propagation
 * Handles both incoming requests and outgoing HTTP calls
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TracingHttpInterceptor implements HandlerInterceptor, ClientHttpRequestInterceptor {

    private final DistributedTracingService tracingService;
    private final OpenTelemetryTracingService openTelemetryService;
    
    // Standard HTTP headers for trace propagation
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";
    private static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-Id";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String BAGGAGE_PREFIX = "X-Baggage-";
    
    // W3C Trace Context headers
    private static final String W3C_TRACEPARENT = "traceparent";
    private static final String W3C_TRACESTATE = "tracestate";
    
    // B3 Propagation headers (Zipkin)
    private static final String B3_TRACE_ID = "X-B3-TraceId";
    private static final String B3_SPAN_ID = "X-B3-SpanId";
    private static final String B3_PARENT_SPAN_ID = "X-B3-ParentSpanId";
    private static final String B3_SAMPLED = "X-B3-Sampled";
    
    /**
     * Intercept incoming HTTP requests and extract trace context
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String operationName = method + " " + uri;
        
        log.debug("Intercepting incoming request: {}", operationName);
        
        try {
            // Extract trace context from headers
            Map<String, String> headers = extractHeaders(request);
            
            // Extract correlation ID
            String correlationId = headers.get(CORRELATION_ID_HEADER);
            if (correlationId == null) {
                correlationId = headers.get("X-Request-Id");
            }
            
            // Start trace for incoming request
            DistributedTracingService.TraceContext traceContext = 
                tracingService.startTrace(operationName, correlationId);
            
            // Set request attributes in trace
            Map<String, String> tags = new HashMap<>();
            tags.put("http.method", method);
            tags.put("http.url", request.getRequestURL().toString());
            tags.put("http.path", uri);
            tags.put("http.remote_addr", request.getRemoteAddr());
            tags.put("http.user_agent", request.getHeader("User-Agent"));
            
            tracingService.addTags(tags);
            
            // Extract OpenTelemetry context if available
            if (openTelemetryService != null) {
                openTelemetryService.extractContext(headers);
                
                // Extract baggage from headers
                extractBaggage(headers);
            }
            
            // Store trace context in request attributes for later use
            request.setAttribute("traceContext", traceContext);
            request.setAttribute("traceStartTime", System.currentTimeMillis());
            
            // Add trace headers to response
            response.setHeader(TRACE_ID_HEADER, tracingService.getCurrentTraceId());
            response.setHeader(SPAN_ID_HEADER, tracingService.getCurrentSpanId());
            response.setHeader(CORRELATION_ID_HEADER, CorrelationContext.getCorrelationId());
            
            log.debug("Trace context established for request: {} with correlation ID: {}", 
                operationName, CorrelationContext.getCorrelationId());
            
        } catch (Exception e) {
            log.error("Error establishing trace context for request: {}", operationName, e);
            // Don't fail the request due to tracing issues
        }
        
        return true;
    }
    
    /**
     * Complete trace after request processing
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, 
                          Object handler, ModelAndView modelAndView) {
        // Add response status to trace
        tracingService.addTag("http.status_code", String.valueOf(response.getStatus()));
        
        if (response.getStatus() >= 400) {
            tracingService.addTag("error", "true");
        }
    }
    
    /**
     * Finish trace after request completion
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        try {
            // Get trace context from request
            DistributedTracingService.TraceContext traceContext = 
                (DistributedTracingService.TraceContext) request.getAttribute("traceContext");
            
            if (traceContext != null) {
                // Record error if exception occurred
                if (ex != null) {
                    tracingService.recordError(ex);
                    tracingService.addTag("error.type", ex.getClass().getSimpleName());
                    tracingService.addTag("error.message", ex.getMessage());
                }
                
                // Calculate request duration
                Long startTime = (Long) request.getAttribute("traceStartTime");
                if (startTime != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    tracingService.addTag("http.duration_ms", String.valueOf(duration));
                }
                
                // Finish trace
                tracingService.finishTrace(traceContext);
                
                log.debug("Completed trace for request: {} {}", 
                    request.getMethod(), request.getRequestURI());
            }
            
        } catch (Exception e) {
            log.error("Error completing trace for request", e);
        } finally {
            // Clear correlation context
            CorrelationContext.clear();
        }
    }
    
    /**
     * Intercept outgoing HTTP requests and inject trace context
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                      ClientHttpRequestExecution execution) throws IOException {
        String operationName = request.getMethod() + " " + request.getURI().getPath();
        
        log.debug("Intercepting outgoing request: {}", operationName);
        
        // Start child trace for outgoing request
        DistributedTracingService.TraceContext childTrace = 
            tracingService.startChildTrace("HTTP " + operationName);
        
        try {
            // Inject trace context into headers
            injectTraceHeaders(request);
            
            // Add request tags
            Map<String, String> tags = new HashMap<>();
            tags.put("http.method", request.getMethod().toString());
            tags.put("http.url", request.getURI().toString());
            tags.put("span.kind", "client");
            
            tracingService.addTags(tags);
            
            // Execute request
            ClientHttpResponse response = execution.execute(request, body);
            
            // Add response tags
            tracingService.addTag("http.status_code", String.valueOf(response.getStatusCode().value()));
            
            if (response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()) {
                tracingService.addTag("error", "true");
            }
            
            return response;
            
        } catch (Exception e) {
            // Record error in trace
            tracingService.recordError(e);
            throw e;
            
        } finally {
            // Finish child trace
            tracingService.finishTrace(childTrace);
        }
    }
    
    /**
     * Extract headers from incoming request
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        
        var headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        
        return headers;
    }
    
    /**
     * Extract baggage items from headers
     */
    private void extractBaggage(Map<String, String> headers) {
        headers.forEach((key, value) -> {
            if (key.startsWith(BAGGAGE_PREFIX)) {
                String baggageKey = key.substring(BAGGAGE_PREFIX.length());
                openTelemetryService.setBaggage(baggageKey, value);
            }
        });
    }
    
    /**
     * Inject trace headers into outgoing request
     */
    private void injectTraceHeaders(HttpRequest request) {
        // Standard headers
        String traceId = tracingService.getCurrentTraceId();
        String spanId = tracingService.getCurrentSpanId();
        String correlationId = CorrelationContext.getCorrelationId();
        
        if (traceId != null) {
            request.getHeaders().set(TRACE_ID_HEADER, traceId);
        }
        
        if (spanId != null) {
            request.getHeaders().set(SPAN_ID_HEADER, spanId);
            request.getHeaders().set(PARENT_SPAN_ID_HEADER, spanId);
        }
        
        if (correlationId != null) {
            request.getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        }
        
        // W3C Trace Context
        if (traceId != null && spanId != null) {
            String traceparent = String.format("00-%s-%s-01", traceId, spanId);
            request.getHeaders().set(W3C_TRACEPARENT, traceparent);
        }
        
        // B3 Propagation (Zipkin compatibility)
        if (traceId != null) {
            request.getHeaders().set(B3_TRACE_ID, traceId);
        }
        
        if (spanId != null) {
            request.getHeaders().set(B3_SPAN_ID, spanId);
            request.getHeaders().set(B3_PARENT_SPAN_ID, spanId);
        }
        
        request.getHeaders().set(B3_SAMPLED, "1");
        
        // OpenTelemetry context injection
        if (openTelemetryService != null) {
            Map<String, String> carrier = new HashMap<>();
            openTelemetryService.injectContext(carrier);
            carrier.forEach((key, value) -> request.getHeaders().set(key, value));
        }
        
        log.debug("Injected trace headers into outgoing request");
    }
}