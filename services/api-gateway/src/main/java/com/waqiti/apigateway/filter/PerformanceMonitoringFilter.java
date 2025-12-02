package com.waqiti.apigateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Performance monitoring filter for API Gateway
 * Tracks request metrics, response times, and system performance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceMonitoringFilter implements GlobalFilter, Ordered {

    private final MeterRegistry meterRegistry;
    private static final String REQUEST_START_TIME = "requestStartTime";
    private static final String REQUEST_ID = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = UUID.randomUUID().toString();
        String path = request.getPath().value();
        String method = request.getMethod().name();
        
        // Add request tracking attributes
        exchange.getAttributes().put(REQUEST_START_TIME, Instant.now());
        exchange.getAttributes().put(REQUEST_ID, requestId);
        
        // Add request ID to response headers for tracking
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add("X-Request-ID", requestId);
        
        // Increment request counter
        meterRegistry.counter("api.requests.total",
            "method", method,
            "path", sanitizePath(path))
            .increment();

        // Track concurrent requests
        meterRegistry.gauge("api.requests.concurrent", 
            meterRegistry.counter("api.requests.concurrent.gauge").count() + 1);

        // Start timer for response time measurement
        Timer.Sample sample = Timer.start(meterRegistry);

        return chain.filter(exchange)
            .doOnSuccess(aVoid -> recordSuccess(exchange, sample, method, path))
            .doOnError(throwable -> recordError(exchange, sample, method, path, throwable))
            .doFinally(signalType -> {
                // Decrement concurrent requests
                meterRegistry.gauge("api.requests.concurrent", 
                    meterRegistry.counter("api.requests.concurrent.gauge").count() - 1);
                
                logRequestCompletion(exchange, method, path);
            });
    }

    private void recordSuccess(ServerWebExchange exchange, Timer.Sample sample, 
                              String method, String path) {
        ServerHttpResponse response = exchange.getResponse();
        int statusCode = response.getStatusCode() != null ? 
            response.getStatusCode().value() : 200;

        // Record response time
        sample.stop(Timer.builder("api.request.duration")
            .description("Request processing time")
            .tag("method", method)
            .tag("path", sanitizePath(path))
            .tag("status", String.valueOf(statusCode))
            .tag("outcome", statusCode >= 400 ? "error" : "success")
            .register(meterRegistry));

        // Record status code metrics
        meterRegistry.counter("api.responses.total",
            "method", method,
            "path", sanitizePath(path),
            "status", String.valueOf(statusCode))
            .increment();

        // Track slow requests (>1 second)
        Duration requestDuration = getRequestDuration(exchange);
        if (requestDuration.toMillis() > 1000) {
            meterRegistry.counter("api.requests.slow",
                "method", method,
                "path", sanitizePath(path))
                .increment();
                
            log.warn("Slow request detected: {} {} took {}ms", 
                method, path, requestDuration.toMillis());
        }

        // Track SLA compliance (95th percentile < 500ms)
        boolean meetsSla = requestDuration.toMillis() < 500;
        meterRegistry.counter("api.sla.compliance",
            "method", method,
            "path", sanitizePath(path),
            "meets_sla", String.valueOf(meetsSla))
            .increment();
    }

    private void recordError(ServerWebExchange exchange, Timer.Sample sample, 
                            String method, String path, Throwable throwable) {
        // Record error response time
        sample.stop(Timer.builder("api.request.duration")
            .description("Request processing time")
            .tag("method", method)
            .tag("path", sanitizePath(path))
            .tag("status", "error")
            .tag("outcome", "error")
            .register(meterRegistry));

        // Record error metrics
        meterRegistry.counter("api.errors.total",
            "method", method,
            "path", sanitizePath(path),
            "error_type", throwable.getClass().getSimpleName())
            .increment();

        // Log error with request context
        String requestId = exchange.getAttribute(REQUEST_ID);
        log.error("Request error: {} {} [{}] - {}", 
            method, path, requestId, throwable.getMessage(), throwable);
    }

    private Duration getRequestDuration(ServerWebExchange exchange) {
        Instant startTime = exchange.getAttribute(REQUEST_START_TIME);
        return startTime != null ? Duration.between(startTime, Instant.now()) : Duration.ZERO;
    }

    private void logRequestCompletion(ServerWebExchange exchange, String method, String path) {
        Duration duration = getRequestDuration(exchange);
        String requestId = exchange.getAttribute(REQUEST_ID);
        ServerHttpResponse response = exchange.getResponse();
        int statusCode = response.getStatusCode() != null ? 
            response.getStatusCode().value() : 0;

        if (log.isDebugEnabled()) {
            log.debug("Request completed: {} {} [{}] - Status: {} - Duration: {}ms",
                method, path, requestId, statusCode, duration.toMillis());
        }

        // Log high-latency requests at INFO level
        if (duration.toMillis() > 2000) {
            log.info("High latency request: {} {} [{}] - Duration: {}ms",
                method, path, requestId, duration.toMillis());
        }
    }

    /**
     * Sanitize path for metrics to avoid high cardinality
     */
    private String sanitizePath(String path) {
        // Replace UUIDs and numeric IDs with placeholders
        return path
            .replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/{uuid}")
            .replaceAll("/\\d+", "/{id}")
            .replaceAll("/[a-zA-Z0-9]{20,}", "/{token}");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1; // Execute after authentication filter
    }
}