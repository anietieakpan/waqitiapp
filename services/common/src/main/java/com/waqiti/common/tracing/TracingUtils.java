package com.waqiti.common.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility class for distributed tracing operations
 * Provides convenient methods for creating and managing spans throughout the application
 */
@Slf4j
@UtilityClass
public class TracingUtils {

    // Standard attribute keys for Waqiti services
    public static final AttributeKey<String> USER_ID = AttributeKey.stringKey("waqiti.user.id");
    public static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("waqiti.tenant.id");
    public static final AttributeKey<String> REQUEST_ID = AttributeKey.stringKey("waqiti.request.id");
    public static final AttributeKey<String> TRANSACTION_ID = AttributeKey.stringKey("waqiti.transaction.id");
    public static final AttributeKey<String> PAYMENT_METHOD = AttributeKey.stringKey("waqiti.payment.method");
    public static final AttributeKey<String> CURRENCY = AttributeKey.stringKey("waqiti.currency");
    public static final AttributeKey<Double> AMOUNT = AttributeKey.doubleKey("waqiti.amount");
    public static final AttributeKey<String> FRAUD_SCORE = AttributeKey.stringKey("waqiti.fraud.score");
    public static final AttributeKey<Boolean> FRAUD_DETECTED = AttributeKey.booleanKey("waqiti.fraud.detected");
    public static final AttributeKey<String> KYC_LEVEL = AttributeKey.stringKey("waqiti.kyc.level");
    public static final AttributeKey<Boolean> KYC_VERIFIED = AttributeKey.booleanKey("waqiti.kyc.verified");
    public static final AttributeKey<String> SERVICE_OPERATION = AttributeKey.stringKey("waqiti.service.operation");
    public static final AttributeKey<String> DATABASE_OPERATION = AttributeKey.stringKey("waqiti.db.operation");
    public static final AttributeKey<String> CACHE_OPERATION = AttributeKey.stringKey("waqiti.cache.operation");
    public static final AttributeKey<Boolean> CACHE_HIT = AttributeKey.booleanKey("waqiti.cache.hit");

    /**
     * Execute a callable with tracing
     */
    public static <T> T traced(Tracer tracer, String operationName, Callable<T> callable) {
        return traced(tracer, operationName, SpanKind.INTERNAL, callable);
    }

    /**
     * Execute a callable with tracing and specific span kind
     */
    public static <T> T traced(Tracer tracer, String operationName, SpanKind spanKind, Callable<T> callable) {
        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(spanKind)
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = callable.call();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    /**
     * Execute a callable with tracing and custom attributes
     */
    public static <T> T traced(Tracer tracer, String operationName, Attributes attributes, Callable<T> callable) {
        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(attributes)
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = callable.call();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    /**
     * Execute a supplier with tracing
     */
    public static <T> T tracedSupplier(Tracer tracer, String operationName, Supplier<T> supplier) {
        Span span = tracer.spanBuilder(operationName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            T result = supplier.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Execute a runnable with tracing
     */
    public static void tracedRunnable(Tracer tracer, String operationName, Runnable runnable) {
        Span span = tracer.spanBuilder(operationName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            runnable.run();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Create a span for fraud detection operations
     */
    public static Span createFraudDetectionSpan(Tracer tracer, String userId, String operation) {
        return tracer.spanBuilder("fraud.detection." + operation)
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(Attributes.builder()
                .put(USER_ID, userId)
                .put(SERVICE_OPERATION, "fraud_detection")
                .build())
            .startSpan();
    }

    /**
     * Create a span for payment processing operations
     */
    public static Span createPaymentProcessingSpan(Tracer tracer, String transactionId, 
                                                   String paymentMethod, double amount, String currency) {
        return tracer.spanBuilder("payment.processing")
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(Attributes.builder()
                .put(TRANSACTION_ID, transactionId)
                .put(PAYMENT_METHOD, paymentMethod)
                .put(AMOUNT, amount)
                .put(CURRENCY, currency)
                .put(SERVICE_OPERATION, "payment_processing")
                .build())
            .startSpan();
    }

    /**
     * Create a span for KYC verification operations
     */
    public static Span createKycVerificationSpan(Tracer tracer, String userId, String kycLevel) {
        return tracer.spanBuilder("kyc.verification")
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(Attributes.builder()
                .put(USER_ID, userId)
                .put(KYC_LEVEL, kycLevel)
                .put(SERVICE_OPERATION, "kyc_verification")
                .build())
            .startSpan();
    }

    /**
     * Create a span for database operations
     */
    public static Span createDatabaseSpan(Tracer tracer, String operation, String table) {
        return tracer.spanBuilder("db." + operation)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(Attributes.builder()
                .put(DATABASE_OPERATION, operation)
                .put("db.table", table)
                .put("db.type", "sql")
                .build())
            .startSpan();
    }

    /**
     * Create a span for cache operations
     */
    public static Span createCacheSpan(Tracer tracer, String operation, String key, boolean hit) {
        return tracer.spanBuilder("cache." + operation)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(Attributes.builder()
                .put(CACHE_OPERATION, operation)
                .put("cache.key", key)
                .put(CACHE_HIT, hit)
                .build())
            .startSpan();
    }

    /**
     * Create a span for external service calls
     */
    public static Span createExternalServiceSpan(Tracer tracer, String serviceName, String operation) {
        return tracer.spanBuilder("external." + serviceName + "." + operation)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(Attributes.builder()
                .put("service.name", serviceName)
                .put("service.operation", operation)
                .build())
            .startSpan();
    }

    /**
     * Add user context to current span
     */
    public static void addUserContext(String userId, String tenantId) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            currentSpan.setAllAttributes(Attributes.builder()
                .put(USER_ID, userId != null ? userId : "unknown")
                .put(TENANT_ID, tenantId != null ? tenantId : "default")
                .build());
        }
    }

    /**
     * Add request context to current span
     */
    public static void addRequestContext(String requestId, String correlationId) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            currentSpan.setAllAttributes(Attributes.builder()
                .put(REQUEST_ID, requestId != null ? requestId : "unknown")
                .put("correlation.id", correlationId != null ? correlationId : "unknown")
                .build());
        }
    }

    /**
     * Add fraud detection results to current span
     */
    public static void addFraudDetectionResults(double riskScore, boolean fraudDetected, String riskLevel) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            currentSpan.setAllAttributes(Attributes.builder()
                .put(FRAUD_SCORE, String.valueOf(riskScore))
                .put(FRAUD_DETECTED, fraudDetected)
                .put("fraud.risk.level", riskLevel != null ? riskLevel : "unknown")
                .build());
        }
    }

    /**
     * Add KYC verification results to current span
     */
    public static void addKycVerificationResults(boolean verified, String level, String reason) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            currentSpan.setAllAttributes(Attributes.builder()
                .put(KYC_VERIFIED, verified)
                .put(KYC_LEVEL, level != null ? level : "unknown")
                .put("kyc.verification.reason", reason != null ? reason : "unknown")
                .build());
        }
    }

    /**
     * Add payment processing results to current span
     */
    public static void addPaymentResults(String status, String authorizationCode, String errorCode) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            var attributesBuilder = Attributes.builder()
                .put("payment.status", status != null ? status : "unknown");
            
            if (authorizationCode != null) {
                attributesBuilder.put("payment.authorization.code", authorizationCode);
            }
            
            if (errorCode != null) {
                attributesBuilder.put("payment.error.code", errorCode);
            }
            
            currentSpan.setAllAttributes(attributesBuilder.build());
        }
    }

    /**
     * Add performance metrics to current span
     */
    public static void addPerformanceMetrics(long durationMs, int recordCount) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            currentSpan.setAllAttributes(Attributes.builder()
                .put("performance.duration.ms", String.valueOf(durationMs))
                .put("performance.record.count", String.valueOf(recordCount))
                .build());
        }
    }

    /**
     * Add error information to current span
     */
    public static void addError(Throwable throwable) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            currentSpan.setStatus(StatusCode.ERROR, throwable.getMessage());
            currentSpan.recordException(throwable);
        }
    }

    /**
     * Add error information with custom message
     */
    public static void addError(String errorMessage, String errorCode) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            currentSpan.setStatus(StatusCode.ERROR, errorMessage);
            currentSpan.setAllAttributes(Attributes.builder()
                .put("error.message", errorMessage)
                .put("error.code", errorCode != null ? errorCode : "unknown")
                .build());
        }
    }

    /**
     * Add business event to current span
     */
    public static void addBusinessEvent(String eventType, String eventDescription, Map<String, String> eventData) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            var attributesBuilder = Attributes.builder()
                .put("business.event.type", eventType)
                .put("business.event.description", eventDescription);
            
            if (eventData != null) {
                eventData.forEach((key, value) -> {
                    if (key != null && value != null) {
                        attributesBuilder.put("business.event." + key, value);
                    }
                });
            }
            
            currentSpan.setAllAttributes(attributesBuilder.build());
        }
    }

    /**
     * Create a child span from current context
     */
    public static Span createChildSpan(Tracer tracer, String operationName) {
        return tracer.spanBuilder(operationName)
            .setParent(Context.current())
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
    }

    /**
     * Create a child span with custom attributes
     */
    public static Span createChildSpan(Tracer tracer, String operationName, Attributes attributes) {
        return tracer.spanBuilder(operationName)
            .setParent(Context.current())
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(attributes)
            .startSpan();
    }

    /**
     * Execute with manual span management
     */
    public static <T> T withSpan(Span span, Callable<T> callable) {
        try (Scope scope = span.makeCurrent()) {
            T result = callable.call();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    /**
     * Execute function with span management
     */
    public static <T, R> R withSpan(Span span, Function<T, R> function, T input) {
        try (Scope scope = span.makeCurrent()) {
            R result = function.apply(input);
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }
}