package com.waqiti.nft.security;

/**
 * Cloud KMS Provider Interface
 *
 * <p>Abstraction for cloud-based Key Management Service providers.</p>
 *
 * <p>Supported implementations:</p>
 * <ul>
 *   <li>{@link GoogleCloudKmsKeyManager} - Google Cloud KMS</li>
 *   <li>{@link AwsKmsKeyManager} - AWS KMS/CloudHSM</li>
 *   <li>{@link AzureKeyVaultManager} - Azure Key Vault with HSM</li>
 * </ul>
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-01-15
 */
public interface CloudKmsProvider {

    /**
     * Encrypt data using cloud KMS
     *
     * @param keyIdentifier Identifier for audit trail and AAD
     * @param plaintext Data to encrypt
     * @return Base64-encoded encrypted data
     * @throws KeyManagementException if encryption fails
     */
    String encrypt(String keyIdentifier, byte[] plaintext);

    /**
     * Decrypt data using cloud KMS
     *
     * @param keyIdentifier Identifier for audit trail and AAD
     * @param encryptedData Base64-encoded encrypted data
     * @return Decrypted plaintext
     * @throws KeyManagementException if decryption fails
     */
    byte[] decrypt(String keyIdentifier, String encryptedData);

    /**
     * Rotate the master encryption key
     *
     * @throws KeyManagementException if rotation fails
     */
    default void rotateKey() {
        throw new UnsupportedOperationException("Key rotation not implemented for this provider");
    }
}
