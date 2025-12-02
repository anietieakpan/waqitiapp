package com.waqiti.payment.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class CancellationApprovalNotificationRequest {
    
    @NotBlank
    private String userId;
    
    @NotBlank
    private String paymentId;
    
    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED|PENDING")
    private String approvalStatus;
    
    @NotNull
    private BigDecimal refundAmount;
    
    private BigDecimal cancellationFee;
    
    @NotBlank
    private String currency;
    
    private String rejectionReason;
    
    private String approvedBy;
    
    private List<String> channels;
    
    @NotBlank
    private String correlationId;
}