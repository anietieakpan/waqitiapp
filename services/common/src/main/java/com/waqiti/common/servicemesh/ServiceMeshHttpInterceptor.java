package com.waqiti.common.servicemesh;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP Interceptor for Service Mesh
 * Adds service mesh capabilities to HTTP requests
 */
@Slf4j
@RequiredArgsConstructor
public class ServiceMeshHttpInterceptor implements ClientHttpRequestInterceptor {

    private final TrafficManager trafficManager;
    private final SecurityManager securityManager;
    private final ObservabilityManager observabilityManager;
    private final ServiceMeshProperties properties;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                       ClientHttpRequestExecution execution) throws IOException {
        
        String serviceName = extractServiceName(request);
        String traceId = getOrCreateTraceId(request);
        String spanId = UUID.randomUUID().toString();
        
        // Start tracing
        ObservabilityManager.TraceSpan span = null;
        if (properties.getObservability().isTracingEnabled()) {
            span = observabilityManager.startSpan(ObservabilityManager.SpanRequest.builder()
                    .traceId(traceId)
                    .serviceName(serviceName)
                    .operationName(request.getMethod() + " " + request.getURI().getPath())
                    .spanKind(ObservabilityManager.SpanRequest.SpanKind.CLIENT)
                    .attributes(Map.of(
                            "http.method", request.getMethod().toString(),
                            "http.url", request.getURI().toString()
                    ))
                    .build());
        }
        
        // Add service mesh headers
        addServiceMeshHeaders(request, traceId, spanId);
        
        // Security checks
        if (properties.getSecurity().isMtlsEnabled() || properties.getSecurity().isAuthorizationEnabled()) {
            performSecurityChecks(request, serviceName);
        }
        
        long startTime = System.currentTimeMillis();
        ClientHttpResponse response = null;
        Exception error = null;
        
        try {
            // Route request through traffic manager
            TrafficManager.ServiceInstance instance = routeRequest(serviceName, request);
            
            // Execute with resilience patterns
            response = trafficManager.executeWithResilience(serviceName, 
                    () -> {
                        try {
                            return execution.execute(request, body);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            
            return response;
            
        } catch (Exception e) {
            error = e;
            log.error("Service mesh request failed for {}: {}", serviceName, e.getMessage());
            
            // Return error response
            return createErrorResponse(e);
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // End tracing
            if (span != null) {
                observabilityManager.endSpan(spanId, ObservabilityManager.SpanStatus.builder()
                        .error(error != null)
                        .errorMessage(error != null ? error.getMessage() : null)
                        .build());
            }
            
            // Log access
            if (properties.getObservability().isAccessLogEnabled()) {
                int statusCode = response != null ? response.getStatusCode().value() : 500;
                
                observabilityManager.logAccess(ObservabilityManager.AccessLogRequest.builder()
                        .serviceName(serviceName)
                        .method(request.getMethod().toString())
                        .path(request.getURI().getPath())
                        .statusCode(statusCode)
                        .responseTime(duration)
                        .traceId(traceId)
                        .build());
            }
            
            // Record metrics
            recordMetrics(serviceName, request, response, duration, error);
        }
    }

    private String extractServiceName(HttpRequest request) {
        String host = request.getURI().getHost();
        
        // Extract service name from host (e.g., payment-service.waqiti.svc.cluster.local)
        if (host != null && host.contains(".")) {
            return host.substring(0, host.indexOf("."));
        }
        
        // Fallback to path-based extraction
        String path = request.getURI().getPath();
        if (path != null && path.startsWith("/")) {
            String[] segments = path.split("/");
            if (segments.length > 1) {
                return segments[1];
            }
        }
        
        return "unknown-service";
    }

    private String getOrCreateTraceId(HttpRequest request) {
        // Check for existing trace ID in headers
        for (String header : properties.getObservability().getPropagationHeaders()) {
            String traceId = request.getHeaders().getFirst(header);
            if (traceId != null) {
                return traceId;
            }
        }
        
        // Generate new trace ID
        return UUID.randomUUID().toString();
    }

    private void addServiceMeshHeaders(HttpRequest request, String traceId, String spanId) {
        HttpHeaders headers = request.getHeaders();
        
        // Add tracing headers
        headers.add("x-trace-id", traceId);
        headers.add("x-span-id", spanId);
        headers.add("x-parent-span-id", UUID.randomUUID().toString());
        
        // Add service mesh headers
        headers.add("x-service-mesh", "waqiti");
        headers.add("x-service-name", properties.getServiceName());
        headers.add("x-service-version", properties.getVersion());
        
        // Add request timestamp
        headers.add("x-request-timestamp", String.valueOf(Instant.now().toEpochMilli()));
    }

    private void performSecurityChecks(HttpRequest request, String serviceName) {
        // Authenticate request
        SecurityManager.AuthenticationResult authResult = securityManager.authenticate(
                SecurityManager.AuthenticationRequest.builder()
                        .sourceService(properties.getServiceName())
                        .headers(convertHeaders(request.getHeaders()))
                        .build()
        );
        
        if (!authResult.isAuthenticated()) {
            throw new SecurityException("Authentication failed: " + authResult.getReason());
        }
        
        // Authorize request
        SecurityManager.AuthorizationResult authzResult = securityManager.authorize(
                SecurityManager.AuthorizationRequest.builder()
                        .sourceService(properties.getServiceName())
                        .targetService(serviceName)
                        .method(request.getMethod().toString())
                        .path(request.getURI().getPath())
                        .headers(convertHeaders(request.getHeaders()))
                        .build()
        );
        
        if (!authzResult.isAuthorized()) {
            throw new SecurityException("Authorization failed: " + authzResult.getReason());
        }
        
        // Add any additional headers from authorization
        if (authzResult.getAdditionalHeaders() != null) {
            authzResult.getAdditionalHeaders().forEach(request.getHeaders()::add);
        }
    }

    private TrafficManager.ServiceInstance routeRequest(String serviceName, HttpRequest request) {
        TrafficManager.RequestContext context = TrafficManager.RequestContext.builder()
                .sessionId(request.getHeaders().getFirst("x-session-id"))
                .headers(convertHeaders(request.getHeaders()))
                .clientId(properties.getServiceName())
                .build();
        
        return trafficManager.route(serviceName, context);
    }

    private void recordMetrics(String serviceName, HttpRequest request, ClientHttpResponse response, 
                               long duration, Exception error) {
        
        String status = error != null ? "error" : "success";
        int statusCode = 500;
        
        if (response != null) {
            try {
                statusCode = response.getStatusCode().value();
                status = statusCode >= 400 ? "error" : "success";
            } catch (IOException e) {
                log.warn("Failed to get response status code", e);
            }
        }
        
        // Record request metrics
        observabilityManager.recordMetric(ObservabilityManager.MetricRequest.builder()
                .name("service.mesh.http.requests")
                .type(ObservabilityManager.MetricRequest.MetricType.COUNTER)
                .value(1)
                .tags(io.micrometer.core.instrument.Tags.of(
                        "service", serviceName,
                        "method", request.getMethod().toString(),
                        "status", status,
                        "status_code", String.valueOf(statusCode)
                ))
                .build());
        
        // Record latency metrics
        observabilityManager.recordMetric(ObservabilityManager.MetricRequest.builder()
                .name("service.mesh.http.latency")
                .type(ObservabilityManager.MetricRequest.MetricType.TIMER)
                .value(duration)
                .tags(io.micrometer.core.instrument.Tags.of(
                        "service", serviceName,
                        "method", request.getMethod().toString()
                ))
                .build());
    }

    private Map<String, String> convertHeaders(HttpHeaders headers) {
        Map<String, String> headerMap = new HashMap<>();
        headers.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headerMap.put(key, values.get(0));
            }
        });
        return headerMap;
    }

    private ClientHttpResponse createErrorResponse(Exception e) {
        return new ClientHttpResponse() {
            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }

            @Override
            public String getStatusText() {
                return "Service Unavailable";
            }

            @Override
            public void close() {
            }

            @Override
            public InputStream getBody() {
                String errorBody = String.format(
                        "{\"error\": \"Service mesh error\", \"message\": \"%s\"}", 
                        e.getMessage()
                );
                return new ByteArrayInputStream(errorBody.getBytes());
            }

            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Content-Type", "application/json");
                headers.add("X-Service-Mesh-Error", "true");
                return headers;
            }
        };
    }
}