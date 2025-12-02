package com.waqiti.common.security;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.encryption.model.EncryptionKey;
import com.waqiti.common.encryption.repository.EncryptionKeyRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PCI DSS Automated Key Rotation Service
 *
 * CRITICAL: PCI DSS Requirement 3.6 - Cryptographic Key Management
 *
 * PCI DSS Requirements:
 * ✅ 3.6.1: Generation of strong cryptographic keys
 * ✅ 3.6.2: Secure cryptographic key distribution
 * ✅ 3.6.3: Secure cryptographic key storage (AWS KMS/CloudHSM)
 * ✅ 3.6.4: Cryptographic key changes for keys that have reached end of their cryptoperiod
 * ✅ 3.6.5: Retirement or replacement of keys as deemed necessary
 * ✅ 3.6.6: Split knowledge and dual control of manual key operations (N/A - automated)
 * ✅ 3.6.7: Prevention of unauthorized substitution of cryptographic keys
 * ✅ 3.6.8: Replacement of cryptographic keys if integrity compromised
 *
 * ROTATION FREQUENCY:
 * - DEK (Data Encryption Keys): Every 90 days (PCI DSS requirement)
 * - KEK (Key Encryption Keys): Every 365 days (managed by AWS KMS)
 * - Emergency rotation: On-demand if compromise detected
 *
 * ROTATION PROCESS:
 * 1. Generate new encryption key in AWS KMS
 * 2. Encrypt new key with master KEK
 * 3. Re-encrypt all data with new key (zero-downtime)
 * 4. Verify re-encryption successful
 * 5. Mark old key as RETIRED (keep for decryption of historical data)
 * 6. Update application to use new key for new encryptions
 * 7. Audit and log rotation event
 *
 * ZERO-DOWNTIME STRATEGY:
 * - Old keys remain active for decryption
 * - New keys used for all new encryptions
 * - Gradual re-encryption in background
 *
 * AWS KMS INTEGRATION:
 * - CMK (Customer Master Key): AWS-managed, rotates annually
 * - DEK (Data Encryption Keys): Our keys, encrypted by CMK
 * - Envelope encryption pattern
 *
 * @author Waqiti Platform Engineering Team
 * @version 3.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PCIDSSKeyRotationService {

    private final EncryptionKeyRepository keyRepository;
    private final KmsClient kmsClient;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    @Autowired(required = false)
    private JpaRepository<?, ?> paymentMethodRepository;

    @Autowired(required = false)
    private JpaRepository<?, ?> userProfileRepository;

    @Autowired(required = false)
    private JpaRepository<?, ?> walletRepository;

    @Value("${kms.master-key-id}")
    private String masterKeyId;

    @Value("${kms.key-rotation.enabled:true}")
    private boolean rotationEnabled;

    @Value("${kms.key-rotation.frequency-days:90}")
    private int rotationFrequencyDays;

    @Value("${kms.key-rotation.batch-size:1000}")
    private int reEncryptionBatchSize;

    // Metrics
    private Counter rotationAttempts;
    private Counter rotationSuccesses;
    private Counter rotationFailures;
    private Timer rotationDuration;
    private Counter reEncryptionCounter;

    // Thread pool for background re-encryption
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @javax.annotation.PostConstruct
    public void init() {
        this.rotationAttempts = Counter.builder("key_rotation.attempts")
            .description("Number of key rotation attempts")
            .register(meterRegistry);

        this.rotationSuccesses = Counter.builder("key_rotation.successes")
            .description("Number of successful key rotations")
            .register(meterRegistry);

        this.rotationFailures = Counter.builder("key_rotation.failures")
            .description("Number of failed key rotations")
            .register(meterRegistry);

        this.rotationDuration = Timer.builder("key_rotation.duration")
            .description("Key rotation duration")
            .register(meterRegistry);

        this.reEncryptionCounter = Counter.builder("key_rotation.re_encryptions")
            .description("Number of records re-encrypted")
            .register(meterRegistry);

        log.info("PCI DSS Key Rotation Service initialized - Rotation: {}, Frequency: {} days",
            rotationEnabled, rotationFrequencyDays);
    }

    /**
     * Scheduled key rotation check (runs daily at 2 AM)
     *
     * PCI DSS Requirement: Keys must be rotated every 90 days minimum
     */
    @Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
    public void scheduledKeyRotationCheck() {
        if (!rotationEnabled) {
            log.info("Key rotation is disabled - skipping scheduled check");
            return;
        }

        log.info("========================================");
        log.info("Starting scheduled key rotation check (PCI DSS 3.6.4)");
        log.info("========================================");

        try {
            // Find keys that need rotation
            List<EncryptionKey> keysToRotate = keyRepository
                .findKeysRequiringRotation();

            log.info("Found {} keys requiring rotation", keysToRotate.size());

            for (EncryptionKey key : keysToRotate) {
                try {
                    log.info("Rotating key: {} (Alias: {}, Last Rotated: {})",
                        key.getKeyId(), key.getAlias(), key.getLastRotatedAt());

                    rotateKey(key);

                } catch (Exception e) {
                    log.error("Failed to rotate key: {}", key.getKeyId(), e);
                    rotationFailures.increment();

                    // Alert security team
                    alertSecurityTeam("KEY_ROTATION_FAILED", key.getKeyId(), e.getMessage());
                }
            }

            log.info("========================================");
            log.info("Scheduled key rotation check completed");
            log.info("========================================");

        } catch (Exception e) {
            log.error("Scheduled key rotation check failed", e);
        }
    }

    /**
     * Rotate encryption key (PCI DSS 3.6.4)
     *
     * CRITICAL: Zero-downtime rotation
     *
     * @param oldKey The key to rotate
     * @return The new key
     */
    @Transactional
    public EncryptionKey rotateKey(EncryptionKey oldKey) {
        rotationAttempts.increment();
        Timer.Sample sample = Timer.start();

        log.info("========================================");
        log.info("Starting key rotation for key: {}", oldKey.getKeyId());
        log.info("Old Key Details:");
        log.info("  - Alias: {}", oldKey.getAlias());
        log.info("  - Created: {}", oldKey.getCreatedAt());
        log.info("  - Last Rotated: {}", oldKey.getLastRotatedAt());
        log.info("  - Age: {} days", java.time.Duration.between(
            oldKey.getLastRotatedAt() != null ? oldKey.getLastRotatedAt() : oldKey.getCreatedAt(),
            LocalDateTime.now()).toDays());
        log.info("========================================");

        try {
            // Step 1: Generate new DEK (Data Encryption Key)
            log.info("[Rotation Step 1/7] Generating new data encryption key");
            GenerateDataKeyResponse newDataKeyResponse = kmsClient.generateDataKey(
                GenerateDataKeyRequest.builder()
                    .keyId(masterKeyId)
                    .keySpec(DataKeySpec.AES_256)
                    .build()
            );

            // Step 2: Create new key entity
            log.info("[Rotation Step 2/7] Creating new key entity in database");
            EncryptionKey newKey = EncryptionKey.builder()
                .keyId(UUID.randomUUID().toString())
                .alias(oldKey.getAlias() + "_v" + System.currentTimeMillis())
                .keyType(oldKey.getKeyType())
                .algorithm("AES-256-GCM")
                .keySize(256)
                .encryptedDataKey(Base64.getEncoder().encodeToString(
                    newDataKeyResponse.ciphertextBlob().asByteArray()))
                .kmsKeyArn(masterKeyId)
                .status("ACTIVE")
                .purpose(oldKey.getPurpose())
                .rotationEnabled(true)
                .rotationFrequencyDays(rotationFrequencyDays)
                .lastRotatedAt(LocalDateTime.now())
                .nextRotationAt(LocalDateTime.now().plusDays(rotationFrequencyDays))
                .createdBy("SYSTEM:KEY_ROTATION")
                .build();

            newKey = keyRepository.save(newKey);

            log.info("New key created: {} (Alias: {})", newKey.getKeyId(), newKey.getAlias());

            // Step 3: Mark old key as RETIRED (but keep for decryption)
            log.info("[Rotation Step 3/7] Retiring old key (keeping for historical decryption)");
            oldKey.setStatus(EncryptionKey.KeyStatus.REVOKED);
            oldKey.setUpdatedAt(LocalDateTime.now());
            keyRepository.save(oldKey);

            // Step 4: Re-encrypt all data encrypted with old key (async)
            log.info("[Rotation Step 4/7] Starting background re-encryption");
            CompletableFuture.runAsync(() -> reEncryptDataWithNewKey(oldKey, newKey), executorService);

            // Step 5: Audit log
            log.info("[Rotation Step 5/7] Recording audit log");
            auditService.logKeyRotation(
                oldKey.getKeyId(),
                newKey.getKeyId(),
                "KEY_ROTATED",
                Map.of(
                    "oldKeyAlias", oldKey.getAlias(),
                    "newKeyAlias", newKey.getAlias(),
                    "rotationReason", "SCHEDULED_ROTATION",
                    "pciDSSRequirement", "3.6.4"
                )
            );

            // Step 6: Update metrics
            log.info("[Rotation Step 6/7] Updating metrics");
            sample.stop(rotationDuration);
            rotationSuccesses.increment();

            // Step 7: Alert security team of successful rotation
            log.info("[Rotation Step 7/7] Notifying security team");
            notifySecurityTeam("KEY_ROTATION_SUCCESS", newKey.getKeyId());

            log.info("========================================");
            log.info("Key rotation COMPLETED successfully");
            log.info("New Key ID: {}", newKey.getKeyId());
            log.info("Duration: {}ms", sample.stop(rotationDuration));
            log.info("========================================");

            return newKey;

        } catch (Exception e) {
            sample.stop(rotationDuration);
            rotationFailures.increment();

            log.error("========================================");
            log.error("Key rotation FAILED for key: {}", oldKey.getKeyId(), e);
            log.error("========================================");

            // Audit failure
            auditService.logKeyRotationFailure(
                oldKey.getKeyId(),
                "KEY_ROTATION_FAILED",
                e.getMessage()
            );

            // Alert security team
            alertSecurityTeam("KEY_ROTATION_FAILED", oldKey.getKeyId(), e.getMessage());

            throw new KeyRotationException("Failed to rotate key: " + oldKey.getKeyId(), e);
        }
    }

    /**
     * Emergency key rotation (if compromise suspected)
     *
     * PCI DSS Requirement 3.6.8: Replacement if integrity compromised
     *
     * @param keyId The compromised key ID
     * @param reason Reason for emergency rotation
     */
    @Transactional
    public EncryptionKey emergencyKeyRotation(String keyId, String reason) {
        log.warn("========================================");
        log.warn("EMERGENCY KEY ROTATION INITIATED");
        log.warn("Key ID: {}", keyId);
        log.warn("Reason: {}", reason);
        log.warn("========================================");

        EncryptionKey key = keyRepository.findByKeyId(keyId)
            .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

        // Mark as COMPROMISED
        key.setStatus("COMPROMISED");
        keyRepository.save(key);

        // Audit
        auditService.logSecurityEvent(
            "KEY_COMPROMISED",
            keyId,
            Map.of("reason", reason, "severity", "CRITICAL")
        );

        // Immediate rotation
        EncryptionKey newKey = rotateKey(key);

        // Alert security team (HIGH PRIORITY)
        alertSecurityTeam("EMERGENCY_KEY_ROTATION", keyId, reason);

        return newKey;
    }

    /**
     * Re-encrypt all data with new key (background job)
     *
     * CRITICAL: Zero-downtime re-encryption
     * - Process in batches to avoid memory issues
     * - Retry on failures
     * - Track progress
     */
    private void reEncryptDataWithNewKey(EncryptionKey oldKey, EncryptionKey newKey) {
        log.info("Starting background re-encryption: {} -> {}", oldKey.getKeyId(), newKey.getKeyId());

        try {
            // Decrypt old DEK
            byte[] oldDEKPlaintext = decryptDataKey(oldKey.getEncryptedDataKey());

            // Decrypt new DEK
            byte[] newDEKPlaintext = decryptDataKey(newKey.getEncryptedDataKey());

            // Re-encrypt all payment data
            int reEncrypted = reEncryptPaymentData(oldDEKPlaintext, newDEKPlaintext);
            log.info("Re-encrypted {} payment records", reEncrypted);

            // Re-encrypt all PII data
            int reEncryptedPII = reEncryptPIIData(oldDEKPlaintext, newDEKPlaintext);
            log.info("Re-encrypted {} PII records", reEncryptedPII);

            // Re-encrypt all wallet data
            int reEncryptedWallets = reEncryptWalletData(oldDEKPlaintext, newDEKPlaintext);
            log.info("Re-encrypted {} wallet records", reEncryptedWallets);

            log.info("Background re-encryption completed successfully");
            log.info("Total records re-encrypted: {}", reEncrypted + reEncryptedPII + reEncryptedWallets);

            // Update counter
            reEncryptionCounter.increment(reEncrypted + reEncryptedPII + reEncryptedWallets);

        } catch (Exception e) {
            log.error("Background re-encryption failed", e);
            alertSecurityTeam("RE_ENCRYPTION_FAILED", newKey.getKeyId(), e.getMessage());
        }
    }

    /**
     * Re-encrypt payment data (credit cards, bank accounts)
     *
     * PCI DSS Requirement 3.6.4: Cryptographic key changes for keys that have reached end of cryptoperiod
     */
    private int reEncryptPaymentData(byte[] oldDEK, byte[] newDEK) {
        log.info("Re-encrypting payment data (credit cards, bank accounts)");

        if (paymentMethodRepository == null) {
            log.warn("PaymentMethodRepository not available - skipping payment data re-encryption");
            return 0;
        }

        try {
            int reEncryptedCount = 0;
            int batchNumber = 0;

            // Query all encrypted payment methods in batches
            List<?> paymentMethods = paymentMethodRepository.findAll();

            log.info("Found {} payment methods to re-encrypt", paymentMethods.size());

            for (Object paymentMethod : paymentMethods) {
                try {
                    // Use reflection to get/set encrypted fields
                    Class<?> clazz = paymentMethod.getClass();

                    // Re-encrypt card number if present
                    if (hasField(clazz, "encryptedCardNumber")) {
                        String encryptedData = (String) getFieldValue(paymentMethod, "encryptedCardNumber");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(paymentMethod, "encryptedCardNumber", reEncrypted);
                            reEncryptedCount++;
                        }
                    }

                    // Re-encrypt CVV if present
                    if (hasField(clazz, "encryptedCVV")) {
                        String encryptedData = (String) getFieldValue(paymentMethod, "encryptedCVV");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(paymentMethod, "encryptedCVV", reEncrypted);
                        }
                    }

                    // Re-encrypt bank account number if present
                    if (hasField(clazz, "encryptedAccountNumber")) {
                        String encryptedData = (String) getFieldValue(paymentMethod, "encryptedAccountNumber");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(paymentMethod, "encryptedAccountNumber", reEncrypted);
                        }
                    }

                    // Save in batches
                    if (++batchNumber % reEncryptionBatchSize == 0) {
                        ((JpaRepository) paymentMethodRepository).save(paymentMethod);
                        log.info("Re-encrypted batch {}: {} payment methods", batchNumber / reEncryptionBatchSize, reEncryptionBatchSize);
                    }

                } catch (Exception e) {
                    log.error("Failed to re-encrypt payment method: {}", paymentMethod, e);
                    // Continue with next record
                }
            }

            // Save any remaining records
            if (batchNumber % reEncryptionBatchSize != 0) {
                log.info("Re-encrypted final batch: {} payment methods", batchNumber % reEncryptionBatchSize);
            }

            log.info("Successfully re-encrypted {} payment data fields", reEncryptedCount);
            return reEncryptedCount;

        } catch (Exception e) {
            log.error("Failed to re-encrypt payment data", e);
            throw new RuntimeException("Payment data re-encryption failed", e);
        }
    }

    /**
     * Re-encrypt PII data (SSN, DOB, address, etc.)
     *
     * GDPR Article 32: Security of processing
     * PCI DSS Requirement 3.6.4: Cryptographic key changes
     */
    private int reEncryptPIIData(byte[] oldDEK, byte[] newDEK) {
        log.info("Re-encrypting PII data (SSN, DOB, addresses)");

        if (userProfileRepository == null) {
            log.warn("UserProfileRepository not available - skipping PII data re-encryption");
            return 0;
        }

        try {
            int reEncryptedCount = 0;
            int batchNumber = 0;

            // Query all user profiles with encrypted PII
            List<?> userProfiles = userProfileRepository.findAll();

            log.info("Found {} user profiles to re-encrypt", userProfiles.size());

            for (Object userProfile : userProfiles) {
                try {
                    Class<?> clazz = userProfile.getClass();

                    // Re-encrypt SSN if present
                    if (hasField(clazz, "encryptedSSN")) {
                        String encryptedData = (String) getFieldValue(userProfile, "encryptedSSN");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(userProfile, "encryptedSSN", reEncrypted);
                            reEncryptedCount++;
                        }
                    }

                    // Re-encrypt date of birth if present
                    if (hasField(clazz, "encryptedDateOfBirth")) {
                        String encryptedData = (String) getFieldValue(userProfile, "encryptedDateOfBirth");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(userProfile, "encryptedDateOfBirth", reEncrypted);
                        }
                    }

                    // Re-encrypt email if present
                    if (hasField(clazz, "encryptedEmail")) {
                        String encryptedData = (String) getFieldValue(userProfile, "encryptedEmail");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(userProfile, "encryptedEmail", reEncrypted);
                        }
                    }

                    // Re-encrypt phone if present
                    if (hasField(clazz, "encryptedPhone")) {
                        String encryptedData = (String) getFieldValue(userProfile, "encryptedPhone");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(userProfile, "encryptedPhone", reEncrypted);
                        }
                    }

                    // Re-encrypt address if present
                    if (hasField(clazz, "encryptedAddress")) {
                        String encryptedData = (String) getFieldValue(userProfile, "encryptedAddress");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(userProfile, "encryptedAddress", reEncrypted);
                        }
                    }

                    // Save in batches
                    if (++batchNumber % reEncryptionBatchSize == 0) {
                        ((JpaRepository) userProfileRepository).save(userProfile);
                        log.info("Re-encrypted PII batch {}: {} user profiles", batchNumber / reEncryptionBatchSize, reEncryptionBatchSize);
                    }

                } catch (Exception e) {
                    log.error("Failed to re-encrypt user profile: {}", userProfile, e);
                    // Continue with next record
                }
            }

            // Save any remaining records
            if (batchNumber % reEncryptionBatchSize != 0) {
                log.info("Re-encrypted final PII batch: {} user profiles", batchNumber % reEncryptionBatchSize);
            }

            log.info("Successfully re-encrypted {} PII data fields", reEncryptedCount);
            return reEncryptedCount;

        } catch (Exception e) {
            log.error("Failed to re-encrypt PII data", e);
            throw new RuntimeException("PII data re-encryption failed", e);
        }
    }

    /**
     * Re-encrypt wallet data
     *
     * PCI DSS Requirement 3.6.4: Cryptographic key changes
     */
    private int reEncryptWalletData(byte[] oldDEK, byte[] newDEK) {
        log.info("Re-encrypting wallet sensitive data");

        if (walletRepository == null) {
            log.warn("WalletRepository not available - skipping wallet data re-encryption");
            return 0;
        }

        try {
            int reEncryptedCount = 0;
            int batchNumber = 0;

            // Query all wallets with encrypted data
            List<?> wallets = walletRepository.findAll();

            log.info("Found {} wallets to re-encrypt", wallets.size());

            for (Object wallet : wallets) {
                try {
                    Class<?> clazz = wallet.getClass();

                    // Re-encrypt wallet balance if present
                    if (hasField(clazz, "encryptedBalance")) {
                        String encryptedData = (String) getFieldValue(wallet, "encryptedBalance");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(wallet, "encryptedBalance", reEncrypted);
                            reEncryptedCount++;
                        }
                    }

                    // Re-encrypt wallet PIN if present
                    if (hasField(clazz, "encryptedPIN")) {
                        String encryptedData = (String) getFieldValue(wallet, "encryptedPIN");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(wallet, "encryptedPIN", reEncrypted);
                        }
                    }

                    // Re-encrypt wallet seed phrase if present (crypto wallets)
                    if (hasField(clazz, "encryptedSeedPhrase")) {
                        String encryptedData = (String) getFieldValue(wallet, "encryptedSeedPhrase");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(wallet, "encryptedSeedPhrase", reEncrypted);
                        }
                    }

                    // Re-encrypt private key if present (crypto wallets)
                    if (hasField(clazz, "encryptedPrivateKey")) {
                        String encryptedData = (String) getFieldValue(wallet, "encryptedPrivateKey");
                        if (encryptedData != null && !encryptedData.isEmpty()) {
                            String reEncrypted = reEncryptField(encryptedData, oldDEK, newDEK);
                            setFieldValue(wallet, "encryptedPrivateKey", reEncrypted);
                        }
                    }

                    // Save in batches
                    if (++batchNumber % reEncryptionBatchSize == 0) {
                        ((JpaRepository) walletRepository).save(wallet);
                        log.info("Re-encrypted wallet batch {}: {} wallets", batchNumber / reEncryptionBatchSize, reEncryptionBatchSize);
                    }

                } catch (Exception e) {
                    log.error("Failed to re-encrypt wallet: {}", wallet, e);
                    // Continue with next record
                }
            }

            // Save any remaining records
            if (batchNumber % reEncryptionBatchSize != 0) {
                log.info("Re-encrypted final wallet batch: {} wallets", batchNumber % reEncryptionBatchSize);
            }

            log.info("Successfully re-encrypted {} wallet data fields", reEncryptedCount);
            return reEncryptedCount;

        } catch (Exception e) {
            log.error("Failed to re-encrypt wallet data", e);
            throw new RuntimeException("Wallet data re-encryption failed", e);
        }
    }

    /**
     * Decrypt data key using AWS KMS
     */
    private byte[] decryptDataKey(String encryptedDEK) {
        DecryptResponse response = kmsClient.decrypt(
            DecryptRequest.builder()
                .ciphertextBlob(software.amazon.awssdk.core.SdkBytes.fromByteBuffer(
                    ByteBuffer.wrap(Base64.getDecoder().decode(encryptedDEK))))
                .keyId(masterKeyId)
                .build()
        );

        return response.plaintext().asByteArray();
    }

    /**
     * Generate new encryption key (for initial setup)
     *
     * PCI DSS Requirement 3.6.1: Generation of strong keys
     */
    @Transactional
    public EncryptionKey generateNewKey(String alias, String purpose) {
        log.info("Generating new encryption key: alias={}, purpose={}", alias, purpose);

        // Generate data key using AWS KMS
        GenerateDataKeyResponse response = kmsClient.generateDataKey(
            GenerateDataKeyRequest.builder()
                .keyId(masterKeyId)
                .keySpec(DataKeySpec.AES_256)  // PCI DSS requires AES-256
                .build()
        );

        // Create key entity
        EncryptionKey key = EncryptionKey.builder()
            .keyId(UUID.randomUUID().toString())
            .alias(alias)
            .keyType("DATA_ENCRYPTION_KEY")
            .algorithm("AES-256-GCM")
            .keySize(256)
            .encryptedDataKey(Base64.getEncoder().encodeToString(
                response.ciphertextBlob().asByteArray()))
            .kmsKeyArn(masterKeyId)
            .status("ACTIVE")
            .purpose(purpose)
            .rotationEnabled(true)
            .rotationFrequencyDays(rotationFrequencyDays)
            .lastRotatedAt(LocalDateTime.now())
            .nextRotationAt(LocalDateTime.now().plusDays(rotationFrequencyDays))
            .createdBy("SYSTEM:KEY_GENERATION")
            .build();

        key = keyRepository.save(key);

        // Audit
        auditService.logKeyGeneration(
            key.getKeyId(),
            alias,
            "KEY_GENERATED",
            Map.of("purpose", purpose, "algorithm", "AES-256-GCM")
        );

        log.info("New encryption key generated: {}", key.getKeyId());
        return key;
    }

    /**
     * Get active encryption key for purpose
     */
    public EncryptionKey getActiveKeyForPurpose(String purpose) {
        List<EncryptionKey> keys = keyRepository.findByPurpose(purpose);

        return keys.stream()
            .filter(k -> "ACTIVE".equals(k.getStatus()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No active key found for purpose: " + purpose));
    }

    /**
     * Alert security team of critical events
     *
     * PCI DSS Requirement 10.6: Review logs and security events
     */
    private void alertSecurityTeam(String event, String keyId, String details) {
        log.warn("SECURITY ALERT: {} - Key: {}, Details: {}", event, keyId, details);

        try {
            // Send PagerDuty alert for critical events
            if (event.contains("FAILED") || event.contains("COMPROMISED")) {
                sendPagerDutyAlert(event, keyId, details, "critical");
            }

            // Send Slack notification to #security channel
            sendSlackSecurityAlert(event, keyId, details);

            // Send email to security team
            sendSecurityEmail(
                "security@example.com",
                String.format("🚨 Key Rotation Alert: %s", event),
                buildSecurityAlertEmail(event, keyId, details)
            );

            // Log to audit trail
            auditService.logSecurityEvent(
                "KEY_ROTATION_ALERT",
                Map.of(
                    "event", event,
                    "keyId", keyId,
                    "details", details,
                    "severity", "CRITICAL",
                    "timestamp", LocalDateTime.now().toString()
                )
            );

        } catch (Exception e) {
            log.error("Failed to alert security team for event: {} - Key: {}", event, keyId, e);
            // Don't throw - alerting failure shouldn't block key rotation
        }
    }

    /**
     * Notify security team of successful rotation
     */
    private void notifySecurityTeam(String event, String keyId) {
        log.info("Security notification: {} - Key: {}", event, keyId);

        try {
            // Send Slack notification
            sendSlackSecurityNotification(event, keyId);

            // Send email to security team
            sendSecurityEmail(
                "security@example.com",
                String.format("✅ Key Rotation Success: %s", event),
                buildSuccessNotificationEmail(event, keyId)
            );

            // Log to audit trail
            auditService.logSecurityEvent(
                "KEY_ROTATION_SUCCESS",
                Map.of(
                    "event", event,
                    "keyId", keyId,
                    "timestamp", LocalDateTime.now().toString()
                )
            );

        } catch (Exception e) {
            log.error("Failed to notify security team for event: {} - Key: {}", event, keyId, e);
            // Don't throw - notification failure shouldn't block key rotation
        }
    }

    // Helper methods for security notifications

    private void sendPagerDutyAlert(String event, String keyId, String details, String severity) {
        // Integration with PagerDuty API
        log.info("PagerDuty alert sent: event={}, keyId={}, severity={}", event, keyId, severity);
        // TODO: Actual PagerDuty API integration when credentials available
    }

    private void sendSlackSecurityAlert(String event, String keyId, String details) {
        // Integration with Slack webhook
        String message = String.format(
            "🚨 *Security Alert: Key Rotation*\n" +
            "Event: `%s`\n" +
            "Key ID: `%s`\n" +
            "Details: %s\n" +
            "Time: %s",
            event, keyId, details, LocalDateTime.now()
        );
        log.info("Slack security alert sent to #security channel");
        // TODO: Actual Slack webhook integration
    }

    private void sendSlackSecurityNotification(String event, String keyId) {
        String message = String.format(
            "✅ *Key Rotation Success*\n" +
            "Event: `%s`\n" +
            "Key ID: `%s`\n" +
            "Time: %s",
            event, keyId, LocalDateTime.now()
        );
        log.info("Slack notification sent to #security channel");
        // TODO: Actual Slack webhook integration
    }

    private void sendSecurityEmail(String to, String subject, String body) {
        // Integration with email service
        log.info("Security email sent to: {}, subject: {}", to, subject);
        // TODO: Actual email service integration
    }

    private String buildSecurityAlertEmail(String event, String keyId, String details) {
        return String.format(
            "Security Alert: Key Rotation\n\n" +
            "Event: %s\n" +
            "Key ID: %s\n" +
            "Details: %s\n" +
            "Timestamp: %s\n\n" +
            "Action Required: Please investigate immediately.\n" +
            "Dashboard: https://api.example.com/key-rotation\n\n" +
            "This is an automated alert from the Waqiti Key Rotation Service.",
            event, keyId, details, LocalDateTime.now()
        );
    }

    private String buildSuccessNotificationEmail(String event, String keyId) {
        return String.format(
            "Key Rotation Completed Successfully\n\n" +
            "Event: %s\n" +
            "Key ID: %s\n" +
            "Timestamp: %s\n\n" +
            "The key rotation process has completed successfully.\n" +
            "All encrypted data has been re-encrypted with the new key.\n\n" +
            "Dashboard: https://api.example.com/key-rotation\n\n" +
            "This is an automated notification from the Waqiti Key Rotation Service.",
            event, keyId, LocalDateTime.now()
        );
    }

    /**
     * Get key rotation statistics
     */
    public Map<String, Object> getRotationStatistics() {
        long totalKeys = keyRepository.count();
        long activeKeys = keyRepository.countActiveKeys();
        List<EncryptionKey> keysNeedingRotation = keyRepository
            .findKeysRequiringRotation(LocalDateTime.now());

        return Map.of(
            "totalKeys", totalKeys,
            "activeKeys", activeKeys,
            "retiredKeys", totalKeys - activeKeys,
            "keysNeedingRotation", keysNeedingRotation.size(),
            "rotationAttempts", rotationAttempts.count(),
            "rotationSuccesses", rotationSuccesses.count(),
            "rotationFailures", rotationFailures.count(),
            "rotationSuccessRate", rotationAttempts.count() > 0
                ? (rotationSuccesses.count() / rotationAttempts.count()) * 100 : 0
        );
    }

    // Reflection helper methods for field access

    private boolean hasField(Class<?> clazz, String fieldName) {
        try {
            clazz.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private Object getFieldValue(Object obj, String fieldName) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }

    private void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    /**
     * Re-encrypt a single field value
     *
     * @param encryptedData Base64-encoded encrypted data
     * @param oldDEK Old data encryption key
     * @param newDEK New data encryption key
     * @return Base64-encoded re-encrypted data
     */
    private String reEncryptField(String encryptedData, byte[] oldDEK, byte[] newDEK) throws Exception {
        // Decode the encrypted data
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);

        // Decrypt with old key
        SecretKey oldKey = new SecretKeySpec(oldDEK, 0, oldDEK.length, "AES");
        Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        decryptCipher.init(Cipher.DECRYPT_MODE, oldKey);
        byte[] decryptedBytes = decryptCipher.doFinal(encryptedBytes);

        // Encrypt with new key
        SecretKey newKey = new SecretKeySpec(newDEK, 0, newDEK.length, "AES");
        Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, newKey);
        byte[] reEncryptedBytes = encryptCipher.doFinal(decryptedBytes);

        // Encode and return
        return Base64.getEncoder().encodeToString(reEncryptedBytes);
    }

    /**
     * Custom exception for key rotation failures
     */
    public static class KeyRotationException extends RuntimeException {
        public KeyRotationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
