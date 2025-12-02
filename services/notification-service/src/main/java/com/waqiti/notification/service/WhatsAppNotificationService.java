package com.waqiti.notification.service;

import com.waqiti.notification.dto.WhatsAppMessage;
import com.waqiti.notification.dto.WhatsAppResponse;
import com.waqiti.notification.exception.WhatsAppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WhatsApp Business API integration service
 * Handles WhatsApp message sending for notifications, 2FA, and marketing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppNotificationService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${whatsapp.api.url:https://graph.facebook.com/v18.0}")
    private String whatsAppApiUrl;
    
    @Value("${whatsapp.business.phone.number.id}")
    private String phoneNumberId;
    
    @Value("${whatsapp.access.token}")
    private String accessToken;
    
    @Value("${whatsapp.webhook.verify.token}")
    private String webhookVerifyToken;
    
    @Value("${whatsapp.enabled:true}")
    private boolean whatsAppEnabled;
    
    @Value("${whatsapp.template.namespace:waqiti_payments}")
    private String templateNamespace;
    
    /**
     * Send a WhatsApp text message
     */
    public WhatsAppResponse sendTextMessage(String phoneNumber, String message, String correlationId) {
        if (!whatsAppEnabled) {
            log.warn("WhatsApp is disabled, skipping message send");
            return WhatsAppResponse.builder()
                    .success(false)
                    .message("WhatsApp service disabled")
                    .build();
        }
        
        try {
            log.info("Sending WhatsApp text message to {} - Correlation: {}", maskPhoneNumber(phoneNumber), correlationId);
            
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messaging_product", "whatsapp");
            messageData.put("to", cleanPhoneNumber(phoneNumber));
            messageData.put("type", "text");
            messageData.put("text", Map.of("body", message));
            
            return sendMessage(messageData, correlationId);
            
        } catch (Exception e) {
            log.error("Failed to send WhatsApp text message to {} - Correlation: {}", 
                    maskPhoneNumber(phoneNumber), correlationId, e);
            throw new WhatsAppException("Failed to send WhatsApp message", e);
        }
    }
    
    /**
     * Send a WhatsApp template message for 2FA
     */
    public WhatsAppResponse send2FAMessage(String phoneNumber, String otp, String correlationId) {
        if (!whatsAppEnabled) {
            return WhatsAppResponse.builder().success(false).message("WhatsApp service disabled").build();
        }
        
        try {
            log.info("Sending WhatsApp 2FA message to {} - Correlation: {}", maskPhoneNumber(phoneNumber), correlationId);
            
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messaging_product", "whatsapp");
            messageData.put("to", cleanPhoneNumber(phoneNumber));
            messageData.put("type", "template");
            
            // 2FA template structure
            Map<String, Object> template = new HashMap<>();
            template.put("name", "authentication_code");
            template.put("language", Map.of("code", "en"));
            
            // Template parameters
            Map<String, Object> component = new HashMap<>();
            component.put("type", "body");
            component.put("parameters", new Object[]{
                Map.of("type", "text", "text", otp)
            });
            
            template.put("components", new Object[]{component});
            messageData.put("template", template);
            
            return sendMessage(messageData, correlationId);
            
        } catch (Exception e) {
            log.error("Failed to send WhatsApp 2FA message to {} - Correlation: {}", 
                    maskPhoneNumber(phoneNumber), correlationId, e);
            throw new WhatsAppException("Failed to send WhatsApp 2FA message", e);
        }
    }
    
    /**
     * Send payment notification via WhatsApp template
     */
    public WhatsAppResponse sendPaymentNotification(String phoneNumber, String recipientName, 
                                                  String amount, String currency, String correlationId) {
        if (!whatsAppEnabled) {
            return WhatsAppResponse.builder().success(false).message("WhatsApp service disabled").build();
        }
        
        try {
            log.info("Sending WhatsApp payment notification to {} - Correlation: {}", 
                    maskPhoneNumber(phoneNumber), correlationId);
            
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messaging_product", "whatsapp");
            messageData.put("to", cleanPhoneNumber(phoneNumber));
            messageData.put("type", "template");
            
            // Payment notification template
            Map<String, Object> template = new HashMap<>();
            template.put("name", "payment_received");
            template.put("language", Map.of("code", "en"));
            
            // Template parameters
            Map<String, Object> component = new HashMap<>();
            component.put("type", "body");
            component.put("parameters", new Object[]{
                Map.of("type", "text", "text", recipientName),
                Map.of("type", "text", "text", amount),
                Map.of("type", "text", "text", currency)
            });
            
            template.put("components", new Object[]{component});
            messageData.put("template", template);
            
            return sendMessage(messageData, correlationId);
            
        } catch (Exception e) {
            log.error("Failed to send WhatsApp payment notification to {} - Correlation: {}", 
                    maskPhoneNumber(phoneNumber), correlationId, e);
            throw new WhatsAppException("Failed to send WhatsApp payment notification", e);
        }
    }
    
    /**
     * Send account alert via WhatsApp
     */
    public WhatsAppResponse sendAccountAlert(String phoneNumber, String alertType, 
                                           String message, String correlationId) {
        if (!whatsAppEnabled) {
            return WhatsAppResponse.builder().success(false).message("WhatsApp service disabled").build();
        }
        
        try {
            log.info("Sending WhatsApp account alert to {} - Type: {} - Correlation: {}", 
                    maskPhoneNumber(phoneNumber), alertType, correlationId);
            
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messaging_product", "whatsapp");
            messageData.put("to", cleanPhoneNumber(phoneNumber));
            messageData.put("type", "template");
            
            // Account alert template
            Map<String, Object> template = new HashMap<>();
            template.put("name", "security_alert");
            template.put("language", Map.of("code", "en"));
            
            // Template parameters
            Map<String, Object> component = new HashMap<>();
            component.put("type", "body");
            component.put("parameters", new Object[]{
                Map.of("type", "text", "text", alertType),
                Map.of("type", "text", "text", message)
            });
            
            template.put("components", new Object[]{component});
            messageData.put("template", template);
            
            return sendMessage(messageData, correlationId);
            
        } catch (Exception e) {
            log.error("Failed to send WhatsApp account alert to {} - Correlation: {}", 
                    maskPhoneNumber(phoneNumber), correlationId, e);
            throw new WhatsAppException("Failed to send WhatsApp account alert", e);
        }
    }
    
    /**
     * Send marketing message via WhatsApp (requires user opt-in)
     */
    public WhatsAppResponse sendMarketingMessage(String phoneNumber, String templateName, 
                                               Map<String, String> templateParams, String correlationId) {
        if (!whatsAppEnabled) {
            return WhatsAppResponse.builder().success(false).message("WhatsApp service disabled").build();
        }
        
        try {
            // Verify user has opted in for marketing messages
            if (!isUserOptedInForMarketing(phoneNumber)) {
                throw new WhatsAppException("User has not opted in for marketing messages");
            }
            
            log.info("Sending WhatsApp marketing message to {} - Template: {} - Correlation: {}", 
                    maskPhoneNumber(phoneNumber), templateName, correlationId);
            
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messaging_product", "whatsapp");
            messageData.put("to", cleanPhoneNumber(phoneNumber));
            messageData.put("type", "template");
            
            // Marketing template structure
            Map<String, Object> template = new HashMap<>();
            template.put("name", templateName);
            template.put("language", Map.of("code", "en"));
            
            // Build template parameters
            if (templateParams != null && !templateParams.isEmpty()) {
                Object[] parameters = templateParams.values().stream()
                        .map(value -> Map.of("type", "text", "text", value))
                        .toArray();
                
                Map<String, Object> component = new HashMap<>();
                component.put("type", "body");
                component.put("parameters", parameters);
                
                template.put("components", new Object[]{component});
            }
            
            messageData.put("template", template);
            
            return sendMessage(messageData, correlationId);
            
        } catch (Exception e) {
            log.error("Failed to send WhatsApp marketing message to {} - Correlation: {}", 
                    maskPhoneNumber(phoneNumber), correlationId, e);
            throw new WhatsAppException("Failed to send WhatsApp marketing message", e);
        }
    }
    
    /**
     * Handle WhatsApp webhook verification
     */
    public String handleWebhookVerification(String mode, String token, String challenge) {
        log.info("WhatsApp webhook verification - Mode: {}, Token: {}", mode, maskToken(token));
        
        if ("subscribe".equals(mode) && webhookVerifyToken.equals(token)) {
            log.info("WhatsApp webhook verified successfully");
            return challenge;
        } else {
            log.warn("WhatsApp webhook verification failed");
            throw new WhatsAppException("Webhook verification failed");
        }
    }
    
    /**
     * Handle WhatsApp webhook messages (incoming messages, delivery receipts, etc.)
     */
    public void handleWebhookMessage(Map<String, Object> payload) {
        try {
            log.debug("Received WhatsApp webhook payload: {}", 
                    objectMapper.writeValueAsString(payload));
            
            // Process webhook payload
            // Handle message deliveries, read receipts, user responses, etc.
            processWebhookPayload(payload);
            
        } catch (Exception e) {
            log.error("Failed to process WhatsApp webhook message", e);
        }
    }
    
    /**
     * Check WhatsApp Business API status
     */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", whatsAppEnabled);
        status.put("phoneNumberId", phoneNumberId);
        status.put("templateNamespace", templateNamespace);
        status.put("timestamp", LocalDateTime.now());
        
        if (whatsAppEnabled) {
            try {
                // Test API connectivity
                testApiConnectivity();
                status.put("apiStatus", "CONNECTED");
            } catch (Exception e) {
                status.put("apiStatus", "ERROR");
                status.put("error", e.getMessage());
            }
        } else {
            status.put("apiStatus", "DISABLED");
        }
        
        return status;
    }
    
    // Private helper methods
    
    private WhatsAppResponse sendMessage(Map<String, Object> messageData, String correlationId) {
        try {
            String url = whatsAppApiUrl + "/" + phoneNumberId + "/messages";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(messageData, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                String messageId = extractMessageId(responseBody);
                
                log.info("WhatsApp message sent successfully - ID: {} - Correlation: {}", 
                        messageId, correlationId);
                
                return WhatsAppResponse.builder()
                        .success(true)
                        .messageId(messageId)
                        .correlationId(correlationId)
                        .timestamp(LocalDateTime.now())
                        .build();
            } else {
                throw new WhatsAppException("WhatsApp API returned status: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message - Correlation: {}", correlationId, e);
            throw new WhatsAppException("Failed to send WhatsApp message", e);
        }
    }
    
    private String extractMessageId(Map<String, Object> responseBody) {
        if (responseBody != null && responseBody.containsKey("messages")) {
            Object messages = responseBody.get("messages");
            if (messages instanceof java.util.List) {
                java.util.List<Map> messagesList = (java.util.List<Map>) messages;
                if (!messagesList.isEmpty()) {
                    Map firstMessage = messagesList.get(0);
                    return (String) firstMessage.get("id");
                }
            }
        }
        return UUID.randomUUID().toString(); // Fallback
    }
    
    private String cleanPhoneNumber(String phoneNumber) {
        // Remove all non-numeric characters
        String cleaned = phoneNumber.replaceAll("[^0-9]", "");
        
        // Ensure international format (add country code if missing)
        if (!cleaned.startsWith("1") && !cleaned.startsWith("234")) { // US or Nigeria
            cleaned = "1" + cleaned; // Default to US
        }
        
        return cleaned;
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        String cleaned = cleanPhoneNumber(phoneNumber);
        if (cleaned.length() >= 4) {
            return "****" + cleaned.substring(cleaned.length() - 4);
        }
        return "****";
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() < 6) {
            return "****";
        }
        return token.substring(0, 3) + "****" + token.substring(token.length() - 3);
    }
    
    private boolean isUserOptedInForMarketing(String phoneNumber) {
        try {
            // Check user preferences from database
            return checkMarketingOptInStatus(phoneNumber);
        } catch (Exception e) {
            log.error("Failed to check marketing opt-in status for {}: {}", phoneNumber, e.getMessage());
            // Default to false to prevent unsolicited marketing
            return false;
        }
    }
    
    /**
     * Check if user has opted in for marketing communications
     */
    private boolean checkMarketingOptInStatus(String phoneNumber) {
        try {
            // This would normally query user preferences from database
            // Implementation requires repository access
            log.debug("Checking marketing opt-in status for {}", phoneNumber);
            
            // For now, return false to prevent unsolicited marketing
            // In production, this would be: return preferencesService.isMarketingOptedIn(phoneNumber);
            return false;
            
        } catch (Exception e) {
            log.error("Error checking opt-in status: {}", e.getMessage());
            return false;
        }
    }
    
    private void processWebhookPayload(Map<String, Object> payload) {
        // Process different types of webhook events:
        // - message_deliveries: Track delivery status
        // - message_reads: Track read receipts
        // - messages: Handle incoming user messages
        // - messaging_handovers: Handle chat handovers
        
        log.info("Processing WhatsApp webhook payload");
        // Implementation would depend on specific webhook event types
    }
    
    private void testApiConnectivity() {
        try {
            String url = whatsAppApiUrl + "/" + phoneNumberId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new WhatsAppException("API connectivity test failed");
            }
            
        } catch (Exception e) {
            throw new WhatsAppException("WhatsApp API connectivity test failed", e);
        }
    }
}