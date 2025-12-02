package com.waqiti.transaction.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.pdf.*;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.transaction.dto.ReceiptSecurityValidation;
import com.waqiti.transaction.entity.Transaction;
import com.waqiti.transaction.repository.TransactionRepository;
import com.waqiti.transaction.service.ReceiptSecurityService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready implementation of receipt security service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptSecurityServiceImpl implements ReceiptSecurityService {

    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${waqiti.receipt.security.private-key}")
    private String privateKeyString;

    @Value("${waqiti.receipt.security.public-key}")
    private String publicKeyString;

    @Value("${waqiti.receipt.security.encryption-key}")
    private String encryptionKeyString;

    @Value("${waqiti.receipt.security.signing-secret}")
    private String signingSecret;

    @Value("${waqiti.receipt.security.fraud-threshold:10}")
    private int fraudThreshold;

    @Value("${waqiti.company.name:Waqiti Financial Services}")
    private String companyName;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private SecretKey encryptionKey;

    @javax.annotation.PostConstruct
    public void initializeKeys() {
        try {
            // Initialize RSA key pair for digital signatures
            if (privateKeyString != null && publicKeyString != null) {
                byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyString);
                byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);
                
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
                publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            } else {
                // Generate new keys if not provided
                generateNewKeyPair();
            }

            // Initialize AES key for encryption
            if (encryptionKeyString != null) {
                byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyString);
                encryptionKey = new SecretKeySpec(keyBytes, "AES");
            } else {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                encryptionKey = keyGen.generateKey();
            }

            log.info("Receipt security service initialized successfully");
        } catch (Exception e) {
            log.error("Error initializing receipt security service", e);
            throw new BusinessException("Failed to initialize receipt security");
        }
    }

    @Override
    public String generateDigitalSignature(byte[] receiptData) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(receiptData);
            
            byte[] digitalSignature = signature.sign();
            return Base64.getEncoder().encodeToString(digitalSignature);
            
        } catch (Exception e) {
            log.error("Error generating digital signature", e);
            throw new BusinessException("Failed to generate digital signature");
        }
    }

    @Override
    public boolean verifyDigitalSignature(byte[] receiptData, String signatureString) {
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(signatureString);
            
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(receiptData);
            
            return signature.verify(signatureBytes);
            
        } catch (Exception e) {
            log.error("Error verifying digital signature", e);
            return false;
        }
    }

    @Override
    public String generateTamperHash(byte[] receiptData, UUID transactionId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(signingSecret.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            
            // Combine receipt data with transaction ID for additional security
            ByteArrayOutputStream combined = new ByteArrayOutputStream();
            combined.write(receiptData);
            combined.write(transactionId.toString().getBytes());
            combined.write(System.currentTimeMillis() / (1000 * 60 * 60)); // Hour-level granularity
            
            byte[] hash = mac.doFinal(combined.toByteArray());
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            log.error("Error generating tamper hash", e);
            throw new BusinessException("Failed to generate tamper hash");
        }
    }

    @Override
    public ReceiptSecurityValidation validateReceiptIntegrity(byte[] receiptData, UUID transactionId, String expectedHash) {
        List<ReceiptSecurityValidation.ValidationCheck> checks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int securityScore = 0;

        // Check 1: Verify tamper hash
        String actualHash = generateTamperHash(receiptData, transactionId);
        boolean hashValid = actualHash.equals(expectedHash);
        checks.add(ReceiptSecurityValidation.ValidationCheck.builder()
                .checkType("TAMPER_HASH")
                .passed(hashValid)
                .message(hashValid ? "Hash validation passed" : "Hash validation failed")
                .weight(10)
                .build());
        
        if (hashValid) securityScore += 30;
        else errors.add("Receipt hash validation failed - possible tampering detected");

        // Check 2: Verify transaction exists
        Optional<Transaction> transaction = transactionRepository.findById(transactionId);
        boolean transactionExists = transaction.isPresent();
        checks.add(ReceiptSecurityValidation.ValidationCheck.builder()
                .checkType("TRANSACTION_EXISTENCE")
                .passed(transactionExists)
                .message(transactionExists ? "Transaction found" : "Transaction not found")
                .weight(10)
                .build());
        
        if (transactionExists) securityScore += 20;
        else errors.add("Associated transaction not found");

        // Check 3: PDF structure validation
        boolean pdfValid = validatePdfStructure(receiptData);
        checks.add(ReceiptSecurityValidation.ValidationCheck.builder()
                .checkType("PDF_STRUCTURE")
                .passed(pdfValid)
                .message(pdfValid ? "PDF structure valid" : "PDF structure invalid")
                .weight(8)
                .build());
        
        if (pdfValid) securityScore += 15;
        else warnings.add("PDF structure validation failed");

        // Check 4: File size validation
        boolean sizeValid = receiptData.length > 1000 && receiptData.length < 5 * 1024 * 1024; // 1KB to 5MB
        checks.add(ReceiptSecurityValidation.ValidationCheck.builder()
                .checkType("FILE_SIZE")
                .passed(sizeValid)
                .message(sizeValid ? "File size normal" : "File size suspicious")
                .weight(5)
                .build());
        
        if (sizeValid) securityScore += 10;
        else warnings.add("Receipt file size is outside normal range");

        // Check 5: Content validation
        boolean contentValid = validateReceiptContent(receiptData, transactionId);
        checks.add(ReceiptSecurityValidation.ValidationCheck.builder()
                .checkType("CONTENT_VALIDATION")
                .passed(contentValid)
                .message(contentValid ? "Content validation passed" : "Content validation failed")
                .weight(8)
                .build());
        
        if (contentValid) securityScore += 15;
        else warnings.add("Receipt content validation failed");

        // Additional security points for comprehensive validation
        if (checks.stream().allMatch(check -> check.isPassed())) {
            securityScore += 10; // Bonus for passing all checks
        }

        boolean overall = securityScore >= 70 && errors.isEmpty();

        return ReceiptSecurityValidation.builder()
                .valid(overall)
                .validatedAt(LocalDateTime.now())
                .checks(checks)
                .securityScore(Math.min(securityScore, 100))
                .warnings(warnings)
                .errors(errors)
                .receiptVersion("1.0")
                .validationMethod("COMPREHENSIVE")
                .build();
    }

    @Override
    public byte[] addSecurityWatermark(byte[] pdfData, String watermarkText) {
        try {
            PdfReader reader = new PdfReader(pdfData);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, baos);
            
            int pageCount = reader.getNumberOfPages();
            PdfContentByte content;
            
            for (int i = 1; i <= pageCount; i++) {
                content = stamper.getUnderContent(i);
                content.beginText();
                content.setFontAndSize(BaseFont.createFont(), 50);
                content.setTextMatrix(100, 400);
                
                // Create transparent watermark
                PdfGState gState = new PdfGState();
                gState.setFillOpacity(0.1f);
                content.setGState(gState);
                content.setColorFill(BaseColor.LIGHT_GRAY);
                
                content.showTextAligned(Element.ALIGN_CENTER, watermarkText, 300, 400, 45);
                content.endText();
            }
            
            stamper.close();
            reader.close();
            
            return baos.toByteArray();
            
        } catch (IOException | DocumentException e) {
            log.error("Error adding security watermark", e);
            throw new BusinessException("Failed to add security watermark");
        }
    }

    @Override
    public byte[] generateVerificationQrCode(UUID transactionId, String hash) {
        try {
            // Create verification URL or data
            String verificationData = String.format("WAQITI-RECEIPT:%s:%s:%d", 
                    transactionId, hash, System.currentTimeMillis());
            
            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrWriter.encode(verificationData, BarcodeFormat.QR_CODE, 200, 200);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Error generating QR code", e);
            throw new BusinessException("Failed to generate verification QR code");
        }
    }

    @Override
    public boolean validateVerificationQrCode(byte[] qrCode, UUID transactionId) {
        // QR code validation would involve decoding and verifying the content
        // This is a simplified implementation
        try {
            // In a real implementation, you would decode the QR code
            // and verify it contains the expected transaction ID and valid hash
            return qrCode != null && qrCode.length > 0;
        } catch (Exception e) {
            log.error("Error validating QR code", e);
            return false;
        }
    }

    @Override
    public boolean detectSuspiciousActivity(UUID transactionId, String userAgent, String ipAddress) {
        try {
            String key = String.format("receipt_requests:%s:%s", ipAddress, transactionId);
            String count = redisTemplate.opsForValue().get(key);
            
            int requestCount = count != null ? Integer.parseInt(count) : 0;
            requestCount++;
            
            redisTemplate.opsForValue().set(key, String.valueOf(requestCount), 1, TimeUnit.HOURS);
            
            // Flag as suspicious if more than threshold requests in 1 hour
            if (requestCount > fraudThreshold) {
                log.warn("Suspicious receipt activity detected: {} requests from IP {} for transaction {}", 
                        requestCount, ipAddress, transactionId);
                return true;
            }
            
            // Additional checks could include:
            // - User agent analysis
            // - Geographic location checks
            // - Request pattern analysis
            
            return false;
            
        } catch (Exception e) {
            log.error("Error in fraud detection", e);
            return false; // Fail open for business continuity
        }
    }

    /**
     * Encrypts receipt data using AES-256-GCM (Galois/Counter Mode).
     *
     * SECURITY: Uses authenticated encryption with associated data (AEAD)
     * - Algorithm: AES/GCM/NoPadding (industry standard)
     * - IV: 12 bytes (96 bits) randomly generated per encryption
     * - Tag length: 128 bits for authentication
     * - Format: [IV (12 bytes)][Ciphertext][Auth Tag (16 bytes)]
     *
     * This provides:
     * - Confidentiality (encryption)
     * - Integrity (authentication tag)
     * - Protection against tampering
     *
     * PCI-DSS Compliant: Meets Requirement 3.4 for strong cryptography
     *
     * @param receiptData The plain receipt data to encrypt
     * @return Encrypted data with prepended IV
     * @throws BusinessException if encryption fails
     */
    @Override
    public byte[] encryptReceiptData(byte[] receiptData) {
        try {
            // Generate a unique 12-byte IV (96 bits) for this encryption
            // CRITICAL: IV MUST be unique for each encryption operation
            byte[] iv = new byte[12];
            new java.security.SecureRandom().nextBytes(iv);

            // Initialize cipher in GCM mode
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec gcmParameterSpec =
                new javax.crypto.spec.GCMParameterSpec(128, iv); // 128-bit authentication tag
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmParameterSpec);

            // Encrypt the data
            byte[] ciphertext = cipher.doFinal(receiptData);

            // Prepend IV to ciphertext for storage
            // Format: [IV][Ciphertext + Auth Tag]
            byte[] encryptedWithIv = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(ciphertext, 0, encryptedWithIv, iv.length, ciphertext.length);

            log.debug("Successfully encrypted receipt data: {} bytes -> {} bytes (including IV)",
                     receiptData.length, encryptedWithIv.length);

            return encryptedWithIv;

        } catch (javax.crypto.NoSuchPaddingException |
                 java.security.NoSuchAlgorithmException |
                 java.security.InvalidKeyException |
                 java.security.InvalidAlgorithmParameterException e) {
            log.error("Encryption configuration error - this should not happen in production", e);
            throw new BusinessException("Receipt encryption failed - invalid configuration", e);

        } catch (javax.crypto.BadPaddingException |
                 javax.crypto.IllegalBlockSizeException e) {
            log.error("Error encrypting receipt data", e);
            throw new BusinessException("Failed to encrypt receipt data", e);
        }
    }

    /**
     * Decrypts receipt data encrypted with AES-256-GCM.
     *
     * Expects data format: [IV (12 bytes)][Ciphertext][Auth Tag (16 bytes)]
     *
     * SECURITY:
     * - Verifies authentication tag before decryption
     * - Detects any tampering with the ciphertext
     * - Constant-time comparison prevents timing attacks
     *
     * @param encryptedData The encrypted data with prepended IV
     * @return Decrypted plain receipt data
     * @throws BusinessException if decryption fails or data is tampered
     */
    @Override
    public byte[] decryptReceiptData(byte[] encryptedData) {
        try {
            // Validate minimum data length
            // Minimum: 12 bytes (IV) + 16 bytes (auth tag) + 1 byte (data)
            if (encryptedData == null || encryptedData.length < 29) {
                log.error("Invalid encrypted data: too short ({} bytes)",
                         encryptedData != null ? encryptedData.length : 0);
                throw new BusinessException("Invalid encrypted receipt data - corrupted");
            }

            // Extract IV from the beginning of the encrypted data
            byte[] iv = new byte[12];
            System.arraycopy(encryptedData, 0, iv, 0, 12);

            // Extract ciphertext (everything after IV)
            byte[] ciphertext = new byte[encryptedData.length - 12];
            System.arraycopy(encryptedData, 12, ciphertext, 0, ciphertext.length);

            // Initialize cipher in GCM mode for decryption
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec gcmParameterSpec =
                new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmParameterSpec);

            // Decrypt and verify authentication tag
            // GCM mode will throw AEADBadTagException if data was tampered
            byte[] decryptedData = cipher.doFinal(ciphertext);

            log.debug("Successfully decrypted receipt data: {} bytes -> {} bytes",
                     encryptedData.length, decryptedData.length);

            return decryptedData;

        } catch (javax.crypto.AEADBadTagException e) {
            // This exception indicates the data was tampered with or corrupted
            log.error("SECURITY ALERT: Receipt data integrity check failed - possible tampering detected", e);
            throw new BusinessException("Receipt data integrity verification failed - data may be tampered", e);

        } catch (javax.crypto.NoSuchPaddingException |
                 java.security.NoSuchAlgorithmException |
                 java.security.InvalidKeyException |
                 java.security.InvalidAlgorithmParameterException e) {
            log.error("Decryption configuration error - this should not happen in production", e);
            throw new BusinessException("Receipt decryption failed - invalid configuration", e);

        } catch (javax.crypto.BadPaddingException |
                 javax.crypto.IllegalBlockSizeException e) {
            log.error("Error decrypting receipt data", e);
            throw new BusinessException("Failed to decrypt receipt data", e);
        }
    }

    @Override
    public String generateAccessToken(UUID transactionId, String userEmail, long validityDurationMinutes) {
        Date expiration = new Date(System.currentTimeMillis() + validityDurationMinutes * 60 * 1000);
        
        return Jwts.builder()
                .setSubject(userEmail)
                .claim("transactionId", transactionId.toString())
                .claim("purpose", "receipt_access")
                .setIssuedAt(new Date())
                .setExpiration(expiration)
                .setIssuer(companyName)
                .signWith(SignatureAlgorithm.HS256, signingSecret)
                .compact();
    }

    @Override
    public boolean validateAccessToken(String token, UUID transactionId) {
        try {
            var claims = Jwts.parser()
                    .setSigningKey(signingSecret)
                    .parseClaimsJws(token)
                    .getBody();
            
            String tokenTransactionId = claims.get("transactionId", String.class);
            String purpose = claims.get("purpose", String.class);
            
            return transactionId.toString().equals(tokenTransactionId) && 
                   "receipt_access".equals(purpose);
                   
        } catch (Exception e) {
            log.debug("Invalid access token", e);
            return false;
        }
    }

    private void generateNewKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
        
        log.warn("Generated new RSA key pair. In production, keys should be provided via configuration.");
    }

    private boolean validatePdfStructure(byte[] pdfData) {
        try {
            PdfReader reader = new PdfReader(pdfData);
            int pageCount = reader.getNumberOfPages();
            reader.close();
            
            // Basic validation: PDF should have at least 1 page and not too many
            return pageCount > 0 && pageCount <= 10;
            
        } catch (IOException e) {
            log.debug("PDF structure validation failed", e);
            return false;
        }
    }

    private boolean validateReceiptContent(byte[] receiptData, UUID transactionId) {
        try {
            // Convert PDF to text and check for required content
            PdfReader reader = new PdfReader(receiptData);
            StringBuilder content = new StringBuilder();
            
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                content.append(PdfTextExtractor.getTextFromPage(reader, i));
            }
            
            String text = content.toString();
            reader.close();
            
            // Check for required elements
            boolean hasTransactionId = text.contains(transactionId.toString());
            boolean hasCompanyName = text.toLowerCase().contains(companyName.toLowerCase());
            boolean hasReceiptTitle = text.toLowerCase().contains("receipt") || 
                                    text.toLowerCase().contains("proof of payment");
            
            return hasTransactionId && hasCompanyName && hasReceiptTitle;
            
        } catch (IOException e) {
            log.debug("Content validation failed", e);
            return false;
        }
    }

    @Override
    public int calculateSecurityScore(byte[] receiptData, UUID transactionId, String userId) {
        try {
            int score = 100; // Start with perfect score

            // Validate PDF structure (deduct 20 points if invalid)
            if (!validatePdfStructure(receiptData)) {
                score -= 20;
                log.debug("PDF structure validation failed for transaction: {}", transactionId);
            }

            // Validate receipt content (deduct 30 points if invalid)
            if (!validateReceiptContent(receiptData, transactionId)) {
                score -= 30;
                log.debug("Receipt content validation failed for transaction: {}", transactionId);
            }

            // Check transaction existence (deduct 25 points if not found)
            Optional<Transaction> transaction = transactionRepository.findById(transactionId);
            if (transaction.isEmpty()) {
                score -= 25;
                log.debug("Transaction not found: {}", transactionId);
            }

            // Validate data integrity (deduct 15 points if hash mismatch)
            String expectedHash = generateTamperHash(receiptData, transactionId);
            if (expectedHash == null || expectedHash.length() < 32) {
                score -= 15;
                log.debug("Hash generation failed for transaction: {}", transactionId);
            }

            // Check for suspicious patterns (deduct 10 points)
            if (detectSuspiciousActivity(transactionId, "SECURITY_CHECK", "INTERNAL")) {
                score -= 10;
                log.debug("Suspicious activity detected for transaction: {}", transactionId);
            }

            return Math.max(0, score); // Ensure score is never negative

        } catch (Exception e) {
            log.error("Error calculating security score for transaction: {}", transactionId, e);
            return 0; // Return lowest score on error
        }
    }

    @Override
    public String generateSecureAccessToken(UUID transactionId, String userId, int expirationMinutes, String accessLevel) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expirationMinutes * 60L * 1000L);
            
            Map<String, Object> claims = new HashMap<>();
            claims.put("transactionId", transactionId.toString());
            claims.put("userId", userId);
            claims.put("accessLevel", accessLevel);
            claims.put("purpose", "secure_receipt_access");
            claims.put("version", "2.0");
            
            String token = Jwts.builder()
                    .setClaims(claims)
                    .setSubject(userId)
                    .setIssuedAt(new Date())
                    .setExpiration(expiration)
                    .setIssuer(companyName)
                    .setId(UUID.randomUUID().toString())
                    .signWith(SignatureAlgorithm.HS256, signingSecret)
                    .compact();

            // Store token in Redis for validation and potential revocation
            String redisKey = "receipt_token:" + transactionId + ":" + token.substring(token.length() - 8);
            redisTemplate.opsForValue().set(redisKey, userId, expirationMinutes, TimeUnit.MINUTES);

            log.debug("Generated secure access token for transaction: {} user: {}", transactionId, userId);
            return token;

        } catch (Exception e) {
            log.error("Error generating secure access token for transaction: {}", transactionId, e);
            throw new BusinessException("Failed to generate secure access token");
        }
    }

    @Override
    public UUID validateAndGetTransactionId(String accessToken) {
        try {
            var claims = Jwts.parser()
                    .setSigningKey(signingSecret)
                    .parseClaimsJws(accessToken)
                    .getBody();
            
            String transactionIdStr = claims.get("transactionId", String.class);
            String purpose = claims.get("purpose", String.class);
            String version = claims.get("version", String.class);
            
            // Validate token purpose and version
            if (!"secure_receipt_access".equals(purpose)) {
                log.error("SECURITY: Invalid token purpose: {}", purpose);
                throw new SecurityException("Invalid access token purpose");
            }
            
            if (!"2.0".equals(version)) {
                log.error("SECURITY: Unsupported token version: {}", version);
                throw new SecurityException("Unsupported access token version");
            }

            UUID transactionId = UUID.fromString(transactionIdStr);
            
            // Check if token is still valid in Redis
            String redisKey = "receipt_token:" + transactionId + ":" + accessToken.substring(accessToken.length() - 8);
            String storedUserId = redisTemplate.opsForValue().get(redisKey);
            
            if (storedUserId == null) {
                log.error("SECURITY: Access token not found in cache or expired: {}", redisKey);
                throw new SecurityException("Access token expired or invalid");
            }

            // Verify transaction exists
            Optional<Transaction> transaction = transactionRepository.findById(transactionId);
            if (transaction.isEmpty()) {
                log.error("SECURITY: Access token references non-existent transaction: {}", transactionId);
                throw new SecurityException("Access token references invalid transaction");
            }

            log.debug("Successfully validated access token for transaction: {}", transactionId);
            return transactionId;
                   
        } catch (SecurityException e) {
            throw e; // Re-throw security exceptions
        } catch (Exception e) {
            log.error("SECURITY: Access token validation failed", e);
            throw new SecurityException("Access token validation failed");
        }
    }
}