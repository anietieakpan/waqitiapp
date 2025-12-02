package com.waqiti.user.service;

import com.waqiti.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for sending notifications to users
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Send fraud alert notification to user
     */
    public void sendFraudAlertNotification(User user, String subject, String message, Map<String, Object> data) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", user.getId().toString());
        notification.put("email", user.getEmail());
        notification.put("phoneNumber", user.getPhoneNumber());
        notification.put("subject", subject);
        notification.put("message", message);
        notification.put("type", "FRAUD_ALERT");
        notification.put("priority", "HIGH");
        notification.put("data", data);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", determineNotificationChannels(user));
        
        // Send to notification service
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("Fraud alert notification sent for user: {}", user.getId());
    }
    
    /**
     * Send account suspension notification
     */
    public void sendAccountSuspensionNotification(User user, String reason) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", user.getId().toString());
        notification.put("email", user.getEmail());
        notification.put("phoneNumber", user.getPhoneNumber());
        notification.put("subject", "Account Suspended");
        notification.put("message", "Your account has been suspended. Reason: " + reason);
        notification.put("type", "ACCOUNT_SUSPENSION");
        notification.put("priority", "CRITICAL");
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("Account suspension notification sent for user: {}", user.getId());
    }
    
    /**
     * Send security alert notification
     */
    public void sendSecurityAlert(User user, String alertType, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", user.getId().toString());
        notification.put("email", user.getEmail());
        notification.put("phoneNumber", user.getPhoneNumber());
        notification.put("subject", "Security Alert");
        notification.put("message", message);
        notification.put("type", "SECURITY_ALERT");
        notification.put("alertType", alertType);
        notification.put("priority", "HIGH");
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", determineNotificationChannels(user));
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("Security alert sent for user: {}", user.getId());
    }
    
    /**
     * Send KYC completion notification
     */
    public void sendKycCompletionNotification(String userId, String email, String phoneNumber, String kycLevel) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("email", email);
        notification.put("phoneNumber", phoneNumber);
        notification.put("subject", "KYC Verification Completed");
        notification.put("message", "Your identity verification has been completed successfully. KYC Level: " + kycLevel);
        notification.put("type", "KYC_COMPLETION");
        notification.put("priority", "HIGH");
        notification.put("kycLevel", kycLevel);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH", "IN_APP"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("KYC completion notification sent for user: {}", userId);
    }

    /**
     * Send KYC rejection notification
     */
    public void sendKycRejectionNotification(String userId, String email, String phoneNumber, 
                                           String rejectionReason, Boolean resubmissionAllowed) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("email", email);
        notification.put("phoneNumber", phoneNumber);
        notification.put("subject", "KYC Verification Rejected");
        notification.put("message", "Your identity verification was rejected. Reason: " + rejectionReason);
        notification.put("type", "KYC_REJECTION");
        notification.put("priority", "HIGH");
        notification.put("rejectionReason", rejectionReason);
        notification.put("resubmissionAllowed", resubmissionAllowed);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH", "IN_APP"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("KYC rejection notification sent for user: {}", userId);
    }

    /**
     * Send KYC error notification
     */
    public void sendKycErrorNotification(String userId, String email, String phoneNumber, String errorMessage) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("email", email);
        notification.put("phoneNumber", phoneNumber);
        notification.put("subject", "KYC Verification Error");
        notification.put("message", "There was an error processing your identity verification: " + errorMessage);
        notification.put("type", "KYC_ERROR");
        notification.put("priority", "MEDIUM");
        notification.put("errorMessage", errorMessage);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "IN_APP"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("KYC error notification sent for user: {}", userId);
    }

    /**
     * Notify about suspicious activity
     */
    public void notifySuspiciousActivity(String userId, String sourceIp, String reason) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("subject", "Suspicious Activity Detected");
        notification.put("message", "Suspicious activity detected on your account from IP: " + sourceIp);
        notification.put("type", "SUSPICIOUS_ACTIVITY");
        notification.put("priority", "CRITICAL");
        notification.put("sourceIp", sourceIp);
        notification.put("reason", reason);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH", "IN_APP"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.warn("Suspicious activity notification sent for user: {} from IP: {}", userId, sourceIp);
    }

    /**
     * Notify about permanent lockout
     */
    public void notifyPermanentLockout(String userId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("subject", "Account Permanently Locked");
        notification.put("message", "Your account has been permanently locked due to security concerns. Please contact support.");
        notification.put("type", "PERMANENT_LOCKOUT");
        notification.put("priority", "CRITICAL");
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.error("Permanent lockout notification sent for user: {}", userId);
    }

    /**
     * Notify about extended lockout
     */
    public void notifyExtendedLockout(String userId, int extendedLockoutHours) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("subject", "Account Extended Lockout");
        notification.put("message", String.format("Your account is locked for %d hours due to multiple security violations.", extendedLockoutHours));
        notification.put("type", "EXTENDED_LOCKOUT");
        notification.put("priority", "HIGH");
        notification.put("lockoutHours", extendedLockoutHours);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.warn("Extended lockout notification sent for user: {} for {} hours", userId, extendedLockoutHours);
    }

    /**
     * Notify about temporary lockout
     */
    public void notifyTemporaryLockout(String userId, int temporaryLockoutMinutes) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("subject", "Account Temporarily Locked");
        notification.put("message", String.format("Your account is temporarily locked for %d minutes due to multiple failed attempts.", temporaryLockoutMinutes));
        notification.put("type", "TEMPORARY_LOCKOUT");
        notification.put("priority", "MEDIUM");
        notification.put("lockoutMinutes", temporaryLockoutMinutes);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "PUSH", "IN_APP"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("Temporary lockout notification sent for user: {} for {} minutes", userId, temporaryLockoutMinutes);
    }

    /**
     * Send payment retry notification
     */
    public void sendPaymentRetryNotification(String userId, String paymentId, String errorMessage, String recoveryStrategy) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("subject", "Payment Retry Scheduled");
        notification.put("message", "We're retrying your payment. Error: " + errorMessage);
        notification.put("type", "PAYMENT_RETRY");
        notification.put("priority", "MEDIUM");
        notification.put("paymentId", paymentId);
        notification.put("errorMessage", errorMessage);
        notification.put("recoveryStrategy", recoveryStrategy);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "PUSH", "IN_APP"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("Payment retry notification sent for user: {} payment: {}", userId, paymentId);
    }

    /**
     * Send payment failure notification
     */
    public void sendPaymentFailureNotification(String userId, String paymentId, 
                                             java.math.BigDecimal amount, String currency, String errorMessage) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("subject", "Payment Failed");
        notification.put("message", String.format("Your payment of %s %s failed. Error: %s", amount, currency, errorMessage));
        notification.put("type", "PAYMENT_FAILURE");
        notification.put("priority", "HIGH");
        notification.put("paymentId", paymentId);
        notification.put("amount", amount);
        notification.put("currency", currency);
        notification.put("errorMessage", errorMessage);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH", "IN_APP"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("Payment failure notification sent for user: {} payment: {}", userId, paymentId);
    }

    /**
     * Send refund completed notification
     */
    public void sendRefundCompletedNotification(String userId, String refundId, 
                                              java.math.BigDecimal amount, String currency) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("subject", "Refund Completed");
        notification.put("message", String.format("Your refund of %s %s has been processed successfully.", amount, currency));
        notification.put("type", "REFUND_COMPLETED");
        notification.put("priority", "MEDIUM");
        notification.put("refundId", refundId);
        notification.put("amount", amount);
        notification.put("currency", currency);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "PUSH", "IN_APP"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.info("Refund completed notification sent for user: {} refund: {}", userId, refundId);
    }

    /**
     * Send refund failed notification
     */
    public void sendRefundFailedNotification(String userId, String refundId, String failureReason) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", userId);
        notification.put("subject", "Refund Failed");
        notification.put("message", "Your refund request failed. Reason: " + failureReason);
        notification.put("type", "REFUND_FAILED");
        notification.put("priority", "HIGH");
        notification.put("refundId", refundId);
        notification.put("failureReason", failureReason);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "PUSH", "IN_APP"});
        
        kafkaTemplate.send("notification-requests", notification);
        
        log.warn("Refund failed notification sent for user: {} refund: {}", userId, refundId);
    }

    /**
     * Send account closure confirmation
     */
    public void sendClosureConfirmation(User user, com.waqiti.user.domain.AccountClosure closure) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", user.getId());
        notification.put("email", user.getEmail());
        notification.put("subject", "Account Closure Confirmation");
        notification.put("message", "Your account has been successfully closed. Closure ID: " + closure.getId());
        notification.put("type", "ACCOUNT_CLOSURE");
        notification.put("priority", "HIGH");
        notification.put("closureId", closure.getId());
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS"});
        
        kafkaTemplate.send("notification-requests", notification);
        log.info("Account closure confirmation sent for user: {}", user.getId());
    }
    
    /**
     * Send closure certificate
     */
    public void sendClosureCertificate(User user, com.waqiti.user.domain.AccountClosure closure) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", user.getId());
        notification.put("email", user.getEmail());
        notification.put("subject", "Account Closure Certificate");
        notification.put("message", "Your account closure certificate is attached.");
        notification.put("type", "CLOSURE_CERTIFICATE");
        notification.put("priority", "MEDIUM");
        notification.put("certificateId", closure.getClosureCertificateId());
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL"});
        
        kafkaTemplate.send("notification-requests", notification);
        log.info("Closure certificate sent for user: {}", user.getId());
    }
    
    /**
     * Send data retention notice
     */
    public void sendDataRetentionNotice(User user, com.waqiti.user.domain.AccountClosure closure) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", user.getId());
        notification.put("email", user.getEmail());
        notification.put("subject", "Data Retention Information");
        notification.put("message", "Information about your data retention policy and deletion timeline.");
        notification.put("type", "DATA_RETENTION");
        notification.put("priority", "LOW");
        notification.put("retentionPolicy", closure.getRetentionPolicy());
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL"});
        
        kafkaTemplate.send("notification-requests", notification);
        log.info("Data retention notice sent for user: {}", user.getId());
    }
    
    /**
     * Send grace period notice
     */
    public void sendGracePeriodNotice(User user, int gracePeriodDays) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", user.getId());
        notification.put("email", user.getEmail());
        notification.put("subject", "Account Closure Grace Period");
        notification.put("message", "You have " + gracePeriodDays + " days to reactivate your account if needed.");
        notification.put("type", "GRACE_PERIOD");
        notification.put("priority", "MEDIUM");
        notification.put("gracePeriodDays", gracePeriodDays);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL"});
        
        kafkaTemplate.send("notification-requests", notification);
        log.info("Grace period notice sent for user: {}", user.getId());
    }
    
    /**
     * Notify account closure team
     */
    public void notifyAccountClosureTeam(com.waqiti.user.domain.AccountClosure closure) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("subject", "Account Closure Completed");
        notification.put("message", "Account closure completed for user: " + closure.getUserId());
        notification.put("type", "TEAM_NOTIFICATION");
        notification.put("priority", "LOW");
        notification.put("closureId", closure.getId());
        notification.put("userId", closure.getUserId());
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL"});
        
        kafkaTemplate.send("notification-requests", notification);
        log.info("Team notification sent for closure: {}", closure.getId());
    }
    
    /**
     * Send security closure alert
     */
    public void sendSecurityClosureAlert(com.waqiti.user.domain.AccountClosure closure) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("subject", "SECURITY: Immediate Account Closure");
        notification.put("message", "Account immediately closed for security reasons: " + closure.getReason());
        notification.put("type", "SECURITY_CLOSURE");
        notification.put("priority", "CRITICAL");
        notification.put("closureId", closure.getId());
        notification.put("userId", closure.getUserId());
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS"});

        kafkaTemplate.send("notification-requests", notification);
        log.error("Security closure alert sent for user: {}", closure.getUserId());
    }

    /**
     * Send security alert with event data
     */
    public void sendSecurityAlert(Map<String, Object> securityEvent) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("userId", securityEvent.get("userId"));
        notification.put("subject", "Security Alert: " + securityEvent.get("eventType"));
        notification.put("message", "Security event detected on your account");
        notification.put("type", "SECURITY_ALERT");
        notification.put("priority", "CRITICAL");
        notification.put("securityEvent", securityEvent);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("channels", new String[]{"EMAIL", "SMS", "PUSH", "IN_APP"});

        kafkaTemplate.send("notification-requests", notification);
        log.warn("Security alert sent for event: {}", securityEvent.get("eventType"));
    }

    /**
     * Determine notification channels based on user preferences and urgency
     */
    private String[] determineNotificationChannels(User user) {
        // For fraud alerts, use all available channels
        return new String[]{"EMAIL", "SMS", "PUSH", "IN_APP"};
    }

    /**
     * Send account activation welcome notification
     */
    public void sendAccountActivationWelcome(String userId, String accountId, String accountType) {
        log.info("Sending account activation welcome to user: {} account: {}", userId, accountId);
        // Implementation would send welcome package
    }

    /**
     * Send account reactivation notice
     */
    public void sendAccountReactivationNotice(String userId, String accountId, java.time.LocalDateTime effectiveDate) {
        log.info("Sending account reactivation notice to user: {} account: {}", userId, accountId);
        // Implementation would send reactivation confirmation
    }

    /**
     * Send account rejection notice
     */
    public void sendAccountRejectionNotice(String userId, String accountId, String rejectionReason, String appealProcess) {
        log.info("Sending account rejection notice to user: {} account: {} reason: {}", userId, accountId, rejectionReason);
        // Implementation would send rejection details and appeal information
    }

    /**
     * Send account status change notification
     */
    public void sendAccountStatusChangeNotification(String userId, String accountId, String previousStatus,
                                                   String newStatus, String changeReason, java.time.LocalDateTime effectiveDate) {
        log.info("Sending status change notification to user: {} account: {} from {} to {}",
                userId, accountId, previousStatus, newStatus);
        // Implementation would send status change details
    }

    /**
     * Send urgent account alert
     */
    public void sendUrgentAccountAlert(String userId, String accountId, String newStatus,
                                     String changeReason, String actionRequired) {
        log.warn("Sending urgent account alert to user: {} account: {} status: {}",
                userId, accountId, newStatus);
        // Implementation would send urgent alert through multiple channels
    }

    /**
     * Notify authorized user about account changes
     */
    public void notifyAuthorizedUser(String authorizedUser, String accountId, String newStatus) {
        log.info("Notifying authorized user: {} about account: {} status: {}",
                authorizedUser, accountId, newStatus);
        // Implementation would notify secondary users
    }
}