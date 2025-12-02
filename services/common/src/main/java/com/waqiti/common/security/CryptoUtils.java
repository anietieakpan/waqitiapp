package com.waqiti.common.security;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Enterprise-grade cryptographic utilities for the Waqiti platform.
 * This class provides secure implementations for all cryptographic operations.
 * 
 * CRITICAL: This replaces all Math.random() usage in security-critical code.
 * All random number generation MUST use SecureRandom.
 */
@Component
public class CryptoUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(CryptoUtils.class);
    
    // Thread-safe SecureRandom instance
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    // Cryptographic constants
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 256;
    
    // OTP and PIN constants
    private static final int OTP_LENGTH = 6;
    private static final int PIN_LENGTH = 4;
    private static final int TRANSACTION_ID_LENGTH = 16;
    
    static {
        // Force SecureRandom to seed itself on class load
        SECURE_RANDOM.setSeed(SECURE_RANDOM.generateSeed(64));
    }
    
    /**
     * Generates a cryptographically secure OTP (One-Time Password).
     * This method MUST be used for all OTP generation in the system.
     * 
     * @return A 6-digit OTP as a string
     */
    public String generateSecureOTP() {
        int otp = SECURE_RANDOM.nextInt(900000) + 100000; // Ensures 6 digits
        logger.debug("Generated secure OTP");
        return String.valueOf(otp);
    }
    
    /**
     * Generates a cryptographically secure PIN.
     * 
     * @return A 4-digit PIN as a string
     */
    public String generateSecurePIN() {
        int pin = SECURE_RANDOM.nextInt(9000) + 1000; // Ensures 4 digits
        logger.debug("Generated secure PIN");
        return String.valueOf(pin);
    }
    
    /**
     * Generates a secure transaction ID.
     * 
     * @return A unique transaction ID
     */
    public String generateSecureTransactionId() {
        byte[] randomBytes = new byte[TRANSACTION_ID_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String transactionId = "TXN" + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes);
        logger.debug("Generated secure transaction ID: {}", transactionId);
        return transactionId;
    }
    
    /**
     * Generates a secure session token.
     * 
     * @return A cryptographically secure session token
     */
    public String generateSecureSessionToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(tokenBytes);
    }
    
    /**
     * Generates a secure API key.
     * 
     * @param prefix Optional prefix for the API key
     * @return A secure API key
     */
    public String generateSecureApiKey(String prefix) {
        byte[] keyBytes = new byte[32];
        SECURE_RANDOM.nextBytes(keyBytes);
        String key = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(keyBytes);
        
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + "_" + key;
        }
        return key;
    }
    
    /**
     * Generates a secure wallet address.
     * 
     * @return A unique wallet address
     */
    public String generateSecureWalletAddress() {
        byte[] addressBytes = new byte[20];
        SECURE_RANDOM.nextBytes(addressBytes);
        String address = "0x" + bytesToHex(addressBytes);
        logger.debug("Generated secure wallet address");
        return address;
    }
    
    /**
     * Generates a secure account number.
     * 
     * @param length The desired length of the account number
     * @return A secure account number
     */
    public String generateSecureAccountNumber(int length) {
        if (length <= 0 || length > 20) {
            throw new IllegalArgumentException("Account number length must be between 1 and 20");
        }
        
        StringBuilder accountNumber = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            accountNumber.append(SECURE_RANDOM.nextInt(10));
        }
        
        // Ensure it doesn't start with 0
        if (accountNumber.charAt(0) == '0') {
            accountNumber.setCharAt(0, (char) ('1' + SECURE_RANDOM.nextInt(9)));
        }
        
        return accountNumber.toString();
    }
    
    /**
     * Generates a secure verification code for email/SMS verification.
     * 
     * @param length The length of the verification code
     * @return A secure verification code
     */
    public String generateSecureVerificationCode(int length) {
        if (length <= 0 || length > 10) {
            throw new IllegalArgumentException("Verification code length must be between 1 and 10");
        }
        
        StringBuilder code = new StringBuilder(length);
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        
        for (int i = 0; i < length; i++) {
            code.append(characters.charAt(SECURE_RANDOM.nextInt(characters.length())));
        }
        
        return code.toString();
    }
    
    /**
     * Generates a secure nonce for cryptographic operations.
     * 
     * @param size The size of the nonce in bytes
     * @return A secure nonce
     */
    public byte[] generateSecureNonce(int size) {
        byte[] nonce = new byte[size];
        SECURE_RANDOM.nextBytes(nonce);
        return nonce;
    }
    
    /**
     * Generates a secure salt for password hashing.
     * 
     * @return A secure salt
     */
    public byte[] generateSecureSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }
    
    /**
     * Encrypts sensitive data using AES-GCM.
     * 
     * @param plaintext The data to encrypt
     * @param key The encryption key
     * @return Base64 encoded encrypted data with IV prepended
     */
    public String encryptData(String plaintext, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        
        // Generate IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        // Encrypt
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // Combine IV and ciphertext
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        byteBuffer.put(iv);
        byteBuffer.put(ciphertext);
        
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }
    
    /**
     * Decrypts data encrypted with encryptData.
     * 
     * @param encryptedData Base64 encoded encrypted data with IV
     * @param key The decryption key
     * @return The decrypted plaintext
     */
    public String decryptData(String encryptedData, SecretKey key) throws Exception {
        byte[] cipherMessage = Base64.getDecoder().decode(encryptedData);
        
        // Extract IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(cipherMessage, 0, iv, 0, iv.length);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        // Extract ciphertext
        byte[] ciphertext = new byte[cipherMessage.length - GCM_IV_LENGTH];
        System.arraycopy(cipherMessage, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
        
        // Decrypt
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
        byte[] plaintext = cipher.doFinal(ciphertext);
        
        return new String(plaintext, StandardCharsets.UTF_8);
    }
    
    /**
     * Generates a secure AES key.
     * 
     * @return A new AES secret key
     */
    public SecretKey generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_SIZE, SECURE_RANDOM);
        return keyGenerator.generateKey();
    }
    
    /**
     * Creates an HMAC signature for message authentication.
     * 
     * @param message The message to sign
     * @param key The signing key
     * @return Base64 encoded HMAC signature
     */
    public String createHMAC(String message, String key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(secretKey);
        byte[] hmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmac);
    }
    
    /**
     * Verifies an HMAC signature.
     * 
     * @param message The original message
     * @param signature The signature to verify
     * @param key The signing key
     * @return true if the signature is valid
     */
    public boolean verifyHMAC(String message, String signature, String key) throws Exception {
        String computedSignature = createHMAC(message, key);
        return MessageDigest.isEqual(
            computedSignature.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
        );
    }
    
    /**
     * Generates a secure random UUID.
     * 
     * @return A cryptographically secure UUID
     */
    public String generateSecureUUID() {
        byte[] randomBytes = new byte[16];
        SECURE_RANDOM.nextBytes(randomBytes);
        
        // Set version (4) and variant bits
        randomBytes[6] = (byte) ((randomBytes[6] & 0x0f) | 0x40);
        randomBytes[8] = (byte) ((randomBytes[8] & 0x3f) | 0x80);
        
        long msb = 0;
        long lsb = 0;
        
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (randomBytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (randomBytes[i] & 0xff);
        }
        
        return new UUID(msb, lsb).toString();
    }
    
    /**
     * Generates a secure random double value between 0.0 and 1.0.
     * This should be used instead of Math.random() for any security-related randomness.
     * 
     * @return A secure random double
     */
    public double generateSecureRandomDouble() {
        return SECURE_RANDOM.nextDouble();
    }
    
    /**
     * Generates a secure random integer within the specified range.
     * 
     * @param min The minimum value (inclusive)
     * @param max The maximum value (exclusive)
     * @return A secure random integer
     */
    public int generateSecureRandomInt(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("Max must be greater than min");
        }
        return SECURE_RANDOM.nextInt(max - min) + min;
    }
    
    /**
     * Utility method to convert bytes to hex string.
     * 
     * @param bytes The byte array to convert
     * @return Hex string representation
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Generates a secure temporary password.
     * 
     * @param length The desired length of the password
     * @return A secure temporary password
     */
    public String generateSecurePassword(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        String allChars = upperCase + lowerCase + digits + special;
        
        StringBuilder password = new StringBuilder(length);
        
        // Ensure at least one character from each category
        password.append(upperCase.charAt(SECURE_RANDOM.nextInt(upperCase.length())));
        password.append(lowerCase.charAt(SECURE_RANDOM.nextInt(lowerCase.length())));
        password.append(digits.charAt(SECURE_RANDOM.nextInt(digits.length())));
        password.append(special.charAt(SECURE_RANDOM.nextInt(special.length())));
        
        // Fill the rest randomly
        for (int i = 4; i < length; i++) {
            password.append(allChars.charAt(SECURE_RANDOM.nextInt(allChars.length())));
        }
        
        // Shuffle the password
        return shuffleString(password.toString());
    }
    
    /**
     * Shuffles a string securely.
     * 
     * @param input The string to shuffle
     * @return The shuffled string
     */
    private String shuffleString(String input) {
        char[] characters = input.toCharArray();
        for (int i = characters.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char temp = characters[i];
            characters[i] = characters[j];
            characters[j] = temp;
        }
        return new String(characters);
    }
}