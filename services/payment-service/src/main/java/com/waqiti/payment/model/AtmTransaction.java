package com.waqiti.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ATM Transaction Entity
 */
@Entity
@Table(name = "atm_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtmTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String transactionId;
    private String customerId;
    private String accountId;
    private String atmId;
    private String transactionType;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    private String currency;
    private LocalDateTime transactionTime;
    private String cardNumber;
    private Boolean isPinVerified;
    private Boolean isReceiptRequested;

    @Embedded
    private AtmLocation atmLocation;

    private String status;
    private String fraudScore;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
