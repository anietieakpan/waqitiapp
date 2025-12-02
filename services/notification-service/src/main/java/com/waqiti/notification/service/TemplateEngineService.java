package com.waqiti.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.notification.model.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for rendering email templates with variables
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateEngineService {
    
    private final ObjectMapper objectMapper;
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+(?:\\.\\w+)*)\\s*\\}\\}");
    private static final Pattern CONDITIONAL_PATTERN = Pattern.compile("\\{\\{#if\\s+(\\w+)\\}\\}(.*?)\\{\\{/if\\}\\}", Pattern.DOTALL);
    private static final Pattern LOOP_PATTERN = Pattern.compile("\\{\\{#each\\s+(\\w+)\\}\\}(.*?)\\{\\{/each\\}\\}", Pattern.DOTALL);
    
    public String renderSubject(EmailTemplate template, Map<String, Object> variables, String locale) {
        return renderTemplate(template.getSubject(), variables);
    }
    
    public String renderSubject(EmailTemplate template, JsonNode variables, String locale) {
        Map<String, Object> variablesMap = convertJsonNodeToMap(variables);
        return renderTemplate(template.getSubject(), variablesMap);
    }
    
    public String renderHtml(EmailTemplate template, Map<String, Object> variables, String locale) {
        return renderTemplate(template.getHtmlContent(), variables);
    }
    
    public String renderHtml(EmailTemplate template, JsonNode variables, String locale) {
        Map<String, Object> variablesMap = convertJsonNodeToMap(variables);
        return renderTemplate(template.getHtmlContent(), variablesMap);
    }
    
    public String renderText(EmailTemplate template, Map<String, Object> variables, String locale) {
        return renderTemplate(template.getTextContent(), variables);
    }
    
    public String renderText(EmailTemplate template, JsonNode variables, String locale) {
        Map<String, Object> variablesMap = convertJsonNodeToMap(variables);
        return renderTemplate(template.getTextContent(), variablesMap);
    }
    
    public String renderTemplate(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        try {
            // First handle conditional blocks
            template = processConditionals(template, variables);
            
            // Then handle loops
            template = processLoops(template, variables);
            
            // Finally replace variables
            template = replaceVariables(template, variables);
            
            return template;
            
        } catch (Exception e) {
            log.error("Error rendering template: {}", e.getMessage(), e);
            return template; // Return original template on error
        }
    }
    
    public String personalizeContent(String content, JsonNode data) {
        Map<String, Object> variables = convertJsonNodeToMap(data);
        return renderTemplate(content, variables);
    }
    
    public String formatAlert(String alertType, String severity, JsonNode data) {
        Map<String, Object> variables = convertJsonNodeToMap(data);
        variables.put("alertType", alertType);
        variables.put("severity", severity);
        variables.put("timestamp", java.time.LocalDateTime.now().toString());
        
        String template = getAlertTemplate(alertType, severity);
        return renderTemplate(template, variables);
    }
    
    public List<EmailTemplate> loadAllTemplates() {
        return createComprehensiveTemplateSet();
    }
    
    private List<EmailTemplate> createComprehensiveTemplateSet() {
        List<EmailTemplate> templates = new ArrayList<>();
        
        // Authentication & Security Templates
        templates.add(createTemplate("welcome", "Welcome Email",
            "Welcome to {{appName}}, {{userName}}!",
            getWelcomeHtmlTemplate(),
            "Welcome {{userName}}! Thank you for joining {{appName}}. Your account is now active.",
            "transactional"));
            
        templates.add(createTemplate("two_factor_code", "Two-Factor Authentication Code",
            "Your {{appName}} verification code: {{verificationCode}}",
            getTwoFactorHtmlTemplate(),
            "Your verification code is: {{verificationCode}}. This code expires in {{expiryMinutes}} minutes.",
            "security"));
            
        templates.add(createTemplate("password_reset", "Password Reset Request",
            "Reset your {{appName}} password",
            getPasswordResetHtmlTemplate(),
            "Click this link to reset your password: {{resetLink}}. Link expires in 24 hours.",
            "security"));
            
        templates.add(createTemplate("account_locked", "Account Security Alert",
            "Your {{appName}} account has been temporarily locked",
            getAccountLockedHtmlTemplate(),
            "Your account was locked due to suspicious activity. Contact support to unlock: {{supportEmail}}",
            "security"));
            
        // Payment & Transaction Templates
        templates.add(createTemplate("payment_confirmation", "Payment Confirmed",
            "Payment Received - {{amount}} {{currency}}",
            getPaymentConfirmationHtmlTemplate(),
            "Payment Confirmed: {{amount}} {{currency}} for {{description}}. Transaction ID: {{transactionId}}",
            "transactional"));
            
        templates.add(createTemplate("payment_failed", "Payment Failed",
            "Payment unsuccessful - {{amount}} {{currency}}",
            getPaymentFailedHtmlTemplate(),
            "Your payment of {{amount}} {{currency}} failed. Reason: {{failureReason}}",
            "transactional"));
            
        templates.add(createTemplate("recurring_payment", "Recurring Payment Processed",
            "Your {{subscriptionName}} payment is complete",
            getRecurringPaymentHtmlTemplate(),
            "Your recurring payment of {{amount}} {{currency}} for {{subscriptionName}} has been processed.",
            "transactional"));
            
        // Account & Compliance Templates
        templates.add(createTemplate("kyc_required", "Identity Verification Required",
            "Complete your {{appName}} identity verification",
            getKycRequiredHtmlTemplate(),
            "Please complete your identity verification to continue using {{appName}}. Login to your account to begin.",
            "compliance"));
            
        templates.add(createTemplate("document_received", "Document Received",
            "We received your {{documentType}}",
            getDocumentReceivedHtmlTemplate(),
            "Thank you for submitting your {{documentType}}. We'll review it within {{reviewTimeframe}} business days.",
            "compliance"));
            
        // Marketing & Promotional Templates  
        templates.add(createTemplate("account_summary", "Monthly Account Summary",
            "Your {{appName}} summary for {{month}} {{year}}",
            getAccountSummaryHtmlTemplate(),
            "Here's your account activity for {{month}} {{year}}: {{totalTransactions}} transactions, {{totalVolume}} {{currency}} total volume.",
            "marketing"));
            
        templates.add(createTemplate("low_balance", "Low Balance Alert",
            "Your {{appName}} balance is running low",
            getLowBalanceHtmlTemplate(),
            "Your account balance is {{currentBalance}} {{currency}}. Consider adding funds to avoid service interruptions.",
            "alert"));
            
        // System & Operational Templates
        templates.add(createTemplate("system_maintenance", "Scheduled Maintenance",
            "{{appName}} scheduled maintenance - {{maintenanceDate}}",
            getMaintenanceHtmlTemplate(),
            "We'll be performing maintenance on {{maintenanceDate}} from {{startTime}} to {{endTime}}. Services may be unavailable.",
            "system"));
            
        templates.add(createTemplate("security_alert", "Security Alert",
            "{{alertType}} - {{appName}} Security Alert",
            getSecurityAlertHtmlTemplate(),
            "Security Alert: {{message}} occurred at {{timestamp}}. If this wasn't you, contact support immediately.",
            "security"));
            
        return templates;
    }
    
    private EmailTemplate createTemplate(String id, String name, String subject, 
                                       String htmlContent, String textContent, String category) {
        return EmailTemplate.builder()
            .id(id)
            .name(name)
            .subject(subject)
            .htmlContent(htmlContent)
            .textContent(textContent)
            .category(category)
            .active(true)
            .build();
    }
    
    private String getWelcomeHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 20px; text-align: center;">
                    <h1 style="color: white; margin: 0; font-size: 28px;">Welcome to {{appName}}!</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <h2 style="color: #333; font-size: 24px;">Hello {{userName}},</h2>
                    <p style="color: #666; font-size: 16px; line-height: 1.6;">
                        Welcome to {{appName}}! We're excited to have you join our community of users who trust us with their financial needs.
                    </p>
                    <div style="background: #f8f9fa; border-radius: 8px; padding: 20px; margin: 20px 0;">
                        <h3 style="color: #333; margin-top: 0;">Your account is now active</h3>
                        <p style="color: #666; margin-bottom: 0;">You can now access all features including:</p>
                        <ul style="color: #666;">
                            <li>Secure payments and transfers</li>
                            <li>Real-time notifications</li>
                            <li>Account management tools</li>
                            <li>24/7 customer support</li>
                        </ul>
                    </div>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="{{loginUrl}}" style="background: #667eea; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">
                            Get Started
                        </a>
                    </div>
                    <p style="color: #999; font-size: 14px;">
                        If you have any questions, our support team is here to help at {{supportEmail}}
                    </p>
                </div>
            </div>
            """;
    }
    
    private String getTwoFactorHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #f8f9fa; padding: 20px; text-align: center; border-bottom: 3px solid #28a745;">
                    <h1 style="color: #333; margin: 0;">Verification Code</h1>
                </div>
                <div style="padding: 40px 20px; text-align: center;">
                    <p style="color: #666; font-size: 16px;">Your {{appName}} verification code is:</p>
                    <div style="background: #28a745; color: white; font-size: 36px; font-weight: bold; padding: 20px; border-radius: 8px; margin: 20px 0; letter-spacing: 5px;">
                        {{verificationCode}}
                    </div>
                    <p style="color: #999; font-size: 14px;">
                        This code expires in {{expiryMinutes}} minutes. Never share this code with anyone.
                    </p>
                </div>
            </div>
            """;
    }
    
    private String getPasswordResetHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #dc3545; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">Password Reset Request</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <p style="color: #666; font-size: 16px;">
                        We received a request to reset your {{appName}} password.
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="{{resetLink}}" style="background: #dc3545; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">
                            Reset Password
                        </a>
                    </div>
                    <p style="color: #999; font-size: 14px;">
                        This link expires in 24 hours. If you didn't request this, please ignore this email.
                    </p>
                </div>
            </div>
            """;
    }
    
    private String getAccountLockedHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #ffc107; padding: 20px; text-align: center;">
                    <h1 style="color: #333; margin: 0;">⚠️ Account Security Alert</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <p style="color: #666; font-size: 16px;">
                        Your {{appName}} account has been temporarily locked due to {{lockReason}}.
                    </p>
                    <div style="background: #fff3cd; border: 1px solid #ffeaa7; padding: 20px; border-radius: 5px; margin: 20px 0;">
                        <p style="margin: 0; color: #856404;">
                            <strong>What to do next:</strong><br>
                            Contact our security team at {{supportEmail}} or call {{supportPhone}} to unlock your account.
                        </p>
                    </div>
                </div>
            </div>
            """;
    }
    
    private String getPaymentConfirmationHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #28a745; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">✅ Payment Confirmed</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <div style="background: #f8f9fa; border-radius: 8px; padding: 20px; margin: 20px 0;">
                        <h3 style="margin-top: 0; color: #333;">Transaction Details</h3>
                        <table style="width: 100%; color: #666;">
                            <tr><td><strong>Amount:</strong></td><td>{{amount}} {{currency}}</td></tr>
                            <tr><td><strong>Description:</strong></td><td>{{description}}</td></tr>
                            <tr><td><strong>Transaction ID:</strong></td><td>{{transactionId}}</td></tr>
                            <tr><td><strong>Date:</strong></td><td>{{transactionDate}}</td></tr>
                        </table>
                    </div>
                    <p style="color: #666; font-size: 16px;">
                        Your payment has been successfully processed and will appear in your account within 1-2 business days.
                    </p>
                </div>
            </div>
            """;
    }
    
    private String getPaymentFailedHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #dc3545; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">❌ Payment Failed</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <div style="background: #f8f9fa; border-radius: 8px; padding: 20px; margin: 20px 0;">
                        <h3 style="margin-top: 0; color: #333;">Failed Transaction Details</h3>
                        <table style="width: 100%; color: #666;">
                            <tr><td><strong>Amount:</strong></td><td>{{amount}} {{currency}}</td></tr>
                            <tr><td><strong>Reason:</strong></td><td>{{failureReason}}</td></tr>
                            <tr><td><strong>Date:</strong></td><td>{{attemptDate}}</td></tr>
                        </table>
                    </div>
                    <p style="color: #666; font-size: 16px;">
                        Please check your payment method and try again. If the problem persists, contact support.
                    </p>
                </div>
            </div>
            """;
    }
    
    private String getRecurringPaymentHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #17a2b8; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">🔄 Recurring Payment Processed</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <p style="color: #666; font-size: 16px;">
                        Your recurring payment for {{subscriptionName}} has been successfully processed.
                    </p>
                    <div style="background: #f8f9fa; border-radius: 8px; padding: 20px; margin: 20px 0;">
                        <table style="width: 100%; color: #666;">
                            <tr><td><strong>Subscription:</strong></td><td>{{subscriptionName}}</td></tr>
                            <tr><td><strong>Amount:</strong></td><td>{{amount}} {{currency}}</td></tr>
                            <tr><td><strong>Next Payment:</strong></td><td>{{nextPaymentDate}}</td></tr>
                        </table>
                    </div>
                </div>
            </div>
            """;
    }
    
    private String getKycRequiredHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #ffc107; padding: 20px; text-align: center;">
                    <h1 style="color: #333; margin: 0;">🔍 Identity Verification Required</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <p style="color: #666; font-size: 16px;">
                        To comply with financial regulations and ensure account security, please complete your identity verification.
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="{{verificationUrl}}" style="background: #ffc107; color: #333; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">
                            Complete Verification
                        </a>
                    </div>
                </div>
            </div>
            """;
    }
    
    private String getDocumentReceivedHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #28a745; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">📄 Document Received</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <p style="color: #666; font-size: 16px;">
                        Thank you for submitting your {{documentType}}. We have received it and will review within {{reviewTimeframe}} business days.
                    </p>
                </div>
            </div>
            """;
    }
    
    private String getAccountSummaryHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #6f42c1; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">📊 Monthly Summary</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <p style="color: #666; font-size: 16px;">Here's your account activity for {{month}} {{year}}:</p>
                    <div style="background: #f8f9fa; border-radius: 8px; padding: 20px; margin: 20px 0;">
                        <table style="width: 100%; color: #666;">
                            <tr><td><strong>Total Transactions:</strong></td><td>{{totalTransactions}}</td></tr>
                            <tr><td><strong>Total Volume:</strong></td><td>{{totalVolume}} {{currency}}</td></tr>
                            <tr><td><strong>Average Transaction:</strong></td><td>{{averageTransaction}} {{currency}}</td></tr>
                        </table>
                    </div>
                </div>
            </div>
            """;
    }
    
    private String getLowBalanceHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #fd7e14; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">⚠️ Low Balance Alert</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <p style="color: #666; font-size: 16px;">
                        Your account balance is {{currentBalance}} {{currency}}, which is below your alert threshold of {{thresholdAmount}} {{currency}}.
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="{{addFundsUrl}}" style="background: #fd7e14; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">
                            Add Funds
                        </a>
                    </div>
                </div>
            </div>
            """;
    }
    
    private String getMaintenanceHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #6c757d; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">🔧 Scheduled Maintenance</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <p style="color: #666; font-size: 16px;">
                        We will be performing scheduled maintenance on {{maintenanceDate}} from {{startTime}} to {{endTime}}.
                    </p>
                    <div style="background: #f8f9fa; border-radius: 8px; padding: 20px; margin: 20px 0;">
                        <p style="margin: 0; color: #666;">
                            During this time, some services may be temporarily unavailable. We apologize for any inconvenience.
                        </p>
                    </div>
                </div>
            </div>
            """;
    }
    
    private String getSecurityAlertHtmlTemplate() {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background: #dc3545; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">🚨 Security Alert</h1>
                </div>
                <div style="padding: 40px 20px;">
                    <div style="background: #f8d7da; border: 1px solid #f5c6cb; padding: 20px; border-radius: 5px; margin: 20px 0;">
                        <p style="margin: 0; color: #721c24;">
                            <strong>{{alertType}}</strong><br>
                            {{message}}<br>
                            <em>Time: {{timestamp}}</em>
                        </p>
                    </div>
                    <p style="color: #666; font-size: 16px;">
                        If this activity wasn't authorized by you, please contact our security team immediately at {{securityEmail}}.
                    </p>
                </div>
            </div>
            """;
    }
    
    private String processConditionals(String template, Map<String, Object> variables) {
        Matcher matcher = CONDITIONAL_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            String content = matcher.group(2);
            
            Object value = getNestedValue(variables, variable);
            boolean shouldShow = isTrue(value);
            
            if (shouldShow) {
                matcher.appendReplacement(result, content);
            } else {
                matcher.appendReplacement(result, "");
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private String processLoops(String template, Map<String, Object> variables) {
        Matcher matcher = LOOP_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            String content = matcher.group(2);
            
            Object value = getNestedValue(variables, variable);
            StringBuilder loopResult = new StringBuilder();
            
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    Map<String, Object> itemVariables = new HashMap<>(variables);
                    itemVariables.put("this", item);
                    itemVariables.put("index", i);
                    itemVariables.put("first", i == 0);
                    itemVariables.put("last", i == list.size() - 1);
                    
                    if (item instanceof Map) {
                        itemVariables.putAll((Map<String, Object>) item);
                    }
                    
                    String renderedItem = replaceVariables(content, itemVariables);
                    loopResult.append(renderedItem);
                }
            }
            
            matcher.appendReplacement(result, loopResult.toString());
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private String replaceVariables(String template, Map<String, Object> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            Object value = getNestedValue(variables, variable);
            String replacement = value != null ? value.toString() : "";
            
            // Escape special regex characters in replacement
            replacement = replacement.replace("$", "\\$").replace("\\", "\\\\");
            
            matcher.appendReplacement(result, replacement);
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private Object getNestedValue(Map<String, Object> variables, String key) {
        if (!key.contains(".")) {
            return variables.get(key);
        }
        
        String[] parts = key.split("\\.");
        Object current = variables;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    private boolean isTrue(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return !((String) value).isEmpty();
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof List) return !((List<?>) value).isEmpty();
        if (value instanceof Map) return !((Map<?, ?>) value).isEmpty();
        return true;
    }
    
    private Map<String, Object> convertJsonNodeToMap(JsonNode node) {
        if (node == null) return new HashMap<>();
        
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            log.error("Failed to convert JsonNode to Map: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    private String getAlertTemplate(String alertType, String severity) {
        switch (severity.toLowerCase()) {
            case "high":
            case "critical":
                return "<div style='color: red; font-weight: bold;'>🚨 CRITICAL ALERT</div>" +
                       "<h3>{{alertType}} Alert</h3>" +
                       "<p>{{message}}</p>" +
                       "<p><strong>Severity:</strong> {{severity}}</p>" +
                       "<p><strong>Time:</strong> {{timestamp}}</p>";
                       
            case "medium":
            case "warning":
                return "<div style='color: orange; font-weight: bold;'>⚠️ WARNING</div>" +
                       "<h3>{{alertType}} Alert</h3>" +
                       "<p>{{message}}</p>" +
                       "<p><strong>Severity:</strong> {{severity}}</p>" +
                       "<p><strong>Time:</strong> {{timestamp}}</p>";
                       
            default:
                return "<div style='color: blue;'>ℹ️ INFORMATION</div>" +
                       "<h3>{{alertType}} Alert</h3>" +
                       "<p>{{message}}</p>" +
                       "<p><strong>Severity:</strong> {{severity}}</p>" +
                       "<p><strong>Time:</strong> {{timestamp}}</p>";
        }
    }
}