package com.waqiti.reconciliation.dto;

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
 * Reconciliation event - handles financial reconciliation between internal records and external systems.
 * Critical for maintaining financial accuracy and detecting discrepancies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReconciliationEvent {
    
    // ======================== Event Metadata ========================
    @NotBlank(message = "Event ID is required")
    private String eventId;
    
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @NotBlank(message = "Source service is required")
    private String source;
    
    // ======================== Reconciliation Identification ========================
    @NotBlank(message = "Reconciliation ID is required")
    private String reconciliationId;
    
    @NotNull(message = "Reconciliation type is required")
    private ReconciliationType reconciliationType;
    
    @NotNull(message = "Reconciliation period start is required")
    private LocalDateTime periodStart;
    
    @NotNull(message = "Reconciliation period end is required")
    private LocalDateTime periodEnd;
    
    private String batchId;
    
    // ======================== Financial Summary ========================
    @NotNull(message = "Internal total is required")
    @DecimalMin(value = "0.00")
    private BigDecimal internalTotal;
    
    @NotNull(message = "External total is required")
    @DecimalMin(value = "0.00")
    private BigDecimal externalTotal;
    
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3)
    private String currency;
    
    private BigDecimal discrepancyAmount;
    
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private Double discrepancyPercentage;
    
    // ======================== Transaction Counts ========================
    @NotNull
    @Min(0)
    private Integer internalTransactionCount;
    
    @NotNull
    @Min(0)
    private Integer externalTransactionCount;
    
    @Min(0)
    private Integer matchedTransactions;
    
    @Min(0)
    private Integer unmatchedInternalTransactions;
    
    @Min(0)
    private Integer unmatchedExternalTransactions;
    
    // ======================== Reconciliation Details ========================
    @NotNull(message = "Reconciliation status is required")
    private ReconciliationStatus status;
    
    @NotNull(message = "External system is required")
    private ExternalSystem externalSystem;
    
    private String externalSystemReference;
    
    private String externalReportId;
    
    private LocalDateTime externalReportDate;
    
    // ======================== Discrepancy Details ========================
    private List<Discrepancy> discrepancies;
    
    @Min(0)
    private Integer criticalDiscrepancyCount;
    
    @Min(0)
    private Integer minorDiscrepancyCount;
    
    private BigDecimal totalDiscrepancyValue;
    
    private List<String> discrepancyCategories;
    
    // ======================== Account Information ========================
    private String merchantId;
    
    private String merchantName;
    
    private String settlementAccountId;
    
    private String bankAccountNumber;
    
    private String processorMerchantId;
    
    // ======================== Settlement Details ========================
    private String settlementBatchId;
    
    private LocalDateTime settlementDate;
    
    @DecimalMin(value = "0.00")
    private BigDecimal settlementAmount;
    
    @DecimalMin(value = "0.00")
    private BigDecimal feesDeducted;
    
    @DecimalMin(value = "0.00")
    private BigDecimal netSettlement;
    
    // ======================== Audit Trail ========================
    private String performedBy;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private Long processingTimeMs;
    
    private String approvedBy;
    
    private LocalDateTime approvedAt;
    
    // ======================== Resolution ========================
    @Builder.Default
    private Boolean requiresManualReview = false;
    
    @Builder.Default
    private Boolean autoResolved = false;
    
    private ResolutionAction resolutionAction;
    
    private String resolutionNotes;
    
    private LocalDateTime resolvedAt;
    
    private String resolvedBy;
    
    // ======================== Risk & Compliance ========================
    @Builder.Default
    private Boolean suspiciousActivity = false;
    
    private List<String> complianceFlags;
    
    private String riskLevel;
    
    @Builder.Default
    private Boolean regulatoryReporting = false;
    
    // ======================== Actions Required ========================
    @NotNull
    @Builder.Default
    private Boolean investigateDiscrepancies = true;
    
    @Builder.Default
    private Boolean adjustInternalRecords = false;
    
    @Builder.Default
    private Boolean contactExternalSystem = false;
    
    @Builder.Default
    private Boolean notifyFinance = true;
    
    private List<String> requiredActions;
    
    // ======================== Supporting Documents ========================
    private List<String> reportUrls;
    
    private Map<String, String> evidenceDocuments;
    
    private String internalReportUrl;
    
    private String externalReportUrl;
    
    // ======================== Metadata ========================
    private Map<String, Object> additionalData;
    
    private String environment;
    
    private String version;
    
    // ======================== Nested Classes ========================
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Discrepancy {
        private String discrepancyId;
        private DiscrepancyType type;
        private String transactionId;
        private String externalTransactionId;
        private BigDecimal internalAmount;
        private BigDecimal externalAmount;
        private BigDecimal difference;
        private LocalDateTime transactionDate;
        private String description;
        private DiscrepancySeverity severity;
        private String possibleCause;
        private Boolean resolved;
        private String resolution;
    }
    
    // ======================== Enums ========================
    
    public enum ReconciliationType {
        DAILY_SETTLEMENT("Daily settlement reconciliation"),
        PAYMENT_GATEWAY("Payment gateway reconciliation"),
        BANK_STATEMENT("Bank statement reconciliation"),
        MERCHANT_PAYOUT("Merchant payout reconciliation"),
        FEE_RECONCILIATION("Fee and commission reconciliation"),
        CHARGEBACK_RECONCILIATION("Chargeback reconciliation"),
        REFUND_RECONCILIATION("Refund reconciliation"),
        WALLET_BALANCE("Wallet balance reconciliation"),
        INTER_SERVICE("Inter-service reconciliation"),
        AUDIT("Audit reconciliation");
        
        private final String description;
        
        ReconciliationType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum ReconciliationStatus {
        INITIATED("Reconciliation started"),
        IN_PROGRESS("Processing reconciliation"),
        MATCHED("All records matched"),
        DISCREPANCIES_FOUND("Discrepancies identified"),
        PARTIALLY_MATCHED("Partially reconciled"),
        UNDER_INVESTIGATION("Under investigation"),
        RESOLVED("Discrepancies resolved"),
        FAILED("Reconciliation failed"),
        APPROVED("Reconciliation approved"),
        REJECTED("Reconciliation rejected");
        
        private final String description;
        
        ReconciliationStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum ExternalSystem {
        STRIPE("Stripe payment gateway"),
        PAYPAL("PayPal"),
        BANK_OF_AMERICA("Bank of America"),
        WELLS_FARGO("Wells Fargo"),
        CHASE("Chase Bank"),
        ADYEN("Adyen"),
        SQUARE("Square"),
        DWOLLA("Dwolla"),
        WISE("Wise (TransferWise)"),
        PLAID("Plaid"),
        INTERNAL("Internal system"),
        OTHER("Other system");
        
        private final String displayName;
        
        ExternalSystem(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum DiscrepancyType {
        AMOUNT_MISMATCH("Transaction amount mismatch"),
        MISSING_IN_INTERNAL("Transaction missing in internal records"),
        MISSING_IN_EXTERNAL("Transaction missing in external records"),
        DUPLICATE_TRANSACTION("Duplicate transaction detected"),
        FEE_DISCREPANCY("Fee calculation discrepancy"),
        CURRENCY_MISMATCH("Currency mismatch"),
        DATE_MISMATCH("Transaction date mismatch"),
        STATUS_MISMATCH("Transaction status mismatch"),
        REFUND_MISMATCH("Refund amount mismatch"),
        CHARGEBACK_MISMATCH("Chargeback discrepancy");
        
        private final String description;
        
        DiscrepancyType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum DiscrepancySeverity {
        CRITICAL("Requires immediate attention"),
        HIGH("Significant discrepancy"),
        MEDIUM("Moderate discrepancy"),
        LOW("Minor discrepancy"),
        INFO("Informational only");
        
        private final String description;
        
        DiscrepancySeverity(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum ResolutionAction {
        ADJUST_INTERNAL("Adjust internal records"),
        REQUEST_EXTERNAL_CORRECTION("Request correction from external system"),
        WRITE_OFF("Write off discrepancy"),
        MANUAL_INVESTIGATION("Requires manual investigation"),
        AUTOMATED_FIX("Automated correction applied"),
        PENDING_APPROVAL("Pending approval"),
        NO_ACTION_REQUIRED("No action required");
        
        private final String description;
        
        ResolutionAction(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // ======================== Helper Methods ========================
    
    public boolean hasDiscrepancies() {
        return discrepancyAmount != null && 
               discrepancyAmount.compareTo(BigDecimal.ZERO) != 0;
    }
    
    public boolean hasCriticalDiscrepancies() {
        return criticalDiscrepancyCount != null && criticalDiscrepancyCount > 0;
    }
    
    public boolean isBalanced() {
        return internalTotal.compareTo(externalTotal) == 0 &&
               internalTransactionCount.equals(externalTransactionCount);
    }
    
    public boolean requiresUrgentAttention() {
        return hasCriticalDiscrepancies() || 
               Boolean.TRUE.equals(suspiciousActivity) ||
               (discrepancyAmount != null && discrepancyAmount.compareTo(new BigDecimal("10000")) > 0);
    }
    
    public BigDecimal calculateDiscrepancy() {
        return internalTotal.subtract(externalTotal).abs();
    }
}