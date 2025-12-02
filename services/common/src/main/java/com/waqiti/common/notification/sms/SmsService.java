package com.waqiti.common.notification.sms;

import com.waqiti.common.notification.sms.dto.*;
import com.waqiti.common.notification.sms.provider.SmsProviderManager;
import com.waqiti.common.notification.sms.template.SmsTemplateService;
import com.waqiti.common.notification.sms.delivery.SmsDeliveryTracker;
import com.waqiti.common.notification.sms.config.SmsConfigurationService;
import com.waqiti.common.notification.sms.rate.SmsRateLimiter;
import com.waqiti.common.notification.sms.compliance.SmsComplianceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise SMS service for fraud alerts and critical security notifications.
 * Supports multiple SMS providers, delivery tracking, rate limiting, and compliance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {
    
    private final SmsProviderManager providerManager;
    private final SmsTemplateService templateService;
    private final SmsDeliveryTracker deliveryTracker;
    private final SmsConfigurationService configService;
    private final SmsRateLimiter rateLimiter;
    private final SmsComplianceService complianceService;
    
    // Configuration constants
    private static final int DEFAULT_RETRY_ATTEMPTS = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 2000;
    private static final int MAX_SMS_LENGTH = 160;
    private static final int URGENT_TIMEOUT_MS = 15000;
    private static final int NORMAL_TIMEOUT_MS = 30000;
    
    /**
     * Send fraud alert SMS
     */
    public CompletableFuture<SmsResult> sendFraudAlert(FraudAlertSmsRequest request) {
        log.info("Sending fraud alert SMS to: {} for transaction: {}", 
            maskPhoneNumber(request.getPhoneNumber()), request.getTransactionId());
        
        try {
            // Check compliance and opt-in status
            if (!complianceService.canSendFraudAlert(request.getPhoneNumber(), request.getUserId())) {
                return CompletableFuture.completedFuture(
                    SmsResult.blocked("User has not opted in for fraud alerts"));
            }
            
            // Check rate limiting
            if (!rateLimiter.allowSend(request.getPhoneNumber(), SmsType.FRAUD_ALERT)) {
                return CompletableFuture.completedFuture(
                    SmsResult.rateLimited(request.getPhoneNumber(), "Rate limit exceeded for fraud alerts"));
            }
            
            String message = templateService.buildFraudAlertMessage(request);
            
            SmsMessage sms = SmsMessage.builder()
                    .phoneNumber(request.getPhoneNumber())
                    .message(message)
                    .type(SmsType.FRAUD_ALERT.name())
                    .priority(com.waqiti.common.notification.sms.dto.SmsPriority.URGENT)
                    .retryAttempts(DEFAULT_RETRY_ATTEMPTS)
                    .timeoutMs(URGENT_TIMEOUT_MS)
                    .userId(request.getUserId())
                    .transactionId(request.getTransactionId())
                    .build();
            
            return sendSmsWithTracking(sms);
            
        } catch (Exception e) {
            log.error("Error sending fraud alert SMS", e);
            return CompletableFuture.completedFuture(
                SmsResult.error("Failed to send fraud alert SMS: " + e.getMessage()));
        }
    }
    
    /**
     * Send OTP (One-Time Password) SMS
     */
    public CompletableFuture<SmsResult> sendOtp(OtpSmsRequest request) {
        log.info("Sending OTP SMS to: {} for verification", maskPhoneNumber(request.getPhoneNumber()));
        
        try {
            // OTP messages are generally exempt from opt-in requirements for security
            if (!rateLimiter.allowSend(request.getPhoneNumber(), SmsType.OTP)) {
                return CompletableFuture.completedFuture(
                    SmsResult.rateLimited(request.getPhoneNumber(), "Rate limit exceeded for OTP messages"));
            }
            
            String message = templateService.buildOtpMessage(request);
            
            SmsMessage sms = SmsMessage.builder()
                    .phoneNumber(request.getPhoneNumber())
                    .message(message)
                    .type(SmsType.OTP.name())
                    .priority(com.waqiti.common.notification.sms.dto.SmsPriority.HIGH)
                    .retryAttempts(DEFAULT_RETRY_ATTEMPTS)
                    .timeoutMs(URGENT_TIMEOUT_MS)
                    .userId(request.getUserId())
                    .expirationMinutes(request.getExpirationMinutes())
                    .build();
            
            return sendSmsWithTracking(sms);
            
        } catch (Exception e) {
            log.error("Error sending OTP SMS", e);
            return CompletableFuture.completedFuture(
                SmsResult.error("Failed to send OTP SMS: " + e.getMessage()));
        }
    }
    
    /**
     * Send account security alert SMS
     */
    public CompletableFuture<SmsResult> sendSecurityAlert(SecurityAlertSmsRequest request) {
        log.info("Sending security alert SMS to: {} for event: {}", 
            maskPhoneNumber(request.getPhoneNumber()), request.getSecurityEvent());
        
        try {
            if (!complianceService.canSendSecurityAlert(request.getPhoneNumber(), request.getUserId())) {
                return CompletableFuture.completedFuture(
                    SmsResult.blocked("User has not opted in for security alerts"));
            }
            
            if (!rateLimiter.allowSend(request.getPhoneNumber(), SmsType.SECURITY_ALERT)) {
                return CompletableFuture.completedFuture(
                    SmsResult.rateLimited(request.getPhoneNumber(), "Rate limit exceeded for security alerts"));
            }
            
            String message = templateService.buildSecurityAlertMessage(request);
            
            SmsMessage sms = SmsMessage.builder()
                    .phoneNumber(request.getPhoneNumber())
                    .message(message)
                    .type(SmsType.SECURITY_ALERT.name())
                    .priority(com.waqiti.common.notification.sms.dto.SmsPriority.HIGH)
                    .retryAttempts(DEFAULT_RETRY_ATTEMPTS)
                    .timeoutMs(URGENT_TIMEOUT_MS)
                    .userId(request.getUserId())
                    .build();
            
            return sendSmsWithTracking(sms);
            
        } catch (Exception e) {
            log.error("Error sending security alert SMS", e);
            return CompletableFuture.completedFuture(
                SmsResult.error("Failed to send security alert SMS: " + e.getMessage()));
        }
    }
    
    /**
     * Send transaction verification SMS
     */
    public CompletableFuture<SmsResult> sendTransactionVerification(TransactionVerificationSmsRequest request) {
        log.info("Sending transaction verification SMS to: {} for amount: {}", 
            maskPhoneNumber(request.getPhoneNumber()), request.getAmount());
        
        try {
            if (!complianceService.canSendTransactionAlert(request.getPhoneNumber(), request.getUserId())) {
                return CompletableFuture.completedFuture(
                    SmsResult.blocked("User has not opted in for transaction alerts"));
            }
            
            if (!rateLimiter.allowSend(request.getPhoneNumber(), SmsType.TRANSACTION_VERIFICATION)) {
                return CompletableFuture.completedFuture(
                    SmsResult.rateLimited(request.getPhoneNumber(), "Rate limit exceeded for transaction verification"));
            }
            
            String message = templateService.buildTransactionVerificationMessage(request);
            
            SmsMessage sms = SmsMessage.builder()
                    .phoneNumber(request.getPhoneNumber())
                    .message(message)
                    .type(SmsType.TRANSACTION_VERIFICATION.name())
                    .priority(com.waqiti.common.notification.sms.dto.SmsPriority.NORMAL)
                    .retryAttempts(DEFAULT_RETRY_ATTEMPTS)
                    .timeoutMs(NORMAL_TIMEOUT_MS)
                    .userId(request.getUserId())
                    .transactionId(request.getTransactionId())
                    .build();
            
            return sendSmsWithTracking(sms);
            
        } catch (Exception e) {
            log.error("Error sending transaction verification SMS", e);
            return CompletableFuture.completedFuture(
                SmsResult.error("Failed to send transaction verification SMS: " + e.getMessage()));
        }
    }
    
    /**
     * Send batch SMS messages
     */
    public CompletableFuture<List<SmsResult>> sendBatch(List<SmsMessage> messages) {
        log.info("Sending batch of {} SMS messages", messages.size());
        
        // Filter out messages that don't pass compliance/rate limiting
        List<CompletableFuture<SmsResult>> futures = messages.stream()
                .map(this::validateAndSend)
                .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }
    
    /**
     * Validate and send individual SMS
     */
    private CompletableFuture<SmsResult> validateAndSend(SmsMessage message) {
        // Pre-send validation
        if (!isValidPhoneNumber(message.getPhoneNumber())) {
            return CompletableFuture.completedFuture(
                SmsResult.error("Invalid phone number format"));
        }
        
        if (message.getMessage().length() > MAX_SMS_LENGTH) {
            return CompletableFuture.completedFuture(
                SmsResult.error("Message exceeds maximum length"));
        }
        
        return sendSmsWithTracking(message);
    }
    
    /**
     * Send SMS with comprehensive tracking
     */
    private CompletableFuture<SmsResult> sendSmsWithTracking(SmsMessage message) {
        String trackingId = deliveryTracker.generateTrackingId();
        message.setTrackingId(trackingId);
        
        // Record send attempt
        deliveryTracker.recordSendAttempt(trackingId, message);
        
        // Update rate limiter
        rateLimiter.recordSendAttempt(message.getPhoneNumber(), message.getType());
        
        return sendSmsInternal(message)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        deliveryTracker.recordFailure(trackingId, throwable.getMessage());
                    } else {
                        deliveryTracker.recordResult(trackingId, result);
                        
                        // Update rate limiter on successful send
                        if (result.isSuccess()) {
                            rateLimiter.recordSuccessfulSend(message.getPhoneNumber(), message.getType());
                        }
                    }
                });
    }
    
    /**
     * Internal SMS sending with provider failover
     */
    private CompletableFuture<SmsResult> sendSmsInternal(SmsMessage message) {
        return sendWithRetryAndFailover(message, 0);
    }
    
    /**
     * Send with retry and provider failover
     */
    private CompletableFuture<SmsResult> sendWithRetryAndFailover(SmsMessage message, int attempt) {
        var provider = providerManager.getPrimaryProvider();
        
        return provider.sendSms(message)
                .handle((result, throwable) -> {
                    if (throwable != null || (result != null && !result.isSuccess())) {
                        // Try failover if available and within retry limits
                        if (attempt < DEFAULT_RETRY_ATTEMPTS) {
                            log.warn("SMS send attempt {} failed, retrying...", attempt + 1);
                            
                            // Try failover provider if primary failed
                            if (attempt > 0 && providerManager.hasFailoverProvider()) {
                                provider = providerManager.getFailoverProvider();
                            }
                            
                            // Delay and retry
                            return CompletableFuture
                                    .delayedExecutor(DEFAULT_RETRY_DELAY_MS * (attempt + 1), 
                                            java.util.concurrent.TimeUnit.MILLISECONDS)
                                    .execute(() -> sendWithRetryAndFailover(message, attempt + 1));
                        } else {
                            String error = throwable != null ? throwable.getMessage() : 
                                         (result != null ? result.getErrorMessage() : "Unknown error");
                            return CompletableFuture.completedFuture(
                                SmsResult.error("All retry attempts failed: " + error));
                        }
                    }
                    
                    return CompletableFuture.completedFuture(result);
                })
                .thenCompose(future -> future instanceof CompletableFuture ? 
                           (CompletableFuture<SmsResult>) future : 
                           CompletableFuture.completedFuture((SmsResult) future));
    }
    
    /**
     * Get SMS delivery status
     */
    public SmsDeliveryStatus getDeliveryStatus(String trackingId) {
        return deliveryTracker.getStatus(trackingId);
    }
    
    /**
     * Get SMS delivery metrics
     */
    public SmsMetrics getMetrics(LocalDateTime from, LocalDateTime to) {
        return deliveryTracker.getMetrics(from, to);
    }
    
    /**
     * Check if user has opted in for SMS notifications
     */
    public boolean isOptedIn(String phoneNumber, String userId, SmsType type) {
        return complianceService.isOptedIn(phoneNumber, userId, type);
    }
    
    /**
     * Opt user in for SMS notifications
     */
    public CompletableFuture<Boolean> optIn(String phoneNumber, String userId, List<SmsType> types) {
        return complianceService.optIn(phoneNumber, userId, types);
    }
    
    /**
     * Opt user out of SMS notifications
     */
    public CompletableFuture<Boolean> optOut(String phoneNumber, String userId, List<SmsType> types) {
        return complianceService.optOut(phoneNumber, userId, types);
    }
    
    /**
     * Validate SMS configuration
     */
    public boolean validateConfiguration() {
        try {
            return providerManager.validateProviders() && 
                   templateService.validateTemplates() &&
                   configService.validateConfiguration() &&
                   complianceService.validateCompliance();
        } catch (Exception e) {
            log.error("SMS configuration validation failed", e);
            return false;
        }
    }
    
    /**
     * Get current rate limiting status for phone number
     */
    public RateLimitStatus getRateLimitStatus(String phoneNumber, SmsType type) {
        return rateLimiter.getStatus(phoneNumber, type);
    }
    
    // Utility methods
    
    private boolean isValidPhoneNumber(String phoneNumber) {
        // Basic phone number validation - in production use proper validation library
        return phoneNumber != null && 
               phoneNumber.matches("^\\+?[1-9]\\d{1,14}$") && 
               phoneNumber.length() >= 10 && 
               phoneNumber.length() <= 15;
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "*".repeat(phoneNumber.length() - 4) + phoneNumber.substring(phoneNumber.length() - 4);
    }
    
    // Supporting enums
    
    public enum SmsType {
        FRAUD_ALERT,
        SECURITY_ALERT,
        OTP,
        TRANSACTION_VERIFICATION,
        ACCOUNT_NOTIFICATION,
        MARKETING
    }
    
    // SmsPriority enum removed - using dto.SmsPriority instead
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RateLimitStatus {
        private boolean allowed;
        private int remainingAttempts;
        private LocalDateTime resetTime;
        private String reason;
        
        public boolean isBlocked() {
            return !allowed;
        }
        
        public long getSecondsUntilReset() {
            return resetTime != null ? 
                java.time.Duration.between(LocalDateTime.now(), resetTime).getSeconds() : 0;
        }
    }
}