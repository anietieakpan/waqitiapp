package com.waqiti.payment.dto;

import com.waqiti.payment.entity.CheckDepositStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event DTO for check deposit status updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckDepositEvent {
    private UUID depositId;
    private UUID userId;
    private CheckDepositStatus status;
    private BigDecimal amount;
    private LocalDateTime timestamp;
    private String message;
}