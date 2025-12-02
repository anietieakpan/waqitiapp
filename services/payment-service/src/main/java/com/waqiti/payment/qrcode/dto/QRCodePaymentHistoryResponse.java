package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CRITICAL P0 FIX: QR Code Payment History Response DTO
 *
 * Response DTO containing payment history for a specific QR code.
 * Used for analytics, auditing, and merchant reconciliation.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing QR code payment history")
public class QRCodePaymentHistoryResponse {

    @Schema(description = "Unique identifier for the QR code", example = "QR1234567890ABCDEF", required = true)
    private String qrCodeId;

    @Schema(description = "QR code type", example = "MERCHANT_STATIC")
    private String qrCodeType;

    @Schema(description = "Owner user ID", example = "usr_123456")
    private String userId;

    @Schema(description = "Merchant ID (if applicable)", example = "mch_789012")
    private String merchantId;

    @Schema(description = "Merchant name", example = "Coffee Shop Inc.")
    private String merchantName;

    @Schema(description = "Total number of payments", example = "42", required = true)
    private Integer totalPayments;

    @Schema(description = "Successful payments count", example = "38")
    private Integer successfulPayments;

    @Schema(description = "Failed payments count", example = "4")
    private Integer failedPayments;

    @Schema(description = "Total payment volume", example = "4200.50")
    private BigDecimal totalVolume;

    @Schema(description = "Average payment amount", example = "110.54")
    private BigDecimal averageAmount;

    @Schema(description = "Minimum payment amount", example = "5.00")
    private BigDecimal minimumPayment;

    @Schema(description = "Maximum payment amount", example = "500.00")
    private BigDecimal maximumPayment;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "First payment timestamp")
    private LocalDateTime firstPaymentAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Most recent payment timestamp")
    private LocalDateTime lastPaymentAt;

    @Schema(description = "List of individual payment records")
    private List<PaymentRecord> payments;

    @Schema(description = "Pagination information")
    private PaginationInfo pagination;

    @Schema(description = "Summary statistics")
    private PaymentStatistics statistics;

    /**
     * Individual payment record
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual payment record")
    public static class PaymentRecord {

        @Schema(description = "Payment ID", example = "pay_abc123xyz")
        private String paymentId;

        @Schema(description = "Transaction ID", example = "txn_xyz789")
        private String transactionId;

        @Schema(description = "Payment amount", example = "125.50")
        private BigDecimal amount;

        @Schema(description = "Currency code", example = "USD")
        private String currency;

        @Schema(description = "Payment status", example = "COMPLETED")
        private String status;

        @Schema(description = "Payer user ID", example = "usr_654321")
        private String payerUserId;

        @Schema(description = "Payer name", example = "John Doe")
        private String payerName;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Payment timestamp")
        private LocalDateTime paymentDate;

        @Schema(description = "Payment method", example = "WALLET")
        private String paymentMethod;

        @Schema(description = "Processing time (ms)", example = "450")
        private Long processingTimeMs;

        @Schema(description = "Failure reason (if failed)")
        private String failureReason;

        @Schema(description = "Device type", example = "mobile")
        private String deviceType;

        @Schema(description = "Payment reference", example = "REF-2024-001")
        private String reference;
    }

    /**
     * Pagination information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Pagination information")
    public static class PaginationInfo {

        @Schema(description = "Current page number", example = "1")
        private Integer currentPage;

        @Schema(description = "Number of items per page", example = "20")
        private Integer pageSize;

        @Schema(description = "Total number of pages", example = "3")
        private Integer totalPages;

        @Schema(description = "Total number of items", example = "42")
        private Integer totalItems;

        @Schema(description = "Has next page", example = "true")
        private Boolean hasNext;

        @Schema(description = "Has previous page", example = "false")
        private Boolean hasPrevious;
    }

    /**
     * Payment statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Payment statistics")
    public static class PaymentStatistics {

        @Schema(description = "Success rate percentage", example = "90.48")
        private BigDecimal successRate;

        @Schema(description = "Average processing time (ms)", example = "420")
        private Long averageProcessingTime;

        @Schema(description = "Peak payment hour (24h format)", example = "14")
        private Integer peakPaymentHour;

        @Schema(description = "Most common payment method", example = "WALLET")
        private String mostCommonPaymentMethod;

        @Schema(description = "Payments today", example = "5")
        private Integer paymentsToday;

        @Schema(description = "Payments this week", example = "28")
        private Integer paymentsThisWeek;

        @Schema(description = "Payments this month", example = "42")
        private Integer paymentsThisMonth;

        @Schema(description = "Volume today", example = "550.00")
        private BigDecimal volumeToday;

        @Schema(description = "Volume this week", example = "3150.00")
        private BigDecimal volumeThisWeek;

        @Schema(description = "Volume this month", example = "4200.50")
        private BigDecimal volumeThisMonth;
    }
}
