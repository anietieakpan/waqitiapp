package com.waqiti.common.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * CRITICAL SYSTEM SMS SERVICE for Waqiti Common Module
 * 
 * ===== ARCHITECTURAL BOUNDARIES =====
 * 
 * PURPOSE: This service handles ONLY critical, time-sensitive SMS notifications
 * for immediate alerts that require instant mobile delivery.
 * 
 * SCOPE LIMITATIONS:
 * - High-risk fraud alerts (immediate user notification)
 * - Account lockout notifications (security breach response)
 * - Transaction blocking alerts (real-time user notification)
 * - Critical system failures (on-call engineer alerts)
 * - Emergency compliance notifications (regulatory response)
 * 
 * NOT IN SCOPE (handled by notification-service):
 * - OTP/verification codes (user authentication flows)
 * - Marketing SMS campaigns
 * - Promotional messages
 * - Bulk SMS operations
 * - User preference-based messaging
 * - Non-critical business notifications
 * 
 * RELATIONSHIP WITH NOTIFICATION-SERVICE:
 * - This service: Critical security/fraud alerts requiring immediate delivery
 * - Notification-service: User authentication, marketing, bulk messaging
 * - Both services coexist for different architectural needs
 * - Clear separation of concerns based on criticality and use case
 * 
 * DESIGN PRINCIPLES:
 * - Minimal external dependencies (high reliability)
 * - Synchronous critical alerts (blocking for immediate delivery)
 * - Rate limiting to prevent SMS spam/costs
 * - Phone number validation and formatting
 * - Audit trail for security and compliance
 * 
 * USAGE EXAMPLES:
 * ✅ High-risk transaction blocked - notify user immediately
 * ✅ Account compromised - alert user of security breach
 * ✅ Critical system failure - alert on-call engineer
 * ❌ Password reset OTP - use notification-service
 * ❌ Marketing promotions - use notification-service
 * ❌ Account updates - use notification-service
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "waqiti.sms.critical-alerts.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SmsService {

    private final SmsProvider smsProvider;
    private final SmsRateLimiter rateLimiter;
    
    // Thread-safe SecureRandom for secure SMS alert ID generation
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    // Phone number validation pattern
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    
    /**
     * Send CRITICAL FRAUD ALERT SMS - Immediate synchronous delivery
     * 
     * Used when high-risk fraud is detected and user must be notified immediately
     * to confirm or deny the transaction.
     */
    public CriticalSmsResult sendCriticalFraudAlertSms(
            String phoneNumber,
            String alertId,
            String transactionAmount,
            String merchantName) {
        
        String message = String.format(
            "WAQITI FRAUD ALERT: Transaction of %s to %s was BLOCKED due to high fraud risk. " +
            "Alert ID: %s. If this was you, contact support immediately. DO NOT share this message.",
            transactionAmount, merchantName, alertId
        );
        
        return sendCriticalSms(phoneNumber, message, "FRAUD_ALERT", alertId);
    }

    /**
     * Send ACCOUNT SECURITY BREACH SMS - Immediate synchronous delivery
     * 
     * Notifies user immediately when account compromise is detected.
     */
    public CriticalSmsResult sendAccountSecurityBreachSms(
            String phoneNumber,
            String breachType,
            String userLocation) {
        
        String message = String.format(
            "WAQITI SECURITY ALERT: %s detected on your account from %s. " +
            "Your account has been secured. Contact support if this wasn't you. Time: %s",
            breachType, userLocation, LocalDateTime.now().toString().substring(0, 16)
        );
        
        return sendCriticalSms(phoneNumber, message, "SECURITY_BREACH", generateAlertId());
    }

    /**
     * Send TRANSACTION BLOCKED SMS - Immediate synchronous delivery
     * 
     * Notifies user when a transaction is blocked for security reasons.
     */
    public CriticalSmsResult sendTransactionBlockedSms(
            String phoneNumber,
            String amount,
            String currency,
            String reason) {
        
        String message = String.format(
            "WAQITI: Your transaction of %s %s was blocked (%s). " +
            "Contact support if you need assistance. Ref: %s",
            amount, currency, reason, generateAlertId()
        );
        
        return sendCriticalSms(phoneNumber, message, "TRANSACTION_BLOCKED", generateAlertId());
    }

    /**
     * Send SYSTEM CRITICAL ALERT SMS - For operations team
     * 
     * Alerts on-call engineers about critical system failures.
     */
    public CriticalSmsResult sendSystemCriticalAlertSms(
            String phoneNumber,
            String componentName,
            String severity,
            String impact) {
        
        String message = String.format(
            "WAQITI SYSTEM ALERT: %s failure (%s severity). Impact: %s. " +
            "Immediate action required. Time: %s",
            componentName, severity, impact, 
            LocalDateTime.now().toString().substring(11, 16)
        );
        
        return sendCriticalSms(phoneNumber, message, "SYSTEM_ALERT", generateAlertId());
    }

    /**
     * Send COMPLIANCE VIOLATION SMS - For compliance team
     * 
     * Immediate notification for regulatory compliance violations.
     */
    public CriticalSmsResult sendComplianceViolationSms(
            String phoneNumber,
            String violationType,
            String regulatoryBody,
            String urgency) {
        
        String message = String.format(
            "WAQITI COMPLIANCE: %s violation detected (%s). Regulatory body: %s. " +
            "Urgency: %s. Review required immediately.",
            violationType, urgency, regulatoryBody
        );
        
        return sendCriticalSms(phoneNumber, message, "COMPLIANCE_VIOLATION", generateAlertId());
    }

    /**
     * Core critical SMS sending method - Synchronous, blocking operation
     */
    private CriticalSmsResult sendCriticalSms(
            String phoneNumber,
            String message,
            String alertCategory,
            String alertId) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate phone number
            if (!isValidPhoneNumber(phoneNumber)) {
                throw new IllegalArgumentException("Invalid phone number format: " + phoneNumber);
            }
            
            // Check rate limiting
            if (!rateLimiter.isAllowed(phoneNumber, alertCategory)) {
                throw new SecurityException("Rate limit exceeded for phone number: " + phoneNumber);
            }
            
            // Validate message length (SMS limit: 160 characters for single SMS)
            if (message.length() > 160) {
                log.warn("SMS message length ({}) exceeds single SMS limit for alert: {}", 
                        message.length(), alertId);
            }
            
            // Send SMS synchronously
            SmsDeliveryResult deliveryResult = smsProvider.sendSms(phoneNumber, message);
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (deliveryResult.isSuccess()) {
                log.warn("CRITICAL SMS SENT: Category={}, Phone={}, AlertId={}, Duration={}ms, MessageId={}", 
                        alertCategory, maskPhoneNumber(phoneNumber), alertId, duration, 
                        deliveryResult.getMessageId());
                
                // Update rate limiter
                rateLimiter.recordSent(phoneNumber, alertCategory);
                
                return CriticalSmsResult.builder()
                    .success(true)
                    .phoneNumber(maskPhoneNumber(phoneNumber))
                    .alertCategory(alertCategory)
                    .alertId(alertId)
                    .sentAt(LocalDateTime.now())
                    .deliveryTimeMs(duration)
                    .messageId(deliveryResult.getMessageId())
                    .build();
            } else {
                throw new RuntimeException("SMS delivery failed: " + deliveryResult.getErrorMessage());
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            log.error("CRITICAL SMS FAILED: Category={}, Phone={}, AlertId={}, Duration={}ms, Error={}", 
                     alertCategory, maskPhoneNumber(phoneNumber), alertId, duration, e.getMessage(), e);
            
            return CriticalSmsResult.builder()
                .success(false)
                .phoneNumber(maskPhoneNumber(phoneNumber))
                .alertCategory(alertCategory)
                .alertId(alertId)
                .sentAt(LocalDateTime.now())
                .deliveryTimeMs(duration)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Send asynchronous system notification SMS
     * For non-critical but system-level notifications
     */
    public CompletableFuture<CriticalSmsResult> sendSystemNotificationSmsAsync(
            String phoneNumber,
            String message,
            String category) {
        
        return CompletableFuture.supplyAsync(() -> 
            sendCriticalSms(phoneNumber, message, category, generateAlertId()));
    }

    /**
     * Validate phone number format (E.164 standard)
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phoneNumber.trim()).matches();
    }

    /**
     * Format phone number to E.164 standard
     */
    public String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        
        // Remove all non-digit characters except +
        String cleaned = phoneNumber.replaceAll("[^+\\d]", "");
        
        // Add + if missing and not empty
        if (!cleaned.startsWith("+") && !cleaned.isEmpty()) {
            cleaned = "+" + cleaned;
        }
        
        return cleaned;
    }

    /**
     * Health check for SMS service
     */
    public boolean isSmsServiceHealthy() {
        try {
            return smsProvider.isHealthy();
        } catch (Exception e) {
            log.error("SMS service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get service statistics for monitoring
     */
    public SmsServiceStats getServiceStats() {
        return SmsServiceStats.builder()
            .serviceName("CriticalSmsService")
            .isHealthy(isSmsServiceHealthy())
            .purpose("Critical fraud, security, and system alerts via SMS")
            .scope("Fraud alerts, Security breaches, System failures, Compliance violations")
            .notInScope("OTP codes, Marketing, User preferences, Bulk messaging")
            .relationshipWithNotificationService("Complementary - handles different use cases")
            .rateLimitingEnabled(rateLimiter != null)
            .checkedAt(LocalDateTime.now())
            .build();
    }

    // Helper methods
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) {
            return "***";
        }
        
        String masked = phoneNumber.substring(0, 3) + "***" + phoneNumber.substring(phoneNumber.length() - 2);
        return masked;
    }

    /**
     * Generate cryptographically secure SMS alert ID.
     * Format: SMS{timestamp}{secure-hex}
     */
    private String generateAlertId() {
        long timestamp = System.currentTimeMillis();
        
        // Generate secure random 4 bytes and convert to hex
        byte[] randomBytes = new byte[4];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : randomBytes) {
            hexString.append(String.format("%02x", b));
        }
        
        return "SMS" + timestamp + hexString.toString();
    }

    // Result and configuration DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CriticalSmsResult {
        private boolean success;
        private String phoneNumber; // Masked for security
        private String alertCategory;
        private String alertId;
        private LocalDateTime sentAt;
        private long deliveryTimeMs;
        private String messageId;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SmsServiceStats {
        private String serviceName;
        private boolean isHealthy;
        private String purpose;
        private String scope;
        private String notInScope;
        private String relationshipWithNotificationService;
        private boolean rateLimitingEnabled;
        private LocalDateTime checkedAt;
    }

    // Provider interface for SMS delivery
    public interface SmsProvider {
        SmsDeliveryResult sendSms(String phoneNumber, String message);
        boolean isHealthy();
    }

    // Rate limiting interface
    public interface SmsRateLimiter {
        boolean isAllowed(String phoneNumber, String category);
        void recordSent(String phoneNumber, String category);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SmsDeliveryResult {
        private boolean success;
        private String messageId;
        private String errorMessage;
        private LocalDateTime deliveredAt;
    }
}