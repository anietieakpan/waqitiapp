package com.waqiti.payment.notification.model;

import com.waqiti.payment.notification.model.NotificationResult.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Enterprise Customer Activation Notification Model
 * 
 * Comprehensive notification data for customer account activation including:
 * - Customer information and activation details
 * - Welcome messaging and onboarding guidance
 * - Account capabilities and feature highlights
 * - Security information and next steps
 * - Multi-channel delivery preferences
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CustomerActivationNotification {
    
    // Notification metadata
    private String notificationId;
    private ActivationNotificationSubject subject;
    private NotificationPriority priority;
    private NotificationChannel[] preferredChannels;
    
    // Customer details
    private String customerId;
    private String customerEmail;
    private String customerPhone;
    private String customerName;
    private String customerFirstName;
    private String customerLastName;
    private String customerPreferredLanguage;
    private String customerTimeZone;
    private String customerCountry;
    
    // Activation details
    private LocalDateTime activatedAt;
    private String activatedBy;
    private String activationMethod;
    private String activationSource;
    private String previousStatus;
    private String currentStatus;
    
    // Account capabilities
    private boolean canSendPayments;
    private boolean canReceivePayments;
    private boolean canAccessInternationalTransfers;
    private boolean canAccessCryptoFeatures;
    private boolean canAccessBusinessFeatures;
    private String dailyTransactionLimit;
    private String monthlyTransactionLimit;
    
    // Welcome content
    private String welcomeMessage;
    private String onboardingGuideUrl;
    private String supportContactInfo;
    private String termsOfServiceUrl;
    private String privacyPolicyUrl;
    private String securityGuidelinesUrl;
    
    // Notification content
    private String emailTemplate;
    private String smsTemplate;
    private String pushTemplate;
    private Map<String, Object> templateVariables;
    private String customMessage;
    
    // Marketing and engagement
    private boolean includePromotionalContent;
    private boolean subscribeToNewsletter;
    private String referralCode;
    private String promotionalOffers;
    
    // Security information
    private String securityTips;
    private boolean twoFactorAuthEnabled;
    private String recoveryInstructions;
    private boolean includeSecurityAlert;
    
    // Delivery preferences
    private boolean requireDeliveryConfirmation;
    private boolean enableRetry;
    private int maxRetryAttempts;
    private NotificationChannel fallbackChannel;
    private LocalDateTime preferredDeliveryTime;
    
    // Compliance and audit
    private String auditTrailId;
    private Map<String, Object> complianceMetadata;
    private boolean requiresRegulatorNotification;
    private String kycStatus;
    private String complianceLevel;
    
    // Integration and webhooks
    private String webhookUrl;
    private Map<String, String> webhookHeaders;
    private String externalCustomerId;
    private Map<String, Object> integrationMetadata;
    
    // Enums
    public enum ActivationNotificationSubject {
        WELCOME_ACTIVATION("Welcome to Waqiti - Account Activated!"),
        ACCOUNT_READY("Your Account is Ready to Use"),
        ACTIVATION_COMPLETE("Account Activation Complete"),
        CAPABILITIES_UNLOCKED("New Payment Capabilities Unlocked"),
        BUSINESS_ACTIVATION("Business Account Activated"),
        PREMIUM_ACTIVATION("Premium Account Features Activated");
        
        private final String displayName;
        
        ActivationNotificationSubject(String displayName) {
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
    public boolean isBusinessAccount() {
        return canAccessBusinessFeatures;
    }
    
    public boolean isPremiumAccount() {
        return canAccessCryptoFeatures && canAccessInternationalTransfers;
    }
    
    public boolean hasHighLimits() {
        return dailyTransactionLimit != null && 
               Double.parseDouble(dailyTransactionLimit.replaceAll("[^\\d.]", "")) > 10000;
    }
    
    public String getPersonalizedGreeting() {
        if (customerFirstName != null && !customerFirstName.isEmpty()) {
            return "Hello " + customerFirstName + "!";
        }
        if (customerName != null && !customerName.isEmpty()) {
            return "Hello " + customerName + "!";
        }
        return "Hello!";
    }
    
    // Static factory methods
    public static CustomerActivationNotification basicActivation(String customerId, String customerEmail,
                                                                 String customerName) {
        return CustomerActivationNotification.builder()
            .customerId(customerId)
            .customerEmail(customerEmail)
            .customerName(customerName)
            .activatedAt(LocalDateTime.now())
            .activatedBy("SYSTEM")
            .activationMethod("AUTOMATED")
            .currentStatus("ACTIVE")
            .previousStatus("PENDING")
            .subject(ActivationNotificationSubject.WELCOME_ACTIVATION)
            .priority(NotificationPriority.HIGH)
            .preferredChannels(new NotificationChannel[]{
                NotificationChannel.EMAIL
            })
            .canSendPayments(true)
            .canReceivePayments(true)
            .canAccessInternationalTransfers(false)
            .canAccessCryptoFeatures(false)
            .canAccessBusinessFeatures(false)
            .dailyTransactionLimit("$1,000")
            .monthlyTransactionLimit("$10,000")
            .emailTemplate("customer-activation-welcome")
            .welcomeMessage("Welcome to Waqiti! Your account has been successfully activated.")
            .onboardingGuideUrl("https://help.example.com/getting-started")
            .supportContactInfo("support@example.com")
            .termsOfServiceUrl("https://example.com/terms")
            .privacyPolicyUrl("https://example.com/privacy")
            .requireDeliveryConfirmation(true)
            .enableRetry(true)
            .maxRetryAttempts(3)
            .includePromotionalContent(true)
            .includeSecurityAlert(true)
            .securityTips("Enable two-factor authentication for enhanced security.")
            .build();
    }
    
    public static CustomerActivationNotification businessActivation(String customerId, String customerEmail,
                                                                    String customerName, String businessName) {
        return basicActivation(customerId, customerEmail, customerName)
            .toBuilder()
            .subject(ActivationNotificationSubject.BUSINESS_ACTIVATION)
            .canAccessBusinessFeatures(true)
            .canAccessInternationalTransfers(true)
            .dailyTransactionLimit("$50,000")
            .monthlyTransactionLimit("$500,000")
            .emailTemplate("business-activation-welcome")
            .welcomeMessage("Welcome to Waqiti Business! Your business account has been activated.")
            .templateVariables(Map.of("businessName", businessName))
            .build();
    }
    
    public static CustomerActivationNotification premiumActivation(String customerId, String customerEmail,
                                                                   String customerName) {
        return basicActivation(customerId, customerEmail, customerName)
            .toBuilder()
            .subject(ActivationNotificationSubject.PREMIUM_ACTIVATION)
            .canAccessCryptoFeatures(true)
            .canAccessInternationalTransfers(true)
            .dailyTransactionLimit("$25,000")
            .monthlyTransactionLimit("$250,000")
            .emailTemplate("premium-activation-welcome")
            .welcomeMessage("Welcome to Waqiti Premium! Enjoy enhanced features and higher limits.")
            .includePromotionalContent(true)
            .promotionalOffers("Free international transfers for your first month!")
            .build();
    }
    
    public static CustomerActivationNotification withSMS(CustomerActivationNotification base, String customerPhone) {
        return base.toBuilder()
            .customerPhone(customerPhone)
            .preferredChannels(new NotificationChannel[]{
                NotificationChannel.EMAIL, 
                NotificationChannel.SMS
            })
            .smsTemplate("customer-activation-sms")
            .build();
    }
    
    public static CustomerActivationNotification withWebhook(CustomerActivationNotification base, 
                                                             String webhookUrl, 
                                                             String externalCustomerId) {
        return base.toBuilder()
            .webhookUrl(webhookUrl)
            .externalCustomerId(externalCustomerId)
            .preferredChannels(new NotificationChannel[]{
                NotificationChannel.EMAIL, 
                NotificationChannel.WEBHOOK
            })
            .build();
    }
}