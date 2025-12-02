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
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundStatusNotificationRequest {
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String refundId;
    
    @NotBlank
    private String paymentId;
    
    @NotBlank
    private String status;
    
    @NotNull
    @Positive
    private BigDecimal refundAmount;
    
    @NotBlank
    private String currency;
    
    private String refundReason;
    
    private Instant completedAt;
    
    private String failureReason;
    
    @NotEmpty
    private List<String> channels;
    
    @NotBlank
    private String correlationId;
}