package com.waqiti.payment.dto;

import com.waqiti.payment.entity.ACHTransferStatus;
import com.waqiti.payment.entity.TransferDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ACH Transfer Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ACHTransferResponse {
    private UUID transferId;
    private ACHTransferStatus status;
    private BigDecimal amount;
    private TransferDirection direction;
    private LocalDate expectedCompletionDate;
    private String message;
    private LocalDateTime createdAt;
}