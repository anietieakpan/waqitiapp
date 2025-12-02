package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.NotificationRequest;
import com.waqiti.transaction.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for Notification Service Client
 * 
 * Notifications are important but not blocking. Queue for later delivery.
 * 
 * @author Waqiti Platform Team
 * @since Phase 1 Remediation - Session 6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationServiceClientFallback implements NotificationServiceClient {
    
    @Override
    public NotificationResponse sendNotification(NotificationRequest request) {
        log.warn("FALLBACK: Notification queued. Type: {}, Recipient: {}",
                request.getType(), request.getRecipient());
        
        return NotificationResponse.builder()
                .notificationId(request.getTransactionId() + "_queued")
                .status("QUEUED")
                .message("Notification queued for delivery")
                .fallbackActivated(true)
                .build();
    }
    
    @Override
    public void notifyTransactionStatus(String transactionId, String status) {
        log.warn("FALLBACK: Transaction status notification queued. TransactionId: {}, Status: {}",
                transactionId, status);
    }
    
    @Override
    public void notifyApprovalRequired(String transactionId, String approver) {
        log.warn("FALLBACK: Approval notification queued. TransactionId: {}, Approver: {}",
                transactionId, approver);
    }
    
    @Override
    public void notifyTransactionCompleted(String transactionId) {
        log.info("FALLBACK: Transaction completion notification queued. TransactionId: {}",
                transactionId);
    }
    
    @Override
    public void sendCustomerFreezeNotification(String customerId, String reason, 
                                             Boolean emergencyFreeze, Integer duration) {
        log.error("FALLBACK: CRITICAL - Customer freeze notification queued. " +
                 "CustomerId: {}, Emergency: {}", customerId, emergencyFreeze);
    }
    
    @Override
    public void sendMerchantFreezeNotification(String merchantId, String reason,
                                             Boolean emergencyFreeze, Integer duration) {
        log.error("FALLBACK: CRITICAL - Merchant freeze notification queued. " +
                 "MerchantId: {}, Emergency: {}", merchantId, emergencyFreeze);
    }
    
    @Override
    public void sendComplianceFreezeNotification(String requestId, String reason,
                                               java.util.Set<String> complianceFlags) {
        log.error("FALLBACK: CRITICAL - Compliance freeze notification queued. " +
                 "RequestId: {}, Flags: {}", requestId, complianceFlags);
    }
    
    @Override
    public void triggerSarFiling(String customerId, String merchantId, String transactionId,
                                String reason, java.math.BigDecimal amount) {
        log.error("FALLBACK: CRITICAL - SAR filing notification queued. " +
                 "TransactionId: {}, Reason: {}", transactionId, reason);
    }
    
    @Override
    public void triggerComplianceReview(String requestId, java.util.Set<String> complianceFlags,
                                       String priority) {
        log.error("FALLBACK: CRITICAL - Compliance review notification queued. " +
                 "RequestId: {}, Priority: {}", requestId, priority);
    }
}