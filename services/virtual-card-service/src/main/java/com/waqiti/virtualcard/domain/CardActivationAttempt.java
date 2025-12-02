package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

/**
 * Entity for tracking card activation attempts (for security auditing)
 */
@Entity
@Table(name = "card_activation_attempts", indexes = {
    @Index(name = "idx_activation_card_id", columnList = "card_id"),
    @Index(name = "idx_activation_user_id", columnList = "user_id"),
    @Index(name = "idx_activation_attempted_at", columnList = "attempted_at"),
    @Index(name = "idx_activation_successful", columnList = "successful")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CardActivationAttempt {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "card_id", nullable = false)
    private String cardId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "activation_code_hash") // Store hash, not actual code
    private String activationCode;
    
    @Column(name = "successful", nullable = false)
    private boolean successful;
    
    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "device_id")
    private String deviceId;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}