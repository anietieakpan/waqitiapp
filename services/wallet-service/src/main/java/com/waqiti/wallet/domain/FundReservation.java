package com.waqiti.wallet.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a fund reservation in a wallet.
 * This prevents double-spending by locking funds for a specific transaction.
 */
@Entity
@Table(name = "fund_reservations", indexes = {
    @Index(name = "idx_reservation_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_reservation_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_reservation_status", columnList = "status"),
    @Index(name = "idx_reservation_expires_at", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundReservation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    
    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(length = 3, nullable = false)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
    
    @Column(name = "released_at")
    private LocalDateTime releasedAt;
    
    @Column(length = 500)
    private String reason;
    
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "wallet_version_at_reservation")
    private Long walletVersionAtReservation;

    @Version
    private Long version;
    
    /**
     * Check if this reservation has expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt) && status == ReservationStatus.ACTIVE;
    }
    
    /**
     * Confirm this reservation
     */
    public void confirm() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot confirm reservation in status: " + status);
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }
    
    /**
     * Release this reservation
     */
    public void release(String reason) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot release reservation in status: " + status);
        }
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
        this.reason = reason;
    }
    
    /**
     * Mark as expired
     */
    public void markExpired() {
        if (status == ReservationStatus.ACTIVE) {
            this.status = ReservationStatus.EXPIRED;
            this.releasedAt = LocalDateTime.now();
            this.reason = "Reservation expired";
        }
    }
    
    public enum ReservationStatus {
        ACTIVE,     // Funds are reserved
        CONFIRMED,  // Transaction completed, funds debited
        RELEASED,   // Reservation cancelled, funds released
        EXPIRED     // Reservation expired, funds released
    }
}