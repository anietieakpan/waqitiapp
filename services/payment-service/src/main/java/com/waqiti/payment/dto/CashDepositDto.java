package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Complete cash deposit DTO with status tracking and full transaction details.
 * Represents the entire lifecycle of a cash deposit transaction.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDepositDto {

    /**
     * Unique deposit transaction ID.
     */
    @NotBlank(message = "Deposit ID cannot be blank")
    @JsonProperty("deposit_id")
    private String depositId;

    /**
     * Reference to the original cash deposit reference.
     */
    @NotNull(message = "Reference cannot be null")
    private CashDepositReference reference;

    /**
     * Current status of the deposit.
     */
    @NotNull(message = "Status cannot be null")
    @Builder.Default
    private DepositStatus status = DepositStatus.PENDING;

    /**
     * Amount actually deposited (may differ from requested amount).
     */
    @Positive(message = "Deposited amount must be positive")
    @JsonProperty("deposited_amount")
    private BigDecimal depositedAmount;

    /**
     * Currency of the deposited amount.
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    @JsonProperty("deposited_currency")
    private String depositedCurrency;

    /**
     * Exchange rate applied (if currency conversion occurred).
     */
    @JsonProperty("exchange_rate")
    private BigDecimal exchangeRate;

    /**
     * Final amount credited to user's account.
     */
    @JsonProperty("credited_amount")
    private BigDecimal creditedAmount;

    /**
     * Fees charged for the deposit.
     */
    @JsonProperty("total_fees")
    private BigDecimal totalFees;

    /**
     * Fee breakdown by type.
     */
    @JsonProperty("fee_breakdown")
    @Builder.Default
    private java.util.List<FeeBreakdown> feeBreakdown = new java.util.ArrayList<>();

    /**
     * Location where deposit was made.
     */
    @JsonProperty("deposit_location")
    private DepositLocation depositLocation;

    /**
     * Deposit completion details.
     */
    @JsonProperty("completion_details")
    private CompletionDetails completionDetails;

    /**
     * Status history and tracking.
     */
    @JsonProperty("status_history")
    @Builder.Default
    private java.util.List<StatusChange> statusHistory = new java.util.ArrayList<>();

    /**
     * Audit trail information.
     */
    @JsonProperty("audit_info")
    private AuditInfo auditInfo;

    /**
     * Partner-specific data.
     */
    @JsonProperty("partner_data")
    @Builder.Default
    private java.util.Map<String, Object> partnerData = new java.util.HashMap<>();

    /**
     * Deposit status enumeration.
     */
    public enum DepositStatus {
        PENDING("Pending completion at partner location"),
        IN_PROGRESS("Deposit in progress"),
        COMPLETED("Deposit completed successfully"),
        FAILED("Deposit failed"),
        CANCELLED("Deposit cancelled"),
        EXPIRED("Reference code expired"),
        REFUNDED("Deposit refunded"),
        UNDER_REVIEW("Under compliance review");

        private final String description;

        DepositStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Fee breakdown item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeBreakdown {
        
        @NotBlank(message = "Fee type cannot be blank")
        @JsonProperty("fee_type")
        private String feeType;
        
        @NotNull(message = "Fee amount cannot be null")
        @Positive(message = "Fee amount must be positive")
        @JsonProperty("fee_amount")
        private BigDecimal feeAmount;
        
        private String description;
        
        @JsonProperty("fee_rate")
        private BigDecimal feeRate;
    }

    /**
     * Location where the deposit was completed.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepositLocation {
        
        @JsonProperty("location_id")
        private String locationId;
        
        @JsonProperty("location_name")
        private String locationName;
        
        private String address;
        
        private String city;
        
        private String state;
        
        @JsonProperty("zip_code")
        private String zipCode;
        
        @JsonProperty("phone_number")
        private String phoneNumber;
        
        /**
         * Network provider at this location.
         */
        @JsonProperty("network_provider")
        private String networkProvider;
        
        /**
         * Store or agent identifier.
         */
        @JsonProperty("agent_id")
        private String agentId;
    }

    /**
     * Details about deposit completion.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletionDetails {
        
        /**
         * Timestamp when deposit was completed.
         */
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("completed_at")
        private LocalDateTime completedAt;
        
        /**
         * Reference number from the partner network.
         */
        @JsonProperty("partner_reference")
        private String partnerReference;
        
        /**
         * Confirmation code provided to customer.
         */
        @JsonProperty("confirmation_code")
        private String confirmationCode;
        
        /**
         * Receipt or transaction number.
         */
        @JsonProperty("receipt_number")
        private String receiptNumber;
        
        /**
         * Additional completion notes.
         */
        private String notes;
    }

    /**
     * Status change tracking.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusChange {
        
        @JsonProperty("previous_status")
        private DepositStatus previousStatus;
        
        @JsonProperty("new_status")
        private DepositStatus newStatus;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime timestamp;
        
        private String reason;
        
        @JsonProperty("changed_by")
        private String changedBy;
    }

    /**
     * Audit information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditInfo {
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("created_at")
        private LocalDateTime createdAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("updated_at")
        private LocalDateTime updatedAt;
        
        @JsonProperty("created_by")
        private String createdBy;
        
        @JsonProperty("last_updated_by")
        private String lastUpdatedBy;
        
        /**
         * Source system or service that created this deposit.
         */
        private String source;
        
        /**
         * IP address of the originating request.
         */
        @JsonProperty("origin_ip")
        private String originIp;
        
        /**
         * User agent of the originating request.
         */
        @JsonProperty("user_agent")
        private String userAgent;
    }

    /**
     * Checks if the deposit is in a final state.
     *
     * @return true if deposit cannot be modified further
     */
    public boolean isFinalState() {
        return status == DepositStatus.COMPLETED ||
               status == DepositStatus.FAILED ||
               status == DepositStatus.CANCELLED ||
               status == DepositStatus.EXPIRED ||
               status == DepositStatus.REFUNDED;
    }

    /**
     * Checks if the deposit is successful.
     *
     * @return true if deposit completed successfully
     */
    public boolean isSuccessful() {
        return status == DepositStatus.COMPLETED;
    }

    /**
     * Gets the net amount credited to user after fees.
     *
     * @return net credited amount
     */
    public BigDecimal getNetAmount() {
        if (creditedAmount != null) {
            return creditedAmount;
        }
        
        if (depositedAmount != null && totalFees != null) {
            return depositedAmount.subtract(totalFees);
        }
        
        return BigDecimal.ZERO;
    }

    /**
     * Adds a status change to the history.
     *
     * @param newStatus the new status
     * @param reason reason for status change
     * @param changedBy who made the change
     */
    public void addStatusChange(DepositStatus newStatus, String reason, String changedBy) {
        StatusChange change = StatusChange.builder()
                .previousStatus(this.status)
                .newStatus(newStatus)
                .timestamp(LocalDateTime.now())
                .reason(reason)
                .changedBy(changedBy)
                .build();
        
        statusHistory.add(change);
        this.status = newStatus;
        
        if (auditInfo != null) {
            auditInfo.setUpdatedAt(LocalDateTime.now());
            auditInfo.setLastUpdatedBy(changedBy);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CashDepositDto that = (CashDepositDto) o;
        return Objects.equals(depositId, that.depositId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(depositId);
    }

    @Override
    public String toString() {
        return "CashDepositDto{" +
               "depositId='" + depositId + '\'' +
               ", status=" + status +
               ", depositedAmount=" + depositedAmount +
               ", creditedAmount=" + creditedAmount +
               ", totalFees=" + totalFees +
               ", completedAt=" + (completionDetails != null ? completionDetails.completedAt : null) +
               '}';
    }
}