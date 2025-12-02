package com.waqiti.nft.security.hsm;

import org.web3j.crypto.Credentials;

/**
 * Hardware Security Module (HSM) Service Interface
 *
 * Defines contract for HSM integration supporting multiple providers:
 * - Google Cloud KMS
 * - AWS KMS / CloudHSM
 * - Azure Key Vault
 * - Thales Luna HSM
 *
 * Security Features:
 * - FIPS 140-2 Level 2/3 compliance
 * - Hardware-backed key generation and storage
 * - Tamper-resistant key operations
 * - Cryptographic key isolation
 * - Audit logging
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
public interface HardwareSecurityModuleService {

    /**
     * Encrypt sensitive data (e.g., private keys) using HSM
     *
     * @param plaintext Data to encrypt
     * @param keyIdentifier Unique identifier for the encryption key
     * @return Encrypted ciphertext
     */
    byte[] encryptPrivateKey(byte[] plaintext, String keyIdentifier);

    /**
     * Decrypt sensitive data using HSM
     *
     * @param ciphertext Encrypted data
     * @param keyIdentifier Unique identifier for the encryption key
     * @return Decrypted plaintext
     */
    byte[] decryptPrivateKey(byte[] ciphertext, String keyIdentifier);

    /**
     * Sign data using HSM-protected private key
     *
     * Private key never leaves HSM boundary.
     *
     * @param data Data to sign
     * @param keyIdentifier Unique identifier for the signing key
     * @return Cryptographic signature
     */
    byte[] signData(byte[] data, String keyIdentifier);

    /**
     * Verify signature using HSM-stored public key
     *
     * @param data Original data
     * @param signature Signature to verify
     * @param keyIdentifier Unique identifier for the verification key
     * @return True if signature is valid
     */
    boolean verifySignature(byte[] data, byte[] signature, String keyIdentifier);

    /**
     * Generate new cryptographic key pair in HSM
     *
     * Key is generated within HSM and never exported in plaintext.
     *
     * @param keyIdentifier Unique identifier for the new key
     * @return Key identifier/reference
     */
    String generateKeyPair(String keyIdentifier);

    /**
     * Get blockchain credentials backed by HSM
     *
     * Returns credentials object where signing operations use HSM.
     *
     * @param keyIdentifier Unique identifier for the key
     * @return Credentials for blockchain operations
     */
    Credentials getCredentials(String keyIdentifier);

    /**
     * Rotate encryption key
     *
     * Creates new key version and migrates encrypted data.
     *
     * @param keyIdentifier Unique identifier for the key to rotate
     */
    void rotateKey(String keyIdentifier);

    /**
     * Health check for HSM connectivity and accessibility
     *
     * @return True if HSM is accessible and healthy
     */
    boolean isHealthy();
}
