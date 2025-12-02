package com.waqiti.common.tracing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP interceptor that automatically manages correlation IDs and tracing context
 * for incoming requests. Integrates with the distributed tracing infrastructure.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Component
public class TracingInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(TracingInterceptor.class);
    
    private final DistributedTracingService tracingService;
    
    public TracingInterceptor(DistributedTracingService tracingService) {
        this.tracingService = tracingService;
    }
    
    private static final String TRACE_CONTEXT_ATTRIBUTE = "traceContext";
    
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, 
                           @NonNull HttpServletResponse response, 
                           @NonNull Object handler) throws Exception {
        
        try {
            // Extract correlation ID from headers
            String correlationId = extractCorrelationId(request);
            String traceId = request.getHeader(CorrelationId.TRACE_ID_HEADER);
            String spanId = request.getHeader(CorrelationId.SPAN_ID_HEADER);
            String userId = extractUserId(request);
            
            // Set context information
            CorrelationContext.setTracingInfo(correlationId, traceId, spanId);
            CorrelationContext.setUserId(userId);
            CorrelationContext.setRequestInfo(request.getMethod(), request.getRequestURI());
            
            // Start trace
            String operationName = buildOperationName(request);
            DistributedTracingService.TraceContext traceContext = 
                tracingService.startTrace(operationName, correlationId);
            
            // Store trace context for use in postHandle
            request.setAttribute(TRACE_CONTEXT_ATTRIBUTE, traceContext);
            
            // Add tracing headers to response
            response.setHeader(CorrelationId.CORRELATION_ID_HEADER, traceContext.getCorrelationId());
            if (tracingService.getCurrentTraceId() != null) {
                response.setHeader(CorrelationId.TRACE_ID_HEADER, tracingService.getCurrentTraceId());
            }
            if (tracingService.getCurrentSpanId() != null) {
                response.setHeader(CorrelationId.SPAN_ID_HEADER, tracingService.getCurrentSpanId());
            }
            
            // Add request metadata as tags
            tracingService.addTag("http.method", request.getMethod());
            tracingService.addTag("http.url", request.getRequestURL().toString());
            tracingService.addTag("http.user_agent", request.getHeader("User-Agent"));
            tracingService.addTag("http.remote_addr", getClientIpAddress(request));
            
            if (userId != null) {
                tracingService.addTag("user.id", userId);
            }
            
            logger.debug("Started tracing for request: {} {} with correlation ID: {}",
                    request.getMethod(), request.getRequestURI(), 
                    CorrelationId.toShortForm(traceContext.getCorrelationId()));
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error in tracing interceptor preHandle", e);
            // Don't block the request if tracing fails
            return true;
        }
    }
    
    @Override
    public void postHandle(@NonNull HttpServletRequest request, 
                         @NonNull HttpServletResponse response,
                         @NonNull Object handler, 
                         org.springframework.web.servlet.ModelAndView modelAndView) throws Exception {
        
        try {
            // Add response status to trace
            tracingService.addTag("http.status_code", String.valueOf(response.getStatus()));
            
            // Mark as error if status code indicates error
            if (response.getStatus() >= 400) {
                tracingService.addTag("error", "true");
                tracingService.addTag("http.error_status", String.valueOf(response.getStatus()));
                
                tracingService.recordEvent("http.error", java.util.Map.of(
                    "status_code", String.valueOf(response.getStatus()),
                    "method", request.getMethod(),
                    "path", request.getRequestURI()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error in tracing interceptor postHandle", e);
        }
    }
    
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, 
                              @NonNull HttpServletResponse response,
                              @NonNull Object handler, 
                              Exception ex) throws Exception {
        
        try {
            // Record exception if present
            if (ex != null) {
                tracingService.recordError(ex);
            }
            
            // Finish the trace
            DistributedTracingService.TraceContext traceContext = 
                (DistributedTracingService.TraceContext) request.getAttribute(TRACE_CONTEXT_ATTRIBUTE);
            
            if (traceContext != null) {
                tracingService.finishTrace(traceContext);
                
                logger.debug("Finished tracing for request: {} {} with correlation ID: {}",
                        request.getMethod(), request.getRequestURI(),
                        CorrelationId.toShortForm(traceContext.getCorrelationId()));
            }
            
        } catch (Exception e) {
            logger.error("Error in tracing interceptor afterCompletion", e);
        } finally {
            // Always clear context to prevent memory leaks
            CorrelationContext.clear();
        }
    }
    
    private String extractCorrelationId(HttpServletRequest request) {
        // Try different header names for correlation ID
        String correlationId = request.getHeader(CorrelationId.CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = request.getHeader("X-Request-ID");
        }
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = request.getHeader("X-Trace-Id");
        }
        
        // Validate and return, will generate new one if invalid
        return CorrelationId.isValid(correlationId) ? correlationId : null;
    }
    
    private String extractUserId(HttpServletRequest request) {
        // Try to extract user ID from various sources
        String userId = request.getHeader("X-User-ID");
        
        if (userId == null || userId.trim().isEmpty()) {
            // Try to get from JWT token or security context if available
            try {
                // This would be implemented based on your authentication mechanism
                // For now, just check for a basic user header
                userId = request.getHeader("X-User-Id");
            } catch (Exception e) {
                // Ignore, user ID is optional
            }
        }
        
        return userId != null && !userId.trim().isEmpty() ? userId : null;
    }
    
    private String buildOperationName(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        
        // Simplify path by removing IDs for better grouping
        String simplifiedPath = path.replaceAll("/\\d+", "/{id}")
                                   .replaceAll("/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", "/{uuid}")
                                   .replaceAll("/wqt-[a-f0-9]{32}", "/{correlationId}");
        
        return method + " " + simplifiedPath;
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        // Check for IP address from various headers (reverse proxy, load balancer, etc.)
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
            String ipAddress = request.getHeader(header);
            if (ipAddress != null && !ipAddress.trim().isEmpty() && !"unknown".equalsIgnoreCase(ipAddress)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                if (ipAddress.contains(",")) {
                    ipAddress = ipAddress.split(",")[0].trim();
                }
                return ipAddress;
            }
        }
        
        return request.getRemoteAddr();
    }
}