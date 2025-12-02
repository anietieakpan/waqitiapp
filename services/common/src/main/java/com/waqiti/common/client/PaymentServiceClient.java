package com.waqiti.common.client;

import com.waqiti.common.api.StandardApiResponse;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Payment Service Client
 * 
 * Provides standardized communication with the Payment Service
 */
@Component
@Slf4j
public class PaymentServiceClient extends ServiceClient {

    public PaymentServiceClient(RestTemplate restTemplate, 
                               @Value("${services.payment-service.url}") String baseUrl) {
        super(restTemplate, baseUrl, "payment-service");
    }

    /**
     * Create payment
     */
    public CompletableFuture<ServiceResponse<PaymentDTO>> createPayment(CreatePaymentRequest request) {
        return post("/api/v1/payments", request, PaymentDTO.class);
    }

    /**
     * Get payment by ID
     */
    public CompletableFuture<ServiceResponse<PaymentDTO>> getPayment(UUID paymentId) {
        return get("/api/v1/payments/" + paymentId, PaymentDTO.class, null);
    }

    /**
     * Process payment
     */
    public CompletableFuture<ServiceResponse<PaymentResultDTO>> processPayment(UUID paymentId, ProcessPaymentRequest request) {
        return post("/api/v1/payments/" + paymentId + "/process", request, PaymentResultDTO.class);
    }

    /**
     * Cancel payment
     */
    public CompletableFuture<ServiceResponse<PaymentDTO>> cancelPayment(UUID paymentId, CancelPaymentRequest request) {
        return post("/api/v1/payments/" + paymentId + "/cancel", request, PaymentDTO.class);
    }

    /**
     * Refund payment
     */
    public CompletableFuture<ServiceResponse<RefundDTO>> refundPayment(UUID paymentId, RefundRequest request) {
        return post("/api/v1/payments/" + paymentId + "/refund", request, RefundDTO.class);
    }

    /**
     * Get payment status
     */
    public CompletableFuture<ServiceResponse<PaymentStatusDTO>> getPaymentStatus(UUID paymentId) {
        return get("/api/v1/payments/" + paymentId + "/status", PaymentStatusDTO.class, null);
    }

    /**
     * Search payments
     */
    public CompletableFuture<ServiceResponse<List<PaymentDTO>>> searchPayments(PaymentSearchRequest request) {
        Map<String, Object> queryParams = Map.of(
            "userId", request.getUserId() != null ? request.getUserId().toString() : "",
            "status", request.getStatus() != null ? request.getStatus() : "",
            "method", request.getMethod() != null ? request.getMethod() : "",
            "fromDate", request.getFromDate() != null ? request.getFromDate().toString() : "",
            "toDate", request.getToDate() != null ? request.getToDate().toString() : "",
            "page", request.getPage(),
            "size", request.getSize()
        );
        return getList("/api/v1/payments/search", 
            new ParameterizedTypeReference<StandardApiResponse<List<PaymentDTO>>>() {}, 
            queryParams);
    }

    /**
     * Get payment methods for user
     */
    public CompletableFuture<ServiceResponse<List<PaymentMethodDTO>>> getPaymentMethods(UUID userId) {
        Map<String, Object> queryParams = Map.of("userId", userId.toString());
        return getList("/api/v1/payment-methods", 
            new ParameterizedTypeReference<StandardApiResponse<List<PaymentMethodDTO>>>() {}, 
            queryParams);
    }

    /**
     * Add payment method
     */
    public CompletableFuture<ServiceResponse<PaymentMethodDTO>> addPaymentMethod(AddPaymentMethodRequest request) {
        return post("/api/v1/payment-methods", request, PaymentMethodDTO.class);
    }

    /**
     * Update payment method
     */
    public CompletableFuture<ServiceResponse<PaymentMethodDTO>> updatePaymentMethod(UUID methodId, UpdatePaymentMethodRequest request) {
        return put("/api/v1/payment-methods/" + methodId, request, PaymentMethodDTO.class);
    }

    /**
     * Delete payment method
     */
    public CompletableFuture<ServiceResponse<Void>> deletePaymentMethod(UUID methodId) {
        return delete("/api/v1/payment-methods/" + methodId, Void.class);
    }

    /**
     * Verify payment method
     */
    public CompletableFuture<ServiceResponse<VerificationResultDTO>> verifyPaymentMethod(UUID methodId, VerifyMethodRequest request) {
        return post("/api/v1/payment-methods/" + methodId + "/verify", request, VerificationResultDTO.class);
    }

    /**
     * Calculate payment fees
     */
    public CompletableFuture<ServiceResponse<FeeCalculationDTO>> calculateFees(FeeCalculationRequest request) {
        return post("/api/v1/payments/calculate-fees", request, FeeCalculationDTO.class);
    }

    /**
     * Get payment analytics
     */
    public CompletableFuture<ServiceResponse<PaymentAnalyticsDTO>> getPaymentAnalytics(UUID userId, AnalyticsRequest request) {
        Map<String, Object> queryParams = Map.of(
            "period", request.getPeriod(),
            "fromDate", request.getFromDate().toString(),
            "toDate", request.getToDate().toString()
        );
        return get("/api/v1/payments/analytics/" + userId, PaymentAnalyticsDTO.class, queryParams);
    }

    /**
     * Process batch payments
     */
    public CompletableFuture<ServiceResponse<BatchPaymentResultDTO>> processBatchPayments(BatchPaymentRequest request) {
        return post("/api/v1/payments/batch", request, BatchPaymentResultDTO.class);
    }

    /**
     * Get recurring payments
     */
    public CompletableFuture<ServiceResponse<List<RecurringPaymentDTO>>> getRecurringPayments(UUID userId) {
        Map<String, Object> queryParams = Map.of("userId", userId.toString());
        return getList("/api/v1/payments/recurring", 
            new ParameterizedTypeReference<StandardApiResponse<List<RecurringPaymentDTO>>>() {}, 
            queryParams);
    }

    /**
     * Create recurring payment
     */
    public CompletableFuture<ServiceResponse<RecurringPaymentDTO>> createRecurringPayment(CreateRecurringPaymentRequest request) {
        return post("/api/v1/payments/recurring", request, RecurringPaymentDTO.class);
    }

    /**
     * Cancel recurring payment
     */
    public CompletableFuture<ServiceResponse<Void>> cancelRecurringPayment(UUID recurringPaymentId) {
        return post("/api/v1/payments/recurring/" + recurringPaymentId + "/cancel", null, Void.class);
    }

    /**
     * Validate payment request
     */
    public CompletableFuture<ServiceResponse<ValidationResultDTO>> validatePayment(PaymentValidationRequest request) {
        return post("/api/v1/payments/validate", request, ValidationResultDTO.class);
    }

    @Override
    protected String getCurrentCorrelationId() {
        return org.slf4j.MDC.get("correlationId");
    }

    @Override
    protected String getCurrentAuthToken() {
        return org.springframework.security.core.context.SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getCredentials()
            .toString();
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentDTO {
        private UUID id;
        private UUID userId;
        private UUID recipientId;
        private String paymentMethodId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String type;
        private String description;
        private String reference;
        private BigDecimal feeAmount;
        private String feeType;
        private LocalDateTime createdAt;
        private LocalDateTime processedAt;
        private LocalDateTime completedAt;
        private Map<String, Object> metadata;
        private String failureReason;
        private int retryCount;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentResultDTO {
        private UUID paymentId;
        private String status;
        private String transactionId;
        private String gatewayReference;
        private BigDecimal processedAmount;
        private BigDecimal feeAmount;
        private LocalDateTime processedAt;
        private Map<String, Object> gatewayResponse;
        private String failureReason;
        private boolean requiresAction;
        private Map<String, Object> nextAction;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RefundDTO {
        private UUID id;
        private UUID paymentId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String reason;
        private String reference;
        private LocalDateTime createdAt;
        private LocalDateTime processedAt;
        private String gatewayReference;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentStatusDTO {
        private UUID paymentId;
        private String status;
        private String substatus;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime lastUpdated;
        private List<PaymentEventDTO> events;
        private Map<String, Object> additionalInfo;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentEventDTO {
        private String event;
        private String status;
        private LocalDateTime timestamp;
        private String description;
        private Map<String, Object> data;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentMethodDTO {
        private UUID id;
        private UUID userId;
        private String type;
        private String provider;
        private String maskedDetails;
        private boolean verified;
        private boolean isDefault;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime lastUsed;
        private LocalDateTime expiresAt;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VerificationResultDTO {
        private UUID verificationId;
        private String status;
        private boolean verified;
        private String method;
        private String failureReason;
        private LocalDateTime verifiedAt;
        private Map<String, Object> verificationData;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeeCalculationDTO {
        private BigDecimal baseFee;
        private BigDecimal percentageFee;
        private BigDecimal totalFee;
        private String currency;
        private String feeStructure;
        private Map<String, BigDecimal> feeBreakdown;
        private Map<String, Object> calculations;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentAnalyticsDTO {
        private UUID userId;
        private String period;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private long totalPayments;
        private BigDecimal totalAmount;
        private BigDecimal averageAmount;
        private BigDecimal totalFees;
        private Map<String, Long> paymentsByStatus;
        private Map<String, BigDecimal> paymentsByMethod;
        private List<DailyPaymentStatsDTO> dailyStats;
        private Map<String, Object> trends;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DailyPaymentStatsDTO {
        private java.time.LocalDate date;
        private long count;
        private BigDecimal amount;
        private BigDecimal fees;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchPaymentResultDTO {
        private UUID batchId;
        private String status;
        private int totalPayments;
        private int successfulPayments;
        private int failedPayments;
        private BigDecimal totalAmount;
        private List<PaymentResultDTO> results;
        private LocalDateTime processedAt;
        private Map<String, Object> summary;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RecurringPaymentDTO {
        private UUID id;
        private UUID userId;
        private UUID recipientId;
        private String paymentMethodId;
        private BigDecimal amount;
        private String currency;
        private String frequency;
        private String status;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private LocalDateTime nextPaymentDate;
        private LocalDateTime lastPaymentDate;
        private int totalPayments;
        private int completedPayments;
        private String description;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationResultDTO {
        private boolean valid;
        private String status;
        private List<ValidationErrorDTO> errors;
        private List<ValidationWarningDTO> warnings;
        private Map<String, Object> details;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationErrorDTO {
        private String field;
        private String code;
        private String message;
        private Object rejectedValue;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationWarningDTO {
        private String field;
        private String code;
        private String message;
        private String recommendation;
    }

    // Request DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreatePaymentRequest {
        private UUID userId;
        private UUID recipientId;
        private String paymentMethodId;
        private BigDecimal amount;
        private String currency;
        private String type;
        private String description;
        private String reference;
        private String idempotencyKey;
        private Map<String, Object> metadata;
        private boolean skipFraudCheck;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProcessPaymentRequest {
        private String confirmation;
        private Map<String, Object> additionalData;
        private boolean skipFraudCheck;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CancelPaymentRequest {
        private String reason;
        private String notes;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RefundRequest {
        private BigDecimal amount;
        private String reason;
        private String reference;
        private String idempotencyKey;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentSearchRequest {
        private UUID userId;
        private String status;
        private String method;
        private String type;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        @Builder.Default
        private int page = 0;
        @Builder.Default
        private int size = 20;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AddPaymentMethodRequest {
        private UUID userId;
        private String type;
        private String provider;
        private Map<String, Object> details;
        private boolean setAsDefault;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdatePaymentMethodRequest {
        private String status;
        private boolean isDefault;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VerifyMethodRequest {
        private String verificationType;
        private String verificationCode;
        private Map<String, Object> verificationData;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeeCalculationRequest {
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String type;
        private String region;
        private Map<String, Object> parameters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AnalyticsRequest {
        private String period; // DAILY, WEEKLY, MONTHLY, YEARLY
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private List<String> metrics;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchPaymentRequest {
        private String batchId;
        private List<CreatePaymentRequest> payments;
        private String idempotencyKey;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateRecurringPaymentRequest {
        private UUID userId;
        private UUID recipientId;
        private String paymentMethodId;
        private BigDecimal amount;
        private String currency;
        private String frequency; // DAILY, WEEKLY, MONTHLY, YEARLY
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String description;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentValidationRequest {
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String type;
        private Map<String, Object> parameters;
    }
}