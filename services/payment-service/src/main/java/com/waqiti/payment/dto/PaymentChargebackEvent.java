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
 * Payment chargeback event - handles card network initiated reversals.
 * More serious than disputes, chargebacks have significant financial and reputational impact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentChargebackEvent {
    
    // ======================== Event Metadata ========================
    @NotBlank(message = "Event ID is required")
    private String eventId;
    
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @NotBlank(message = "Source service is required")
    private String source;
    
    // ======================== Chargeback Identification ========================
    @NotBlank(message = "Chargeback ID is required")
    private String chargebackId;
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotBlank(message = "Payment ID is required")
    private String paymentId;
    
    private String originalDisputeId;
    
    @NotBlank(message = "ARN is required")
    private String acquirerReferenceNumber;
    
    // ======================== Financial Details ========================
    @NotNull(message = "Chargeback amount is required")
    @DecimalMin(value = "0.01")
    private BigDecimal chargebackAmount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3)
    private String currency;
    
    @NotNull(message = "Original transaction amount is required")
    private BigDecimal originalAmount;
    
    @NotNull(message = "Transaction date is required")
    private LocalDateTime transactionDate;
    
    // ======================== Card Network Information ========================
    @NotNull(message = "Card network is required")
    private CardNetwork cardNetwork;
    
    @NotBlank(message = "Reason code is required")
    private String reasonCode;
    
    @NotBlank(message = "Reason description is required")
    private String reasonDescription;
    
    private String networkCaseNumber;
    
    @NotNull(message = "Chargeback stage is required")
    private ChargebackStage chargebackStage;
    
    private LocalDateTime networkDeadline;
    
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
    
    private String merchantCategoryCode;
    
    // ======================== Card Details ========================
    private String cardLastFour;
    
    private String cardBin;
    
    private String cardType;
    
    private String issuingBank;
    
    private String issuerCountry;
    
    // ======================== Liability & Fees ========================
    @NotNull(message = "Liability shift is required")
    private LiabilityShift liabilityShift;
    
    @NotNull(message = "Chargeback fee is required")
    @DecimalMin(value = "0.00")
    private BigDecimal chargebackFee;
    
    private BigDecimal additionalFees;
    
    private String liabilityHolder;
    
    private Boolean merchantLiable;
    
    // ======================== Response & Evidence ========================
    @NotNull(message = "Response required flag is required")
    @Builder.Default
    private Boolean responseRequired = true;
    
    private LocalDateTime responseDeadline;
    
    private ChargebackResponse merchantResponseStatus;
    
    private List<String> evidenceDocuments;
    
    private Map<String, String> evidenceUrls;
    
    private String merchantResponseText;
    
    private LocalDateTime responseSubmittedAt;
    
    private Boolean evidenceAccepted;
    
    // ======================== Risk & Fraud Indicators ========================
    @NotNull(message = "Fraud indicator is required")
    @Builder.Default
    private Boolean fraudRelated = false;
    
    private String fraudType;
    
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private Double fraudScore;
    
    private List<String> riskIndicators;
    
    private Boolean threeDSecureUsed;
    
    private String authenticationMethod;
    
    // ======================== Processing Status ========================
    @NotNull(message = "Chargeback status is required")
    private ChargebackStatus status;
    
    @NotNull(message = "Priority is required")
    private Priority priority;
    
    private String assignedTo;
    
    private LocalDateTime acceptedAt;
    
    private LocalDateTime disputedAt;
    
    private LocalDateTime resolvedAt;
    
    // ======================== Financial Impact ========================
    @NotNull(message = "Total loss amount is required")
    private BigDecimal totalLossAmount;
    
    private BigDecimal recoveredAmount;
    
    private BigDecimal netLoss;
    
    private Boolean fundsDebited;
    
    private LocalDateTime debitDate;
    
    private String debitReference;
    
    // ======================== Representment ========================
    private Boolean representmentEligible;
    
    private Boolean representmentSubmitted;
    
    private LocalDateTime representmentDate;
    
    private RepresentmentStatus representmentStatus;
    
    private String representmentOutcome;
    
    // ======================== Prevention & Monitoring ========================
    private Boolean blacklistMerchant;
    
    private Boolean blockCustomer;
    
    private Integer merchantChargebackCount;
    
    private BigDecimal merchantChargebackRatio;
    
    private Boolean highRiskMerchant;
    
    // ======================== Compliance ========================
    private Boolean regulatoryReporting;
    
    private List<String> complianceFlags;
    
    private String reportingCategory;
    
    private LocalDateTime reportingDeadline;
    
    // ======================== Actions Required ========================
    @NotNull
    @Builder.Default
    private Boolean immediateAction = true;
    
    @Builder.Default
    private Boolean debitMerchantAccount = true;
    
    @Builder.Default
    private Boolean notifyRiskTeam = true;
    
    private List<String> requiredActions;
    
    // ======================== Metadata ========================
    private Map<String, Object> additionalData;
    
    private String processorReference;
    
    private String apiVersion;
    
    // ======================== Enums ========================
    
    public enum CardNetwork {
        VISA("Visa"),
        MASTERCARD("Mastercard"),
        AMERICAN_EXPRESS("American Express"),
        DISCOVER("Discover"),
        JCB("JCB"),
        UNIONPAY("UnionPay"),
        DINERS("Diners Club"),
        OTHER("Other");
        
        private final String displayName;
        
        CardNetwork(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum ChargebackStage {
        FIRST_CHARGEBACK("Initial chargeback"),
        PRE_ARBITRATION("Pre-arbitration"),
        ARBITRATION("Arbitration"),
        PRE_COMPLIANCE("Pre-compliance"),
        COMPLIANCE("Compliance case");
        
        private final String description;
        
        ChargebackStage(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum ChargebackStatus {
        RECEIVED("Chargeback received"),
        ACCEPTED("Chargeback accepted"),
        DISPUTED("Chargeback disputed"),
        UNDER_REVIEW("Under review"),
        REPRESENTMENT_SUBMITTED("Representment submitted"),
        WON("Chargeback won"),
        LOST("Chargeback lost"),
        REVERSED("Chargeback reversed"),
        CLOSED("Chargeback closed");
        
        private final String description;
        
        ChargebackStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum LiabilityShift {
        MERCHANT("Merchant liable"),
        ISSUER("Issuer liable"),
        PLATFORM("Platform liable"),
        SHARED("Shared liability"),
        NONE("No liability shift");
        
        private final String description;
        
        LiabilityShift(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum ChargebackResponse {
        NOT_CONTESTED("Accept chargeback"),
        CONTESTED("Contest chargeback"),
        PENDING("Response pending"),
        SUBMITTED("Response submitted"),
        EXPIRED("Response deadline expired");
        
        private final String description;
        
        ChargebackResponse(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum RepresentmentStatus {
        PENDING("Pending submission"),
        SUBMITTED("Submitted to network"),
        ACCEPTED("Representment accepted"),
        REJECTED("Representment rejected"),
        PARTIALLY_ACCEPTED("Partially accepted");
        
        private final String description;
        
        RepresentmentStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum Priority {
        CRITICAL(1),  // Immediate action required
        HIGH(2),       // Response deadline approaching
        MEDIUM(3),     // Standard processing
        LOW(4);        // Low value or already accepted
        
        private final int level;
        
        Priority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    // ======================== Helper Methods ========================
    
    public boolean isHighValue() {
        return chargebackAmount != null && chargebackAmount.compareTo(new BigDecimal("1000")) > 0;
    }
    
    public boolean isUrgent() {
        return priority == Priority.CRITICAL ||
               (responseDeadline != null && responseDeadline.isBefore(LocalDateTime.now().plusDays(2)));
    }
    
    public boolean canContest() {
        return Boolean.TRUE.equals(responseRequired) &&
               Boolean.TRUE.equals(representmentEligible) &&
               responseDeadline != null &&
               responseDeadline.isAfter(LocalDateTime.now());
    }
    
    public boolean isFinalStage() {
        return chargebackStage == ChargebackStage.ARBITRATION ||
               chargebackStage == ChargebackStage.COMPLIANCE;
    }
    
    public BigDecimal calculateTotalLoss() {
        BigDecimal loss = chargebackAmount.add(chargebackFee);
        if (additionalFees != null) {
            loss = loss.add(additionalFees);
        }
        return loss;
    }
}