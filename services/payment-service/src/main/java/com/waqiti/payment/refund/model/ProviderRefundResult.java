package com.waqiti.payment.refund.model;

import com.waqiti.payment.core.model.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Provider Refund Result Model
 * 
 * Captures provider-specific refund processing results including:
 * - Provider response data and status
 * - Transaction tracking information
 * - Error details and retry information
 * - Settlement and reconciliation data
 * - Performance metrics
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRefundResult {
    
    // Provider identification
    private ProviderType providerType;
    private String providerId;
    private String providerName;
    private String providerVersion;
    
    // Primary identifiers
    private String providerRefundId;
    private String providerTransactionId;
    private String providerCorrelationId;
    private String providerBatchId;
    
    // Status and processing
    private ProviderRefundStatus status;
    private String statusCode;
    private String statusMessage;
    private String providerStatusCode;
    private String providerStatusMessage;
    
    // Financial information
    private BigDecimal requestedAmount;
    private BigDecimal processedAmount;
    private BigDecimal refundedAmount;
    private BigDecimal feeAmount;
    private BigDecimal netAmount;
    private String currency;
    
    // Provider response details
    private String rawResponse;
    private Map<String, Object> providerResponseData;
    private String responseFormat;
    private Integer httpStatusCode;
    private Map<String, String> responseHeaders;
    
    // Timing information
    private Instant requestSentAt;
    private Instant responseReceivedAt;
    private Instant refundInitiatedAt;
    private Instant refundCompletedAt;
    private Instant estimatedSettlement;
    private Long processingTimeMillis;
    
    // Error handling
    private boolean successful;
    private String errorCode;
    private String errorMessage;
    private String errorCategory;
    private String errorSource;
    private List<String> errorDetails;
    private boolean retryable;
    private Integer retryAttempt;
    private Instant nextRetryAt;
    
    // Settlement information
    private String settlementBatchId;
    private String settlementReference;
    private Instant settlementDate;
    private SettlementStatus settlementStatus;
    private BigDecimal settlementAmount;
    private String settlementCurrency;
    
    // Reconciliation data
    private String reconciliationId;
    private ReconciliationStatus reconciliationStatus;
    private Instant reconciledAt;
    private BigDecimal reconciledAmount;
    private List<String> reconciliationDiscrepancies;
    
    // Provider-specific metadata
    private String authorizationCode;
    private String referenceNumber;
    private String traceNumber;
    private String merchantId;
    private String terminalId;
    private Map<String, Object> additionalData;
    
    // Network and routing
    private String networkType;
    private String routingCode;
    private String processingCode;
    private String messageType;
    
    // Security and compliance
    private String encryptionMethod;
    private String securityToken;
    private List<String> complianceFlags;
    private String riskAssessment;
    
    // Performance metrics
    private Long networkLatencyMillis;
    private Long providerLatencyMillis;
    private Long totalLatencyMillis;
    private String performanceCategory;
    private Map<String, Long> detailedMetrics;
    
    // Callback information
    private boolean callbackExpected;
    private String callbackUrl;
    private Instant callbackReceivedAt;
    private String callbackStatus;
    private Map<String, Object> callbackData;
    
    // Audit trail
    private String requestId;
    private String sessionId;
    private String clientIpAddress;
    private String userAgent;
    private String apiVersion;
    
    // Enums
    public enum ProviderRefundStatus {
        INITIATED,
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REJECTED,
        CANCELLED,
        EXPIRED,
        REVERSED,
        PARTIAL,
        REQUIRES_MANUAL_REVIEW,
        CALLBACK_PENDING
    }
    
    public enum SettlementStatus {
        PENDING,
        PROCESSING,
        SETTLED,
        FAILED,
        REJECTED,
        CANCELLED,
        PARTIAL
    }
    
    public enum ReconciliationStatus {
        PENDING,
        MATCHED,
        DISCREPANCY,
        RESOLVED,
        FAILED,
        MANUAL_REVIEW_REQUIRED
    }
    
    public enum ErrorCategory {
        TECHNICAL,
        BUSINESS,
        NETWORK,
        TIMEOUT,
        AUTHENTICATION,
        AUTHORIZATION,
        VALIDATION,
        COMPLIANCE,
        INSUFFICIENT_FUNDS,
        PROVIDER_ERROR
    }
    
    // Helper methods
    public boolean isSuccessful() {
        return successful && (status == ProviderRefundStatus.COMPLETED || 
                             status == ProviderRefundStatus.PARTIAL);
    }
    
    public boolean isFailed() {
        return !successful || status == ProviderRefundStatus.FAILED || 
               status == ProviderRefundStatus.REJECTED;
    }
    
    public boolean isPending() {
        return status == ProviderRefundStatus.PENDING || 
               status == ProviderRefundStatus.PROCESSING ||
               status == ProviderRefundStatus.CALLBACK_PENDING;
    }
    
    public boolean requiresManualReview() {
        return status == ProviderRefundStatus.REQUIRES_MANUAL_REVIEW;
    }
    
    public boolean canRetry() {
        return retryable && isFailed() && 
               (retryAttempt == null || retryAttempt < 3);
    }
    
    public boolean hasErrors() {
        return errorCode != null || errorMessage != null || 
               (errorDetails != null && !errorDetails.isEmpty());
    }
    
    public Long getTotalProcessingTime() {
        if (requestSentAt != null && responseReceivedAt != null) {
            return responseReceivedAt.toEpochMilli() - requestSentAt.toEpochMilli();
        }
        return processingTimeMillis;
    }
    
    public boolean isSettled() {
        return settlementStatus == SettlementStatus.SETTLED;
    }
    
    public boolean isReconciled() {
        return reconciliationStatus == ReconciliationStatus.MATCHED ||
               reconciliationStatus == ReconciliationStatus.RESOLVED;
    }
    
    public BigDecimal getEffectiveAmount() {
        return netAmount != null ? netAmount : 
               (refundedAmount != null ? refundedAmount : processedAmount);
    }
    
    public String getProviderReference() {
        return providerRefundId != null ? providerRefundId : 
               (referenceNumber != null ? referenceNumber : providerTransactionId);
    }
    
    public boolean hasDiscrepancies() {
        return reconciliationDiscrepancies != null && 
               !reconciliationDiscrepancies.isEmpty();
    }
    
    public String getErrorSummary() {
        if (!hasErrors()) {
            return null;
        }
        
        StringBuilder summary = new StringBuilder();
        if (errorCode != null) {
            summary.append("Code: ").append(errorCode);
        }
        if (errorMessage != null) {
            if (summary.length() > 0) summary.append(", ");
            summary.append("Message: ").append(errorMessage);
        }
        if (providerStatusCode != null) {
            if (summary.length() > 0) summary.append(", ");
            summary.append("Provider: ").append(providerStatusCode);
        }
        
        return summary.toString();
    }
    
    // Static factory methods
    public static ProviderRefundResult success(ProviderType providerType,
                                             String providerRefundId,
                                             BigDecimal refundedAmount,
                                             String currency) {
        return ProviderRefundResult.builder()
            .providerType(providerType)
            .providerRefundId(providerRefundId)
            .status(ProviderRefundStatus.COMPLETED)
            .successful(true)
            .refundedAmount(refundedAmount)
            .processedAmount(refundedAmount)
            .netAmount(refundedAmount)
            .currency(currency)
            .refundCompletedAt(Instant.now())
            .responseReceivedAt(Instant.now())
            .build();
    }
    
    public static ProviderRefundResult pending(ProviderType providerType,
                                             String providerRefundId,
                                             BigDecimal requestedAmount) {
        return ProviderRefundResult.builder()
            .providerType(providerType)
            .providerRefundId(providerRefundId)
            .status(ProviderRefundStatus.PENDING)
            .successful(false)
            .requestedAmount(requestedAmount)
            .refundInitiatedAt(Instant.now())
            .build();
    }
    
    public static ProviderRefundResult failed(ProviderType providerType,
                                            String errorCode,
                                            String errorMessage) {
        return ProviderRefundResult.builder()
            .providerType(providerType)
            .status(ProviderRefundStatus.FAILED)
            .successful(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .responseReceivedAt(Instant.now())
            .build();
    }
    
    public static ProviderRefundResult rejected(ProviderType providerType,
                                              String providerStatusCode,
                                              String rejectionReason) {
        return ProviderRefundResult.builder()
            .providerType(providerType)
            .status(ProviderRefundStatus.REJECTED)
            .successful(false)
            .providerStatusCode(providerStatusCode)
            .statusMessage(rejectionReason)
            .errorMessage(rejectionReason)
            .responseReceivedAt(Instant.now())
            .build();
    }
    
    public static ProviderRefundResult timeout(ProviderType providerType,
                                             Long timeoutMillis) {
        return ProviderRefundResult.builder()
            .providerType(providerType)
            .status(ProviderRefundStatus.FAILED)
            .successful(false)
            .errorCode("TIMEOUT")
            .errorMessage("Provider request timed out after " + timeoutMillis + "ms")
            .errorCategory("TIMEOUT")
            .retryable(true)
            .processingTimeMillis(timeoutMillis)
            .responseReceivedAt(Instant.now())
            .build();
    }
    
    public static ProviderRefundResult networkError(ProviderType providerType,
                                                  String networkError) {
        return ProviderRefundResult.builder()
            .providerType(providerType)
            .status(ProviderRefundStatus.FAILED)
            .successful(false)
            .errorCode("NETWORK_ERROR")
            .errorMessage(networkError)
            .errorCategory("NETWORK")
            .retryable(true)
            .responseReceivedAt(Instant.now())
            .build();
    }
    
    // Builder customization
    public static class ProviderRefundResultBuilder {
        
        public ProviderRefundResultBuilder withTiming(Instant requestSent, 
                                                    Instant responseReceived) {
            this.requestSentAt = requestSent;
            this.responseReceivedAt = responseReceived;
            
            if (requestSent != null && responseReceived != null) {
                this.processingTimeMillis = responseReceived.toEpochMilli() - 
                                          requestSent.toEpochMilli();
            }
            
            return this;
        }
        
        public ProviderRefundResultBuilder withError(String code, 
                                                   String message, 
                                                   String category,
                                                   boolean retryable) {
            this.successful = false;
            this.errorCode = code;
            this.errorMessage = message;
            this.errorCategory = category;
            this.retryable = retryable;
            this.status = ProviderRefundStatus.FAILED;
            return this;
        }
        
        public ProviderRefundResultBuilder withSettlement(String batchId, 
                                                        Instant settlementDate,
                                                        BigDecimal amount) {
            this.settlementBatchId = batchId;
            this.settlementDate = settlementDate;
            this.settlementAmount = amount;
            this.settlementStatus = SettlementStatus.SETTLED;
            return this;
        }
        
        public ProviderRefundResultBuilder withProviderResponse(String rawResponse,
                                                              Map<String, Object> responseData,
                                                              Integer httpStatus) {
            this.rawResponse = rawResponse;
            this.providerResponseData = responseData;
            this.httpStatusCode = httpStatus;
            return this;
        }
        
        public ProviderRefundResultBuilder withMetrics(Long networkLatency,
                                                     Long providerLatency,
                                                     String performanceCategory) {
            this.networkLatencyMillis = networkLatency;
            this.providerLatencyMillis = providerLatency;
            this.performanceCategory = performanceCategory;
            
            if (networkLatency != null && providerLatency != null) {
                this.totalLatencyMillis = networkLatency + providerLatency;
            }
            
            return this;
        }
    }
}