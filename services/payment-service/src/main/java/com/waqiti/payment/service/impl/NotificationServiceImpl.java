/**
 * Notification Service Implementation
 * Sends notifications via events that the notification service consumes
 */
package com.waqiti.payment.service.impl;

import com.waqiti.common.event.EventPublisher;
import com.waqiti.payment.businessprofile.BusinessProfile;
import com.waqiti.payment.entity.ScheduledPayment;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    
    private final EventPublisher eventPublisher;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");
    
    @Override
    public void sendScheduledPaymentCreated(ScheduledPayment payment) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "SCHEDULED_PAYMENT_CREATED");
        notificationData.put("userId", payment.getUserId());
        notificationData.put("paymentId", payment.getId());
        notificationData.put("recipientName", payment.getRecipientName());
        notificationData.put("amount", payment.getAmount());
        notificationData.put("currency", payment.getCurrency());
        notificationData.put("frequency", payment.getRecurrencePattern().name());
        notificationData.put("nextPaymentDate", payment.getNextPaymentDate().format(DATE_FORMATTER));
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published scheduled payment created notification for payment: {}", payment.getId());
    }
    
    @Override
    public void sendScheduledPaymentPaused(ScheduledPayment payment) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "SCHEDULED_PAYMENT_PAUSED");
        notificationData.put("userId", payment.getUserId());
        notificationData.put("paymentId", payment.getId());
        notificationData.put("recipientName", payment.getRecipientName());
        notificationData.put("amount", payment.getAmount());
        notificationData.put("currency", payment.getCurrency());
        notificationData.put("reason", payment.getPauseReason());
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published scheduled payment paused notification for payment: {}", payment.getId());
    }
    
    @Override
    public void sendScheduledPaymentResumed(ScheduledPayment payment) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "SCHEDULED_PAYMENT_RESUMED");
        notificationData.put("userId", payment.getUserId());
        notificationData.put("paymentId", payment.getId());
        notificationData.put("recipientName", payment.getRecipientName());
        notificationData.put("amount", payment.getAmount());
        notificationData.put("currency", payment.getCurrency());
        notificationData.put("nextPaymentDate", payment.getNextPaymentDate().format(DATE_FORMATTER));
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published scheduled payment resumed notification for payment: {}", payment.getId());
    }
    
    @Override
    public void sendScheduledPaymentCancelled(ScheduledPayment payment) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "SCHEDULED_PAYMENT_CANCELLED");
        notificationData.put("userId", payment.getUserId());
        notificationData.put("paymentId", payment.getId());
        notificationData.put("recipientName", payment.getRecipientName());
        notificationData.put("amount", payment.getAmount());
        notificationData.put("currency", payment.getCurrency());
        notificationData.put("reason", payment.getCancellationReason());
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published scheduled payment cancelled notification for payment: {}", payment.getId());
    }
    
    @Override
    public void sendScheduledPaymentReminder(ScheduledPayment payment) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "SCHEDULED_PAYMENT_REMINDER");
        notificationData.put("userId", payment.getUserId());
        notificationData.put("paymentId", payment.getId());
        notificationData.put("recipientName", payment.getRecipientName());
        notificationData.put("amount", payment.getAmount());
        notificationData.put("currency", payment.getCurrency());
        notificationData.put("paymentDate", payment.getNextPaymentDate().format(DATE_FORMATTER));
        if (payment.getPreferredTime() != null) {
            notificationData.put("preferredTime", payment.getPreferredTime().format(TIME_FORMATTER));
        }
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published scheduled payment reminder notification for payment: {}", payment.getId());
    }
    
    @Override
    public void sendScheduledPaymentProcessed(ScheduledPayment payment, PaymentResponse paymentResponse) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "SCHEDULED_PAYMENT_PROCESSED");
        notificationData.put("userId", payment.getUserId());
        notificationData.put("scheduledPaymentId", payment.getId());
        notificationData.put("paymentId", paymentResponse.getId());
        notificationData.put("recipientName", payment.getRecipientName());
        notificationData.put("amount", payment.getAmount());
        notificationData.put("currency", payment.getCurrency());
        notificationData.put("transactionReference", paymentResponse.getTransactionReference());
        
        if (payment.getNextPaymentDate() != null) {
            notificationData.put("nextPaymentDate", payment.getNextPaymentDate().format(DATE_FORMATTER));
        }
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published scheduled payment processed notification for payment: {}", payment.getId());
    }
    
    @Override
    public void sendScheduledPaymentFailed(ScheduledPayment payment, String error) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "SCHEDULED_PAYMENT_FAILED");
        notificationData.put("userId", payment.getUserId());
        notificationData.put("paymentId", payment.getId());
        notificationData.put("recipientName", payment.getRecipientName());
        notificationData.put("amount", payment.getAmount());
        notificationData.put("currency", payment.getCurrency());
        notificationData.put("error", error);
        notificationData.put("failureCount", payment.getFailedPayments());
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published scheduled payment failed notification for payment: {}", payment.getId());
    }
    
    @Override
    public void sendBusinessWelcomeEmail(BusinessProfile profile) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "BUSINESS_WELCOME");
        notificationData.put("businessId", profile.getId());
        notificationData.put("businessName", profile.getBusinessName());
        notificationData.put("email", profile.getEmail());
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published business welcome email for: {}", profile.getBusinessName());
    }
    
    @Override
    public void sendVerificationSuccessEmail(BusinessProfile profile) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "VERIFICATION_SUCCESS");
        notificationData.put("businessId", profile.getId());
        notificationData.put("businessName", profile.getBusinessName());
        notificationData.put("email", profile.getEmail());
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published verification success email for: {}", profile.getBusinessName());
    }
    
    @Override
    public void sendVerificationFailureEmail(BusinessProfile profile, String reason) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "VERIFICATION_FAILURE");
        notificationData.put("businessId", profile.getId());
        notificationData.put("businessName", profile.getBusinessName());
        notificationData.put("email", profile.getEmail());
        notificationData.put("reason", reason);
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published verification failure email for: {}", profile.getBusinessName());
    }
    
    @Override
    public void sendSuspensionEmail(BusinessProfile profile, String reason) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "BUSINESS_SUSPENSION");
        notificationData.put("businessId", profile.getId());
        notificationData.put("businessName", profile.getBusinessName());
        notificationData.put("email", profile.getEmail());
        notificationData.put("reason", reason);
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published suspension email for: {}", profile.getBusinessName());
    }
    
    @Override
    public void sendReactivationEmail(BusinessProfile profile) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "BUSINESS_REACTIVATION");
        notificationData.put("businessId", profile.getId());
        notificationData.put("businessName", profile.getBusinessName());
        notificationData.put("email", profile.getEmail());
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published reactivation email for: {}", profile.getBusinessName());
    }
    
    public void sendPoolInvitationNotification(String userId, String poolName, String invitedBy) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "POOL_INVITATION");
        notificationData.put("userId", userId);
        notificationData.put("poolName", poolName);
        notificationData.put("invitedBy", invitedBy);
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published pool invitation notification for user: {}", userId);
    }
    
    public void sendPoolRemovalNotification(String userId, String poolName, String removedBy) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "POOL_REMOVAL");
        notificationData.put("userId", userId);
        notificationData.put("poolName", poolName);
        notificationData.put("removedBy", removedBy);
        
        eventPublisher.publishEvent("notifications", notificationData);
        log.info("Published pool removal notification for user: {}", userId);
    }
}