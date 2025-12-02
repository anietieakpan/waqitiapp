package com.waqiti.payment.dto;

import com.waqiti.payment.service.ACHSettlementDateCalculator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ACH Transfer Result DTO
 *
 * Enhanced with NACHA-compliant settlement date calculation.
 * Addresses CRITICAL P0 issue identified in forensic audit (October 28, 2025).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ACHTransferResult {

    private UUID transferId;

    private String status;

    private String message;

    private String failureReason;

    private String transactionId;

    private String trackingNumber;

    private LocalDateTime processedAt;

    private boolean sameDayACH;

    // Pre-calculated settlement date (set during transfer processing)
    private LocalDateTime estimatedSettlementDate;

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    /**
     * Get estimated settlement date for this ACH transfer.
     *
     * NACHA-COMPLIANT IMPLEMENTATION:
     * - Standard ACH: T+2 business days
     * - Same-Day ACH: Same business day if before cutoff
     * - Excludes weekends and federal banking holidays
     *
     * @return estimated settlement date/time
     */
    public LocalDateTime getEstimatedSettlementDate() {
        if (estimatedSettlementDate != null) {
            return estimatedSettlementDate;
        }

        // Fallback calculation if not pre-calculated
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }

        // Use static helper for fallback
        ACHSettlementDateCalculator calculator = new ACHSettlementDateCalculator();

        if (sameDayACH) {
            return calculator.calculateSameDayACHSettlement(processedAt);
        } else {
            return calculator.calculateStandardACHSettlement(processedAt);
        }
    }

    /**
     * Set the estimated settlement date (should be called during transfer processing).
     *
     * @param estimatedSettlementDate the calculated settlement date
     */
    public void setEstimatedSettlementDate(LocalDateTime estimatedSettlementDate) {
        this.estimatedSettlementDate = estimatedSettlementDate;
    }
}
