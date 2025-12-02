package com.waqiti.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

/**
 * Annotation to mark methods for automatic idempotency handling
 *
 * Usage:
 * <pre>
 * @Idempotent(
 *     keyExpression = "'payment:' + #request.userId + ':' + #request.transactionId",
 *     serviceName = "payment-service",
 *     operationType = "PROCESS_PAYMENT"
 * )
 * public PaymentResponse processPayment(PaymentRequest request) {
 *     // Implementation
 * }
 * </pre>
 *
 * Features:
 * - SpEL expression support for dynamic key generation
 * - Automatic duplicate detection
 * - Cached result return for duplicates
 * - Full audit trail
 * - Configurable TTL
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-01
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * SpEL expression for generating idempotency key
     *
     * Available variables:
     * - #method - Method name
     * - #className - Class name
     * - #args - Method arguments array
     * - Parameter names (e.g., #request, #userId)
     *
     * Examples:
     * - "'payment:' + #request.getUserId() + ':' + #request.getTransactionId()"
     * - "'wallet:transfer:' + #userId + ':' + #transferId"
     * - "'order:' + #orderId"
     */
    String keyExpression();

    /**
     * Service name for classification
     * Example: "payment-service", "wallet-service"
     */
    String serviceName();

    /**
     * Operation type for classification
     * Example: "PROCESS_PAYMENT", "CREATE_WALLET", "TRANSFER_FUNDS"
     */
    String operationType();

    /**
     * SpEL expression for user ID (optional)
     * Example: "#request.getUserId()", "#userId"
     */
    String userIdExpression() default "";

    /**
     * SpEL expression for correlation ID (optional)
     * Example: "#request.getCorrelationId()", "#correlationId"
     */
    String correlationIdExpression() default "";

    /**
     * SpEL expression for transaction amount (optional)
     * Example: "#request.getAmount()", "#amount"
     */
    String amountExpression() default "";

    /**
     * SpEL expression for currency (optional)
     * Example: "#request.getCurrency()", "'USD'"
     */
    String currencyExpression() default "";

    /**
     * TTL for idempotency record in hours (default: 24)
     */
    int ttlHours() default 24;

    /**
     * Whether to capture request payload for duplicate detection
     * Set to false for large payloads or sensitive data
     */
    boolean captureRequestPayload() default true;
}
