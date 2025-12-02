package com.waqiti.notification.controller;

import com.waqiti.notification.dto.WhatsAppMessage;
import com.waqiti.notification.dto.WhatsAppResponse;
import com.waqiti.notification.service.WhatsAppNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;

/**
 * WhatsApp notification controller
 * Handles WhatsApp Business API integration for messaging
 */
@RestController
@RequestMapping("/api/v1/whatsapp")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WhatsApp", description = "WhatsApp Business API integration")
public class WhatsAppController {
    
    private final WhatsAppNotificationService whatsAppService;
    
    @PostMapping("/send/text")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    @Operation(summary = "Send WhatsApp text message", 
               description = "Send a plain text message via WhatsApp Business API")
    public ResponseEntity<WhatsAppResponse> sendTextMessage(
            @Parameter(description = "Phone number in international format") 
            @RequestParam String phoneNumber,
            @Parameter(description = "Message content")
            @RequestParam String message,
            @Parameter(description = "Correlation ID for tracking")
            @RequestParam(required = false) String correlationId
    ) {
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        
        WhatsAppResponse response = whatsAppService.sendTextMessage(phoneNumber, message, corrId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/send/2fa")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('SYSTEM')")
    @Operation(summary = "Send 2FA code via WhatsApp", 
               description = "Send two-factor authentication code using WhatsApp template")
    public ResponseEntity<WhatsAppResponse> send2FACode(
            @Parameter(description = "Phone number in international format")
            @RequestParam String phoneNumber,
            @Parameter(description = "OTP code")
            @RequestParam String otp,
            @Parameter(description = "Correlation ID for tracking")
            @RequestParam(required = false) String correlationId
    ) {
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        
        WhatsAppResponse response = whatsAppService.send2FAMessage(phoneNumber, otp, corrId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/send/payment-notification")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "Send payment notification via WhatsApp", 
               description = "Send payment received notification using WhatsApp template")
    public ResponseEntity<WhatsAppResponse> sendPaymentNotification(
            @Parameter(description = "Phone number in international format")
            @RequestParam String phoneNumber,
            @Parameter(description = "Recipient name")
            @RequestParam String recipientName,
            @Parameter(description = "Payment amount")
            @RequestParam String amount,
            @Parameter(description = "Currency code")
            @RequestParam String currency,
            @Parameter(description = "Correlation ID for tracking")
            @RequestParam(required = false) String correlationId
    ) {
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        
        WhatsAppResponse response = whatsAppService.sendPaymentNotification(
                phoneNumber, recipientName, amount, currency, corrId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/send/alert")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "Send security alert via WhatsApp", 
               description = "Send account security alert using WhatsApp template")
    public ResponseEntity<WhatsAppResponse> sendSecurityAlert(
            @Parameter(description = "Phone number in international format")
            @RequestParam String phoneNumber,
            @Parameter(description = "Alert type")
            @RequestParam String alertType,
            @Parameter(description = "Alert message")
            @RequestParam String message,
            @Parameter(description = "Correlation ID for tracking")
            @RequestParam(required = false) String correlationId
    ) {
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        
        WhatsAppResponse response = whatsAppService.sendAccountAlert(
                phoneNumber, alertType, message, corrId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/send/marketing")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send marketing message via WhatsApp", 
               description = "Send marketing message using WhatsApp template (requires user opt-in)")
    public ResponseEntity<WhatsAppResponse> sendMarketingMessage(
            @Parameter(description = "Phone number in international format")
            @RequestParam String phoneNumber,
            @Parameter(description = "Template name")
            @RequestParam String templateName,
            @Parameter(description = "Template parameters")
            @RequestBody Map<String, String> templateParams,
            @Parameter(description = "Correlation ID for tracking")
            @RequestParam(required = false) String correlationId
    ) {
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        
        WhatsAppResponse response = whatsAppService.sendMarketingMessage(
                phoneNumber, templateName, templateParams, corrId);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get WhatsApp service status", 
               description = "Check WhatsApp Business API service status and connectivity")
    public ResponseEntity<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = whatsAppService.getServiceStatus();
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/webhook")
    @Operation(summary = "WhatsApp webhook verification", 
               description = "Verify WhatsApp webhook endpoint")
    public ResponseEntity<String> verifyWebhook(
            @Parameter(description = "Webhook mode")
            @RequestParam("hub.mode") String mode,
            @Parameter(description = "Verification token")
            @RequestParam("hub.verify_token") String token,
            @Parameter(description = "Challenge string")
            @RequestParam("hub.challenge") String challenge
    ) {
        String response = whatsAppService.handleWebhookVerification(mode, token, challenge);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/webhook")
    @Operation(summary = "WhatsApp webhook handler", 
               description = "Handle incoming WhatsApp webhook events")
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
        whatsAppService.handleWebhookMessage(payload);
        return ResponseEntity.ok().build();
    }
}