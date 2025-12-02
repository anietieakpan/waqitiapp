package com.waqiti.audit.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;

/**
 * Encryption Key metadata holder
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionKey {

    private String keyId;
    private SecretKey key;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String algorithm;
    private Integer keySize;
    private String status;

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status) && !isExpired();
    }
}
