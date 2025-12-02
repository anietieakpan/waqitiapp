// File: services/user-service/src/main/java/com/waqiti/user/domain/MfaVerificationCode.java
package com.waqiti.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mfa_verification_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MfaVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MfaMethod method;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Creates a new verification code
     */
    public static MfaVerificationCode create(UUID userId, MfaMethod method, String code, int expiryMinutes) {
        MfaVerificationCode verificationCode = new MfaVerificationCode();
        verificationCode.userId = userId;
        verificationCode.method = method;
        verificationCode.code = code;
        verificationCode.expiryDate = LocalDateTime.now().plusMinutes(expiryMinutes);
        verificationCode.used = false;
        verificationCode.createdAt = LocalDateTime.now();
        return verificationCode;
    }

    /**
     * Marks this code as used
     */
    public void markUsed() {
        this.used = true;
    }

    /**
     * Checks if this code is valid (not expired and not used)
     */
    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiryDate);
    }
}