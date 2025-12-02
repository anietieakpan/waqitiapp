package com.waqiti.corebanking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for fund reservation information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundReservationDto {
    
    private UUID reservationId;
    private UUID accountId;
    private UUID transactionId;
    private BigDecimal amount;
    private String currency;
    private FundReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime releasedAt;
    private String reason;
    
    /**
     * Checks if the reservation is currently active
     */
    public boolean isActive() {
        return status == FundReservationStatus.ACTIVE && 
               (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }
    
    /**
     * Checks if the reservation has expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}

/**
 * Fund reservation status enumeration
 */
enum FundReservationStatus {
    ACTIVE,     // Reservation is active and holding funds
    CONFIRMED,  // Reservation has been confirmed/used
    RELEASED,   // Reservation has been released/cancelled
    EXPIRED     // Reservation has expired
}