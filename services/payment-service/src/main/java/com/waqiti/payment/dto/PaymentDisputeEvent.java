package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Payment dispute event - handles customer payment disputes.
 * Critical for maintaining customer trust and regulatory compliance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentDisputeEvent {
    
    // ======================== Event Metadata ========================
    @NotBlank(message = "Event ID is required")
    private String eventId;
    
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @NotBlank(message = "Source service is required")
    private String source;
    
    // ======================== Dispute Identification ========================
    @NotBlank(message = "Dispute ID is required")
    private String disputeId;
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotBlank(message = "Payment ID is required")
    private String paymentId;
    
    private String referenceNumber;
    
    // ======================== Parties Involved ========================
    @NotBlank(message = "Customer ID is required")
    private String customerId;
    
    @NotBlank(message = "Customer account ID is required")
    private String customerAccountId;
    
    @NotBlank(message = "Merchant ID is required")
    private String merchantId;
    
    @NotBlank(message = "Merchant account ID is required")
    private String merchantAccountId;
    
    private String merchantName;
    
    // ======================== Transaction Details ========================
    @NotNull(message = "Transaction amount is required")
    @DecimalMin(value = "0.01")
    private BigDecimal transactionAmount;
    
    @NotBlank(message = "Transaction currency is required")
    @Size(min = 3, max = 3)
    private String transactionCurrency;
    
    @NotNull(message = "Transaction date is required")
    private LocalDateTime transactionDate;
    
    private String transactionType;
    
    private String paymentMethod;
    
    private String cardLastFour;
    
    // ======================== Dispute Details ========================
    @NotNull(message = "Dispute type is required")
    private DisputeType disputeType;
    
    @NotNull(message = "Dispute reason is required")
    private DisputeReason disputeReason;
    
    @NotBlank(message = "Dispute description is required")
    @Size(max = 2000)
    private String disputeDescription;
    
    @NotNull(message = "Dispute amount is required")
    @DecimalMin(value = "0.01")
    private BigDecimal disputeAmount;
    
    @NotNull(message = "Dispute status is required")
    private DisputeStatus disputeStatus;
    
    @NotNull(message = "Dispute initiated date is required")
    private LocalDateTime disputeInitiatedDate;
    
    private LocalDateTime disputeDueDate;
    
    // ======================== Evidence & Documentation ========================
    private List<String> evidenceDocumentIds;
    
    private Map<String, String> evidenceUrls;
    
    private String customerStatement;
    
    private String merchantResponse;
    
    private List<String> supportingDocuments;
    
    private Boolean evidenceProvided;
    
    private LocalDateTime evidenceSubmittedDate;
    
    // ======================== Processing Information ========================
    @NotNull(message = "Priority is required")
    private Priority priority;
    
    private String assignedTo;
    
    private String processingStage;
    
    private LocalDateTime reviewStartedAt;
    
    private LocalDateTime lastUpdatedAt;
    
    private Integer daysToRespond;
    
    private Boolean autoResolve;
    
    // ======================== Financial Impact ========================
    private BigDecimal liabilityAmount;
    
    private String liabilityHolder; // MERCHANT, PLATFORM, CUSTOMER
    
    private BigDecimal refundAmount;
    
    private Boolean refundIssued;
    
    private LocalDateTime refundDate;
    
    private BigDecimal platformFee;
    
    private BigDecimal merchantFee;
    
    // ======================== Card Network Information ========================
    private String cardNetwork; // VISA, MASTERCARD, AMEX, etc.
    
    private String networkReasonCode;
    
    private String networkCaseNumber;
    
    private LocalDateTime networkDeadline;
    
    private String acquirerReferenceNumber;
    
    private String issuerResponseCode;
    
    // ======================== Resolution Details ========================
    private ResolutionType resolution;
    
    private String resolutionReason;
    
    private LocalDateTime resolvedAt;
    
    private String resolvedBy;
    
    private BigDecimal settlementAmount;
    
    private LocalDateTime settlementDate;
    
    // ======================== Compliance & Regulatory ========================
    private Boolean regulatoryReporting;
    
    private String regulatoryCategory;
    
    private List<String> complianceFlags;
    
    private Boolean fraudSuspected;
    
    private String riskLevel;
    
    // ======================== Communication ========================
    @Builder.Default
    private Boolean notifyCustomer = true;
    
    @Builder.Default
    private Boolean notifyMerchant = true;
    
    private List<String> notificationsSent;
    
    private LocalDateTime lastCustomerContact;
    
    private LocalDateTime lastMerchantContact;
    
    // ======================== Actions Required ========================
    @NotNull
    @Builder.Default
    private Boolean freezeTransaction = true;
    
    @Builder.Default
    private Boolean holdMerchantFunds = true;
    
    @Builder.Default
    private Boolean requireInvestigation = false;
    
    private List<String> requiredActions;
    
    // ======================== Metadata ========================
    private Map<String, Object> additionalData;
    
    private String externalSystemId;
    
    private String apiVersion;
    
    // ======================== Enums ========================
    
    public enum DisputeType {
        UNAUTHORIZED_TRANSACTION("Unauthorized transaction"),
        DUPLICATE_CHARGE("Duplicate charge"),
        PRODUCT_NOT_RECEIVED("Product not received"),
        SERVICE_NOT_PROVIDED("Service not provided"),
        DEFECTIVE_PRODUCT("Defective product"),
        SUBSCRIPTION_CANCELLATION("Subscription cancellation issue"),
        INCORRECT_AMOUNT("Incorrect amount charged"),
        REFUND_NOT_PROCESSED("Refund not processed"),
        FRAUD("Fraudulent transaction"),
        IDENTITY_THEFT("Identity theft"),
        PROCESSING_ERROR("Processing error"),
        OTHER("Other dispute reason");
        
        private final String description;
        
        DisputeType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum DisputeReason {
        // Fraud reasons
        CARD_NOT_PRESENT,
        STOLEN_CARD,
        COUNTERFEIT_CARD,
        ACCOUNT_TAKEOVER,
        
        // Authorization reasons
        NO_AUTHORIZATION,
        EXPIRED_AUTHORIZATION,
        EXCEEDED_AUTHORIZATION,
        
        // Processing errors
        DUPLICATE_PROCESSING,
        LATE_PRESENTMENT,
        INCORRECT_CURRENCY,
        INCORRECT_AMOUNT,
        
        // Product/Service issues
        MERCHANDISE_NOT_RECEIVED,
        SERVICES_NOT_PROVIDED,
        DEFECTIVE_MERCHANDISE,
        QUALITY_ISSUE,
        NOT_AS_DESCRIBED,
        
        // Cancellation/Return
        CANCELLED_RECURRING,
        CANCELLED_MERCHANDISE,
        CREDIT_NOT_PROCESSED,
        RETURN_NOT_ACCEPTED,
        
        // Other
        UNRECOGNIZED_CHARGE,
        CUSTOMER_DISPUTE,
        OTHER_REASON
    }
    
    public enum DisputeStatus {
        INITIATED("Dispute initiated"),
        PENDING_EVIDENCE("Pending evidence from merchant"),
        UNDER_REVIEW("Under review"),
        ESCALATED("Escalated to senior team"),
        PENDING_NETWORK("Pending card network decision"),
        WON("Dispute won by customer"),
        LOST("Dispute lost by customer"),
        RESOLVED("Dispute resolved"),
        CLOSED("Dispute closed"),
        EXPIRED("Dispute expired");
        
        private final String description;
        
        DisputeStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum Priority {
        CRITICAL(1),
        HIGH(2),
        MEDIUM(3),
        LOW(4);
        
        private final int level;
        
        Priority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    public enum ResolutionType {
        CUSTOMER_FAVOR("Resolved in customer's favor"),
        MERCHANT_FAVOR("Resolved in merchant's favor"),
        SPLIT_LIABILITY("Split liability"),
        PLATFORM_LIABILITY("Platform assumes liability"),
        WITHDRAWN("Dispute withdrawn by customer"),
        EXPIRED("Dispute expired"),
        SETTLED("Settled between parties"),
        ARBITRATION("Went to arbitration");
        
        private final String description;
        
        ResolutionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // ======================== Helper Methods ========================
    
    public boolean isFraudRelated() {
        return disputeType == DisputeType.FRAUD || 
               disputeType == DisputeType.IDENTITY_THEFT ||
               disputeType == DisputeType.UNAUTHORIZED_TRANSACTION ||
               Boolean.TRUE.equals(fraudSuspected);
    }
    
    public boolean requiresUrgentAction() {
        return priority == Priority.CRITICAL ||
               (disputeDueDate != null && disputeDueDate.isBefore(LocalDateTime.now().plusDays(2)));
    }
    
    public boolean isHighValue() {
        return disputeAmount != null && disputeAmount.compareTo(new BigDecimal("1000")) > 0;
    }
    
    public boolean canAutoResolve() {
        return Boolean.TRUE.equals(autoResolve) && 
               disputeAmount.compareTo(new BigDecimal("100")) < 0 &&
               !isFraudRelated();
    }
}