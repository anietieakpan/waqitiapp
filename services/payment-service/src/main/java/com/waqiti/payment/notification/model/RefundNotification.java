package com.waqiti.payment.notification.model;

import com.waqiti.payment.core.model.RefundRecord;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.notification.model.NotificationResult.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Enterprise Refund Notification Model
 * 
 * Comprehensive notification data for refund operations including:
 * - Complete refund and original payment details
 * - Customer and merchant information for targeted messaging
 * - Financial breakdown with fees and net amounts
 * - Delivery preferences and channel selection
 * - Template variables for personalized content
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RefundNotification {
    
    // Notification metadata
    private String notificationId;
    private RefundNotificationSubject subject;
    private NotificationPriority priority;
    private NotificationChannel[] preferredChannels;
    
    // Refund details
    private String refundId;
    private String originalPaymentId;
    private String refundReason;
    private BigDecimal refundAmount;
    private BigDecimal refundFee;
    private BigDecimal netRefundAmount;
    private String currency;
    private LocalDateTime estimatedArrival;
    private String refundStatus;
    
    // Original payment details
    private String originalTransactionId;
    private BigDecimal originalAmount;
    private String paymentMethod;
    private LocalDateTime originalPaymentDate;
    private String merchantName;
    private String merchantId;
    
    // Customer information
    private String customerId;
    private String customerEmail;
    private String customerPhone;
    private String customerName;
    private String customerPreferredLanguage;
    private String customerTimeZone;
    
    // Merchant information
    private String merchantEmail;
    private String merchantPhone;
    private String merchantContactName;
    private String merchantWebhookUrl;
    
    // Operations team information
    private String operationsEmail;
    private String operationsSlackChannel;
    
    // Notification content
    private String emailTemplate;
    private String smsTemplate;
    private String pushTemplate;
    private Map<String, Object> templateVariables;
    private String customMessage;
    
    // Delivery preferences
    private boolean requireDeliveryConfirmation;
    private boolean enableRetry;
    private int maxRetryAttempts;
    private NotificationChannel fallbackChannel;
    
    // Compliance and audit
    private boolean isPciSensitive;
    private boolean requiresEncryption;
    private String auditTrailId;
    private Map<String, Object> complianceMetadata;
    
    // Enums
    public enum RefundNotificationSubject {
        REFUND_INITIATED("Refund Process Started"),
        REFUND_COMPLETED("Refund Successfully Processed"),
        REFUND_FAILED("Refund Processing Failed"),
        REFUND_DELAYED("Refund Processing Delayed"),
        REFUND_CANCELLED("Refund Request Cancelled"),
        PARTIAL_REFUND("Partial Refund Processed"),
        FULL_REFUND("Full Refund Processed");
        
        private final String displayName;
        
        RefundNotificationSubject(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum NotificationPriority {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        URGENT(4),
        CRITICAL(5);
        
        private final int level;
        
        NotificationPriority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    // Helper methods
    public boolean isHighValueRefund() {
        return refundAmount.compareTo(new BigDecimal("1000")) > 0;
    }
    
    public boolean isInstantRefund() {
        return estimatedArrival != null && 
               estimatedArrival.isBefore(LocalDateTime.now().plusMinutes(30));
    }
    
    public String getFormattedRefundAmount() {
        return String.format("$%.2f %s", refundAmount, currency);
    }
    
    public String getFormattedNetAmount() {
        return String.format("$%.2f %s", netRefundAmount, currency);
    }
    
    // Static factory methods
    public static RefundNotification fromRefundRecord(RefundRecord refundRecord, PaymentRequest originalPayment) {
        return RefundNotification.builder()
            .refundId(refundRecord.getRefundId())
            .originalPaymentId(originalPayment.getId())
            .refundAmount(refundRecord.getAmount())
            .refundFee(refundRecord.getFeeAmount())
            .netRefundAmount(refundRecord.getNetAmount())
            .currency(originalPayment.getCurrency())
            .originalAmount(originalPayment.getAmount())
            .paymentMethod(originalPayment.getPaymentMethod())
            .originalPaymentDate(originalPayment.getCreatedAt())
            .customerId(originalPayment.getCustomerId())
            .refundReason(refundRecord.getReason())
            .refundStatus(refundRecord.getStatus().toString())
            .subject(RefundNotificationSubject.REFUND_COMPLETED)
            .priority(NotificationPriority.NORMAL)
            .preferredChannels(new NotificationChannel[]{
                NotificationChannel.EMAIL, 
                NotificationChannel.SMS
            })
            .requireDeliveryConfirmation(true)
            .enableRetry(true)
            .maxRetryAttempts(3)
            .isPciSensitive(true)
            .requiresEncryption(true)
            .build();
    }
    
    public static RefundNotification forCustomer(RefundRecord refundRecord, PaymentRequest originalPayment,
                                                String customerEmail, String customerPhone) {
        return fromRefundRecord(refundRecord, originalPayment)
            .toBuilder()
            .customerEmail(customerEmail)
            .customerPhone(customerPhone)
            .emailTemplate("customer-refund-notification")
            .smsTemplate("customer-refund-sms")
            .priority(NotificationPriority.HIGH)
            .build();
    }
    
    public static RefundNotification forMerchant(RefundRecord refundRecord, PaymentRequest originalPayment,
                                                String merchantEmail, String webhookUrl) {
        return fromRefundRecord(refundRecord, originalPayment)
            .toBuilder()
            .merchantEmail(merchantEmail)
            .merchantWebhookUrl(webhookUrl)
            .emailTemplate("merchant-refund-notification")
            .priority(NotificationPriority.NORMAL)
            .preferredChannels(new NotificationChannel[]{
                NotificationChannel.EMAIL, 
                NotificationChannel.WEBHOOK
            })
            .build();
    }
    
    public static RefundNotification forOperations(RefundRecord refundRecord, PaymentRequest originalPayment) {
        return fromRefundRecord(refundRecord, originalPayment)
            .toBuilder()
            .operationsEmail("payments-ops@example.com")
            .operationsSlackChannel("#payment-operations")
            .emailTemplate("operations-refund-notification")
            .priority(refundRecord.getAmount().compareTo(new BigDecimal("1000")) > 0 ? 
                     NotificationPriority.HIGH : NotificationPriority.NORMAL)
            .preferredChannels(new NotificationChannel[]{
                NotificationChannel.EMAIL, 
                NotificationChannel.SLACK
            })
            .requireDeliveryConfirmation(false)
            .build();
    }
}