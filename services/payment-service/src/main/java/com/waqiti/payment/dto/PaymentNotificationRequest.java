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
 * DTO for payment notification requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentNotificationRequest {
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String transactionId;
    
    @NotBlank
    private String notificationType;
    
    @NotNull
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    private String merchantName;
    private String paymentMethod;
    private String status;
    private String title;
    private String message;
    private String template;
    
    // Channel preferences
    private boolean sendEmail;
    private boolean sendSms;
    private boolean sendPush;
    
    // Additional data
    private Map<String, Object> templateData;
    private Map<String, String> metadata;
    
    @Builder.Default
    private String priority = "NORMAL";
}