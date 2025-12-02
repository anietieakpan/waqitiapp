package com.waqiti.wallet.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fund Reservation Response DTO
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundReservationResponse {

    private UUID reservationId;
    private UUID walletId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Long remainingSeconds;
}
