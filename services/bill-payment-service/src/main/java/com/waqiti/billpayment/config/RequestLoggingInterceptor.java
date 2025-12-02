package com.waqiti.billpayment.config;

import com.waqiti.billpayment.util.DataMaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

/**
 * Interceptor for logging HTTP requests and responses with PII masking
 * Adds correlation IDs for distributed tracing
 */
@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String TRACE_ID = "traceId";
    private static final String START_TIME = "startTime";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // Generate or extract correlation ID
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Add to MDC for logging
        MDC.put(TRACE_ID, correlationId);
        MDC.put(START_TIME, String.valueOf(System.currentTimeMillis()));

        // Add to response headers
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        // Log request (with masked sensitive data)
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String queryString = request.getQueryString();
        String maskedQuery = queryString != null ? DataMaskingUtil.maskAll(queryString) : "";

        log.info("Incoming request: {} {} {} - TraceID: {}",
                method,
                uri,
                maskedQuery.isEmpty() ? "" : "?" + maskedQuery,
                correlationId);

        return true;
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request,
                           @NonNull HttpServletResponse response,
                           @NonNull Object handler,
                           ModelAndView modelAndView) {
        // Optional: Add custom headers or perform post-processing
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        String startTimeStr = MDC.get(START_TIME);
        String correlationId = MDC.get(TRACE_ID);

        if (startTimeStr != null) {
            long startTime = Long.parseLong(startTimeStr);
            long duration = System.currentTimeMillis() - startTime;

            String uri = request.getRequestURI();
            String method = request.getMethod();
            int status = response.getStatus();

            // Log response with duration
            if (status >= 500) {
                log.error("Request completed: {} {} - Status: {} - Duration: {}ms - TraceID: {}",
                        method, uri, status, duration, correlationId);
            } else if (status >= 400) {
                log.warn("Request completed: {} {} - Status: {} - Duration: {}ms - TraceID: {}",
                        method, uri, status, duration, correlationId);
            } else {
                log.info("Request completed: {} {} - Status: {} - Duration: {}ms - TraceID: {}",
                        method, uri, status, duration, correlationId);
            }

            // Alert if request takes too long
            if (duration > 5000) {
                log.warn("SLOW REQUEST DETECTED: {} {} took {}ms - TraceID: {}",
                        method, uri, duration, correlationId);
            }
        }

        // Clear MDC to prevent memory leaks
        MDC.clear();
    }
}
