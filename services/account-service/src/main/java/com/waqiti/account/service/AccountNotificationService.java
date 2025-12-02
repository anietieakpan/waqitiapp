package com.waqiti.account.service;

import com.waqiti.account.entity.Account;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.NotificationTemplate;
import com.waqiti.common.notification.NotificationType;
import com.waqiti.user.domain.User;
import com.waqiti.user.domain.NotificationPreferences;
import com.waqiti.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for sending account-related notifications
 * 
 * Handles all notification logic for account operations including
 * email, SMS, and push notifications with proper templating and async processing.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountNotificationService {
    
    private final NotificationService notificationService;
    private final UserService userService;
    
    /**
     * Send account created notification
     */
    @Async
    public void sendAccountCreatedNotification(Account account) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("accountNumber", maskAccountNumber(account.getAccountNumber()));
            params.put("accountType", account.getAccountType().toString());
            params.put("accountName", account.getAccountName());
            params.put("currency", account.getCurrency());
            params.put("createdAt", account.getCreatedAt());
            
            NotificationTemplate template = NotificationTemplate.builder()
                .templateId("ACCOUNT_CREATED")
                .subject("Account Created Successfully")
                .priority(NotificationTemplate.Priority.HIGH)
                .parameters(params)
                .build();
            
            sendNotification(account.getUserId(), template, NotificationType.EMAIL);
            sendNotification(account.getUserId(), template, NotificationType.PUSH);
            
            log.info("Account created notification sent for account: {}", account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to send account created notification for account: {}", 
                account.getAccountNumber(), e);
        }
    }
    
    /**
     * Send low balance alert
     */
    @Async
    public void sendLowBalanceAlert(Account account) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("accountNumber", maskAccountNumber(account.getAccountNumber()));
            params.put("accountName", account.getAccountName());
            params.put("currentBalance", account.getBalance());
            params.put("currency", account.getCurrency());
            params.put("threshold", new BigDecimal("100.00"));
            
            NotificationTemplate template = NotificationTemplate.builder()
                .templateId("LOW_BALANCE_ALERT")
                .subject("Low Balance Alert")
                .priority(NotificationTemplate.Priority.HIGH)
                .parameters(params)
                .build();
            
            sendNotification(account.getUserId(), template, NotificationType.EMAIL);
            sendNotification(account.getUserId(), template, NotificationType.PUSH);
            sendNotification(account.getUserId(), template, NotificationType.SMS);
            
            log.info("Low balance alert sent for account: {}", account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to send low balance alert for account: {}", 
                account.getAccountNumber(), e);
        }
    }
    
    /**
     * Send account frozen notification
     */
    @Async
    public void sendAccountFrozenNotification(Account account, String reason) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("accountNumber", maskAccountNumber(account.getAccountNumber()));
            params.put("accountName", account.getAccountName());
            params.put("reason", reason);
            params.put("frozenAt", LocalDateTime.now());
            params.put("supportEmail", "support@example.com");
            params.put("supportPhone", "+1-800-WAQITI");
            
            NotificationTemplate template = NotificationTemplate.builder()
                .templateId("ACCOUNT_FROZEN")
                .subject("Account Frozen - Immediate Action Required")
                .priority(NotificationTemplate.Priority.CRITICAL)
                .parameters(params)
                .build();
            
            sendNotification(account.getUserId(), template, NotificationType.EMAIL);
            sendNotification(account.getUserId(), template, NotificationType.SMS);
            sendNotification(account.getUserId(), template, NotificationType.PUSH);
            
            log.warn("Account frozen notification sent for account: {}", account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to send account frozen notification for account: {}", 
                account.getAccountNumber(), e);
        }
    }
    
    /**
     * Send account unfrozen notification
     */
    @Async
    public void sendAccountUnfrozenNotification(Account account) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("accountNumber", maskAccountNumber(account.getAccountNumber()));
            params.put("accountName", account.getAccountName());
            params.put("unfrozenAt", LocalDateTime.now());
            
            NotificationTemplate template = NotificationTemplate.builder()
                .templateId("ACCOUNT_UNFROZEN")
                .subject("Account Restored - Access Resumed")
                .priority(NotificationTemplate.Priority.HIGH)
                .parameters(params)
                .build();
            
            sendNotification(account.getUserId(), template, NotificationType.EMAIL);
            sendNotification(account.getUserId(), template, NotificationType.PUSH);
            
            log.info("Account unfrozen notification sent for account: {}", account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to send account unfrozen notification for account: {}", 
                account.getAccountNumber(), e);
        }
    }
    
    /**
     * Send transaction alert
     */
    @Async
    public void sendTransactionAlert(Account account, String transactionType, 
                                    BigDecimal amount, BigDecimal newBalance) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("accountNumber", maskAccountNumber(account.getAccountNumber()));
            params.put("transactionType", transactionType);
            params.put("amount", amount);
            params.put("currency", account.getCurrency());
            params.put("newBalance", newBalance);
            params.put("transactionTime", LocalDateTime.now());
            
            NotificationTemplate template = NotificationTemplate.builder()
                .templateId("TRANSACTION_ALERT")
                .subject(String.format("%s Alert - %s %s", 
                    transactionType, account.getCurrency(), amount))
                .priority(NotificationTemplate.Priority.MEDIUM)
                .parameters(params)
                .build();
            
            // Check user notification preferences
            if (shouldSendTransactionAlert(account, amount)) {
                sendNotification(account.getUserId(), template, NotificationType.EMAIL);
                sendNotification(account.getUserId(), template, NotificationType.PUSH);
                
                // Send SMS for high-value transactions
                if (amount.compareTo(new BigDecimal("1000")) > 0) {
                    sendNotification(account.getUserId(), template, NotificationType.SMS);
                }
            }
            
            log.debug("Transaction alert sent for account: {}", account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to send transaction alert for account: {}", 
                account.getAccountNumber(), e);
        }
    }
    
    /**
     * Send monthly statement notification
     */
    @Async
    public void sendMonthlyStatement(Account account, String statementUrl) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("accountNumber", maskAccountNumber(account.getAccountNumber()));
            params.put("accountName", account.getAccountName());
            params.put("statementMonth", LocalDateTime.now().minusMonths(1).getMonth());
            params.put("statementYear", LocalDateTime.now().minusMonths(1).getYear());
            params.put("statementUrl", statementUrl);
            params.put("balance", account.getBalance());
            params.put("currency", account.getCurrency());
            
            NotificationTemplate template = NotificationTemplate.builder()
                .templateId("MONTHLY_STATEMENT")
                .subject("Your Monthly Account Statement is Ready")
                .priority(NotificationTemplate.Priority.LOW)
                .parameters(params)
                .build();
            
            sendNotification(account.getUserId(), template, NotificationType.EMAIL);
            sendNotification(account.getUserId(), template, NotificationType.PUSH);
            
            log.info("Monthly statement notification sent for account: {}", account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to send monthly statement notification for account: {}", 
                account.getAccountNumber(), e);
        }
    }
    
    /**
     * Send KYC verification reminder
     */
    @Async
    public void sendKycReminderNotification(Account account) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("accountNumber", maskAccountNumber(account.getAccountNumber()));
            params.put("currentKycLevel", account.getKycLevel());
            params.put("requiredKycLevel", determineRequiredKycLevel(account));
            params.put("kycUrl", "https://app.example.com/kyc/verify");
            params.put("deadline", LocalDateTime.now().plusDays(7));
            
            NotificationTemplate template = NotificationTemplate.builder()
                .templateId("KYC_REMINDER")
                .subject("Complete Your KYC Verification")
                .priority(NotificationTemplate.Priority.HIGH)
                .parameters(params)
                .build();
            
            sendNotification(account.getUserId(), template, NotificationType.EMAIL);
            sendNotification(account.getUserId(), template, NotificationType.PUSH);
            
            log.info("KYC reminder notification sent for account: {}", account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to send KYC reminder notification for account: {}", 
                account.getAccountNumber(), e);
        }
    }
    
    /**
     * Send card expiry reminder
     */
    @Async
    public void sendCardExpiryReminder(Account account, String cardLastFour, LocalDate expiryDate) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("accountNumber", maskAccountNumber(account.getAccountNumber()));
            params.put("cardLastFour", cardLastFour);
            params.put("expiryDate", expiryDate);
            params.put("daysUntilExpiry", java.time.Period.between(LocalDate.now(), expiryDate).getDays());
            
            NotificationTemplate template = NotificationTemplate.builder()
                .templateId("CARD_EXPIRY_REMINDER")
                .subject("Your Card is Expiring Soon")
                .priority(NotificationTemplate.Priority.MEDIUM)
                .parameters(params)
                .build();
            
            sendNotification(account.getUserId(), template, NotificationType.EMAIL);
            sendNotification(account.getUserId(), template, NotificationType.PUSH);
            
            log.info("Card expiry reminder sent for account: {}", account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to send card expiry reminder for account: {}", 
                account.getAccountNumber(), e);
        }
    }
    
    /**
     * Send dormant account notification
     */
    @Async
    public void sendDormantAccountNotification(Account account) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("accountNumber", maskAccountNumber(account.getAccountNumber()));
            params.put("accountName", account.getAccountName());
            params.put("lastTransactionDate", account.getLastTransactionAt());
            params.put("daysSinceLastTransaction", 
                java.time.Duration.between(account.getLastTransactionAt(), LocalDateTime.now()).toDays());
            
            NotificationTemplate template = NotificationTemplate.builder()
                .templateId("DORMANT_ACCOUNT")
                .subject("Your Account Has Been Inactive")
                .priority(NotificationTemplate.Priority.LOW)
                .parameters(params)
                .build();
            
            sendNotification(account.getUserId(), template, NotificationType.EMAIL);
            
            log.info("Dormant account notification sent for account: {}", account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to send dormant account notification for account: {}", 
                account.getAccountNumber(), e);
        }
    }
    
    /**
     * Helper method to send notification
     */
    private void sendNotification(UUID userId, NotificationTemplate template, NotificationType type) {
        try {
            User user = userService.findById(userId);
            
            if (user != null && isNotificationEnabled(user, type)) {
                notificationService.send(user, template, type);
            }
        } catch (Exception e) {
            log.error("Failed to send {} notification to user: {}", type, userId, e);
        }
    }
    
    /**
     * Check if notification is enabled for user
     */
    private boolean isNotificationEnabled(User user, NotificationType type) {
        // Check user notification preferences
        return user.getNotificationPreferences() != null && 
               user.getNotificationPreferences().isEnabled(type);
    }
    
    /**
     * Check if transaction alert should be sent
     */
    private boolean shouldSendTransactionAlert(Account account, BigDecimal amount) {
        // Check notification preferences from account metadata
        if (account.getNotificationPreferences() != null) {
            try {
                // Parse notification preferences and check transaction alerts setting
                return true; // Simplified for now
            } catch (Exception e) {
                log.error("Failed to parse notification preferences", e);
            }
        }
        return true; // Default to sending alerts
    }
    
    /**
     * Mask account number for security
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 8) {
            return accountNumber;
        }
        return accountNumber.substring(0, 4) + "****" + 
               accountNumber.substring(accountNumber.length() - 4);
    }
    
    /**
     * Determine required KYC level based on account features
     */
    private String determineRequiredKycLevel(Account account) {
        if (Boolean.TRUE.equals(account.getInternationalEnabled())) {
            return "LEVEL_3";
        }
        if (account.getTierLevel() == Account.TierLevel.VIP || 
            account.getTierLevel() == Account.TierLevel.PLATINUM) {
            return "LEVEL_3";
        }
        if (account.getDailyTransactionLimit() != null && 
            account.getDailyTransactionLimit().compareTo(new BigDecimal("10000")) > 0) {
            return "LEVEL_2";
        }
        return "LEVEL_1";
    }
}