/**
 * Security Service for SMS/USSD Banking
 * Handles PIN authentication, rate limiting, and security monitoring
 */
package com.waqiti.smsbanking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final CoreBankingService coreBankingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${sms.banking.rate.limit.requests:10}")
    private int rateLimitRequests;
    
    @Value("${sms.banking.rate.limit.window.minutes:60}")
    private int rateLimitWindowMinutes;
    
    @Value("${sms.banking.pin.max.attempts:3}")
    private int maxPinAttempts;
    
    @Value("${sms.banking.pin.lockout.minutes:30}")
    private int pinLockoutMinutes;
    
    private static final String RATE_LIMIT_KEY_PREFIX = "sms_rate_limit:";
    private static final String PIN_ATTEMPTS_KEY_PREFIX = "pin_attempts:";
    private static final String PIN_LOCKOUT_KEY_PREFIX = "pin_lockout:";
    private static final String TRANSACTION_LOG_KEY_PREFIX = "sms_transaction:";
    
    public boolean verifyPin(String phoneNumber, String pin) {
        try {
            // Check if account is locked
            if (isPinLocked(phoneNumber)) {
                log.warn("PIN verification attempted for locked account: {}", phoneNumber);
                return false;
            }
            
            // Get user by phone number
            UUID userId = coreBankingService.getUserIdByPhoneNumber(phoneNumber);
            if (userId == null) {
                log.warn("PIN verification attempted for non-existent account: {}", phoneNumber);
                return false;
            }
            
            // Get stored PIN hash from core banking service
            String storedPinHash = coreBankingService.getUserPinHash(userId);
            if (storedPinHash == null) {
                log.warn("No PIN found for user: {}", userId);
                return false;
            }
            
            // Verify PIN
            boolean pinValid = passwordEncoder.matches(pin, storedPinHash);
            
            if (pinValid) {
                // Reset PIN attempts on successful verification
                clearPinAttempts(phoneNumber);
                log.info("Successful PIN verification for: {}", phoneNumber);
                return true;
            } else {
                // Record failed attempt
                recordFailedPinAttempt(phoneNumber);
                log.warn("Failed PIN verification for: {}", phoneNumber);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error verifying PIN for {}: {}", phoneNumber, e.getMessage(), e);
            return false;
        }
    }
    
    public void recordFailedPinAttempt(String phoneNumber) {
        try {
            String key = PIN_ATTEMPTS_KEY_PREFIX + phoneNumber;
            String attemptsStr = (String) redisTemplate.opsForValue().get(key);
            
            int attempts = (attemptsStr != null) ? Integer.parseInt(attemptsStr) : 0;
            attempts++;
            
            // Store attempt count with expiration
            redisTemplate.opsForValue().set(key, String.valueOf(attempts), 
                Duration.ofMinutes(pinLockoutMinutes));
            
            // Lock account if max attempts reached
            if (attempts >= maxPinAttempts) {
                lockPinAccount(phoneNumber);
                log.warn("Account locked due to max PIN attempts: {}", phoneNumber);
                
                // Send security alert
                sendSecurityAlert(phoneNumber, "ACCOUNT_LOCKED", 
                    "Account locked due to multiple failed PIN attempts");
            }
            
        } catch (Exception e) {
            log.error("Error recording failed PIN attempt for {}: {}", phoneNumber, e.getMessage(), e);
        }
    }
    
    public void clearPinAttempts(String phoneNumber) {
        try {
            String key = PIN_ATTEMPTS_KEY_PREFIX + phoneNumber;
            redisTemplate.delete(key);
            
            // Also clear any lockout
            String lockoutKey = PIN_LOCKOUT_KEY_PREFIX + phoneNumber;
            redisTemplate.delete(lockoutKey);
            
        } catch (Exception e) {
            log.error("Error clearing PIN attempts for {}: {}", phoneNumber, e.getMessage(), e);
        }
    }
    
    public boolean isPinLocked(String phoneNumber) {
        try {
            String key = PIN_LOCKOUT_KEY_PREFIX + phoneNumber;
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("Error checking PIN lock status for {}: {}", phoneNumber, e.getMessage(), e);
            return false;
        }
    }
    
    private void lockPinAccount(String phoneNumber) {
        try {
            String key = PIN_LOCKOUT_KEY_PREFIX + phoneNumber;
            redisTemplate.opsForValue().set(key, "LOCKED", Duration.ofMinutes(pinLockoutMinutes));
        } catch (Exception e) {
            log.error("Error locking PIN account for {}: {}", phoneNumber, e.getMessage(), e);
        }
    }
    
    public boolean isAllowedSmsRequest(String phoneNumber) {
        try {
            String key = RATE_LIMIT_KEY_PREFIX + phoneNumber;
            String requestsStr = (String) redisTemplate.opsForValue().get(key);
            
            int requests = (requestsStr != null) ? Integer.parseInt(requestsStr) : 0;
            
            if (requests >= rateLimitRequests) {
                log.warn("Rate limit exceeded for: {}", phoneNumber);
                return false;
            }
            
            // Increment request count
            requests++;
            redisTemplate.opsForValue().set(key, String.valueOf(requests), 
                Duration.ofMinutes(rateLimitWindowMinutes));
            
            return true;
            
        } catch (Exception e) {
            log.error("Error checking rate limit for {}: {}", phoneNumber, e.getMessage(), e);
            return true; // Allow request on error to avoid blocking legitimate users
        }
    }
    
    public void logSmsTransaction(String phoneNumber, String operation, String status) {
        try {
            String key = TRANSACTION_LOG_KEY_PREFIX + UUID.randomUUID().toString();
            
            SmsTransactionLog log = SmsTransactionLog.builder()
                .phoneNumber(phoneNumber)
                .operation(operation)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
            
            // Store for 30 days
            redisTemplate.opsForValue().set(key, log, Duration.ofDays(30));
            
            // Also log to application logs
            this.log.info("SMS Transaction - Phone: {}, Operation: {}, Status: {}", 
                phoneNumber, operation, status);
            
        } catch (Exception e) {
            log.error("Error logging SMS transaction: {}", e.getMessage(), e);
        }
    }
    
    public SecurityMetrics getSecurityMetrics(String phoneNumber) {
        try {
            // Get current PIN attempts
            String attemptsKey = PIN_ATTEMPTS_KEY_PREFIX + phoneNumber;
            String attemptsStr = (String) redisTemplate.opsForValue().get(attemptsKey);
            int pinAttempts = (attemptsStr != null) ? Integer.parseInt(attemptsStr) : 0;
            
            // Get current request count
            String rateLimitKey = RATE_LIMIT_KEY_PREFIX + phoneNumber;
            String requestsStr = (String) redisTemplate.opsForValue().get(rateLimitKey);
            int requestCount = (requestsStr != null) ? Integer.parseInt(requestsStr) : 0;
            
            // Check lock status
            boolean isLocked = isPinLocked(phoneNumber);
            
            return SecurityMetrics.builder()
                .phoneNumber(phoneNumber)
                .pinAttempts(pinAttempts)
                .requestCount(requestCount)
                .isLocked(isLocked)
                .rateLimitRemaining(rateLimitRequests - requestCount)
                .maxPinAttempts(maxPinAttempts)
                .pinAttemptsRemaining(maxPinAttempts - pinAttempts)
                .build();
            
        } catch (Exception e) {
            log.error("Error getting security metrics for {}: {}", phoneNumber, e.getMessage(), e);
            return SecurityMetrics.builder()
                .phoneNumber(phoneNumber)
                .pinAttempts(0)
                .requestCount(0)
                .isLocked(false)
                .rateLimitRemaining(rateLimitRequests)
                .maxPinAttempts(maxPinAttempts)
                .pinAttemptsRemaining(maxPinAttempts)
                .build();
        }
    }
    
    public boolean validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        // Remove any spaces or special characters
        String cleanPhone = phoneNumber.replaceAll("[^0-9+]", "");
        
        // Check format - should be international format or local
        return cleanPhone.matches("^\\+?[1-9]\\d{1,14}$");
    }
    
    public boolean validatePin(String pin) {
        if (pin == null || pin.trim().isEmpty()) {
            return false;
        }
        
        // PIN should be exactly 4 digits
        return pin.matches("^\\d{4}$");
    }
    
    public boolean validateAmount(String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);
            return amount > 0 && amount <= 100000; // Max transaction limit
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private void sendSecurityAlert(String phoneNumber, String alertType, String message) {
        try {
            // This would integrate with notification service
            log.warn("SECURITY ALERT [{}] for {}: {}", alertType, phoneNumber, message);
            
            // Send SMS notification to user about security event
            sendSecuritySMS(phoneNumber, alertType, message);
            
            // Send alert to security monitoring system
            publishSecurityAlert(phoneNumber, alertType, message);
            
        } catch (Exception e) {
            log.error("Error sending security alert: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send security alert via SMS
     */
    private void sendSecuritySMS(String phoneNumber, String alertType, String message) {
        try {
            String smsMessage = String.format("SECURITY ALERT: %s - %s. If this wasn't you, contact support immediately.", 
                    alertType, message);
            
            // Send via SMS service
            Map<String, Object> smsRequest = Map.of(
                "phoneNumber", phoneNumber,
                "message", smsMessage,
                "priority", "HIGH",
                "type", "SECURITY_ALERT"
            );
            
            kafkaTemplate.send("sms-notifications", smsRequest);
            log.info("Security SMS sent to {} for alert type: {}", phoneNumber, alertType);
            
        } catch (Exception e) {
            log.error("Failed to send security SMS to {}: {}", phoneNumber, e.getMessage());
        }
    }
    
    /**
     * Publish security alert to monitoring system
     */
    private void publishSecurityAlert(String phoneNumber, String alertType, String message) {
        try {
            Map<String, Object> alertData = Map.of(
                "alertType", alertType,
                "phoneNumber", phoneNumber,
                "message", message,
                "timestamp", System.currentTimeMillis(),
                "service", "sms-banking",
                "severity", determineSeverity(alertType),
                "userId", getUserIdByPhone(phoneNumber)
            );
            
            // Publish to security monitoring topic
            kafkaTemplate.send("security-alerts", phoneNumber, alertData);
            
            // Store in Redis for real-time monitoring
            String alertKey = "security:alert:" + phoneNumber + ":" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(alertKey, alertData, Duration.ofHours(24));
            
            log.info("Security alert published for phone {}: {}", phoneNumber, alertType);
            
        } catch (Exception e) {
            log.error("Failed to publish security alert for {}: {}", phoneNumber, e.getMessage());
        }
    }
    
    /**
     * Determine severity level based on alert type
     */
    private String determineSeverity(String alertType) {
        switch (alertType.toUpperCase()) {
            case "MULTIPLE_FAILED_ATTEMPTS":
            case "SUSPICIOUS_ACTIVITY":
            case "ACCOUNT_LOCKED":
                return "HIGH";
            case "FAILED_LOGIN":
            case "INVALID_PIN":
                return "MEDIUM";
            default:
                return "LOW";
        }
    }
    
    /**
     * Get user ID by phone number
     */
    private String getUserIdByPhone(String phoneNumber) {
        try {
            // This would typically call user service to get user ID
            return "user-" + phoneNumber.replaceAll("[^0-9]", "");
        } catch (Exception e) {
            log.debug("Could not resolve user ID for phone {}", phoneNumber);
            return "unknown";
        }
    }
    
    public void resetAccountSecurity(String phoneNumber) {
        try {
            clearPinAttempts(phoneNumber);
            
            // Clear rate limiting
            String rateLimitKey = RATE_LIMIT_KEY_PREFIX + phoneNumber;
            redisTemplate.delete(rateLimitKey);
            
            log.info("Security reset completed for: {}", phoneNumber);
            
        } catch (Exception e) {
            log.error("Error resetting account security for {}: {}", phoneNumber, e.getMessage(), e);
        }
    }
    
    // DTOs
    public static class SmsTransactionLog {
        private String phoneNumber;
        private String operation;
        private String status;
        private LocalDateTime timestamp;
        
        public static SmsTransactionLogBuilder builder() {
            return new SmsTransactionLogBuilder();
        }
        
        public static class SmsTransactionLogBuilder {
            private String phoneNumber;
            private String operation;
            private String status;
            private LocalDateTime timestamp;
            
            public SmsTransactionLogBuilder phoneNumber(String phoneNumber) {
                this.phoneNumber = phoneNumber;
                return this;
            }
            
            public SmsTransactionLogBuilder operation(String operation) {
                this.operation = operation;
                return this;
            }
            
            public SmsTransactionLogBuilder status(String status) {
                this.status = status;
                return this;
            }
            
            public SmsTransactionLogBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public SmsTransactionLog build() {
                SmsTransactionLog log = new SmsTransactionLog();
                log.phoneNumber = this.phoneNumber;
                log.operation = this.operation;
                log.status = this.status;
                log.timestamp = this.timestamp;
                return log;
            }
        }
        
        // Getters
        public String getPhoneNumber() { return phoneNumber; }
        public String getOperation() { return operation; }
        public String getStatus() { return status; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class SecurityMetrics {
        private String phoneNumber;
        private int pinAttempts;
        private int requestCount;
        private boolean isLocked;
        private int rateLimitRemaining;
        private int maxPinAttempts;
        private int pinAttemptsRemaining;
        
        public static SecurityMetricsBuilder builder() {
            return new SecurityMetricsBuilder();
        }
        
        public static class SecurityMetricsBuilder {
            private String phoneNumber;
            private int pinAttempts;
            private int requestCount;
            private boolean isLocked;
            private int rateLimitRemaining;
            private int maxPinAttempts;
            private int pinAttemptsRemaining;
            
            public SecurityMetricsBuilder phoneNumber(String phoneNumber) {
                this.phoneNumber = phoneNumber;
                return this;
            }
            
            public SecurityMetricsBuilder pinAttempts(int pinAttempts) {
                this.pinAttempts = pinAttempts;
                return this;
            }
            
            public SecurityMetricsBuilder requestCount(int requestCount) {
                this.requestCount = requestCount;
                return this;
            }
            
            public SecurityMetricsBuilder isLocked(boolean isLocked) {
                this.isLocked = isLocked;
                return this;
            }
            
            public SecurityMetricsBuilder rateLimitRemaining(int rateLimitRemaining) {
                this.rateLimitRemaining = rateLimitRemaining;
                return this;
            }
            
            public SecurityMetricsBuilder maxPinAttempts(int maxPinAttempts) {
                this.maxPinAttempts = maxPinAttempts;
                return this;
            }
            
            public SecurityMetricsBuilder pinAttemptsRemaining(int pinAttemptsRemaining) {
                this.pinAttemptsRemaining = pinAttemptsRemaining;
                return this;
            }
            
            public SecurityMetrics build() {
                SecurityMetrics metrics = new SecurityMetrics();
                metrics.phoneNumber = this.phoneNumber;
                metrics.pinAttempts = this.pinAttempts;
                metrics.requestCount = this.requestCount;
                metrics.isLocked = this.isLocked;
                metrics.rateLimitRemaining = this.rateLimitRemaining;
                metrics.maxPinAttempts = this.maxPinAttempts;
                metrics.pinAttemptsRemaining = this.pinAttemptsRemaining;
                return metrics;
            }
        }
        
        // Getters
        public String getPhoneNumber() { return phoneNumber; }
        public int getPinAttempts() { return pinAttempts; }
        public int getRequestCount() { return requestCount; }
        public boolean isLocked() { return isLocked; }
        public int getRateLimitRemaining() { return rateLimitRemaining; }
        public int getMaxPinAttempts() { return maxPinAttempts; }
        public int getPinAttemptsRemaining() { return pinAttemptsRemaining; }
    }
}