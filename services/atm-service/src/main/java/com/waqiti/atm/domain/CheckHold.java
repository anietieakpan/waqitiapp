package com.waqiti.atm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "check_holds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckHold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deposit_id", nullable = false)
    private UUID depositId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "hold_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal holdAmount;

    @Column(name = "hold_type")
    @Enumerated(EnumType.STRING)
    private HoldType holdType;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "hold_placed_at", nullable = false)
    private LocalDateTime holdPlacedAt;

    @Column(name = "hold_release_date", nullable = false)
    private LocalDateTime holdReleaseDate;

    @Column(name = "hold_released_at")
    private LocalDateTime holdReleasedAt;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private HoldStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum HoldType {
        REGULATORY, // Regulation CC
        LARGE_DEPOSIT, // Large unusual deposit
        NEW_ACCOUNT, // New account hold
        REPEATED_OVERDRAFT, // Exception hold
        FRAUD_PREVENTION // Fraud hold
    }

    public enum HoldStatus {
        ACTIVE, RELEASED, CANCELLED
    }
}
