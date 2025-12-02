package com.waqiti.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusUpdate {
    @NotBlank
    private String paymentId;
    
    @NotBlank
    private String status;
    
    @NotNull
    private List<String> participantIds;
    
    private BigDecimal amount;
    private String currency;
    private String reason;
    private Instant timestamp;
    private String transactionId;
    
    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED
    }
}