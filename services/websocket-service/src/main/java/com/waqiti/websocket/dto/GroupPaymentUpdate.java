package com.waqiti.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentUpdate {
    @NotBlank
    private String groupId;
    
    @NotBlank
    private String paymentId;
    
    private String updateType;
    private String userId;
    private BigDecimal amount;
    private String status;
    private List<String> participants;
    private Instant timestamp;
    private String description;
    
    public enum UpdateType {
        PAYMENT_CREATED,
        PAYMENT_JOINED,
        PAYMENT_LEFT,
        PAYMENT_COMPLETED,
        PAYMENT_CANCELLED,
        SPLIT_UPDATED,
        REMINDER_SENT
    }
}