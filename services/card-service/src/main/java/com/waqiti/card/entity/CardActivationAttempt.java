package com.waqiti.card.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CardActivationAttempt - Tracks card activation attempts for security
 *
 * Prevents brute-force attacks on activation codes
 * Implements automatic card blocking after max failed attempts
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "card_activation_attempt", indexes = {
        @Index(name = "idx_activation_card_id", columnList = "card_id"),
        @Index(name = "idx_activation_timestamp", columnList = "attempt_timestamp"),
        @Index(name = "idx_activation_card_timestamp", columnList = "card_id, attempt_timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardActivationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "attempt_id", updatable = false, nullable = false)
    private UUID attemptId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "attempt_timestamp", nullable = false)
    private LocalDateTime attemptTimestamp;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "attempt_method", length = 50)
    private String attemptMethod; // "ACTIVATION_CODE", "CVV", "PIN"

    @PrePersist
    protected void onCreate() {
        if (this.attemptTimestamp == null) {
            this.attemptTimestamp = LocalDateTime.now();
        }
    }
}
