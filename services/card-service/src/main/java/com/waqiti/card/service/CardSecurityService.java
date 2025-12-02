package com.waqiti.card.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.UUID;

/**
 * CardSecurityService - Production-grade cryptographic operations for card data
 *
 * Provides PCI-DSS compliant security:
 * - AES-256-GCM encryption for card numbers (HSM-ready)
 * - BCrypt hashing for PINs (cost factor 12)
 * - Secure activation code generation and hashing
 * - CVV encryption (temporary storage only)
 * - PAN tokenization
 * - Cryptographically secure random generation
 *
 * PCI-DSS Requirements:
 * - Requirement 3.2: Encryption of stored cardholder data
 * - Requirement 3.4: Rendering PAN unreadable
 * - Requirement 8.2.3: Strong cryptography for authentication
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardSecurityService {

    @Value("${card.security.encryption.key:}")
    private String encryptionKeyBase64;

    @Value("${card.security.bcrypt.cost:12}")
    private int bcryptCost;

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_SIZE = 256;
    private static final int ACTIVATION_CODE_LENGTH = 6;
    private static final int PIN_LENGTH = 4;
    private static final SecureRandom secureRandom = new SecureRandom();

    // ========================================================================
    // CARD NUMBER ENCRYPTION (PCI-DSS Requirement 3.2)
    // ========================================================================

    /**
     * Encrypt card number using AES-256-GCM
     *
     * @param cardNumber Plain card number (PAN)
     * @return Encrypted card number (Base64 encoded)
     * @throws RuntimeException if encryption fails
     */
    public String encryptCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Card number cannot be null or empty");
        }

        try {
            // Generate random IV for GCM mode
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Get encryption key
            SecretKey key = getEncryptionKey();

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            // Encrypt
            byte[] encryptedBytes = cipher.doFinal(cardNumber.getBytes(StandardCharsets.UTF_8));

            // Combine IV + encrypted data for storage
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            // Return Base64 encoded
            String encrypted = Base64.getEncoder().encodeToString(byteBuffer.array());

            log.debug("Card number encrypted successfully (length: {})", encrypted.length());
            return encrypted;

        } catch (Exception e) {
            log.error("Failed to encrypt card number", e);
            throw new RuntimeException("Card number encryption failed", e);
        }
    }

    /**
     * Decrypt card number
     *
     * @param encryptedCardNumber Encrypted card number (Base64 encoded)
     * @return Decrypted card number (PAN)
     * @throws RuntimeException if decryption fails
     */
    public String decryptCardNumber(String encryptedCardNumber) {
        if (encryptedCardNumber == null || encryptedCardNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Encrypted card number cannot be null or empty");
        }

        try {
            // Decode Base64
            byte[] encryptedData = Base64.getDecoder().decode(encryptedCardNumber);

            // Extract IV and encrypted bytes
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encryptedBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedBytes);

            // Get decryption key
            SecretKey key = getEncryptionKey();

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            // Decrypt
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String decrypted = new String(decryptedBytes, StandardCharsets.UTF_8);

            log.debug("Card number decrypted successfully");
            return decrypted;

        } catch (Exception e) {
            log.error("Failed to decrypt card number", e);
            throw new RuntimeException("Card number decryption failed", e);
        }
    }

    // ========================================================================
    // PIN HASHING (PCI-DSS Requirement 8.2.3)
    // ========================================================================

    /**
     * Hash PIN using BCrypt with cost factor 12
     *
     * @param pin Plain PIN (4-6 digits)
     * @return BCrypt hashed PIN
     */
    public String hashPin(String pin) {
        if (pin == null || pin.trim().isEmpty()) {
            throw new IllegalArgumentException("PIN cannot be null or empty");
        }

        if (!pin.matches("\\d{4,6}")) {
            throw new IllegalArgumentException("PIN must be 4-6 digits");
        }

        // Use BCrypt with cost factor (default 12 = 2^12 iterations)
        String hashed = BCrypt.withDefaults().hashToString(bcryptCost, pin.toCharArray());

        log.debug("PIN hashed successfully with BCrypt cost factor {}", bcryptCost);
        return hashed;
    }

    /**
     * Verify PIN against BCrypt hash
     *
     * @param plainPin Plain PIN to verify
     * @param hashedPin BCrypt hashed PIN
     * @return true if PIN matches, false otherwise
     */
    public boolean verifyPin(String plainPin, String hashedPin) {
        if (plainPin == null || hashedPin == null) {
            return false;
        }

        if (!plainPin.matches("\\d{4,6}")) {
            log.warn("Invalid PIN format during verification");
            return false;
        }

        try {
            BCrypt.Result result = BCrypt.verifyer().verify(plainPin.toCharArray(), hashedPin);
            boolean verified = result.verified;

            if (verified) {
                log.debug("PIN verification successful");
            } else {
                log.warn("PIN verification failed");
            }

            return verified;

        } catch (Exception e) {
            log.error("PIN verification error", e);
            return false;
        }
    }

    // ========================================================================
    // ACTIVATION CODE GENERATION AND HASHING
    // ========================================================================

    /**
     * Generate secure activation code (6-digit numeric)
     *
     * @return 6-digit activation code
     */
    public String generateActivationCode() {
        // Generate cryptographically secure 6-digit code
        int code = secureRandom.nextInt(900000) + 100000;
        String activationCode = String.valueOf(code);

        log.debug("Activation code generated");
        return activationCode;
    }

    /**
     * Hash activation code using BCrypt
     *
     * @param activationCode Plain activation code
     * @return BCrypt hashed activation code
     */
    public String hashActivationCode(String activationCode) {
        if (activationCode == null || !activationCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("Activation code must be 6 digits");
        }

        return BCrypt.withDefaults().hashToString(bcryptCost, activationCode.toCharArray());
    }

    /**
     * Verify activation code against hash
     *
     * @param plainCode Plain activation code
     * @param hashedCode BCrypt hashed activation code
     * @return true if code matches, false otherwise
     */
    public boolean verifyActivationCode(String plainCode, String hashedCode) {
        if (plainCode == null || hashedCode == null) {
            return false;
        }

        if (!plainCode.matches("\\d{6}")) {
            log.warn("Invalid activation code format");
            return false;
        }

        try {
            BCrypt.Result result = BCrypt.verifyer().verify(plainCode.toCharArray(), hashedCode);
            return result.verified;
        } catch (Exception e) {
            log.error("Activation code verification error", e);
            return false;
        }
    }

    // ========================================================================
    // CVV ENCRYPTION
    // ========================================================================

    /**
     * Encrypt CVV for temporary storage
     * Note: PCI-DSS prohibits storing CVV after authorization
     *
     * @param cvv Plain CVV
     * @return Encrypted CVV
     */
    public String encryptCvv(String cvv) {
        if (cvv == null || !cvv.matches("\\d{3,4}")) {
            throw new IllegalArgumentException("CVV must be 3-4 digits");
        }

        // Use same AES-256-GCM encryption as card numbers
        return encryptCardNumber(cvv);
    }

    /**
     * Decrypt CVV
     *
     * @param encryptedCvv Encrypted CVV
     * @return Plain CVV
     */
    public String decryptCvv(String encryptedCvv) {
        return decryptCardNumber(encryptedCvv);
    }

    /**
     * Verify CVV against encrypted version
     *
     * @param plainCvv Plain CVV
     * @param encryptedCvv Encrypted CVV
     * @return true if CVV matches, false otherwise
     */
    public boolean verifyCvv(String plainCvv, String encryptedCvv) {
        if (plainCvv == null || encryptedCvv == null) {
            return false;
        }

        if (!plainCvv.matches("\\d{3,4}")) {
            return false;
        }

        try {
            String decrypted = decryptCvv(encryptedCvv);
            return plainCvv.equals(decrypted);
        } catch (Exception e) {
            log.error("CVV verification error", e);
            return false;
        }
    }

    // ========================================================================
    // PAN TOKENIZATION
    // ========================================================================

    /**
     * Generate PAN token (Format-preserving tokenization)
     * In production: Integrate with external tokenization service (e.g., Stripe, Adyen)
     *
     * @param cardNumber Plain card number
     * @return PAN token (preserves format)
     */
    public String generatePanToken(String cardNumber) {
        if (cardNumber == null || !cardNumber.matches("\\d{13,19}")) {
            throw new IllegalArgumentException("Invalid card number format");
        }

        // Generate format-preserving token: preserve first 6 and last 4 digits
        // Middle digits replaced with random numbers
        String bin = cardNumber.substring(0, 6);
        String lastFour = cardNumber.substring(cardNumber.length() - 4);
        int middleLength = cardNumber.length() - 10;

        StringBuilder token = new StringBuilder(bin);
        for (int i = 0; i < middleLength; i++) {
            token.append(secureRandom.nextInt(10));
        }
        token.append(lastFour);

        log.debug("PAN token generated (preserving BIN and last 4)");
        return token.toString();
    }

    /**
     * Generate cryptographically secure token ID
     *
     * @return Token ID (UUID)
     */
    public String generateTokenId() {
        return UUID.randomUUID().toString();
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Mask card number for display (show only last 4 digits)
     *
     * @param cardNumber Full card number
     * @return Masked card number (e.g., ****1234)
     */
    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }

        String lastFour = cardNumber.substring(cardNumber.length() - 4);
        return "************" + lastFour;
    }

    /**
     * Extract last 4 digits from card number
     *
     * @param cardNumber Full card number
     * @return Last 4 digits
     */
    public String extractLastFour(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            throw new IllegalArgumentException("Invalid card number");
        }

        return cardNumber.substring(cardNumber.length() - 4);
    }

    /**
     * Generate secure random PIN
     *
     * @return 4-digit PIN
     */
    public String generateSecurePin() {
        int pin = secureRandom.nextInt(9000) + 1000;
        return String.valueOf(pin);
    }

    /**
     * Validate card number using Luhn algorithm
     *
     * @param cardNumber Card number to validate
     * @return true if valid, false otherwise
     */
    public boolean validateCardNumberLuhn(String cardNumber) {
        if (cardNumber == null || !cardNumber.matches("\\d{13,19}")) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return (sum % 10 == 0);
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Get encryption key from configuration or HSM
     * In production: Retrieve from HashiCorp Vault or AWS KMS
     *
     * @return AES-256 secret key
     */
    private SecretKey getEncryptionKey() {
        try {
            if (encryptionKeyBase64 != null && !encryptionKeyBase64.isEmpty()) {
                // Use configured key (from Vault)
                byte[] decodedKey = Base64.getDecoder().decode(encryptionKeyBase64);
                return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
            } else {
                // Development only: Generate ephemeral key
                log.warn("No encryption key configured - generating ephemeral key (NOT for production)");
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(AES_KEY_SIZE, secureRandom);
                return keyGen.generateKey();
            }
        } catch (Exception e) {
            log.error("Failed to get encryption key", e);
            throw new RuntimeException("Encryption key retrieval failed", e);
        }
    }

    /**
     * Generate AES-256 key for production deployment
     * Run once during initial setup and store in Vault
     *
     * @return Base64 encoded AES-256 key
     */
    public static String generateEncryptionKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
}
