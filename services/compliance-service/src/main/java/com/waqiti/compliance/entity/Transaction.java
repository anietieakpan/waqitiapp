package com.waqiti.compliance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "sender_country", length = 2)
    private String senderCountry;

    @Column(name = "recipient_country", length = 2)
    private String recipientCountry;

    @Column(name = "flagged_for_review")
    private boolean flaggedForReview;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "sanctions_screened")
    private boolean sanctionsScreened;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}