package com.waqiti.common.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * AspectJ aspect to intercept @Idempotent annotated methods and provide
 * automatic idempotency handling using EnhancedIdempotencyService
 *
 * Features:
 * - SpEL expression parsing for dynamic key generation
 * - Automatic HTTP context extraction (IP, user agent)
 * - Request metadata capture (user ID, amount, currency)
 * - Complete integration with dual-layer idempotency service
 *
 * Usage:
 * <pre>
 * @Idempotent(
 *     keyExpression = "'payment:' + #request.userId + ':' + #request.transactionId",
 *     serviceName = "payment-service",
 *     operationType = "PROCESS_PAYMENT",
 *     userIdExpression = "#request.userId",
 *     amountExpression = "#request.amount",
 *     currencyExpression = "#request.currency"
 * )
 * public PaymentResponse processPayment(PaymentRequest request) {
 *     // Implementation
 * }
 * </pre>
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-01
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotentAspect {

    private final EnhancedIdempotencyService idempotencyService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * Intercept all methods annotated with @Idempotent
     */
    @Around("@annotation(idempotent)")
    public Object handleIdempotent(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {

        // Extract method signature and parameters
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        // Build SpEL evaluation context
        EvaluationContext context = buildEvaluationContext(method, args);

        try {
            // Parse all SpEL expressions
            String idempotencyKey = parseExpression(idempotent.keyExpression(), context);
            String userId = parseOptionalExpression(idempotent.userIdExpression(), context);
            String correlationId = parseOptionalExpression(idempotent.correlationIdExpression(), context);
            BigDecimal amount = parseAmountExpression(idempotent.amountExpression(), context);
            String currency = parseOptionalExpression(idempotent.currencyExpression(), context);

            // Extract HTTP context (IP address, user agent, session ID)
            HttpContext httpContext = extractHttpContext();

            // Build IdempotencyContext
            IdempotencyContext idempotencyContext = IdempotencyContext.builder()
                .idempotencyKey(idempotencyKey)
                .serviceName(idempotent.serviceName())
                .operationType(idempotent.operationType())
                .requestPayload(idempotent.captureRequestPayload() ? args : null)
                .ttl(Duration.ofHours(idempotent.ttlHours()))
                .userId(userId)
                .correlationId(correlationId)
                .sessionId(httpContext.sessionId)
                .clientIpAddress(httpContext.clientIp)
                .userAgent(httpContext.userAgent)
                .deviceFingerprint(httpContext.deviceFingerprint)
                .amount(amount)
                .currency(currency)
                .build();

            log.debug("IDEMPOTENCY: Processing {} with key: {}",
                     idempotent.operationType(), idempotencyKey);

            // Execute with idempotency guarantees
            return idempotencyService.executeIdempotent(
                idempotencyContext,
                () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException("Idempotent operation failed", e);
                    }
                }
            );

        } catch (Exception e) {
            log.error("IDEMPOTENCY ERROR: Failed to process @Idempotent for {}.{}: {}",
                     signature.getDeclaringType().getSimpleName(),
                     signature.getName(),
                     e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Build SpEL evaluation context with method parameters
     */
    private EvaluationContext buildEvaluationContext(Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // Add method metadata
        context.setVariable("method", method.getName());
        context.setVariable("className", method.getDeclaringClass().getSimpleName());
        context.setVariable("args", args);

        // Add parameter names and values
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String paramName = parameters[i].getName();
            context.setVariable(paramName, args[i]);
        }

        return context;
    }

    /**
     * Parse required SpEL expression
     */
    private String parseExpression(String expressionString, EvaluationContext context) {
        if (expressionString == null || expressionString.trim().isEmpty()) {
            throw new IllegalArgumentException("Required SpEL expression is empty");
        }

        try {
            Expression expression = expressionParser.parseExpression(expressionString);
            Object value = expression.getValue(context);

            if (value == null) {
                throw new IllegalArgumentException(
                    "SpEL expression returned null: " + expressionString);
            }

            return value.toString();

        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to parse SpEL expression: " + expressionString, e);
        }
    }

    /**
     * Parse optional SpEL expression (returns null if empty or fails)
     */
    private String parseOptionalExpression(String expressionString, EvaluationContext context) {
        if (expressionString == null || expressionString.trim().isEmpty()) {
            return null;
        }

        try {
            Expression expression = expressionParser.parseExpression(expressionString);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : null;

        } catch (Exception e) {
            log.warn("IDEMPOTENCY: Failed to parse optional expression '{}': {}",
                    expressionString, e.getMessage());
            return null;
        }
    }

    /**
     * Parse amount expression (handles BigDecimal, Double, Long, etc.)
     */
    private BigDecimal parseAmountExpression(String expressionString, EvaluationContext context) {
        if (expressionString == null || expressionString.trim().isEmpty()) {
            return null;
        }

        try {
            Expression expression = expressionParser.parseExpression(expressionString);
            Object value = expression.getValue(context);

            if (value == null) {
                return null;
            }

            // Handle different numeric types
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            } else if (value instanceof Double) {
                return BigDecimal.valueOf((Double) value);
            } else if (value instanceof Long) {
                return BigDecimal.valueOf((Long) value);
            } else if (value instanceof Integer) {
                return BigDecimal.valueOf((Integer) value);
            } else if (value instanceof String) {
                return new BigDecimal((String) value);
            } else {
                return new BigDecimal(value.toString());
            }

        } catch (Exception e) {
            log.warn("IDEMPOTENCY: Failed to parse amount expression '{}': {}",
                    expressionString, e.getMessage());
            return null;
        }
    }

    /**
     * Extract HTTP context from current request (if available)
     */
    private HttpContext extractHttpContext() {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                return HttpContext.empty();
            }

            HttpServletRequest request = attributes.getRequest();

            return new HttpContext(
                extractClientIp(request),
                request.getHeader("User-Agent"),
                Optional.ofNullable(request.getSession(false))
                    .map(session -> session.getId())
                    .orElse(null),
                extractDeviceFingerprint(request)
            );

        } catch (Exception e) {
            log.debug("IDEMPOTENCY: No HTTP context available (async/background operation)");
            return HttpContext.empty();
        }
    }

    /**
     * Extract client IP address (handles X-Forwarded-For, X-Real-IP)
     */
    private String extractClientIp(HttpServletRequest request) {
        // Check proxy headers first
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // Fallback to remote address
        return request.getRemoteAddr();
    }

    /**
     * Extract device fingerprint from custom header
     */
    private String extractDeviceFingerprint(HttpServletRequest request) {
        return request.getHeader("X-Device-Fingerprint");
    }

    /**
     * HTTP context holder
     */
    private static class HttpContext {
        final String clientIp;
        final String userAgent;
        final String sessionId;
        final String deviceFingerprint;

        HttpContext(String clientIp, String userAgent, String sessionId, String deviceFingerprint) {
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.sessionId = sessionId;
            this.deviceFingerprint = deviceFingerprint;
        }

        static HttpContext empty() {
            return new HttpContext(null, null, null, null);
        }
    }
}
