package com.waqiti.payment.commons.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.waqiti.payment.commons.domain.Money;
import com.waqiti.payment.commons.domain.PaymentMethod;
import com.waqiti.payment.commons.domain.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Standardized payment response DTO used across all payment services
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {
    
    // Core identifiers
    private UUID paymentId;
    private UUID requestId;
    private String externalTransactionId; // ID from external payment processor
    private String reference;
    
    // Parties
    private UUID senderId;
    private UUID recipientId;
    private String recipientType;
    
    // Amount and currency
    private Money amount;
    private Money processedAmount; // Actual amount processed (may differ due to fees)
    private Money fees;
    private Money netAmount; // Amount after fees
    
    // Status and tracking
    private PaymentStatus status;
    private String statusReason;
    private PaymentMethod paymentMethod;
    private String paymentMethodId;
    
    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;
    private Instant processedAt;
    private Instant settledAt;
    private Instant expiresAt;
    
    // Description and metadata
    private String description;
    private String memo;
    private String category;
    private Map<String, String> tags;
    
    // Processing details
    private String processingChannel; // INTERNAL, STRIPE, BANK, etc.
    private String authorizationCode;
    private String settlementBatch;
    private Integer retryAttempts;
    
    // Split payment details
    private Boolean isSplitPayment;
    private UUID splitPaymentId;
    private List<SplitPaymentDetail> splitDetails;
    
    // Compliance and security
    private String complianceLevel;
    private Boolean kycVerified;
    private String riskScore;
    private List<String> complianceFlags;
    
    // Geographic info
    private String senderCountry;
    private String recipientCountry;
    private String processingCountry;
    
    // Error details (if applicable)
    private String errorCode;
    private String errorMessage;
    private String errorDetails;
    
    // Receipt and confirmation
    private String receiptUrl;
    private String confirmationNumber;
    private String trackingNumber; // For services that provide tracking
    
    // Reconciliation
    private String reconciliationId;
    private Instant reconciledAt;
    
    // Links and actions
    private List<PaymentAction> availableActions;
    private Map<String, String> links; // HATEOAS-style links
    
    // Nested DTOs
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitPaymentDetail {
        private UUID participantId;
        private String participantType;
        private Money amount;
        private PaymentStatus status;
        private String reference;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentAction {
        private String action; // CANCEL, REFUND, VOID, CAPTURE, etc.
        private String displayName;
        private String description;
        private Boolean enabled;
        private String reason; // Why action is/isn't available
        private Map<String, Object> parameters; // Required parameters for action
    }
    
    // Convenience methods
    public boolean isSuccessful() {
        return status != null && status.isSuccessful();
    }
    
    public boolean isFailed() {
        return status != null && status.isFailed();
    }
    
    public boolean isPending() {
        return status != null && status.isPending();
    }
    
    public boolean isProcessing() {
        return status != null && status.isProcessing();
    }
    
    public boolean isCancelled() {
        return status != null && status.isCancelled();
    }
    
    public boolean isUnderReview() {
        return status != null && status.isUnderReview();
    }
    
    public boolean isFinal() {
        return status != null && status.isFinal();
    }
    
    public boolean hasError() {
        return errorCode != null || (status != null && status.isFailed());
    }
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
    
    public boolean isInternational() {
        return senderCountry != null && recipientCountry != null && 
               !senderCountry.equals(recipientCountry);
    }
    
    public boolean isHighValue() {
        if (amount == null) return false;
        
        switch (amount.getCurrencyCode()) {
            case "USD":
            case "EUR":
            case "GBP":
                return amount.getAmount().doubleValue() >= 10000.0;
            default:
                return amount.getAmount().doubleValue() >= 10000.0;
        }
    }
    
    public boolean canPerformAction(String actionName) {
        if (availableActions == null) return false;
        
        return availableActions.stream()
            .anyMatch(action -> action.getAction().equals(actionName) && 
                              Boolean.TRUE.equals(action.getEnabled()));
    }
    
    public PaymentAction getAction(String actionName) {
        if (availableActions == null) return null;
        
        return availableActions.stream()
            .filter(action -> action.getAction().equals(actionName))
            .findFirst()
            .orElse(null);
    }
    
    public long getProcessingTimeMinutes() {
        if (createdAt == null || processedAt == null) return 0;
        return java.time.Duration.between(createdAt, processedAt).toMinutes();
    }
    
    public long getSettlementTimeMinutes() {
        if (processedAt == null || settledAt == null) return 0;
        return java.time.Duration.between(processedAt, settledAt).toMinutes();
    }
    
    public String getStatusCode() {
        return status != null ? status.getCode() : null;
    }
    
    public String getPaymentMethodCode() {
        return paymentMethod != null ? paymentMethod.getCode() : null;
    }
    
    // Builder customizations
    public static class PaymentResponseBuilder {
        public PaymentResponseBuilder amount(BigDecimal amount, String currencyCode) {
            this.amount = Money.of(amount, currencyCode);
            return this;
        }

        public PaymentResponseBuilder processedAmount(BigDecimal amount, String currencyCode) {
            this.processedAmount = Money.of(amount, currencyCode);
            return this;
        }

        public PaymentResponseBuilder fees(BigDecimal amount, String currencyCode) {
            this.fees = Money.of(amount, currencyCode);
            return this;
        }

        public PaymentResponseBuilder netAmount(BigDecimal amount, String currencyCode) {
            this.netAmount = Money.of(amount, currencyCode);
            return this;
        }
        
        public PaymentResponseBuilder status(String statusCode) {
            this.status = PaymentStatus.fromCode(statusCode);
            return this;
        }
        
        public PaymentResponseBuilder paymentMethod(String methodCode) {
            this.paymentMethod = PaymentMethod.fromCode(methodCode);
            return this;
        }
        
        public PaymentResponseBuilder generatePaymentId() {
            this.paymentId = UUID.randomUUID();
            return this;
        }
        
        public PaymentResponseBuilder withTimestamps() {
            Instant now = Instant.now();
            this.createdAt = now;
            this.updatedAt = now;
            return this;
        }
        
        public PaymentResponseBuilder addAction(String action, String displayName, boolean enabled) {
            if (this.availableActions == null) {
                this.availableActions = new java.util.ArrayList<>();
            }
            this.availableActions.add(PaymentAction.builder()
                .action(action)
                .displayName(displayName)
                .enabled(enabled)
                .build());
            return this;
        }
    }
}