// File: services/user-service/src/main/java/com/waqiti/user/domain/MfaConfiguration.java
package com.waqiti.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "mfa_configurations", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "method"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MfaConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MfaMethod method;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "secret")
    private String secret;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    /**
     * Creates a new MFA configuration for a user
     */
    public static MfaConfiguration create(UUID userId, MfaMethod method, String secret) {
        return MfaConfiguration.builder()
            .userId(userId)
            .method(method)
            .enabled(false)
            .verified(false)
            .secret(secret)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // Setter methods with audit trail

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
        this.updatedAt = LocalDateTime.now();
    }

    public void setSecret(String secret) {
        this.secret = secret;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the secret for this MFA configuration
     */
    public void updateSecret(String secret) {
        this.secret = secret;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks this MFA configuration as verified
     */
    public void markVerified() {
        this.verified = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Enables this MFA method
     */
    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Disables this MFA method
     */
    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if this MFA method is enabled and verified
     */
    public boolean isActive() {
        return enabled && verified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MfaConfiguration that = (MfaConfiguration) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}