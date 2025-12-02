package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Cryptographic key pair for NFC payments and secure communications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyPair {

    private PublicKey publicKey;
    private PrivateKey privateKey;
    
    private String algorithm;
    private int keySize;
    private String keyId;
    private String curve; // For ECC keys
    
    /**
     * Creates a new key pair instance
     */
    public static KeyPair of(PublicKey publicKey, PrivateKey privateKey) {
        return KeyPair.builder()
                .publicKey(publicKey)
                .privateKey(privateKey)
                .build();
    }
    
    /**
     * Gets the public key
     */
    public PublicKey getPublic() {
        return publicKey;
    }
    
    /**
     * Gets the private key
     */
    public PrivateKey getPrivate() {
        return privateKey;
    }
}