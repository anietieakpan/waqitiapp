package com.waqiti.common.security.hsm;

import com.waqiti.common.security.hsm.exception.HSMException;

import java.util.List;

/**
 * Hardware Security Module Provider Interface
 * Defines operations for interacting with HSM devices for secure key management
 */
public interface HSMProvider {
    
    /**
     * Initialize the HSM provider with default configuration
     */
    void initialize() throws HSMException;
    
    /**
     * Initialize the HSM provider with custom configuration
     */
    void initialize(HSMConfig config) throws HSMException;
    
    /**
     * Generate a secret key in the HSM
     */
    HSMKeyHandle generateSecretKey(String keyId, String algorithm, int keySize) throws HSMException;
    
    /**
     * Generate a secret key with specific usage permissions
     */
    HSMKeyHandle generateSecretKey(String keyId, String algorithm, int keySize, HSMKeyHandle.HSMKeyUsage[] usages) throws HSMException;
    
    /**
     * Generate a key pair in the HSM
     */
    HSMKeyPair generateKeyPair(String keyId, String algorithm, int keySize) throws HSMException;
    
    /**
     * Generate a key pair with specific usage permissions
     */
    HSMKeyPair generateKeyPair(String keyId, String algorithm, int keySize, HSMKeyHandle.HSMKeyUsage[] usages) throws HSMException;
    
    /**
     * Encrypt data using an HSM key
     */
    byte[] encrypt(String keyId, byte[] data, String algorithm) throws HSMException;
    
    /**
     * Decrypt data using an HSM key
     */
    byte[] decrypt(String keyId, byte[] encryptedData, String algorithm) throws HSMException;
    
    /**
     * Sign data using an HSM private key
     */
    byte[] sign(String keyId, byte[] data, String algorithm) throws HSMException;
    
    /**
     * Verify signature using an HSM public key
     */
    boolean verify(String keyId, byte[] data, byte[] signature, String algorithm) throws HSMException;
    
    /**
     * Get a key handle for an existing key
     */
    default HSMKeyHandle getKeyHandle(String keyId) throws HSMException {
        List<HSMKeyInfo> keys = listKeys();
        return keys.stream()
            .filter(key -> keyId.equals(key.getKeyId()))
            .findFirst()
            .map(keyInfo -> HSMKeyHandle.builder()
                .keyId(keyInfo.getKeyId())
                .label(keyInfo.getLabel())
                .keyType(keyInfo.getKeyType())
                .algorithm(keyInfo.getAlgorithm())
                .keySize(keyInfo.getKeySize())
                .createdAt(keyInfo.getCreatedAt())
                .expiresAt(keyInfo.getExpiresAt())
                .extractable(keyInfo.isExtractable())
                .sensitive(keyInfo.isSensitive())
                .usages(keyInfo.getUsages())
                .build())
            .orElse(null);
    }
    
    /**
     * Get current HSM status and health information
     */
    HSMStatus getStatus() throws HSMException;
    
    /**
     * List all keys stored in the HSM
     */
    List<HSMKeyInfo> listKeys() throws HSMException;
    
    /**
     * Delete a key from the HSM
     */
    void deleteKey(String keyId) throws HSMException;

    /**
     * Close HSM connection and cleanup resources
     * PRODUCTION FIX: Removed duplicate close() method, kept HSMException version
     */
    void close() throws HSMException;
    
    /**
     * Get HSM provider type
     */
    default HSMProviderType getProviderType() {
        return HSMProviderType.PKCS11_GENERIC;
    }
    
    /**
     * Test HSM connectivity
     */
    default boolean testConnection() {
        try {
            HSMStatus status = getStatus();
            return status != null && status.isHealthy();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * HSM Provider Types
     */
    enum HSMProviderType {
        PKCS11_GENERIC,
        AWS_CLOUDHSM,
        THALES_CIPHERTRUST,
        UTIMACO_CRYPTOSERVER,
        NCIPHER_NSHIELD,
        SAFENET_LUNA,
        YUBICO_YUBIHSM,
        AWS_CLOUD_HSM,
        AZURE_KEY_VAULT,
        GCP_CLOUD_HSM,
        HARDWARE_HSM,
        SOFTWARE_HSM,
        PKCS11,
        THALES,
        SAFENET,
        UTIMACO
    }
}