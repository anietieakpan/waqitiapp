package com.waqiti.common.security.biometric;

import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.security.hsm.HSMProvider;
import com.waqiti.common.security.credential.CredentialManagementService;
import com.waqiti.common.security.SecureSystemUtils;
import com.waqiti.common.audit.ComprehensiveAuditService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Comprehensive Biometric Authentication Service with HSM Integration
 * Provides secure biometric enrollment, authentication, and management
 * with fallback mechanisms for various security module availability
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiometricAuthenticationService {
    
    private final EncryptionService encryptionService;
    private final HSMProvider hsmProvider;
    private final CredentialManagementService credentialService;
    private final ComprehensiveAuditService auditService;
    private final SecureSystemUtils secureSystemUtils;
    
    @Value("${biometric.hsm.enabled:true}")
    private boolean hsmEnabled;
    
    @Value("${biometric.hsm.fallback.tpm:true}")
    private boolean tpmFallbackEnabled;
    
    @Value("${biometric.hsm.fallback.vault:true}")
    private boolean vaultFallbackEnabled;
    
    @Value("${biometric.template.max-age-days:90}")
    private int templateMaxAgeDays;
    
    @Value("${biometric.match.threshold:0.85}")
    private double matchThreshold;
    
    @Value("${biometric.liveness.required:true}")
    private boolean livenessRequired;
    
    @Value("${biometric.multi-factor.required:false}")
    private boolean multiFactorRequired;
    
    @Value("${biometric.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${biometric.cache.ttl.minutes:5}")
    private int cacheTtlMinutes;
    
    // Biometric template storage
    private final Map<String, BiometricProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, CachedTemplate> templateCache = new ConcurrentHashMap<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // Security modules availability
    private boolean hsmAvailable = false;
    private boolean tpmAvailable = false;
    private boolean vaultAvailable = false;
    
    // Secure random for cryptographic operations
    private final SecureRandom secureRandom = new SecureRandom();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Biometric Authentication Service");
        
        // Check available security modules
        checkSecurityModules();
        
        // Initialize biometric subsystems
        initializeBiometricSubsystems();
        
        log.info("Biometric Authentication Service initialized - HSM: {}, TPM: {}, Vault: {}", 
            hsmAvailable, tpmAvailable, vaultAvailable);
    }
    
    /**
     * Enroll user biometric data
     */
    @Transactional
    public BiometricEnrollmentResult enrollBiometric(BiometricEnrollmentRequest request) {
        log.info("Enrolling biometric for user: {}, type: {}", request.getUserId(), request.getBiometricType());
        
        try {
            // Validate enrollment request
            validateEnrollmentRequest(request);

            // Check liveness if required
            if (livenessRequired) {
                try {
                    if (!verifyLiveness(request)) {
                        throw new BiometricException("Liveness check failed");
                    }
                } catch (BiometricAuthenticationNotConfiguredException e) {
                    // SECURITY: Liveness detection not configured - reject enrollment
                    log.error("Cannot enroll biometric - liveness detection not configured: {}", e.getMessage());
                    auditService.auditSecurityEvent("BIOMETRIC_ENROLLMENT_REJECTED_NO_LIVENESS",
                        Map.of(
                            "userId", request.getUserId(),
                            "biometricType", request.getBiometricType().toString(),
                            "reason", "LIVENESS_NOT_CONFIGURED"
                        ));
                    throw new BiometricException("Biometric enrollment failed: " + e.getMessage(), e);
                }
            }
            
            // Extract biometric template
            BiometricTemplate template = extractTemplate(request);
            
            // Generate cancelable biometric
            CancelableBiometric cancelable = generateCancelableBiometric(template);
            
            // Encrypt and store template
            String templateId = storeTemplate(request.getUserId(), cancelable);
            
            // Create biometric profile
            BiometricProfile profile = createProfile(request.getUserId(), request.getBiometricType(), templateId);
            userProfiles.put(request.getUserId(), profile);
            
            // Generate recovery codes
            List<String> recoveryCodes = generateRecoveryCodes(request.getUserId());
            
            // Audit enrollment
            auditEnrollment(request.getUserId(), request.getBiometricType(), true);
            
            return BiometricEnrollmentResult.builder()
                .success(true)
                .enrollmentId(templateId)
                .biometricType(request.getBiometricType())
                .recoveryCodes(recoveryCodes)
                .enrolledAt(Instant.now())
                .expiresAt(Instant.now().plus(templateMaxAgeDays, ChronoUnit.DAYS))
                .build();
            
        } catch (Exception e) {
            log.error("Biometric enrollment failed for user: {}", request.getUserId(), e);
            auditEnrollment(request.getUserId(), request.getBiometricType(), false);
            throw new BiometricException("Enrollment failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Authenticate user with biometric
     */
    public BiometricAuthenticationResult authenticate(BiometricAuthenticationRequest request) {
        log.debug("Authenticating user: {} with biometric", request.getUserId());
        
        try {
            // Get user profile
            BiometricProfile profile = userProfiles.get(request.getUserId());
            if (profile == null) {
                return BiometricAuthenticationResult.failure("No biometric enrolled");
            }
            
            // Check template expiry
            if (profile.isExpired()) {
                return BiometricAuthenticationResult.failure("Biometric template expired");
            }
            
            // Verify liveness if required
            if (livenessRequired) {
                try {
                    if (!verifyLiveness(request)) {
                        recordFailedAttempt(request.getUserId(), "Liveness check failed");
                        return BiometricAuthenticationResult.failure("Liveness check failed");
                    }
                } catch (BiometricAuthenticationNotConfiguredException e) {
                    // SECURITY: Liveness detection not configured - reject authentication
                    log.error("Cannot authenticate biometric - liveness detection not configured: {}", e.getMessage());
                    auditService.auditSecurityEvent("BIOMETRIC_AUTH_REJECTED_NO_LIVENESS",
                        Map.of(
                            "userId", request.getUserId(),
                            "biometricType", request.getBiometricType().toString(),
                            "reason", "LIVENESS_NOT_CONFIGURED",
                            "severity", "CRITICAL"
                        ));
                    recordFailedAttempt(request.getUserId(), "Liveness detection not configured");
                    return BiometricAuthenticationResult.failure(
                        "Biometric authentication failed: " + e.getMessage());
                }
            }
            
            // Extract template from authentication request
            BiometricTemplate candidateTemplate = extractTemplate(request);
            
            // Retrieve stored template
            BiometricTemplate storedTemplate = retrieveTemplate(profile.getTemplateId());
            
            // Perform matching
            double matchScore = performMatching(candidateTemplate, storedTemplate);
            
            // Check threshold
            boolean matched = matchScore >= matchThreshold;
            
            if (matched) {
                // Check if multi-factor is required
                if (multiFactorRequired && !request.hasSecondFactor()) {
                    return BiometricAuthenticationResult.builder()
                        .success(false)
                        .requiresSecondFactor(true)
                        .message("Second factor required")
                        .build();
                }
                
                // Update profile with successful authentication
                profile.recordSuccessfulAuth();
                
                // Generate session token
                String sessionToken = generateSessionToken(request.getUserId());
                
                // Audit successful authentication
                auditAuthentication(request.getUserId(), true, matchScore);
                
                return BiometricAuthenticationResult.builder()
                    .success(true)
                    .matchScore(matchScore)
                    .sessionToken(sessionToken)
                    .authenticatedAt(Instant.now())
                    .build();
                
            } else {
                // Record failed attempt
                recordFailedAttempt(request.getUserId(), "Match score below threshold");
                
                // Check if account should be locked
                if (profile.getFailedAttempts() >= 5) {
                    lockAccount(request.getUserId());
                    return BiometricAuthenticationResult.failure("Account locked due to multiple failed attempts");
                }
                
                // Audit failed authentication
                auditAuthentication(request.getUserId(), false, matchScore);
                
                return BiometricAuthenticationResult.failure("Biometric match failed");
            }
            
        } catch (Exception e) {
            log.error("Biometric authentication failed for user: {}", request.getUserId(), e);
            return BiometricAuthenticationResult.failure("Authentication error: " + e.getMessage());
        }
    }
    
    /**
     * Update biometric template (adaptive update)
     */
    @Transactional
    public void updateTemplate(String userId, BiometricTemplate newTemplate) {
        log.info("Updating biometric template for user: {}", userId);
        
        try {
            BiometricProfile profile = userProfiles.get(userId);
            if (profile == null) {
                throw new BiometricException("No biometric profile found");
            }
            
            // Retrieve current template
            BiometricTemplate currentTemplate = retrieveTemplate(profile.getTemplateId());
            
            // Merge templates (adaptive update)
            BiometricTemplate mergedTemplate = mergeTemplates(currentTemplate, newTemplate);
            
            // Store updated template
            String newTemplateId = storeTemplate(userId, 
                generateCancelableBiometric(mergedTemplate));
            
            // Update profile
            profile.setTemplateId(newTemplateId);
            profile.setLastUpdated(Instant.now());
            
            // Audit template update
            auditService.auditSecurityEvent("BIOMETRIC_TEMPLATE_UPDATE", 
                Map.of("userId", userId, "templateId", newTemplateId));
            
        } catch (Exception e) {
            log.error("Failed to update biometric template for user: {}", userId, e);
            throw new BiometricException("Template update failed", e);
        }
    }
    
    /**
     * Revoke biometric enrollment
     */
    @Transactional
    public void revokeBiometric(String userId, String reason) {
        log.warn("Revoking biometric for user: {}, reason: {}", userId, reason);
        
        try {
            BiometricProfile profile = userProfiles.remove(userId);
            if (profile != null) {
                // Delete template from secure storage
                deleteTemplate(profile.getTemplateId());
                
                // Invalidate cache
                invalidateCache(userId);
                
                // Audit revocation
                auditService.auditSecurityEvent("BIOMETRIC_REVOKED", 
                    Map.of("userId", userId, "reason", reason));
            }
            
        } catch (Exception e) {
            log.error("Failed to revoke biometric for user: {}", userId, e);
            throw new BiometricException("Revocation failed", e);
        }
    }
    
    // Template extraction and processing
    
    private BiometricTemplate extractTemplate(BiometricData data) {
        log.debug("Extracting biometric template");
        
        switch (data.getBiometricType()) {
            case FINGERPRINT:
                return extractFingerprintTemplate(data);
            case FACE:
                return extractFaceTemplate(data);
            case VOICE:
                return extractVoiceTemplate(data);
            case IRIS:
                return extractIrisTemplate(data);
            default:
                throw new BiometricException("Unsupported biometric type: " + data.getBiometricType());
        }
    }
    
    private BiometricTemplate extractFingerprintTemplate(BiometricData data) {
        // Extract minutiae points and create template
        byte[] rawData = data.getRawData();
        
        // Apply feature extraction algorithm
        List<MinutiaePoint> minutiae = extractMinutiae(rawData);
        
        // Create template
        return BiometricTemplate.builder()
            .type(BiometricType.FINGERPRINT)
            .features(serializeMinutiae(minutiae))
            .quality(assessQuality(rawData))
            .timestamp(Instant.now())
            .build();
    }
    
    private BiometricTemplate extractFaceTemplate(BiometricData data) {
        // Extract facial features and create template
        byte[] rawData = data.getRawData();
        
        // Apply face recognition algorithm (e.g., FaceNet, dlib)
        double[] faceEmbedding = extractFaceEmbedding(rawData);
        
        return BiometricTemplate.builder()
            .type(BiometricType.FACE)
            .features(serializeEmbedding(faceEmbedding))
            .quality(assessFaceQuality(rawData))
            .timestamp(Instant.now())
            .build();
    }
    
    private BiometricTemplate extractVoiceTemplate(BiometricData data) {
        // Extract voice features
        byte[] rawData = data.getRawData();
        
        // Apply voice recognition (e.g., MFCC features)
        double[] voiceFeatures = extractVoiceFeatures(rawData);
        
        return BiometricTemplate.builder()
            .type(BiometricType.VOICE)
            .features(serializeEmbedding(voiceFeatures))
            .quality(assessVoiceQuality(rawData))
            .timestamp(Instant.now())
            .build();
    }
    
    private BiometricTemplate extractIrisTemplate(BiometricData data) {
        // Extract iris features
        byte[] rawData = data.getRawData();
        
        // Apply iris recognition algorithm (e.g., IrisCode)
        byte[] irisCode = extractIrisCode(rawData);
        
        return BiometricTemplate.builder()
            .type(BiometricType.IRIS)
            .features(irisCode)
            .quality(assessIrisQuality(rawData))
            .timestamp(Instant.now())
            .build();
    }
    
    // Cancelable biometric generation
    
    private CancelableBiometric generateCancelableBiometric(BiometricTemplate template) {
        log.debug("Generating cancelable biometric");
        
        try {
            // Generate random transformation key
            byte[] transformKey = new byte[32];
            secureRandom.nextBytes(transformKey);
            
            // Apply non-invertible transformation
            byte[] transformedTemplate = applyTransformation(template.getFeatures(), transformKey);
            
            // Generate revocation token
            String revocationToken = generateRevocationToken();
            
            return CancelableBiometric.builder()
                .transformedTemplate(transformedTemplate)
                .transformKeyId(storeTransformKey(transformKey))
                .revocationToken(revocationToken)
                .createdAt(Instant.now())
                .build();
            
        } catch (Exception e) {
            throw new BiometricException("Failed to generate cancelable biometric", e);
        }
    }
    
    private byte[] applyTransformation(byte[] template, byte[] key) {
        try {
            // Apply non-invertible transformation (e.g., BioHashing)
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(key);
            digest.update(template);
            
            // Multiple rounds for security
            byte[] result = digest.digest();
            for (int i = 0; i < 1000; i++) {
                digest.reset();
                digest.update(result);
                digest.update(key);
                result = digest.digest();
            }
            
            return result;
            
        } catch (Exception e) {
            throw new BiometricException("Transformation failed", e);
        }
    }
    
    // Template storage with HSM/TPM/Vault fallback
    
    private String storeTemplate(String userId, CancelableBiometric cancelable) {
        log.debug("Storing biometric template for user: {}", userId);
        
        String templateId = UUID.randomUUID().toString();
        
        try {
            byte[] encryptedTemplate;
            String storageMethod;
            
            if (hsmAvailable && hsmEnabled) {
                // Store in HSM
                encryptedTemplate = storeInHSM(templateId, cancelable);
                storageMethod = "HSM";
                
            } else if (tpmAvailable && tpmFallbackEnabled) {
                // Store in TPM
                encryptedTemplate = storeInTPM(templateId, cancelable);
                storageMethod = "TPM";
                
            } else if (vaultAvailable && vaultFallbackEnabled) {
                // Store in Vault
                encryptedTemplate = storeInVault(templateId, cancelable);
                storageMethod = "VAULT";
                
            } else {
                // Software-based encryption as last resort
                encryptedTemplate = encryptWithSoftware(cancelable);
                storageMethod = "SOFTWARE";
                log.warn("Using software encryption for biometric template - less secure");
            }
            
            // Store metadata
            BiometricTemplateMetadata metadata = BiometricTemplateMetadata.builder()
                .templateId(templateId)
                .userId(userId)
                .storageMethod(storageMethod)
                .encryptedData(Base64.getEncoder().encodeToString(encryptedTemplate))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(templateMaxAgeDays, ChronoUnit.DAYS))
                .build();
            
            // Persist metadata
            persistTemplateMetadata(metadata);
            
            log.info("Template stored successfully using {}: {}", storageMethod, templateId);
            return templateId;
            
        } catch (Exception e) {
            log.error("Failed to store template", e);
            throw new BiometricException("Template storage failed", e);
        }
    }
    
    private byte[] storeInHSM(String templateId, CancelableBiometric cancelable) throws Exception {
        log.debug("Storing template in HSM");
        
        // Generate AES key in HSM
        String keyId = "biometric-" + templateId;
        hsmProvider.generateSecretKey(keyId, "AES", 256, null);
        
        // Encrypt template using HSM
        return hsmProvider.encrypt(keyId, serializeCancelable(cancelable));
    }
    
    private byte[] storeInTPM(String templateId, CancelableBiometric cancelable) throws Exception {
        log.debug("Storing template in TPM");
        
        // TPM operations (platform-specific)
        // This would use JNI or JNA to interact with TPM
        return encryptWithTPM(serializeCancelable(cancelable));
    }
    
    private byte[] storeInVault(String templateId, CancelableBiometric cancelable) throws Exception {
        log.debug("Storing template in Vault");
        
        // Encrypt and store in Vault
        String encryptedData = encryptionService.encrypt(serializeCancelable(cancelable));
        
        credentialService.storeCredential(
            "biometric/" + templateId,
            "template",
            encryptedData,
            Map.of("userId", cancelable.toString(), "type", "BIOMETRIC")
        );
        
        return encryptedData.getBytes(StandardCharsets.UTF_8);
    }
    
    private byte[] encryptWithSoftware(CancelableBiometric cancelable) throws Exception {
        // Software-based AES encryption
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey key = keyGen.generateKey();
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        
        byte[] encrypted = cipher.doFinal(serializeCancelable(cancelable));
        
        // Combine IV and encrypted data
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
        buffer.put(iv);
        buffer.put(encrypted);
        
        return buffer.array();
    }
    
    // Template retrieval
    
    private BiometricTemplate retrieveTemplate(String templateId) {
        log.debug("Retrieving template: {}", templateId);
        
        // Check cache first
        if (cacheEnabled) {
            CachedTemplate cached = getFromCache(templateId);
            if (cached != null && !cached.isExpired()) {
                return cached.getTemplate();
            }
        }
        
        try {
            // Retrieve metadata
            BiometricTemplateMetadata metadata = getTemplateMetadata(templateId);
            
            // Decrypt based on storage method
            byte[] decrypted;
            switch (metadata.getStorageMethod()) {
                case "HSM":
                    decrypted = retrieveFromHSM(templateId, metadata);
                    break;
                case "TPM":
                    decrypted = retrieveFromTPM(templateId, metadata);
                    break;
                case "VAULT":
                    decrypted = retrieveFromVault(templateId, metadata);
                    break;
                default:
                    decrypted = decryptWithSoftware(metadata.getEncryptedData());
            }
            
            // Deserialize cancelable biometric
            CancelableBiometric cancelable = deserializeCancelable(decrypted);
            
            // Extract original template (for matching)
            BiometricTemplate template = reconstructTemplate(cancelable);
            
            // Update cache
            if (cacheEnabled) {
                cacheTemplate(templateId, template);
            }
            
            return template;
            
        } catch (Exception e) {
            log.error("Failed to retrieve template: {}", templateId, e);
            throw new BiometricException("Template retrieval failed", e);
        }
    }
    
    // Biometric matching
    
    private double performMatching(BiometricTemplate candidate, BiometricTemplate stored) {
        log.debug("Performing biometric matching");
        
        if (candidate.getType() != stored.getType()) {
            throw new BiometricException("Biometric type mismatch");
        }
        
        switch (candidate.getType()) {
            case FINGERPRINT:
                return matchFingerprints(candidate, stored);
            case FACE:
                return matchFaces(candidate, stored);
            case VOICE:
                return matchVoices(candidate, stored);
            case IRIS:
                return matchIrises(candidate, stored);
            default:
                throw new BiometricException("Unsupported biometric type for matching");
        }
    }
    
    private double matchFingerprints(BiometricTemplate candidate, BiometricTemplate stored) {
        // Minutiae-based matching
        List<MinutiaePoint> candidateMinutiae = deserializeMinutiae(candidate.getFeatures());
        List<MinutiaePoint> storedMinutiae = deserializeMinutiae(stored.getFeatures());
        
        // Calculate match score based on common minutiae
        int commonPoints = findCommonMinutiae(candidateMinutiae, storedMinutiae);
        int totalPoints = Math.max(candidateMinutiae.size(), storedMinutiae.size());
        
        return (double) commonPoints / totalPoints;
    }
    
    private double matchFaces(BiometricTemplate candidate, BiometricTemplate stored) {
        // Embedding-based matching (cosine similarity)
        double[] candidateEmbedding = deserializeEmbedding(candidate.getFeatures());
        double[] storedEmbedding = deserializeEmbedding(stored.getFeatures());
        
        return calculateCosineSimilarity(candidateEmbedding, storedEmbedding);
    }
    
    private double matchVoices(BiometricTemplate candidate, BiometricTemplate stored) {
        // Voice feature matching
        double[] candidateFeatures = deserializeEmbedding(candidate.getFeatures());
        double[] storedFeatures = deserializeEmbedding(stored.getFeatures());
        
        return calculateCosineSimilarity(candidateFeatures, storedFeatures);
    }
    
    private double matchIrises(BiometricTemplate candidate, BiometricTemplate stored) {
        // Hamming distance for iris codes
        byte[] candidateCode = candidate.getFeatures();
        byte[] storedCode = stored.getFeatures();
        
        int hammingDistance = calculateHammingDistance(candidateCode, storedCode);
        int totalBits = candidateCode.length * 8;
        
        return 1.0 - ((double) hammingDistance / totalBits);
    }
    
    // Liveness detection
    
    private boolean verifyLiveness(BiometricData data) {
        log.debug("Verifying liveness for biometric type: {}", data.getBiometricType());

        switch (data.getBiometricType()) {
            case FINGERPRINT:
                return verifyFingerprintLiveness(data);
            case FACE:
                return verifyFaceLiveness(data);
            case VOICE:
                return verifyVoiceLiveness(data);
            case IRIS:
                return verifyIrisLiveness(data);
            default:
                // SECURITY FIX: Never default to allowing authentication when liveness detection is not implemented
                // This prevents authentication bypass through unsupported biometric types
                log.error("SECURITY ALERT: Attempted biometric authentication with unsupported type: {} - User: {}",
                    data.getBiometricType(),
                    data instanceof BiometricAuthenticationRequest ? ((BiometricAuthenticationRequest) data).getUserId() : "unknown");
                auditService.auditSecurityEvent("BIOMETRIC_LIVENESS_NOT_CONFIGURED",
                    Map.of(
                        "biometricType", data.getBiometricType().toString(),
                        "severity", "CRITICAL",
                        "action", "AUTHENTICATION_REJECTED"
                    ));
                throw new BiometricAuthenticationNotConfiguredException(
                    "Biometric authentication is not properly configured for this device/platform. " +
                    "Liveness detection is not implemented for biometric type: " + data.getBiometricType());
        }
    }
    
    private boolean verifyFingerprintLiveness(BiometricData data) {
        // Check for pulse, temperature, electrical conductivity
        Map<String, Object> livenessData = data.getLivenessData();
        
        if (livenessData == null) {
            return false;
        }
        
        // Check pulse detection
        Boolean hasPulse = (Boolean) livenessData.get("pulse_detected");
        
        // Check temperature range (human skin: 32-37°C)
        Double temperature = (Double) livenessData.get("temperature");
        boolean validTemp = temperature != null && temperature >= 32 && temperature <= 37;
        
        return Boolean.TRUE.equals(hasPulse) && validTemp;
    }
    
    private boolean verifyFaceLiveness(BiometricData data) {
        // Check for eye blink, head movement, expression change
        Map<String, Object> livenessData = data.getLivenessData();
        
        if (livenessData == null) {
            return false;
        }
        
        Boolean eyeBlink = (Boolean) livenessData.get("eye_blink_detected");
        Boolean headMovement = (Boolean) livenessData.get("head_movement_detected");
        Boolean depthDetected = (Boolean) livenessData.get("3d_depth_detected");
        
        return Boolean.TRUE.equals(eyeBlink) || 
               Boolean.TRUE.equals(headMovement) || 
               Boolean.TRUE.equals(depthDetected);
    }
    
    private boolean verifyVoiceLiveness(BiometricData data) {
        // Check for random phrase challenge
        Map<String, Object> livenessData = data.getLivenessData();
        
        if (livenessData == null) {
            return false;
        }
        
        String challengePhrase = (String) livenessData.get("challenge_phrase");
        String spokenPhrase = (String) livenessData.get("spoken_phrase");
        
        return challengePhrase != null && challengePhrase.equals(spokenPhrase);
    }
    
    private boolean verifyIrisLiveness(BiometricData data) {
        // Check for pupil dilation response
        Map<String, Object> livenessData = data.getLivenessData();
        
        if (livenessData == null) {
            return false;
        }
        
        Boolean pupilResponse = (Boolean) livenessData.get("pupil_response_detected");
        Boolean irisMovement = (Boolean) livenessData.get("iris_movement_detected");
        
        return Boolean.TRUE.equals(pupilResponse) || Boolean.TRUE.equals(irisMovement);
    }
    
    // Recovery and fallback mechanisms
    
    private List<String> generateRecoveryCodes(String userId) {
        List<String> codes = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            byte[] randomBytes = new byte[16];
            secureRandom.nextBytes(randomBytes);
            String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            codes.add(code);
        }
        
        // Store hashed versions
        storeRecoveryCodes(userId, codes);
        
        return codes;
    }
    
    // Security module checks
    
    private void checkSecurityModules() {
        // Check HSM availability
        try {
            if (hsmProvider != null && hsmProvider.testConnection()) {
                hsmAvailable = true;
                log.info("HSM is available for biometric operations");
            }
        } catch (Exception e) {
            log.warn("HSM not available: {}", e.getMessage());
        }
        
        // Check TPM availability
        tpmAvailable = checkTPMAvailability();
        
        // Check Vault availability
        try {
            credentialService.getCredential("health", "check");
            vaultAvailable = true;
            log.info("Vault is available for biometric operations");
        } catch (Exception e) {
            log.warn("Vault not available: {}", e.getMessage());
        }
    }
    
    private boolean checkTPMAvailability() {
        // Platform-specific TPM check
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            // Windows TPM check
            return checkWindowsTPM();
        } else if (os.contains("linux")) {
            // Linux TPM check
            return checkLinuxTPM();
        }
        
        return false;
    }
    
    private boolean checkWindowsTPM() {
        try {
            // SECURITY FIX: Use secure TPM detection instead of Runtime.exec
            return secureSystemUtils.isWindowsTPMAvailable();
        } catch (Exception e) {
            log.warn("Secure Windows TPM check failed: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean checkLinuxTPM() {
        try {
            // SECURITY FIX: Use secure TPM detection instead of Runtime.exec
            return secureSystemUtils.isLinuxTPMAvailable();
        } catch (Exception e) {
            log.warn("Secure Linux TPM check failed: {}", e.getMessage());
            return false;
        }
    }
    
    // Cleanup and maintenance
    
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void cleanupExpiredTemplates() {
        log.info("Starting cleanup of expired biometric templates");
        
        int cleaned = 0;
        for (Map.Entry<String, BiometricProfile> entry : userProfiles.entrySet()) {
            if (entry.getValue().isExpired()) {
                revokeBiometric(entry.getKey(), "Template expired");
                cleaned++;
            }
        }
        
        log.info("Cleaned up {} expired biometric templates", cleaned);
    }
    
    @Scheduled(fixedDelay = 60000) // Every minute
    public void cleanupCache() {
        if (!cacheEnabled) {
            return;
        }
        
        cacheLock.writeLock().lock();
        try {
            templateCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    // Helper methods (simplified implementations)
    
    private void validateEnrollmentRequest(BiometricEnrollmentRequest request) {
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            throw new BiometricException("User ID is required");
        }
        
        if (request.getBiometricData() == null || request.getBiometricData().length == 0) {
            throw new BiometricException("Biometric data is required");
        }
        
        // Check quality
        if (request.getQualityScore() < 0.7) {
            throw new BiometricException("Biometric quality too low");
        }
    }
    
    private byte[] serializeCancelable(CancelableBiometric cancelable) {
        // Serialize to byte array
        return new byte[0]; // Simplified
    }
    
    private CancelableBiometric deserializeCancelable(byte[] data) {
        // Deserialize from byte array
        return null; // Simplified
    }
    
    private double calculateCosineSimilarity(double[] a, double[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    private int calculateHammingDistance(byte[] a, byte[] b) {
        int distance = 0;
        for (int i = 0; i < a.length; i++) {
            distance += Integer.bitCount(a[i] ^ b[i]);
        }
        return distance;
    }
    
    // Data classes and interfaces
    
    @Data
    @Builder
    public static class BiometricEnrollmentRequest implements BiometricData {
        private String userId;
        private BiometricType biometricType;
        private byte[] biometricData;
        private Map<String, Object> livenessData;
        private double qualityScore;
        private String deviceId;
        private Map<String, Object> metadata;
        
        @Override
        public byte[] getRawData() {
            return biometricData;
        }
        
        @Override
        public BiometricType getBiometricType() {
            return biometricType;
        }
        
        @Override
        public Map<String, Object> getLivenessData() {
            return livenessData;
        }
    }
    
    @Data
    @Builder
    public static class BiometricAuthenticationRequest implements BiometricData {
        private String userId;
        private BiometricType biometricType;
        private byte[] biometricData;
        private Map<String, Object> livenessData;
        private String secondFactorToken;
        private String deviceId;
        
        @Override
        public byte[] getRawData() {
            return biometricData;
        }
        
        @Override
        public BiometricType getBiometricType() {
            return biometricType;
        }
        
        @Override
        public Map<String, Object> getLivenessData() {
            return livenessData;
        }
        
        public boolean hasSecondFactor() {
            return secondFactorToken != null && !secondFactorToken.isEmpty();
        }
    }
    
    @Data
    @Builder
    public static class BiometricEnrollmentResult {
        private boolean success;
        private String enrollmentId;
        private BiometricType biometricType;
        private List<String> recoveryCodes;
        private Instant enrolledAt;
        private Instant expiresAt;
        private String message;
    }
    
    @Data
    @Builder
    public static class BiometricAuthenticationResult {
        private boolean success;
        private double matchScore;
        private String sessionToken;
        private boolean requiresSecondFactor;
        private Instant authenticatedAt;
        private String message;
        
        public static BiometricAuthenticationResult failure(String message) {
            return BiometricAuthenticationResult.builder()
                .success(false)
                .message(message)
                .build();
        }
    }
    
    @Data
    @Builder
    public static class BiometricProfile {
        private String userId;
        private String templateId;
        private BiometricType biometricType;
        private Instant enrolledAt;
        private Instant lastUsed;
        private Instant lastUpdated;
        private Instant expiresAt;
        private int successfulAuths;
        private int failedAttempts;
        private boolean locked;
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
        
        public void recordSuccessfulAuth() {
            this.successfulAuths++;
            this.failedAttempts = 0;
            this.lastUsed = Instant.now();
        }
        
        public void recordFailedAttempt() {
            this.failedAttempts++;
        }
    }
    
    @Data
    @Builder
    public static class BiometricTemplate {
        private BiometricType type;
        private byte[] features;
        private double quality;
        private Instant timestamp;
    }
    
    @Data
    @Builder
    public static class CancelableBiometric {
        private byte[] transformedTemplate;
        private String transformKeyId;
        private String revocationToken;
        private Instant createdAt;
    }
    
    @Data
    @Builder
    public static class BiometricTemplateMetadata {
        private String templateId;
        private String userId;
        private String storageMethod;
        private String encryptedData;
        private Instant createdAt;
        private Instant expiresAt;
    }
    
    @Data
    @Builder
    public static class CachedTemplate {
        private BiometricTemplate template;
        private Instant cachedAt;
        private Instant expiresAt;
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
    
    @Data
    public static class MinutiaePoint {
        private int x;
        private int y;
        private double angle;
        private String type; // ending, bifurcation, etc.
    }
    
    public interface BiometricData {
        byte[] getRawData();
        BiometricType getBiometricType();
        Map<String, Object> getLivenessData();
    }
    
    public enum BiometricType {
        FINGERPRINT,
        FACE,
        VOICE,
        IRIS
    }
    
    public static class BiometricException extends RuntimeException {
        public BiometricException(String message) {
            super(message);
        }
        
        public BiometricException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Stub implementations for demonstration
    
    private void initializeBiometricSubsystems() {
        // Initialize biometric subsystems
    }
    
    private boolean verifyLiveness(BiometricEnrollmentRequest request) {
        return verifyLiveness((BiometricData) request);
    }
    
    private boolean verifyLiveness(BiometricAuthenticationRequest request) {
        return verifyLiveness((BiometricData) request);
    }
    
    private BiometricTemplate extractTemplate(BiometricEnrollmentRequest request) {
        return extractTemplate((BiometricData) request);
    }
    
    private BiometricTemplate extractTemplate(BiometricAuthenticationRequest request) {
        return extractTemplate((BiometricData) request);
    }
    
    private List<MinutiaePoint> extractMinutiae(byte[] data) {
        return new ArrayList<>(); // Simplified
    }
    
    private byte[] serializeMinutiae(List<MinutiaePoint> minutiae) {
        return new byte[0]; // Simplified
    }
    
    private List<MinutiaePoint> deserializeMinutiae(byte[] data) {
        return new ArrayList<>(); // Simplified
    }
    
    private double[] extractFaceEmbedding(byte[] data) {
        return new double[128]; // Simplified - typically 128 or 512 dimensions
    }
    
    private double[] extractVoiceFeatures(byte[] data) {
        return new double[40]; // Simplified - MFCC features
    }
    
    private byte[] extractIrisCode(byte[] data) {
        return new byte[256]; // Simplified - IrisCode
    }
    
    private byte[] serializeEmbedding(double[] embedding) {
        return new byte[0]; // Simplified
    }
    
    private double[] deserializeEmbedding(byte[] data) {
        return new double[0]; // Simplified
    }
    
    private double assessQuality(byte[] data) {
        return 0.85; // Simplified
    }
    
    private double assessFaceQuality(byte[] data) {
        return 0.90; // Simplified
    }
    
    private double assessVoiceQuality(byte[] data) {
        return 0.88; // Simplified
    }
    
    private double assessIrisQuality(byte[] data) {
        return 0.92; // Simplified
    }
    
    private int findCommonMinutiae(List<MinutiaePoint> a, List<MinutiaePoint> b) {
        return Math.min(a.size(), b.size()) * 7 / 10; // Simplified
    }
    
    private String generateRevocationToken() {
        return UUID.randomUUID().toString();
    }
    
    private String storeTransformKey(byte[] key) {
        return UUID.randomUUID().toString(); // Simplified
    }
    
    private BiometricProfile createProfile(String userId, BiometricType type, String templateId) {
        return BiometricProfile.builder()
            .userId(userId)
            .templateId(templateId)
            .biometricType(type)
            .enrolledAt(Instant.now())
            .expiresAt(Instant.now().plus(templateMaxAgeDays, ChronoUnit.DAYS))
            .successfulAuths(0)
            .failedAttempts(0)
            .locked(false)
            .build();
    }
    
    private void persistTemplateMetadata(BiometricTemplateMetadata metadata) {
        // Store in database
    }
    
    private BiometricTemplateMetadata getTemplateMetadata(String templateId) {
        return null; // Simplified
    }
    
    private byte[] retrieveFromHSM(String templateId, BiometricTemplateMetadata metadata) throws Exception {
        String keyId = "biometric-" + templateId;
        byte[] encrypted = Base64.getDecoder().decode(metadata.getEncryptedData());
        return hsmProvider.decrypt(keyId, encrypted);
    }
    
    private byte[] retrieveFromTPM(String templateId, BiometricTemplateMetadata metadata) throws Exception {
        return decryptWithTPM(Base64.getDecoder().decode(metadata.getEncryptedData()));
    }
    
    private byte[] retrieveFromVault(String templateId, BiometricTemplateMetadata metadata) throws Exception {
        String encryptedData = credentialService.getCredential("biometric/" + templateId, "template");
        return encryptionService.decrypt(encryptedData).getBytes(StandardCharsets.UTF_8);
    }
    
    private byte[] decryptWithSoftware(String encryptedData) throws Exception {
        return Base64.getDecoder().decode(encryptedData); // Simplified
    }
    
    private byte[] encryptWithTPM(byte[] data) {
        return data; // Simplified - would use TPM API
    }
    
    private byte[] decryptWithTPM(byte[] data) {
        return data; // Simplified - would use TPM API
    }
    
    private BiometricTemplate reconstructTemplate(CancelableBiometric cancelable) {
        return null; // Simplified
    }
    
    private BiometricTemplate mergeTemplates(BiometricTemplate current, BiometricTemplate newTemplate) {
        // Adaptive template merging
        return current; // Simplified
    }
    
    private void deleteTemplate(String templateId) {
        // Delete from storage
    }
    
    private void invalidateCache(String userId) {
        cacheLock.writeLock().lock();
        try {
            templateCache.entrySet().removeIf(entry -> entry.getKey().contains(userId));
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    private CachedTemplate getFromCache(String templateId) {
        cacheLock.readLock().lock();
        try {
            return templateCache.get(templateId);
        } finally {
            cacheLock.readLock().unlock();
        }
    }
    
    private void cacheTemplate(String templateId, BiometricTemplate template) {
        cacheLock.writeLock().lock();
        try {
            CachedTemplate cached = CachedTemplate.builder()
                .template(template)
                .cachedAt(Instant.now())
                .expiresAt(Instant.now().plus(cacheTtlMinutes, ChronoUnit.MINUTES))
                .build();
            templateCache.put(templateId, cached);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    private String generateSessionToken(String userId) {
        return UUID.randomUUID().toString(); // Simplified
    }
    
    private void recordFailedAttempt(String userId, String reason) {
        BiometricProfile profile = userProfiles.get(userId);
        if (profile != null) {
            profile.recordFailedAttempt();
        }
        auditService.auditSecurityEvent("BIOMETRIC_AUTH_FAILED", 
            Map.of("userId", userId, "reason", reason));
    }
    
    private void lockAccount(String userId) {
        BiometricProfile profile = userProfiles.get(userId);
        if (profile != null) {
            profile.setLocked(true);
        }
        auditService.auditSecurityEvent("BIOMETRIC_ACCOUNT_LOCKED", 
            Map.of("userId", userId));
    }
    
    private void storeRecoveryCodes(String userId, List<String> codes) {
        // Store hashed recovery codes
    }
    
    private void auditEnrollment(String userId, BiometricType type, boolean success) {
        auditService.auditSecurityEvent("BIOMETRIC_ENROLLMENT", 
            Map.of("userId", userId, "type", type.toString(), "success", success));
    }
    
    private void auditAuthentication(String userId, boolean success, double matchScore) {
        auditService.auditSecurityEvent("BIOMETRIC_AUTHENTICATION", 
            Map.of("userId", userId, "success", success, "matchScore", matchScore));
    }
}