package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for P2P notification requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class P2PNotificationRequest {
    
    @NotBlank
    private String senderId;
    
    @NotBlank
    private String receiverId;
    
    @NotBlank
    private String transactionId;
    
    @NotBlank
    private String notificationType;
    
    @NotNull
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    private String senderName;
    private String receiverName;
    private String message;
    private String status;
    private String title;
    private String template;
    
    // Channel preferences for sender
    private boolean sendSenderEmail;
    private boolean sendSenderSms;
    private boolean sendSenderPush;
    
    // Channel preferences for receiver
    private boolean sendReceiverEmail;
    private boolean sendReceiverSms;
    private boolean sendReceiverPush;
    
    // Additional data
    private Map<String, Object> templateData;
    private Map<String, String> metadata;
    
    @Builder.Default
    private String priority = "NORMAL";
}