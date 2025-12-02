package com.waqiti.security.encryption;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of encryption/decryption operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionResult {

    private String ciphertext;
    private String plaintext;
    private String keyId;
    private String algorithm;
    private boolean success;
    private String errorMessage;

    public static EncryptionResult success(String ciphertext, String keyId) {
        return EncryptionResult.builder()
            .ciphertext(ciphertext)
            .keyId(keyId)
            .success(true)
            .build();
    }

    public static EncryptionResult failure(String errorMessage) {
        return EncryptionResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }
}
