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
public class DisputeResolutionNotificationRequest {
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String merchantId;
    
    @NotBlank
    private String disputeId;
    
    @NotBlank
    private String paymentId;
    
    @NotBlank
    private String decision;
    
    @NotBlank
    private String resolutionType;
    
    @NotNull
    @Positive
    private BigDecimal amount;
    
    @NotNull
    private BigDecimal refundAmount;
    
    @NotBlank
    private String currency;
    
    @NotBlank
    private String disputeReason;
    
    @NotEmpty
    private List<String> channels;
    
    @NotBlank
    private String correlationId;
}