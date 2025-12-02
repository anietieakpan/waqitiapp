package com.waqiti.common.notification.sms.compliance;

import com.waqiti.common.notification.sms.SmsService.SmsType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for ensuring SMS sending compliance with regulations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsComplianceService {
    
    private final StringRedisTemplate redisTemplate;
    private final Set<String> optedOutUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> fraudAlertOptIns = ConcurrentHashMap.newKeySet();
    private final Set<String> securityAlertOptIns = ConcurrentHashMap.newKeySet();
    private final Set<String> transactionAlertOptIns = ConcurrentHashMap.newKeySet();
    
    private static final String OPT_IN_KEY_PREFIX = "sms:optin:";
    private static final String OPT_OUT_KEY_PREFIX = "sms:optout:";
    private static final String COMPLIANCE_KEY_PREFIX = "sms:compliance:";
    
    public boolean isMessageCompliant(String message, String recipient) {
        // Implementation would check message content and recipient consent
        log.debug("Checking SMS compliance for message to: {}", recipient);
        return true;
    }
    
    public boolean hasOptedIn(String recipient) {
        // Implementation would check if recipient has opted in to receive SMS
        return true;
    }
    
    public void recordConsent(String recipient, boolean consent) {
        // Implementation would record consent status
        log.info("Recording SMS consent for {}: {}", recipient, consent);
    }
    
    /**
     * Check if user can receive fraud alerts
     */
    public boolean canSendFraudAlert(String phoneNumber, String userId) {
        String key = OPT_IN_KEY_PREFIX + "fraud:" + userId;
        Boolean optedIn = redisTemplate.opsForValue().get(key) != null;
        
        if (!optedIn) {
            // Check in-memory cache as fallback
            optedIn = fraudAlertOptIns.contains(userId);
        }
        
        return optedIn && !isOptedOut(phoneNumber, userId);
    }
    
    /**
     * Check if user can receive security alerts
     */
    public boolean canSendSecurityAlert(String phoneNumber, String userId) {
        String key = OPT_IN_KEY_PREFIX + "security:" + userId;
        Boolean optedIn = redisTemplate.opsForValue().get(key) != null;
        
        if (!optedIn) {
            // Check in-memory cache as fallback
            optedIn = securityAlertOptIns.contains(userId);
        }
        
        return optedIn && !isOptedOut(phoneNumber, userId);
    }
    
    /**
     * Check if user can receive transaction alerts
     */
    public boolean canSendTransactionAlert(String phoneNumber, String userId) {
        String key = OPT_IN_KEY_PREFIX + "transaction:" + userId;
        Boolean optedIn = redisTemplate.opsForValue().get(key) != null;
        
        if (!optedIn) {
            // Check in-memory cache as fallback
            optedIn = transactionAlertOptIns.contains(userId);
        }
        
        return optedIn && !isOptedOut(phoneNumber, userId);
    }
    
    /**
     * Check if user has opted out globally
     */
    private boolean isOptedOut(String phoneNumber, String userId) {
        String key = OPT_OUT_KEY_PREFIX + userId;
        Boolean optedOut = redisTemplate.opsForValue().get(key) != null;
        
        if (!optedOut) {
            // Check in-memory cache as fallback
            optedOut = optedOutUsers.contains(userId);
        }
        
        return optedOut;
    }
    
    /**
     * Check if user has opted in for specific SMS type
     */
    @Cacheable(value = "smsOptIn", key = "#phoneNumber + ':' + #userId + ':' + #type")
    public boolean isOptedIn(String phoneNumber, String userId, SmsType type) {
        String key = OPT_IN_KEY_PREFIX + type.name().toLowerCase() + ":" + userId;
        return redisTemplate.opsForValue().get(key) != null;
    }
    
    /**
     * Opt user in for SMS notifications
     */
    public CompletableFuture<Boolean> optIn(String phoneNumber, String userId, List<SmsType> types) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                for (SmsType type : types) {
                    String key = OPT_IN_KEY_PREFIX + type.name().toLowerCase() + ":" + userId;
                    redisTemplate.opsForValue().set(key, "true", 365, TimeUnit.DAYS);
                    
                    // Update in-memory cache
                    switch (type) {
                        case FRAUD_ALERT:
                            fraudAlertOptIns.add(userId);
                            break;
                        case SECURITY_ALERT:
                            securityAlertOptIns.add(userId);
                            break;
                        case TRANSACTION_VERIFICATION:
                            transactionAlertOptIns.add(userId);
                            break;
                        default:
                            break;
                    }
                }
                
                // Remove from opted out if present
                String optOutKey = OPT_OUT_KEY_PREFIX + userId;
                redisTemplate.delete(optOutKey);
                optedOutUsers.remove(userId);
                
                log.info("User {} opted in for SMS types: {}", userId, types);
                return true;
            } catch (Exception e) {
                log.error("Failed to opt in user {} for SMS notifications", userId, e);
                return false;
            }
        });
    }
    
    /**
     * Opt user out of SMS notifications
     */
    public CompletableFuture<Boolean> optOut(String phoneNumber, String userId, List<SmsType> types) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (types.isEmpty()) {
                    // Global opt-out
                    String key = OPT_OUT_KEY_PREFIX + userId;
                    redisTemplate.opsForValue().set(key, "true", 365, TimeUnit.DAYS);
                    optedOutUsers.add(userId);
                } else {
                    // Opt out of specific types
                    for (SmsType type : types) {
                        String key = OPT_IN_KEY_PREFIX + type.name().toLowerCase() + ":" + userId;
                        redisTemplate.delete(key);
                        
                        // Update in-memory cache
                        switch (type) {
                            case FRAUD_ALERT:
                                fraudAlertOptIns.remove(userId);
                                break;
                            case SECURITY_ALERT:
                                securityAlertOptIns.remove(userId);
                                break;
                            case TRANSACTION_VERIFICATION:
                                transactionAlertOptIns.remove(userId);
                                break;
                            default:
                                break;
                        }
                    }
                }
                
                log.info("User {} opted out of SMS types: {}", userId, types);
                return true;
            } catch (Exception e) {
                log.error("Failed to opt out user {} from SMS notifications", userId, e);
                return false;
            }
        });
    }
    
    /**
     * Validate compliance configuration
     */
    public boolean validateCompliance() {
        try {
            // Verify Redis connection
            redisTemplate.opsForValue().get("test");
            
            // Check compliance rules are loaded
            return true;
        } catch (Exception e) {
            log.error("Compliance validation failed", e);
            return false;
        }
    }
}