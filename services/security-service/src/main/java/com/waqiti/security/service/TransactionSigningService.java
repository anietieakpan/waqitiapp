package com.waqiti.security.service;

import com.waqiti.security.domain.SigningKey;
import com.waqiti.security.domain.SigningMethod;
import com.waqiti.security.domain.TransactionSignature;
import com.waqiti.security.dto.*;
import com.waqiti.security.exception.SigningException;
import com.waqiti.security.exception.InvalidSignatureException;
import com.waqiti.security.repository.SigningKeyRepository;
import com.waqiti.security.repository.TransactionSignatureRepository;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.vault.core.VaultTemplate;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionSigningService {

    private final SigningKeyRepository signingKeyRepository;
    private final TransactionSignatureRepository signatureRepository;
    private final HardwareKeyService hardwareKeyService;
    private final BiometricService biometricService;
    private final AuditService auditService;
    private final VaultTemplate vaultTemplate;
    
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String KEY_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;
    private static final int SIGNATURE_VALIDITY_MINUTES = 5;

    /**
     * Sign a transaction with the specified method
     */
    public TransactionSignatureDTO signTransaction(SignTransactionRequest request) {
        log.info("Signing transaction {} with method {}", request.getTransactionId(), request.getSigningMethod());
        
        // Validate transaction data
        validateTransactionData(request);
        
        // Check if already signed
        if (isTransactionSigned(request.getTransactionId())) {
            throw new BusinessException("Transaction already signed");
        }
        
        TransactionSignature signature;
        
        switch (request.getSigningMethod()) {
            case SOFTWARE_KEY:
                signature = signWithSoftwareKey(request);
                break;
            case HARDWARE_KEY:
                signature = signWithHardwareKey(request);
                break;
            case BIOMETRIC:
                signature = signWithBiometric(request);
                break;
            case MULTI_SIGNATURE:
                signature = initiateMultiSignature(request);
                break;
            default:
                throw new BusinessException("Unsupported signing method: " + request.getSigningMethod());
        }
        
        signature = signatureRepository.save(signature);
        
        // Audit the signing event
        auditService.logTransactionSigning(signature);
        
        return mapToDTO(signature);
    }

    /**
     * Verify a transaction signature
     */
    public SignatureVerificationResult verifySignature(String transactionId, String signatureValue) {
        log.info("Verifying signature for transaction {}", transactionId);
        
        TransactionSignature signature = signatureRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("Signature not found for transaction"));
        
        // Check signature expiry
        if (signature.isExpired()) {
            return SignatureVerificationResult.builder()
                .valid(false)
                .reason("Signature has expired")
                .build();
        }
        
        // Verify based on signing method
        boolean isValid;
        String verificationDetails;
        
        try {
            switch (signature.getSigningMethod()) {
                case SOFTWARE_KEY:
                    isValid = verifySoftwareKeySignature(signature, signatureValue);
                    verificationDetails = "Software key verification";
                    break;
                case HARDWARE_KEY:
                    isValid = verifyHardwareKeySignature(signature, signatureValue);
                    verificationDetails = "Hardware key verification";
                    break;
                case BIOMETRIC:
                    isValid = verifyBiometricSignature(signature, signatureValue);
                    verificationDetails = "Biometric verification";
                    break;
                case MULTI_SIGNATURE:
                    isValid = verifyMultiSignature(signature, signatureValue);
                    verificationDetails = "Multi-signature verification";
                    break;
                default:
                    throw new BusinessException("Unknown signing method");
            }
            
            // Update verification count
            signature.incrementVerificationCount();
            signatureRepository.save(signature);
            
            // Audit verification
            auditService.logSignatureVerification(transactionId, isValid);
            
            return SignatureVerificationResult.builder()
                .valid(isValid)
                .signatureId(signature.getId())
                .signingMethod(signature.getSigningMethod())
                .signedAt(signature.getSignedAt())
                .verificationDetails(verificationDetails)
                .build();
                
        } catch (Exception e) {
            log.error("Error verifying signature", e);
            return SignatureVerificationResult.builder()
                .valid(false)
                .reason("Verification failed: " + e.getMessage())
                .build();
        }
    }

    /**
     * Generate new signing key pair
     */
    public SigningKeyDTO generateSigningKey(String userId, SigningMethod method) {
        log.info("Generating signing key for user {} with method {}", userId, method);
        
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyGen.initialize(KEY_SIZE, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            
            SigningKey signingKey = SigningKey.builder()
                .userId(userId)
                .keyId(UUID.randomUUID().toString())
                .publicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
                .privateKeyEncrypted(encryptPrivateKey(keyPair.getPrivate()))
                .signingMethod(method)
                .algorithm(KEY_ALGORITHM)
                .keySize(KEY_SIZE)
                .isActive(true)
                .lastUsedAt(LocalDateTime.now())
                .build();
            
            signingKey = signingKeyRepository.save(signingKey);
            
            return mapKeyToDTO(signingKey);
            
        } catch (Exception e) {
            log.error("Error generating signing key", e);
            throw new SigningException("Failed to generate signing key", e);
        }
    }

    /**
     * Register a hardware key for transaction signing
     */
    public HardwareKeyDTO registerHardwareKey(RegisterHardwareKeyRequest request) {
        log.info("Registering hardware key for user {}", request.getUserId());
        
        // Verify hardware key challenge
        if (!hardwareKeyService.verifyChallengeResponse(
                request.getChallengeId(), 
                request.getChallengeResponse())) {
            throw new BusinessException("Hardware key verification failed");
        }
        
        // Extract public key from hardware device
        PublicKey publicKey = hardwareKeyService.extractPublicKey(request.getDeviceInfo());
        
        SigningKey signingKey = SigningKey.builder()
            .userId(request.getUserId())
            .keyId(UUID.randomUUID().toString())
            .publicKey(Base64.getEncoder().encodeToString(publicKey.getEncoded()))
            .signingMethod(SigningMethod.HARDWARE_KEY)
            .algorithm(publicKey.getAlgorithm())
            .hardwareDeviceId(request.getDeviceInfo().getDeviceId())
            .hardwareDeviceType(request.getDeviceInfo().getDeviceType())
            .certificateChain(request.getDeviceInfo().getCertificateChain())
            .isActive(true)
            .build();
        
        signingKey = signingKeyRepository.save(signingKey);
        
        return HardwareKeyDTO.builder()
            .keyId(signingKey.getKeyId())
            .deviceId(signingKey.getHardwareDeviceId())
            .deviceType(signingKey.getHardwareDeviceType())
            .registeredAt(signingKey.getCreatedAt())
            .lastUsed(signingKey.getLastUsedAt())
            .status("ACTIVE")
            .build();
    }

    /**
     * List user's signing keys
     */
    public List<SigningKeyDTO> getUserSigningKeys(String userId) {
        return signingKeyRepository.findByUserIdAndIsActiveTrue(userId).stream()
            .map(this::mapKeyToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Revoke a signing key
     */
    public void revokeSigningKey(String userId, String keyId, String reason) {
        SigningKey signingKey = signingKeyRepository.findByUserIdAndKeyId(userId, keyId)
            .orElseThrow(() -> new ResourceNotFoundException("Signing key not found"));
        
        signingKey.revoke(reason);
        signingKeyRepository.save(signingKey);
        
        // Audit key revocation
        auditService.logKeyRevocation(userId, keyId, reason);
    }

    /**
     * Enable multi-signature for high-value transactions
     */
    public MultiSignatureConfigDTO configureMultiSignature(ConfigureMultiSignatureRequest request) {
        log.info("Configuring multi-signature for user {}", request.getUserId());
        
        // Validate signers
        if (request.getRequiredSignatures() > request.getSigners().size()) {
            throw new BusinessException("Required signatures cannot exceed number of signers");
        }
        
        // Create multi-sig configuration
        MultiSignatureConfig config = MultiSignatureConfig.builder()
            .userId(request.getUserId())
            .configId(UUID.randomUUID().toString())
            .requiredSignatures(request.getRequiredSignatures())
            .signers(request.getSigners())
            .thresholdAmount(request.getThresholdAmount())
            .thresholdCurrency(request.getThresholdCurrency())
            .isActive(true)
            .build();
        
        // Save configuration
        // multiSigRepository.save(config);
        
        return MultiSignatureConfigDTO.builder()
            .configId(config.getConfigId())
            .requiredSignatures(config.getRequiredSignatures())
            .totalSigners(config.getSigners().size())
            .thresholdAmount(config.getThresholdAmount())
            .status("ACTIVE")
            .build();
    }

    // Private helper methods

    private TransactionSignature signWithSoftwareKey(SignTransactionRequest request) {
        SigningKey signingKey = signingKeyRepository
            .findActiveKeyForUser(request.getUserId(), SigningMethod.SOFTWARE_KEY)
            .orElseThrow(() -> new BusinessException("No active software key found"));
        
        try {
            // Decrypt private key
            PrivateKey privateKey = decryptPrivateKey(signingKey.getPrivateKeyEncrypted());
            
            // Create signature
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(createSignaturePayload(request));
            
            byte[] signatureBytes = signature.sign();
            String signatureValue = Base64.getEncoder().encodeToString(signatureBytes);
            
            return TransactionSignature.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .signingKeyId(signingKey.getKeyId())
                .signatureValue(signatureValue)
                .signatureHash(calculateHash(signatureValue))
                .signingMethod(SigningMethod.SOFTWARE_KEY)
                .transactionData(request.getTransactionData())
                .deviceInfo(request.getDeviceInfo())
                .ipAddress(request.getIpAddress())
                .signedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(SIGNATURE_VALIDITY_MINUTES))
                .build();
                
        } catch (Exception e) {
            throw new SigningException("Failed to sign with software key", e);
        }
    }

    private TransactionSignature signWithHardwareKey(SignTransactionRequest request) {
        // Verify hardware key is connected
        if (!hardwareKeyService.isDeviceConnected(request.getHardwareDeviceId())) {
            throw new BusinessException("Hardware key not connected");
        }
        
        SigningKey signingKey = signingKeyRepository
            .findByHardwareDeviceId(request.getHardwareDeviceId())
            .orElseThrow(() -> new BusinessException("Hardware key not registered"));
        
        try {
            // Request signature from hardware device
            byte[] payload = createSignaturePayload(request);
            HardwareSignatureResponse hwResponse = hardwareKeyService.requestSignature(
                request.getHardwareDeviceId(),
                payload,
                request.getPinCode()
            );
            
            if (!hwResponse.isSuccess()) {
                throw new SigningException("Hardware signing failed: " + hwResponse.getError());
            }
            
            return TransactionSignature.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .signingKeyId(signingKey.getKeyId())
                .signatureValue(hwResponse.getSignature())
                .signatureHash(calculateHash(hwResponse.getSignature()))
                .signingMethod(SigningMethod.HARDWARE_KEY)
                .hardwareDeviceId(request.getHardwareDeviceId())
                .hardwareAttestationData(hwResponse.getAttestationData())
                .transactionData(request.getTransactionData())
                .deviceInfo(request.getDeviceInfo())
                .ipAddress(request.getIpAddress())
                .signedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(SIGNATURE_VALIDITY_MINUTES))
                .build();
                
        } catch (Exception e) {
            throw new SigningException("Failed to sign with hardware key", e);
        }
    }

    private TransactionSignature signWithBiometric(SignTransactionRequest request) {
        // Create verification request
        BiometricVerificationRequest verificationRequest = BiometricVerificationRequest.builder()
                .userId(request.getUserId())
                .biometricType(request.getBiometricData().getType())
                .biometricData(request.getBiometricData().getData())
                .build();
        
        // Verify biometric authentication
        BiometricAuthResponse bioResult = biometricService.verifyBiometric(verificationRequest);
        
        if (!bioResult.isSuccess()) {
            throw new BusinessException("Biometric authentication failed: " + bioResult.getErrorMessage());
        }
        
        // Use biometric-derived key for signing
        try {
            // Generate biometric signature using the matched template
            String payloadString = new String(createSignaturePayload(request), StandardCharsets.UTF_8);
            String biometricSignature = generateBiometricSignature(
                request.getUserId(),
                payloadString,
                bioResult.getTemplateId()
            );
            
            return TransactionSignature.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .signatureValue(biometricSignature)
                .signatureHash(calculateHash(biometricSignature))
                .signingMethod(SigningMethod.BIOMETRIC)
                .biometricType(request.getBiometricData().getType())
                .biometricScore(bioResult.getMatchScore())
                .transactionData(request.getTransactionData())
                .deviceInfo(request.getDeviceInfo())
                .ipAddress(request.getIpAddress())
                .signedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(SIGNATURE_VALIDITY_MINUTES))
                .build();
                
        } catch (Exception e) {
            throw new SigningException("Failed to sign with biometric", e);
        }
    }

    private TransactionSignature initiateMultiSignature(SignTransactionRequest request) {
        // Create pending multi-signature transaction
        TransactionSignature multiSig = TransactionSignature.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .signingMethod(SigningMethod.MULTI_SIGNATURE)
            .multiSigConfigId(request.getMultiSigConfigId())
            .requiredSignatures(request.getRequiredSignatures())
            .collectedSignatures(new ArrayList<>())
            .signerIds(request.getSignerIds())
            .transactionData(request.getTransactionData())
            .status(SignatureStatus.PENDING_SIGNATURES)
            .signedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(24)) // 24 hour window for multi-sig
            .build();
        
        // Add initiator's signature
        String initiatorSignature = signWithSoftwareKey(request).getSignatureValue();
        multiSig.addSignature(request.getUserId(), initiatorSignature);
        
        // Notify other signers
        notifySigners(multiSig);
        
        return multiSig;
    }

    private boolean verifySoftwareKeySignature(TransactionSignature signature, String signatureValue) {
        try {
            SigningKey signingKey = signingKeyRepository.findByKeyId(signature.getSigningKeyId())
                .orElseThrow(() -> new BusinessException("Signing key not found"));
            
            // Decode public key
            byte[] publicKeyBytes = Base64.getDecoder().decode(signingKey.getPublicKey());
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            
            // Verify signature
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(signature.getTransactionData().getBytes(StandardCharsets.UTF_8));
            
            byte[] signatureBytes = Base64.getDecoder().decode(signatureValue);
            return sig.verify(signatureBytes);
            
        } catch (Exception e) {
            log.error("Error verifying software key signature", e);
            return false;
        }
    }

    private boolean verifyHardwareKeySignature(TransactionSignature signature, String signatureValue) {
        return hardwareKeyService.verifySignature(
            signature.getHardwareDeviceId(),
            signature.getTransactionData().getBytes(StandardCharsets.UTF_8),
            signatureValue,
            signature.getHardwareAttestationData()
        );
    }

    private boolean verifyBiometricSignature(TransactionSignature signature, String signatureValue) {
        return verifyBiometricSignatureInternal(
            signature.getUserId(),
            signature.getTransactionData(),
            signatureValue
        );
    }
    
    private String generateBiometricSignature(String userId, String data, String templateId) throws Exception {
        // Generate a deterministic signature based on biometric template and data
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String combined = userId + "|" + templateId + "|" + data;
        byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
    
    private boolean verifyBiometricSignatureInternal(String userId, String data, String signature) {
        try {
            // This would typically verify against the stored biometric template
            // For now, we'll do a hash-based verification
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expectedHash = digest.digest((userId + "|" + data).getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(expectedHash);
            
            return signature.equals(expectedSignature);
        } catch (Exception e) {
            log.error("Error verifying biometric signature", e);
            return false;
        }
    }

    private boolean verifyMultiSignature(TransactionSignature signature, String signatureValue) {
        // Check if we have enough signatures
        if (signature.getCollectedSignatures().size() < signature.getRequiredSignatures()) {
            return false;
        }
        
        // Verify each individual signature
        for (MultiSignature multiSig : signature.getCollectedSignatures()) {
            SigningKey signerKey = signingKeyRepository
                .findActiveKeyForUser(multiSig.getSignerId(), SigningMethod.SOFTWARE_KEY)
                .orElse(null);
                
            if (signerKey == null || !verifySingleSignature(signerKey, multiSig.getSignature(), signature.getTransactionData())) {
                return false;
            }
        }
        
        return true;
    }

    private boolean verifySingleSignature(SigningKey key, String signature, String data) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(key.getPublicKey());
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            
            return sig.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] createSignaturePayload(SignTransactionRequest request) {
        // Create canonical representation of transaction data
        StringBuilder payload = new StringBuilder();
        payload.append(request.getTransactionId()).append("|");
        payload.append(request.getUserId()).append("|");
        payload.append(request.getTransactionData()).append("|");
        payload.append(request.getTimestamp()).append("|");
        payload.append(request.getNonce());
        
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String encryptPrivateKey(PrivateKey privateKey) throws Exception {
        // Use master key encryption
        SecretKey masterKey = getMasterKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, masterKey);
        
        byte[] encryptedKey = cipher.doFinal(privateKey.getEncoded());
        byte[] iv = cipher.getIV();
        
        // Combine IV and encrypted key
        byte[] combined = new byte[iv.length + encryptedKey.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedKey, 0, combined, iv.length, encryptedKey.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }

    private PrivateKey decryptPrivateKey(String encryptedKey) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedKey);
        
        // Extract IV and encrypted key
        byte[] iv = Arrays.copyOfRange(combined, 0, 12);
        byte[] encrypted = Arrays.copyOfRange(combined, 12, combined.length);
        
        // Decrypt
        SecretKey masterKey = getMasterKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new javax.crypto.spec.GCMParameterSpec(128, iv));
        
        byte[] keyBytes = cipher.doFinal(encrypted);
        
        // Reconstruct private key
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return keyFactory.generatePrivate(keySpec);
    }

    private SecretKey getMasterKey() throws Exception {
        try {
            // SECURITY FIX: Get master key from Vault instead of hardcoded placeholder
            String masterKeyBase64 = vaultTemplate.read("secret/transaction-signing")
                .getData()
                .get("master-key")
                .toString();
            
            if (masterKeyBase64 != null && !masterKeyBase64.isEmpty()) {
                byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
                return new SecretKeySpec(keyBytes, "AES");
            }
            
            // Fallback: generate a new secure 256-bit key if none exists
            log.warn("SECURITY: No master key found in Vault, generating new secure key");
            SecureRandom secureRandom = new SecureRandom();
            byte[] newKey = new byte[32]; // 256-bit AES key
            secureRandom.nextBytes(newKey);
            
            // Store the new key in Vault
            Map<String, Object> keyData = Map.of(
                "master-key", Base64.getEncoder().encodeToString(newKey),
                "created_at", LocalDateTime.now().toString(),
                "algorithm", "AES-256",
                "purpose", "transaction-signing"
            );
            
            vaultTemplate.write("secret/transaction-signing", keyData);
            
            return new SecretKeySpec(newKey, "AES");
            
        } catch (Exception e) {
            log.error("CRITICAL SECURITY ERROR: Failed to get transaction signing master key", e);
            throw new SecurityException("Unable to retrieve secure master key for transaction signing");
        }
    }

    private String calculateHash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new SigningException("Failed to calculate hash", e);
        }
    }

    private void validateTransactionData(SignTransactionRequest request) {
        if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
            throw new BusinessException("Transaction ID is required");
        }
        
        if (request.getTransactionData() == null || request.getTransactionData().isEmpty()) {
            throw new BusinessException("Transaction data is required");
        }
        
        if (request.getTimestamp() == null || request.getTimestamp() < System.currentTimeMillis() - 300000) {
            throw new BusinessException("Invalid or expired timestamp");
        }
        
        if (request.getNonce() == null || request.getNonce().isEmpty()) {
            throw new BusinessException("Nonce is required");
        }
    }

    private boolean isTransactionSigned(String transactionId) {
        return signatureRepository.existsByTransactionIdAndStatusNot(
            transactionId, 
            SignatureStatus.REVOKED
        );
    }

    private void notifySigners(TransactionSignature multiSig) {
        // Send notifications to required signers
        for (String signerId : multiSig.getSignerIds()) {
            if (!signerId.equals(multiSig.getUserId())) {
                // Send notification
                log.info("Notifying signer {} for multi-sig transaction {}", 
                    signerId, multiSig.getTransactionId());
            }
        }
    }

    private TransactionSignatureDTO mapToDTO(TransactionSignature signature) {
        return TransactionSignatureDTO.builder()
            .signatureId(signature.getId())
            .transactionId(signature.getTransactionId())
            .signatureValue(signature.getSignatureValue())
            .signingMethod(signature.getSigningMethod())
            .signedAt(signature.getSignedAt())
            .expiresAt(signature.getExpiresAt())
            .status(signature.getStatus())
            .verificationCount(signature.getVerificationCount())
            .build();
    }

    private SigningKeyDTO mapKeyToDTO(SigningKey key) {
        return SigningKeyDTO.builder()
            .keyId(key.getKeyId())
            .userId(key.getUserId())
            .signingMethod(key.getSigningMethod())
            .algorithm(key.getAlgorithm())
            .publicKey(key.getPublicKey())
            .isActive(key.isActive())
            .createdAt(key.getCreatedAt())
            .lastUsedAt(key.getLastUsedAt())
            .build();
    }
}