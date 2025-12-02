package com.waqiti.payment.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversalFailureNotificationRequest {
    
    @NotBlank
    private String paymentId;
    
    @NotBlank
    private String reversalId;
    
    @NotBlank
    private String customerId;
    
    @NotBlank
    private String merchantId;
    
    @NotNull
    @Positive
    private BigDecimal reversalAmount;
    
    @NotBlank
    private String currency;
    
    @NotBlank
    private String errorCode;
    
    @NotBlank
    private String errorMessage;
    
    @NotBlank
    private String errorCategory;
    
    @NotBlank
    private String failureReason;
    
    @NotNull
    private Boolean isRetryable;
    
    @NotNull
    private Integer retryAttempt;
    
    @NotBlank
    private String reversalReason;
    
    @NotBlank
    private String gatewayId;
    
    private String gatewayErrorCode;
    
    @NotBlank
    private String notificationType;
    
    @NotBlank
    private String priority;
    
    @NotEmpty
    private List<String> channels;
    
    @NotBlank
    private String correlationId;
}