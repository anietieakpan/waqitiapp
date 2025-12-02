package com.waqiti.corebanking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PRODUCTION-GRADE Fund Reservation Entity
 * Replaces in-memory reservation tracking with persistent database storage
 * 
 * Features:
 * - Database-persistent fund reservations
 * - Atomic reservation operations
 * - Automatic expiration handling
 * - Comprehensive audit trail
 * - Optimistic locking for concurrency
 */
@Entity
@Table(name = "fund_reservations", indexes = {
    @Index(name = "idx_fund_reservation_account_id", columnList = "account_id"),
    @Index(name = "idx_fund_reservation_transaction_id", columnList = "transaction_id", unique = true),
    @Index(name = "idx_fund_reservation_status", columnList = "status"),
    @Index(name = "idx_fund_reservation_expires_at", columnList = "expires_at"),
    @Index(name = "idx_fund_reservation_account_status", columnList = "account_id, status")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundReservation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "account_id", nullable = false)
    private String accountId;
    
    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;
    
    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedAmount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.ACTIVE;
    
    @Column(name = "reservation_reason", nullable = false)
    private String reservationReason;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "reserved_by", nullable = false)
    private String reservedBy;
    
    @Column(name = "reserved_for_service")
    private String reservedForService;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "released_at")
    private LocalDateTime releasedAt;
    
    @Column(name = "released_by")
    private String releasedBy;
    
    @Column(name = "release_reason")
    private String releaseReason;
    
    // CRITICAL SECURITY: Optimistic locking to prevent concurrent modifications
    @Version
    private Long version;
    
    // Business logic methods
    
    /**
     * Check if reservation is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if reservation is still active
     */
    public boolean isActive() {
        return status == ReservationStatus.ACTIVE && !isExpired();
    }
    
    /**
     * Release the reservation
     */
    public void release(String releasedBy, String reason) {
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
        this.releasedBy = releasedBy;
        this.releaseReason = reason;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Expire the reservation
     */
    public void expire() {
        this.status = ReservationStatus.EXPIRED;
        this.releasedAt = LocalDateTime.now();
        this.releasedBy = "SYSTEM";
        this.releaseReason = "AUTOMATIC_EXPIRATION";
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Use the reservation (convert to actual transaction)
     */
    public void use(String usedBy) {
        this.status = ReservationStatus.USED;
        this.releasedAt = LocalDateTime.now();
        this.releasedBy = usedBy;
        this.releaseReason = "TRANSACTION_COMPLETED";
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Extend the reservation expiration
     */
    public void extend(LocalDateTime newExpiryTime, String extendedBy) {
        this.expiresAt = newExpiryTime;
        this.reservedBy = extendedBy;
        this.updatedAt = LocalDateTime.now();
    }
    
    public enum ReservationStatus {
        ACTIVE,     // Reservation is active and holds funds
        RELEASED,   // Reservation was manually released
        EXPIRED,    // Reservation expired automatically
        USED        // Reservation was used in a completed transaction
    }
}