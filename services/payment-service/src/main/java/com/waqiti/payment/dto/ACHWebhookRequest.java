package com.waqiti.payment.dto;

import com.waqiti.payment.entity.ACHTransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * ACH Webhook Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ACHWebhookRequest {
    
    @NotNull
    private UUID transferId;
    
    @NotNull
    private ACHTransferStatus status;
    
    private String failureReason;
    private String returnCode;
    private String returnReason;
    private LocalDateTime timestamp;
    private Map<String, Object> additionalData;
}