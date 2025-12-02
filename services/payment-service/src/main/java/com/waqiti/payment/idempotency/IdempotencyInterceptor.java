package com.waqiti.payment.idempotency;

import com.waqiti.common.correlation.CorrelationIdService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Interceptor to handle idempotency for payment operations
 * Automatically manages idempotency keys for annotated endpoints
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    private final IdempotencyKeyService idempotencyService;
    private final CorrelationIdService correlationIdService;
    
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_KEY_ATTRIBUTE = "idempotencyKey";
    
    // Payment endpoints that require idempotency
    private static final List<String> IDEMPOTENT_PATHS = Arrays.asList(
        "/api/v1/payments",
        "/api/v1/payments/transfer",
        "/api/v1/payments/withdraw",
        "/api/v1/payments/deposit",
        "/api/v1/payments/refund",
        "/api/v1/wallets/transfer",
        "/api/v1/wallets/topup",
        "/api/v1/transactions",
        "/api/v1/crypto/transfer",
        "/api/v1/international/transfer"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                            Object handler) throws Exception {
        
        // Only process if it's a controller method
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        
        // Check if method or class has @Idempotent annotation
        Idempotent idempotentAnnotation = method.getAnnotation(Idempotent.class);
        if (idempotentAnnotation == null) {
            idempotentAnnotation = handlerMethod.getBeanType().getAnnotation(Idempotent.class);
        }
        
        // Check if path requires idempotency
        boolean requiresIdempotency = idempotentAnnotation != null || 
                                      isIdempotentPath(request.getRequestURI());
        
        if (!requiresIdempotency) {
            return true;
        }
        
        // Only apply to POST, PUT, PATCH methods
        String httpMethod = request.getMethod();
        if (!"POST".equals(httpMethod) && !"PUT".equals(httpMethod) && !"PATCH".equals(httpMethod)) {
            return true;
        }
        
        /**
         * P0-019 CRITICAL FIX: Enforce idempotency key requirement
         *
         * BEFORE: Auto-generated keys if missing - allowed duplicate payments ❌
         * AFTER: REQUIRED idempotency key for ALL payment endpoints ✅
         */
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            // CRITICAL: Always require idempotency key for financial operations
            log.error("❌ MISSING IDEMPOTENCY KEY - rejecting payment request: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Idempotency-Key header is required for all payment operations\"," +
                "\"code\":\"MISSING_IDEMPOTENCY_KEY\"," +
                "\"documentation\":\"https://docs.example.com/api/idempotency\"}"
            );
            return false;
        }
        
        // Validate idempotency key format
        if (!idempotencyService.isValidKey(idempotencyKey)) {
            log.warn("Invalid idempotency key format: {}", idempotencyKey);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid Idempotency-Key format\",\"code\":\"INVALID_KEY_FORMAT\"}");
            return false;
        }

        /**
         * P0-019 CRITICAL FIX: Check for duplicate requests using Redis
         *
         * BEFORE: No duplicate detection - payments could be processed twice ❌
         * AFTER: Redis-based duplicate detection with cached responses ✅
         */
        // Check if this idempotency key was already processed
        IdempotencyKeyService.IdempotencyRecord existingRecord =
            idempotencyService.getExistingRecord(idempotencyKey);

        if (existingRecord != null) {
            // Duplicate request detected
            log.warn("⚠️ DUPLICATE REQUEST DETECTED - idempotency key: {}, original timestamp: {}",
                idempotencyKey, existingRecord.getCreatedAt());

            // Return cached response from original request
            response.setStatus(existingRecord.getStatusCode());
            response.setContentType("application/json");
            response.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
            response.setHeader("X-Idempotency-Replay", "true");
            response.setHeader("X-Original-Request-Time", existingRecord.getCreatedAt().toString());

            if (existingRecord.getResponseBody() != null) {
                response.getWriter().write(existingRecord.getResponseBody());
            }

            // Increment duplicate detection metric
            log.info("✅ Prevented duplicate payment - idempotency key: {}", idempotencyKey);
            return false; // Stop processing - return cached response
        }

        // Store idempotency key for processing
        request.setAttribute(IDEMPOTENCY_KEY_ATTRIBUTE, idempotencyKey);

        // Add idempotency key to response header
        response.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);

        log.debug("Processing NEW request with idempotency key: {} for endpoint: {}",
                idempotencyKey, request.getRequestURI());

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, 
                          Object handler, ModelAndView modelAndView) throws Exception {
        // Log successful processing
        String idempotencyKey = (String) request.getAttribute(IDEMPOTENCY_KEY_ATTRIBUTE);
        if (idempotencyKey != null) {
            log.debug("Successfully processed request with idempotency key: {}", idempotencyKey);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) throws Exception {
        // Clean up if needed
        if (ex != null) {
            String idempotencyKey = (String) request.getAttribute(IDEMPOTENCY_KEY_ATTRIBUTE);
            if (idempotencyKey != null) {
                log.error("Request failed with idempotency key: {}", idempotencyKey, ex);
                // Could mark the idempotency record as failed here
            }
        }
    }

    /**
     * Check if the request path requires idempotency
     */
    private boolean isIdempotentPath(String path) {
        return IDEMPOTENT_PATHS.stream()
                .anyMatch(path::startsWith);
    }

    /**
     * Generate idempotency key based on request attributes
     */
    private String generateIdempotencyKey(HttpServletRequest request) {
        String correlationId = correlationIdService.getCorrelationId().orElse("");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String method = request.getMethod();
        String path = request.getRequestURI();
        
        // Create a composite key
        String composite = String.format("%s-%s-%s-%s", 
                correlationId, method, path.replace("/", "-"), timestamp);
        
        return idempotencyService.generateKey(composite);
    }
}