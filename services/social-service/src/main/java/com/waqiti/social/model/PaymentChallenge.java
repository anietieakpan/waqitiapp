package com.waqiti.social.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_challenges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentChallenge {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private UUID challengerId;
    private UUID challengedUserId;
    private String challengeType;
    private String title;
    private String description;
    private BigDecimal targetAmount;
    private Integer targetTransactions;
    private Duration timeLimit;
    private String prize;
    private PaymentChallengeStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}

