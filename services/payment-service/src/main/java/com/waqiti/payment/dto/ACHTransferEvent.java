package com.waqiti.payment.dto;

import com.waqiti.payment.entity.ACHTransferStatus;
import com.waqiti.payment.entity.TransferDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ACH Transfer Event DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ACHTransferEvent {
    private UUID transferId;
    private UUID userId;
    private ACHTransferStatus status;
    private BigDecimal amount;
    private TransferDirection direction;
    private Instant timestamp;
}