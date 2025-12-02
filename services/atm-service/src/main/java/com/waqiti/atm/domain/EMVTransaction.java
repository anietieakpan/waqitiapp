package com.waqiti.atm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "emv_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EMVTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "arqc", length = 16)
    private String arqc; // Application Request Cryptogram

    @Column(name = "atc")
    private Integer atc; // Application Transaction Counter

    @Column(name = "cvr", length = 6)
    private String cvr; // Card Verification Results

    @Column(name = "tvr", length = 10)
    private String tvr; // Terminal Verification Results

    @Column(name = "unpredictable_number", length = 8)
    private String unpredictableNumber;

    @Column(name = "authorization_code")
    private String authorizationCode;

    @Column(name = "validation_status")
    @Enumerated(EnumType.STRING)
    private ValidationStatus validationStatus;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "validated_at", nullable = false)
    private LocalDateTime validatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    public enum ValidationStatus {
        SUCCESS, FAILED, PENDING
    }
}
