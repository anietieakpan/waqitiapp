package com.waqiti.common.security.masking;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback converter for masking sensitive data in logs
 */
public class LogMaskingConverter extends ClassicConverter {

    private static final DataMaskingService maskingService = new DataMaskingService();
    
    // Patterns for detecting sensitive data in logs
    private static final Pattern EMAIL_LOG_PATTERN = 
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    
    private static final Pattern PHONE_LOG_PATTERN = 
        Pattern.compile("\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b");
    
    private static final Pattern CARD_LOG_PATTERN = 
        Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b");
    
    private static final Pattern SSN_LOG_PATTERN = 
        Pattern.compile("\\b(?!000|666|9\\d{2})\\d{3}[-]?(?!00)\\d{2}[-]?(?!0000)\\d{4}\\b");
    
    private static final Pattern API_KEY_PATTERN = 
        Pattern.compile("(?i)(api[_-]?key|apikey|api[_-]?secret)[\"']?\\s*[:=]\\s*[\"']?([\\w-]+)[\"']?");
    
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("(?i)(password|passwd|pwd)[\"']?\\s*[:=]\\s*[\"']?([^\\s\"']+)[\"']?");
    
    private static final Pattern TOKEN_PATTERN = 
        Pattern.compile("(?i)(token|bearer|authorization)[\"']?\\s*[:=]\\s*[\"']?([\\w.-]+)[\"']?");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        
        if (message == null || message.isEmpty()) {
            return message;
        }
        
        // Mask various types of sensitive data
        message = maskEmails(message);
        message = maskPhoneNumbers(message);
        message = maskCardNumbers(message);
        message = maskSSNs(message);
        message = maskApiKeys(message);
        message = maskPasswords(message);
        message = maskTokens(message);
        
        return message;
    }
    
    private String maskEmails(String message) {
        Matcher matcher = EMAIL_LOG_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        
        while (matcher.find()) {
            String email = matcher.group();
            String masked = maskingService.maskEmail(email);
            matcher.appendReplacement(sb, masked);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    private String maskPhoneNumbers(String message) {
        Matcher matcher = PHONE_LOG_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        
        while (matcher.find()) {
            String phone = matcher.group();
            String masked = maskingService.maskPhoneNumber(phone);
            matcher.appendReplacement(sb, masked);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    private String maskCardNumbers(String message) {
        Matcher matcher = CARD_LOG_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        
        while (matcher.find()) {
            String card = matcher.group();
            String masked = maskingService.maskCardNumber(card);
            matcher.appendReplacement(sb, masked);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    private String maskSSNs(String message) {
        Matcher matcher = SSN_LOG_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        
        while (matcher.find()) {
            String ssn = matcher.group();
            String masked = maskingService.maskSSN(ssn);
            matcher.appendReplacement(sb, masked);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    private String maskApiKeys(String message) {
        Matcher matcher = API_KEY_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String value = matcher.group(2);
            String masked = prefix + "=****";
            matcher.appendReplacement(sb, masked);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    private String maskPasswords(String message) {
        Matcher matcher = PASSWORD_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String masked = prefix + "=****";
            matcher.appendReplacement(sb, masked);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    private String maskTokens(String message) {
        Matcher matcher = TOKEN_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String value = matcher.group(2);
            String masked = prefix + "=" + maskingService.maskGeneric(value);
            matcher.appendReplacement(sb, masked);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
}