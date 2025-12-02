package com.waqiti.atm.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "atm_cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ATMCard {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "card_number", unique = true, nullable = false, length = 16)
    private String cardNumber;  // Exposed via masked getter only

    /**
     * Get masked card number (shows only last 4 digits)
     * Use this for API responses instead of raw cardNumber
     */
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "************" + cardNumber.substring(cardNumber.length() - 4);
    }
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Column(name = "card_holder_name", nullable = false)
    private String cardHolderName;
    
    @Column(name = "card_type", length = 20)
    @Enumerated(EnumType.STRING)
    private CardType cardType;
    
    @JsonIgnore  // SECURITY: Never expose PIN hash in API responses
    @Column(name = "pin_hash", nullable = false)
    private String pinHash;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private CardStatus status;
    
    @Column(name = "issued_date")
    private LocalDateTime issuedDate;
    
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;
    
    @Column(name = "failed_pin_attempts")
    private Integer failedPinAttempts = 0;
    
    @Column(name = "temporary_pin")
    private Boolean temporaryPin = false;
    
    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;
    
    @Column(name = "block_reason")
    private String blockReason;
    
    @Column(name = "unblocked_at")
    private LocalDateTime unblockedAt;
    
    @Column(name = "pin_changed_at")
    private LocalDateTime pinChangedAt;
    
    @Column(name = "pin_reset_at")
    private LocalDateTime pinResetAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    public enum CardType {
        DEBIT, CREDIT, PREPAID
    }
    
    public enum CardStatus {
        ACTIVE, BLOCKED, EXPIRED, SUSPENDED, CANCELLED
    }
}