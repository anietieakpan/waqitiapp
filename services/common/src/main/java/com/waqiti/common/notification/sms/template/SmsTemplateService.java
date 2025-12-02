package com.waqiti.common.notification.sms.template;

import com.waqiti.common.notification.sms.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing SMS templates and message formatting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsTemplateService {
    
    private static final Map<String, String> TEMPLATES = new ConcurrentHashMap<>();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
    
    static {
        // Initialize templates
        TEMPLATES.put("FRAUD_ALERT", "ALERT: Suspicious activity detected on your account. Transaction of {0} at {1}. If this wasn't you, reply STOP or call immediately.");
        TEMPLATES.put("OTP", "Your verification code is: {0}. This code expires in {1} minutes. Never share this code with anyone.");
        TEMPLATES.put("SECURITY_ALERT", "Security Alert: {0} from IP {1}. If this wasn't you, secure your account immediately.");
        TEMPLATES.put("TRANSACTION_VERIFICATION", "Please confirm transaction of {0} to {1}. Reply YES to confirm or NO to cancel. Reference: {2}");
        TEMPLATES.put("ACCOUNT_NOTIFICATION", "Your account {0}. {1}");
        TEMPLATES.put("PASSWORD_RESET", "Password reset requested. Code: {0}. Valid for {1} minutes. If you didn't request this, please contact support.");
    }
    
    public String formatMessage(String templateId, Map<String, Object> variables) {
        // Implementation would load template and substitute variables
        log.debug("Formatting SMS template: {} with variables: {}", templateId, variables);
        return "SMS message formatted from template: " + templateId;
    }
    
    public boolean validateTemplate(String templateId) {
        // Implementation would validate template exists and is well-formed
        return true;
    }
    
    /**
     * Build fraud alert message
     */
    public String buildFraudAlertMessage(FraudAlertSmsRequest request) {
        String template = TEMPLATES.get("FRAUD_ALERT");
        String formattedAmount = formatCurrency(request.getAmount(), request.getCurrency());
        String location = request.getMerchantName() != null ? request.getMerchantName() : "Unknown Location";
        
        return MessageFormat.format(template, formattedAmount, location);
    }
    
    /**
     * Build OTP message
     */
    public String buildOtpMessage(OtpSmsRequest request) {
        String template = TEMPLATES.get("OTP");
        return MessageFormat.format(template, request.getOtpCode(), request.getExpirationMinutes());
    }
    
    /**
     * Build security alert message
     */
    public String buildSecurityAlertMessage(SecurityAlertSmsRequest request) {
        String template = TEMPLATES.get("SECURITY_ALERT");
        String event = request.getSecurityEvent() != null ? request.getSecurityEvent() : "Security event detected";
        String ipAddress = request.getIpAddress() != null ? request.getIpAddress() : "Unknown";
        
        return MessageFormat.format(template, event, ipAddress);
    }
    
    /**
     * Build transaction verification message
     */
    public String buildTransactionVerificationMessage(TransactionVerificationSmsRequest request) {
        String template = TEMPLATES.get("TRANSACTION_VERIFICATION");
        String formattedAmount = formatCurrency(request.getAmount(), request.getCurrency());
        String recipient = request.getRecipientName() != null ? request.getRecipientName() : request.getRecipientAccount();
        
        return MessageFormat.format(template, formattedAmount, recipient, request.getTransactionId());
    }
    
    /**
     * Validate all templates are properly configured
     */
    public boolean validateTemplates() {
        try {
            // Check all required templates are present
            String[] requiredTemplates = {
                "FRAUD_ALERT", "OTP", "SECURITY_ALERT", 
                "TRANSACTION_VERIFICATION", "ACCOUNT_NOTIFICATION"
            };
            
            for (String templateId : requiredTemplates) {
                if (!TEMPLATES.containsKey(templateId)) {
                    log.error("Missing required template: {}", templateId);
                    return false;
                }
                
                String template = TEMPLATES.get(templateId);
                if (template == null || template.trim().isEmpty()) {
                    log.error("Invalid template content for: {}", templateId);
                    return false;
                }
                
                // Validate template syntax
                try {
                    MessageFormat.format(template, "test", "test", "test", "test");
                } catch (Exception e) {
                    log.error("Invalid template syntax for {}: {}", templateId, e.getMessage());
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.error("Template validation failed", e);
            return false;
        }
    }
    
    /**
     * Format currency amount
     */
    private String formatCurrency(BigDecimal amount, String currency) {
        if (amount == null) {
            return "$0.00";
        }
        
        String currencySymbol = getCurrencySymbol(currency);
        return currencySymbol + amount.setScale(2, RoundingMode.HALF_UP).toString();
    }
    
    /**
     * Get currency symbol
     */
    private String getCurrencySymbol(String currency) {
        if (currency == null) {
            return "$";
        }
        
        switch (currency.toUpperCase()) {
            case "USD": return "$";
            case "EUR": return "€";
            case "GBP": return "£";
            case "JPY": return "¥";
            case "CNY": return "¥";
            case "INR": return "₹";
            case "AUD": return "A$";
            case "CAD": return "C$";
            default: return currency + " ";
        }
    }
    
    /**
     * Load custom template
     */
    @Cacheable(value = "smsTemplates", key = "#templateId")
    public String loadTemplate(String templateId) {
        return TEMPLATES.getOrDefault(templateId, "Default SMS message");
    }
    
    /**
     * Register custom template
     */
    public void registerTemplate(String templateId, String template) {
        TEMPLATES.put(templateId, template);
        log.info("Registered SMS template: {}", templateId);
    }
}