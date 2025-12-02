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
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Data Transfer Object for cash deposit reference numbers and barcode data.
 * Contains all information needed for customers to complete cash deposits at partner locations.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDepositReference {

    /**
     * Unique reference code for the cash deposit transaction.
     */
    @NotBlank(message = "Reference code cannot be blank")
    @Size(min = 8, max = 20, message = "Reference code must be between 8 and 20 characters")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Reference code must contain only uppercase letters and numbers")
    @JsonProperty("reference_code")
    private String referenceCode;

    /**
     * ID of the user making the deposit.
     */
    @NotNull(message = "User ID cannot be null")
    @JsonProperty("user_id")
    private String userId;

    /**
     * Amount to be deposited.
     */
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217).
     */
    @NotBlank(message = "Currency cannot be blank")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    private String currency;

    /**
     * Network provider (e.g., MoneyGram, Western Union).
     */
    @JsonProperty("network_provider")
    private String networkProvider;

    /**
     * Expiration time for the reference code.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("expiration_time")
    private LocalDateTime expirationTime;

    /**
     * ID of the specific location where deposit can be made (optional).
     */
    @JsonProperty("location_id")
    private String locationId;

    /**
     * Barcode data for scanning at partner locations.
     */
    private String barcode;

    /**
     * QR code data for mobile scanning.
     */
    @JsonProperty("qr_code")
    private String qrCode;

    /**
     * Current status of the reference code.
     */
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Fee charged for the cash deposit.
     */
    @Positive(message = "Fee must be positive")
    private BigDecimal fee;

    /**
     * Instructions for completing the deposit.
     */
    private String instructions;

    /**
     * Transaction ID for tracking purposes.
     */
    @JsonProperty("transaction_id")
    private String transactionId;

    /**
     * Created timestamp.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Partner-specific metadata.
     */
    @JsonProperty("partner_metadata")
    @Builder.Default
    private java.util.Map<String, Object> partnerMetadata = new java.util.HashMap<>();

    /**
     * Whether the reference is still valid.
     *
     * @return true if not expired and status is ACTIVE
     */
    public boolean isValid() {
        return "ACTIVE".equals(status) && 
               (expirationTime == null || expirationTime.isAfter(LocalDateTime.now()));
    }

    /**
     * Gets the time remaining until expiration.
     *
     * @return minutes until expiration, or -1 if already expired or no expiration
     */
    public long getMinutesUntilExpiration() {
        if (expirationTime == null) {
            return -1;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (expirationTime.isBefore(now)) {
            return 0;
        }
        
        return java.time.Duration.between(now, expirationTime).toMinutes();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CashDepositReference that = (CashDepositReference) o;
        return Objects.equals(referenceCode, that.referenceCode) &&
               Objects.equals(userId, that.userId) &&
               Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceCode, userId, transactionId);
    }

    @Override
    public String toString() {
        return "CashDepositReferenceDto{" +
               "referenceCode='" + referenceCode + '\'' +
               ", userId='" + userId + '\'' +
               ", amount=" + amount +
               ", currency='" + currency + '\'' +
               ", networkProvider='" + networkProvider + '\'' +
               ", status='" + status + '\'' +
               ", expirationTime=" + expirationTime +
               '}';
    }
}