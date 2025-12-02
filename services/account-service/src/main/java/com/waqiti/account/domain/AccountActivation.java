package com.waqiti.account.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Account Activation tracking entity
 */
@Entity
@Table(name = "account_activations", indexes = {
    @Index(name = "idx_account_id", columnList = "accountId"),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_activated_at", columnList = "activatedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountActivation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID accountId;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private String accountTier;
    
    @Column(nullable = false)
    private String activationMethod;
    
    @Column(nullable = false)
    private LocalDateTime activatedAt;
    
    private String verificationLevel;
    
    private String correlationId;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
}
