package com.waqiti.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationType type;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Creates a new verification token
     */
    public static VerificationToken create(UUID userId, String token, VerificationType type, int expiryTimeInMinutes) {
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.userId = userId;
        verificationToken.token = token;
        verificationToken.type = type;
        verificationToken.expiryDate = LocalDateTime.now().plusMinutes(expiryTimeInMinutes);
        verificationToken.used = false;
        verificationToken.createdAt = LocalDateTime.now();
        return verificationToken;
    }

    /**
     * Marks the token as used
     */
    public void markAsUsed() {
        this.used = true;
    }

    /**
     * Checks if the token is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }

    /**
     * Checks if the token is valid
     */
    public boolean isValid() {
        return !used && !isExpired();
    }
}