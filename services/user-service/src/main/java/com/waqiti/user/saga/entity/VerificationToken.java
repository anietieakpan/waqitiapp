package com.waqiti.user.saga.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Verification Token Entity
 *
 * Stores email and phone verification tokens
 * Auto-expires after configured duration
 */
@Entity
@Table(name = "verification_tokens",
    indexes = {
        @Index(name = "idx_verification_token", columnList = "token"),
        @Index(name = "idx_verification_user_id", columnList = "user_id"),
        @Index(name = "idx_verification_type", columnList = "type"),
        @Index(name = "idx_verification_expires_at", columnList = "expires_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, unique = true, length = 500)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private TokenType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "is_used", nullable = false)
    private Boolean isUsed = false;

    /**
     * Token Types
     */
    public enum TokenType {
        EMAIL_VERIFICATION,
        PHONE_VERIFICATION,
        PASSWORD_RESET,
        TWO_FACTOR_AUTH
    }

    /**
     * Check if token is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if token is valid (not used and not expired)
     */
    public boolean isValid() {
        return !isUsed && !isExpired();
    }

    /**
     * Mark token as used
     */
    public void markAsUsed() {
        this.isUsed = true;
        this.usedAt = LocalDateTime.now();
    }
}
