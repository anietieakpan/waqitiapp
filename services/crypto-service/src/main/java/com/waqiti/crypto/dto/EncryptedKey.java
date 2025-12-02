/**
 * Encrypted Key DTO
 * Contains encrypted private key data from AWS KMS
 */
package com.waqiti.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedKey {
    private String encryptedData;
    private String keyId;
    private Map<String, String> encryptionContext;
    private String algorithm;
}