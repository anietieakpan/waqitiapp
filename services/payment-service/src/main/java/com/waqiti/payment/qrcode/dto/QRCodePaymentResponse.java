package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for QR code payment processing
 * Contains comprehensive payment result information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response after processing a QR code payment")
public class QRCodePaymentResponse {

    @Schema(description = "QR code identifier", required = true)
    @JsonProperty("qr_code_id")
    private String qrCodeId;

    @Schema(description = "Transaction identifier", required = true)
    @JsonProperty("transaction_id")
    private String transactionId;

    @Schema(description = "Payment status", example = "COMPLETED")
    private String status;

    @Schema(description = "Final payment amount")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Payer user ID")
    @JsonProperty("payer_id")
    private String payerId;

    @Schema(description = "Recipient user ID")
    @JsonProperty("recipient_id")
    private String recipientId;

    @Schema(description = "Merchant information if applicable")
    @JsonProperty("merchant_info")
    private MerchantInfo merchantInfo;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Payment processing timestamp")
    @JsonProperty("processed_at")
    private LocalDateTime processedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Payment completion timestamp")
    @JsonProperty("completed_at")
    private LocalDateTime completedAt;

    @Schema(description = "Payment method used")
    @JsonProperty("payment_method")
    private PaymentMethod paymentMethod;

    @Schema(description = "Payment receipt information")
    @JsonProperty("receipt_info")
    private ReceiptInfo receiptInfo;

    @Schema(description = "Transaction fees breakdown")
    @JsonProperty("fees_breakdown")
    private FeesBreakdown feesBreakdown;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Loyalty points information")
    @JsonProperty("loyalty_info")
    private LoyaltyInfo loyaltyInfo;

    @Schema(description = "Split payment details if applicable")
    @JsonProperty("split_payment")
    private SplitPaymentResult splitPayment;

    @Schema(description = "Installment information if applicable")
    @JsonProperty("installment_info")
    private InstallmentInfo installmentInfo;

    @Schema(description = "Error information if payment failed")
    @JsonProperty("error_info")
    private ErrorInfo errorInfo;

    @Schema(description = "Refund information if applicable")
    @JsonProperty("refund_info")
    private RefundInfo refundInfo;

    @Schema(description = "Fraud check results")
    @JsonProperty("fraud_check")
    private FraudCheckResult fraudCheck;

    @Schema(description = "Notification status")
    @JsonProperty("notification_status")
    private NotificationStatus notificationStatus;

    /**
     * Merchant information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantInfo {
        @JsonProperty("merchant_id")
        private String merchantId;
        
        @JsonProperty("merchant_name")
        private String merchantName;
        
        @JsonProperty("store_id")
        private String storeId;
        
        @JsonProperty("terminal_id")
        private String terminalId;
    }

    /**
     * Payment method information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethod {
        private String type;
        private String brand;
        
        @JsonProperty("last_four")
        private String lastFour;
        
        @JsonProperty("expiry_month")
        private String expiryMonth;
        
        @JsonProperty("expiry_year")
        private String expiryYear;
    }

    /**
     * Receipt information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptInfo {
        @JsonProperty("receipt_number")
        private String receiptNumber;
        
        @JsonProperty("receipt_url")
        private String receiptUrl;
        
        @JsonProperty("pdf_url")
        private String pdfUrl;
        
        @JsonProperty("email_sent")
        private Boolean emailSent;
    }

    /**
     * Fees breakdown
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeesBreakdown {
        @JsonProperty("processing_fee")
        private BigDecimal processingFee;
        
        @JsonProperty("service_fee")
        private BigDecimal serviceFee;
        
        @JsonProperty("network_fee")
        private BigDecimal networkFee;
        
        @JsonProperty("total_fees")
        private BigDecimal totalFees;
    }

    /**
     * Loyalty information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoyaltyInfo {
        @JsonProperty("points_earned")
        private Integer pointsEarned;
        
        @JsonProperty("points_redeemed")
        private Integer pointsRedeemed;
        
        @JsonProperty("new_balance")
        private Integer newBalance;
        
        @JsonProperty("tier_progress")
        private TierProgress tierProgress;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TierProgress {
            @JsonProperty("current_tier")
            private String currentTier;
            
            @JsonProperty("next_tier")
            private String nextTier;
            
            @JsonProperty("points_to_next_tier")
            private Integer pointsToNextTier;
        }
    }

    // Additional nested classes for SplitPaymentResult, InstallmentInfo, etc.
    // would continue here following the same comprehensive pattern...
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String code;
        private String message;
        private String details;
        
        @JsonProperty("retry_possible")
        private Boolean retryPossible;
    }
}