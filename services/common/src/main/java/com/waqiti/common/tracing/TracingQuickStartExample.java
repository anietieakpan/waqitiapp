package com.waqiti.common.tracing;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Quick Start Example for Waqiti Distributed Tracing
 *
 * This file demonstrates all major tracing features:
 * 1. Method-level tracing with @Traced
 * 2. Manual span creation
 * 3. Baggage propagation
 * 4. HTTP client tracing
 * 5. Kafka message tracing
 * 6. Database query tracing
 * 7. Error handling and exception tracking
 * 8. Performance metrics
 * 9. Custom attributes and events
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 1.0
 */
@Slf4j
@Service
public class TracingQuickStartExample {

    @Autowired
    private Tracer tracer;

    @Autowired
    private TraceIdGenerator traceIdGenerator;

    @Autowired
    private RestTemplate tracedRestTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // ========================================
    // Example 1: Basic Method Tracing
    // ========================================

    /**
     * Simple method tracing with @Traced annotation
     * Automatically creates a span for this method
     */
    @Traced(
            operationName = "payment.process",
            businessOperation = "PAYMENT",
            priority = Traced.TracingPriority.HIGH
    )
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Processing payment for amount: {}", request.getAmount());

        // Business logic here
        PaymentResult result = new PaymentResult();
        result.setTransactionId(traceIdGenerator.generateCorrelationId());
        result.setStatus("SUCCESS");
        result.setAmount(request.getAmount());

        return result;
    }

    // ========================================
    // Example 2: Advanced Method Tracing
    // ========================================

    /**
     * Advanced tracing with parameters and result capture
     */
    @Traced(
            operationName = "payment.refund.high-value",
            businessOperation = "REFUND",
            priority = Traced.TracingPriority.CRITICAL,
            includeParameters = true,  // Include method parameters in span
            includeResult = true,      // Include return value in span
            tags = {
                    "payment.type=refund",
                    "priority=critical",
                    "department=finance"
            }
    )
    public RefundResult processRefund(String transactionId, BigDecimal amount) {
        log.info("Processing refund for transaction: {}, amount: {}", transactionId, amount);

        // Critical operation with full tracing
        RefundResult result = new RefundResult();
        result.setRefundId(traceIdGenerator.generateCorrelationId());
        result.setOriginalTransactionId(transactionId);
        result.setRefundAmount(amount);
        result.setStatus("COMPLETED");

        return result;
    }

    // ========================================
    // Example 3: Manual Span Creation
    // ========================================

    /**
     * Manual span creation for fine-grained control
     */
    public void processComplexTransaction(TransactionRequest request) {
        // Create a parent span for the entire transaction
        Span transactionSpan = tracer.spanBuilder("transaction.process.complex")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("transaction.id", request.getId())
                .setAttribute("transaction.type", request.getType())
                .setAttribute("transaction.amount", request.getAmount().doubleValue())
                .startSpan();

        try (Scope scope = transactionSpan.makeCurrent()) {

            // Step 1: Validate transaction
            Span validationSpan = tracer.spanBuilder("transaction.validate")
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();

            try (Scope validationScope = validationSpan.makeCurrent()) {
                validateTransaction(request);
                validationSpan.addEvent("validation.completed");
                validationSpan.setStatus(StatusCode.OK);
            } catch (Exception e) {
                validationSpan.recordException(e);
                validationSpan.setStatus(StatusCode.ERROR, "Validation failed");
                throw e;
            } finally {
                validationSpan.end();
            }

            // Step 2: Check fraud
            Span fraudCheckSpan = tracer.spanBuilder("fraud.check")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setAttribute("check.type", "ml_model")
                    .startSpan();

            try (Scope fraudScope = fraudCheckSpan.makeCurrent()) {
                boolean isFraudulent = checkForFraud(request);
                fraudCheckSpan.setAttribute("fraud.detected", isFraudulent);
                fraudCheckSpan.setAttribute("fraud.score", calculateFraudScore(request));

                if (isFraudulent) {
                    fraudCheckSpan.addEvent("fraud.detected.high_risk");
                    fraudCheckSpan.setStatus(StatusCode.ERROR, "Fraudulent transaction");
                    throw new FraudException("Transaction flagged as fraudulent");
                }

                fraudCheckSpan.setStatus(StatusCode.OK);
            } finally {
                fraudCheckSpan.end();
            }

            // Step 3: Process payment
            Span paymentSpan = tracer.spanBuilder("payment.execute")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("payment.method", request.getPaymentMethod())
                    .startSpan();

            try (Scope paymentScope = paymentSpan.makeCurrent()) {
                executePayment(request);
                paymentSpan.addEvent("payment.authorized");
                paymentSpan.setStatus(StatusCode.OK);
            } finally {
                paymentSpan.end();
            }

            // Mark parent span as successful
            transactionSpan.addEvent("transaction.completed");
            transactionSpan.setStatus(StatusCode.OK);

        } catch (Exception e) {
            transactionSpan.recordException(e);
            transactionSpan.setStatus(StatusCode.ERROR, e.getMessage());
            log.error("Transaction processing failed", e);
            throw e;
        } finally {
            transactionSpan.end();
        }
    }

    // ========================================
    // Example 4: Baggage Propagation
    // ========================================

    /**
     * Demonstrate baggage propagation across services
     */
    @Traced(operationName = "user.transaction.with.baggage")
    public void processUserTransaction(String userId, String tenantId, TransactionRequest request) {
        // Set baggage items that will propagate to all child spans and services
        Baggage baggage = Baggage.builder()
                .put("user.id", userId)
                .put("tenant.id", tenantId)
                .put("request.source", "mobile_app")
                .put("user.tier", "premium")
                .build();

        try (Scope scope = baggage.storeInContext(Context.current()).makeCurrent()) {
            log.info("Processing transaction with baggage - User: {}, Tenant: {}", userId, tenantId);

            // Baggage automatically propagates to:
            // 1. All child spans
            // 2. HTTP requests to downstream services
            // 3. Kafka messages
            // 4. Any async operations

            // Call downstream service - baggage will be in headers
            callUserService(userId);

            // Publish Kafka event - baggage will be in message headers
            publishTransactionEvent(request);

            // Access baggage anywhere in the trace
            String currentUserId = Baggage.current().getEntryValue("user.id");
            log.info("Retrieved user ID from baggage: {}", currentUserId);
        }
    }

    // ========================================
    // Example 5: HTTP Client Tracing
    // ========================================

    /**
     * HTTP client requests are automatically traced
     */
    @Traced(operationName = "external.api.call")
    public UserData fetchUserData(String userId) {
        Span currentSpan = Span.current();
        currentSpan.setAttribute("user.id", userId);

        // Create a child span for the HTTP call
        Span httpSpan = tracer.spanBuilder("http.get.user.data")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("http.method", "GET")
                .setAttribute("http.url", "https://api.example.com/users/" + userId)
                .startSpan();

        try (Scope scope = httpSpan.makeCurrent()) {
            // HTTP request automatically includes trace context in headers
            UserData userData = tracedRestTemplate.getForObject(
                    "https://api.example.com/users/" + userId,
                    UserData.class
            );

            httpSpan.setAttribute("http.status_code", 200);
            httpSpan.setStatus(StatusCode.OK);

            return userData;

        } catch (Exception e) {
            httpSpan.recordException(e);
            httpSpan.setAttribute("error", true);
            httpSpan.setStatus(StatusCode.ERROR, "HTTP request failed");
            throw e;
        } finally {
            httpSpan.end();
        }
    }

    // ========================================
    // Example 6: Kafka Message Tracing
    // ========================================

    /**
     * Kafka messages automatically include trace context
     */
    @Traced(operationName = "event.publish.payment")
    public void publishPaymentEvent(PaymentEvent event) {
        Span currentSpan = Span.current();

        currentSpan.setAttribute("event.type", "payment.completed");
        currentSpan.setAttribute("event.id", event.getId());
        currentSpan.setAttribute("payment.amount", event.getAmount().doubleValue());

        // Publish to Kafka - trace context automatically added to headers
        kafkaTemplate.send("payment-events", event.getId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        currentSpan.recordException(ex);
                        currentSpan.setStatus(StatusCode.ERROR, "Failed to publish event");
                    } else {
                        currentSpan.addEvent("event.published");
                        currentSpan.setStatus(StatusCode.OK);
                    }
                });
    }

    // ========================================
    // Example 7: Error Handling
    // ========================================

    /**
     * Proper error handling in traced methods
     */
    @Traced(
            operationName = "payment.process.with.retry",
            priority = Traced.TracingPriority.HIGH
    )
    public PaymentResult processPaymentWithRetry(PaymentRequest request) {
        Span currentSpan = Span.current();
        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            currentSpan.setAttribute("retry.attempt", attempt);

            try {
                PaymentResult result = executePayment(request);

                currentSpan.addEvent("payment.success", io.opentelemetry.api.common.Attributes.builder()
                        .put("attempt", (long) attempt)
                        .build());

                currentSpan.setStatus(StatusCode.OK);
                return result;

            } catch (TemporaryPaymentException e) {
                currentSpan.addEvent("payment.retry", io.opentelemetry.api.common.Attributes.builder()
                        .put("attempt", (long) attempt)
                        .put("error", e.getMessage())
                        .build());

                if (attempt >= maxRetries) {
                    currentSpan.recordException(e);
                    currentSpan.setStatus(StatusCode.ERROR, "Max retries exceeded");
                    throw new PaymentException("Payment failed after " + maxRetries + " attempts", e);
                }

                // Wait before retry
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new PaymentException("Payment interrupted", ie);
                }
            }
        }

        throw new PaymentException("Unexpected error in payment processing");
    }

    // ========================================
    // Example 8: Async Operations
    // ========================================

    /**
     * Tracing async operations
     */
    @Traced(operationName = "notification.send.async")
    public CompletableFuture<NotificationResult> sendNotificationAsync(String userId, String message) {
        // Capture current context
        Context currentContext = Context.current();
        Span parentSpan = Span.current();

        return CompletableFuture.supplyAsync(() -> {
            // Restore context in async thread
            try (Scope scope = currentContext.makeCurrent()) {

                Span asyncSpan = tracer.spanBuilder("notification.send")
                        .setParent(currentContext)
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("user.id", userId)
                        .setAttribute("notification.type", "email")
                        .startSpan();

                try (Scope asyncScope = asyncSpan.makeCurrent()) {

                    // Send notification
                    NotificationResult result = sendNotification(userId, message);

                    asyncSpan.addEvent("notification.sent");
                    asyncSpan.setStatus(StatusCode.OK);

                    return result;

                } catch (Exception e) {
                    asyncSpan.recordException(e);
                    asyncSpan.setStatus(StatusCode.ERROR, "Notification failed");
                    throw e;
                } finally {
                    asyncSpan.end();
                }
            }
        });
    }

    // ========================================
    // Example 9: Performance Monitoring
    // ========================================

    /**
     * Add performance metrics to spans
     */
    @Traced(operationName = "batch.process.transactions")
    public BatchResult processBatchTransactions(BatchRequest batchRequest) {
        Span currentSpan = Span.current();
        Instant startTime = Instant.now();

        int totalTransactions = batchRequest.getTransactions().size();
        int successCount = 0;
        int errorCount = 0;

        currentSpan.setAttribute("batch.size", totalTransactions);

        for (TransactionRequest transaction : batchRequest.getTransactions()) {
            try {
                processTransaction(transaction);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to process transaction: {}", transaction.getId(), e);
            }
        }

        // Add performance metrics
        long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
        currentSpan.setAttribute("batch.duration_ms", durationMs);
        currentSpan.setAttribute("batch.success_count", successCount);
        currentSpan.setAttribute("batch.error_count", errorCount);
        currentSpan.setAttribute("batch.throughput_tps", (successCount * 1000.0) / durationMs);

        currentSpan.addEvent("batch.completed", io.opentelemetry.api.common.Attributes.builder()
                .put("total", (long) totalTransactions)
                .put("success", (long) successCount)
                .put("errors", (long) errorCount)
                .build());

        if (errorCount > 0) {
            currentSpan.setAttribute("warning", true);
        }

        BatchResult result = new BatchResult();
        result.setTotalProcessed(totalTransactions);
        result.setSuccessCount(successCount);
        result.setErrorCount(errorCount);
        result.setDurationMs(durationMs);

        return result;
    }

    // ========================================
    // Helper Methods (Stubs for demonstration)
    // ========================================

    private void validateTransaction(TransactionRequest request) {
        // Validation logic
    }

    private boolean checkForFraud(TransactionRequest request) {
        return false;  // Stub
    }

    private double calculateFraudScore(TransactionRequest request) {
        return 0.1;  // Stub
    }

    private PaymentResult executePayment(TransactionRequest request) {
        return new PaymentResult();  // Stub
    }

    private void callUserService(String userId) {
        // Call user service
    }

    private void publishTransactionEvent(TransactionRequest request) {
        // Publish event
    }

    private void processTransaction(TransactionRequest transaction) {
        // Process transaction
    }

    private NotificationResult sendNotification(String userId, String message) {
        return new NotificationResult();  // Stub
    }

    // ========================================
    // Sample Data Classes
    // ========================================

    public static class PaymentRequest {
        private String id;
        private BigDecimal amount;
        private String paymentMethod;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    }

    public static class PaymentResult {
        private String transactionId;
        private String status;
        private BigDecimal amount;

        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    public static class RefundResult {
        private String refundId;
        private String originalTransactionId;
        private BigDecimal refundAmount;
        private String status;

        public String getRefundId() { return refundId; }
        public void setRefundId(String refundId) { this.refundId = refundId; }
        public String getOriginalTransactionId() { return originalTransactionId; }
        public void setOriginalTransactionId(String id) { this.originalTransactionId = id; }
        public BigDecimal getRefundAmount() { return refundAmount; }
        public void setRefundAmount(BigDecimal amount) { this.refundAmount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class TransactionRequest {
        private String id;
        private String type;
        private BigDecimal amount;
        private String paymentMethod;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String method) { this.paymentMethod = method; }
    }

    public static class UserData {
        private String userId;
        private String name;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class PaymentEvent {
        private String id;
        private BigDecimal amount;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    public static class NotificationResult {
        private boolean sent;

        public boolean isSent() { return sent; }
        public void setSent(boolean sent) { this.sent = sent; }
    }

    public static class BatchRequest {
        private java.util.List<TransactionRequest> transactions;

        public java.util.List<TransactionRequest> getTransactions() { return transactions; }
        public void setTransactions(java.util.List<TransactionRequest> transactions) {
            this.transactions = transactions;
        }
    }

    public static class BatchResult {
        private int totalProcessed;
        private int successCount;
        private int errorCount;
        private long durationMs;

        public int getTotalProcessed() { return totalProcessed; }
        public void setTotalProcessed(int total) { this.totalProcessed = total; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int count) { this.successCount = count; }
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int count) { this.errorCount = count; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long duration) { this.durationMs = duration; }
    }

    public static class FraudException extends RuntimeException {
        public FraudException(String message) { super(message); }
    }

    public static class PaymentException extends RuntimeException {
        public PaymentException(String message) { super(message); }
        public PaymentException(String message, Throwable cause) { super(message, cause); }
    }

    public static class TemporaryPaymentException extends RuntimeException {
        public TemporaryPaymentException(String message) { super(message); }
    }
}
