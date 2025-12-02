package com.waqiti.voice.security.encryption;

import com.waqiti.voice.security.vault.VaultSecretService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM Encryption Service
 *
 * CRITICAL SECURITY SERVICE
 * Encrypts sensitive data at rest:
 * - Voice biometric signatures
 * - Voice transcriptions
 * - Biometric features
 * - PII data
 *
 * Algorithm: AES-256-GCM (Galois/Counter Mode)
 * - Provides both confidentiality and authenticity
 * - NIST approved
 * - Resistant to padding oracle attacks
 *
 * Key Management:
 * - Keys stored in HashiCorp Vault
 * - Key rotation supported
 * - Per-environment keys (dev/staging/prod)
 *
 * Compliance:
 * - GDPR Article 32 (encryption of personal data)
 * - PCI-DSS Requirement 3.4 (render PAN unreadable)
 * - BIPA (biometric data encryption)
 */
@Slf4j
@Service
public class AESEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    private static final int AES_KEY_SIZE = 256; // 256 bits

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    @Autowired(required = false)
    private VaultSecretService vaultSecretService;

    public AESEncryptionService(
            @Value("${voice-payment.encryption.key:#{null}}") String encryptionKeyBase64,
            @Value("${voice-payment.encryption.vault-enabled:true}") boolean vaultEnabled,
            @Value("${voice-payment.encryption.vault-path:voice-payment-service/encryption}") String vaultPath,
            @Autowired(required = false) VaultSecretService vaultSecretService) {

        this.secureRandom = new SecureRandom();
        this.vaultSecretService = vaultSecretService;

        // Priority 1: Try to load from Vault
        if (vaultEnabled && vaultSecretService != null) {
            try {
                log.info("Attempting to load encryption key from Vault: {}", vaultPath);
                String keyFromVault = vaultSecretService.getSecret(vaultPath, "aes-key");
                byte[] keyBytes = Base64.getDecoder().decode(keyFromVault);
                this.secretKey = new SecretKeySpec(keyBytes, "AES");
                log.info("✅ Encryption key loaded from Vault successfully");
                return;
            } catch (Exception e) {
                log.warn("Failed to load encryption key from Vault, falling back to configuration", e);
            }
        }

        // Priority 2: Load from configuration (application.yml)
        if (encryptionKeyBase64 != null && !encryptionKeyBase64.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("Encryption key loaded from configuration (application.yml)");
        } else {
            // Priority 3: Generate temporary key for development (WARNING!)
            log.warn("⚠️ No encryption key configured in Vault or config, generating temporary key");
            log.warn("⚠️ THIS IS NOT SECURE FOR PRODUCTION!");
            this.secretKey = generateKey();
        }
    }

    /**
     * Encrypt plaintext string
     *
     * @param plaintext Data to encrypt
     * @return Base64-encoded encrypted data (IV + ciphertext + auth tag)
     * @throws EncryptionException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            // Generate random IV (Initialization Vector)
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            // Encrypt
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            // Combine IV + ciphertext for storage
            ByteBuffer byteBuffer = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            // Encode to Base64 for string storage
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt encrypted string
     *
     * @param encryptedData Base64-encoded encrypted data
     * @return Decrypted plaintext
     * @throws EncryptionException if decryption fails
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            // Decode from Base64
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            // Decrypt
            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }

    /**
     * Encrypt byte array (for binary data like audio)
     *
     * @param plaintext Binary data to encrypt
     * @return Encrypted data (IV + ciphertext + auth tag)
     */
    public byte[] encryptBytes(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return byteBuffer.array();

        } catch (Exception e) {
            log.error("Byte encryption failed", e);
            throw new EncryptionException("Failed to encrypt bytes", e);
        }
    }

    /**
     * Decrypt byte array
     *
     * @param encryptedData Encrypted binary data
     * @return Decrypted plaintext bytes
     */
    public byte[] decryptBytes(byte[] encryptedData) {
        if (encryptedData == null || encryptedData.length == 0) {
            return encryptedData;
        }

        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            return cipher.doFinal(ciphertext);

        } catch (Exception e) {
            log.error("Byte decryption failed", e);
            throw new EncryptionException("Failed to decrypt bytes", e);
        }
    }

    /**
     * Generate new AES-256 key
     * WARNING: For development only, use Vault in production
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_SIZE, secureRandom);
            SecretKey key = keyGenerator.generateKey();

            // Log Base64 encoded key for configuration (dev only!)
            String keyBase64 = Base64.getEncoder().encodeToString(key.getEncoded());
            log.warn("Generated encryption key (Base64): {}", keyBase64);
            log.warn("Add to application.yml: voice-payment.encryption.key={}", keyBase64);

            return key;
        } catch (Exception e) {
            throw new EncryptionException("Failed to generate encryption key", e);
        }
    }

    /**
     * Verify encryption is working
     */
    public boolean testEncryption() {
        try {
            String testData = "Test encryption: " + System.currentTimeMillis();
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);
            return testData.equals(decrypted);
        } catch (Exception e) {
            log.error("Encryption test failed", e);
            return false;
        }
    }

    /**
     * Encryption exception
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
