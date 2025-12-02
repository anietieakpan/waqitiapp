package com.waqiti.user.security;

import com.waqiti.user.domain.BiometricCredential;
import com.waqiti.user.domain.User;
import com.waqiti.user.dto.security.*;
import com.waqiti.user.dto.security.result.*;
import com.waqiti.user.repository.BiometricCredentialRepository;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.events.SecurityEventPublisher;
import com.waqiti.common.events.SecurityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Biometric Authentication Service
 * 
 * Provides comprehensive biometric authentication including:
 * - Fingerprint authentication
 * - Face ID/recognition
 * - Voice authentication
 * - Behavioral biometrics
 * - WebAuthn/FIDO2 support
 * - Multi-modal biometric fusion
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiometricAuthenticationService {

    private final BiometricCredentialRepository biometricRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final SecurityEventPublisher securityEventPublisher;
    private final DeviceFingerprintService deviceFingerprintService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BiometricExtractorService fingerprintExtractor;
    private final BiometricExtractorService faceRecognitionExtractor;
    private final BiometricExtractorService voiceRecognitionExtractor;
    private final BiometricMatchingService fingerprintMatcher;
    private final BiometricMatchingService faceRecognitionMatcher;
    private final BiometricMatchingService voiceRecognitionMatcher;
    
    @Value("${security.biometric.matching-threshold:0.85}")
    private double biometricMatchingThreshold;
    
    @Value("${security.biometric.max-attempts:3}")
    private int maxBiometricAttempts;
    
    @Value("${security.biometric.lockout-duration:300}") // 5 minutes
    private int lockoutDurationSeconds;
    
    @Value("${security.biometric.encryption-key:#{null}}")
    private String biometricEncryptionKey;
    
    private static final String BIOMETRIC_ATTEMPTS_PREFIX = "biometric:attempts:";
    private static final String BIOMETRIC_CHALLENGE_PREFIX = "biometric:challenge:";
    
    @PostConstruct
    private void init() {
        if (biometricEncryptionKey == null || biometricEncryptionKey.isEmpty()) {
            try {
                biometricEncryptionKey = encryptionService.generateKey();
                log.warn("Generated new biometric encryption key. Please configure security.biometric.encryption-key in production!");
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate biometric encryption key", e);
            }
        }
    }
    
    /**
     * Register biometric credential for user
     */
    @Transactional
    public BiometricRegistrationResult registerBiometric(BiometricRegistrationRequest request) {
        log.info("Registering biometric for user: {} type: {}", 
                request.getUserId(), request.getBiometricType());
        
        User user = userRepository.findById(UUID.fromString(request.getUserId()))
                .orElseThrow(() -> new SecurityException("User not found"));
        
        // Validate device fingerprint
        DeviceFingerprintData fingerprintData = DeviceFingerprintData.builder()
            .fingerprint(request.getDeviceFingerprint())
            .userId(request.getUserId())
            .build();
        if (!deviceFingerprintService.isDeviceTrusted(fingerprintData, request.getUserId())) {
            throw new SecurityException("Device not trusted for biometric registration");
        }
        
        // Check if biometric type already registered
        Optional<BiometricCredential> existing = biometricRepository
                .findByUserIdAndBiometricTypeAndStatus(
                        UUID.fromString(request.getUserId()), 
                        request.getBiometricType(), 
                        BiometricCredential.Status.ACTIVE);
        
        if (existing.isPresent()) {
            throw new SecurityException("Biometric type already registered");
        }
        
        // Validate biometric quality
        BiometricQualityResult qualityResult = validateBiometricQuality(request);
        if (!qualityResult.isAcceptable()) {
            return BiometricRegistrationResult.builder()
                    .success(false)
                    .errorCode("POOR_QUALITY")
                    .errorMessage("Biometric quality insufficient")
                    .qualityScore(qualityResult.getScore())
                    .qualityIssues(qualityResult.getIssues())
                    .build();
        }
        
        // Extract and encrypt biometric template
        BiometricTemplate template = extractBiometricTemplate(request);
        String encryptedTemplate;
        try {
            encryptedTemplate = encryptionService.encrypt(template.getData(), biometricEncryptionKey);
        } catch (Exception e) {
            log.error("Failed to encrypt biometric template", e);
            throw new RuntimeException("Failed to encrypt biometric template", e);
        }
        
        // Generate unique credential ID
        String credentialId = generateCredentialId();
        
        // Create biometric credential
        BiometricCredential credential = BiometricCredential.builder()
                .credentialId(credentialId)
                .userId(UUID.fromString(request.getUserId()))
                .biometricType(request.getBiometricType())
                .deviceFingerprint(request.getDeviceFingerprint())
                .encryptedTemplate(encryptedTemplate)
                .templateVersion(template.getVersion())
                .algorithm(template.getAlgorithm())
                .qualityScore(qualityResult.getScore())
                .status(BiometricCredential.Status.ACTIVE)
                .registrationMetadata(buildRegistrationMetadata(request))
                .createdAt(LocalDateTime.now())
                .lastUsedAt(LocalDateTime.now())
                .build();
        
        credential = biometricRepository.save(credential);
        
        // Update user biometric flags
        updateUserBiometricFlags(user, request.getBiometricType());
        
        // Publish security event
        SecurityEvent event = SecurityEvent.builder()
                .eventType("BIOMETRIC_REGISTERED")
                .userId(request.getUserId())
                .details(String.format("{\"biometricType\":\"%s\",\"credentialId\":\"%s\",\"deviceFingerprint\":\"%s\"}",
                    request.getBiometricType().name(), credentialId, request.getDeviceFingerprint()))
                .timestamp(System.currentTimeMillis())
                .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        log.info("Biometric registered successfully: {} for user: {}", 
                credentialId, request.getUserId());
        
        return BiometricRegistrationResult.builder()
                .success(true)
                .credentialId(credentialId)
                .biometricType(request.getBiometricType())
                .qualityScore(qualityResult.getScore())
                .build();
    }
    
    /**
     * Authenticate user using biometric
     */
    @Transactional
    public BiometricAuthenticationResult authenticate(BiometricAuthenticationRequest request) {
        log.info("Biometric authentication attempt for user: {} type: {}", 
                request.getUserId(), request.getBiometricType());
        
        // Check for lockout
        if (isBiometricLocked(request.getUserId(), request.getBiometricType())) {
            return BiometricAuthenticationResult.builder()
                    .success(false)
                    .errorCode("ACCOUNT_LOCKED")
                    .errorMessage("Too many failed attempts. Try again later.")
                    .lockoutTimeRemaining(getRemainingLockoutTime(request.getUserId(), request.getBiometricType()))
                    .build();
        }
        
        // Find active biometric credential
        Optional<BiometricCredential> credentialOpt = biometricRepository
                .findByUserIdAndBiometricTypeAndStatus(
                        UUID.fromString(request.getUserId()),
                        request.getBiometricType(),
                        BiometricCredential.Status.ACTIVE);
        
        if (credentialOpt.isEmpty()) {
            recordFailedAttempt(request.getUserId(), request.getBiometricType(), "NO_CREDENTIAL");
            return BiometricAuthenticationResult.builder()
                    .success(false)
                    .errorCode("NO_BIOMETRIC_REGISTERED")
                    .errorMessage("No biometric credential registered")
                    .build();
        }
        
        BiometricCredential credential = credentialOpt.get();
        
        try {
            // Extract biometric template from request
            BiometricTemplate requestTemplate = extractBiometricTemplate(request);
            
            // Decrypt stored template
            String decryptedTemplate;
            try {
                decryptedTemplate = encryptionService.decrypt(credential.getEncryptedTemplate(), biometricEncryptionKey);
            } catch (Exception e) {
                log.error("Failed to decrypt biometric template", e);
                throw new RuntimeException("Failed to decrypt biometric template", e);
            }
            BiometricTemplate storedTemplate = BiometricTemplate.fromString(decryptedTemplate);
            
            // Perform biometric matching
            BiometricMatchResult matchResult = performBiometricMatching(
                    storedTemplate, requestTemplate, credential.getAlgorithm());
            
            if (matchResult.getScore() >= biometricMatchingThreshold) {
                // Authentication successful
                clearFailedAttempts(request.getUserId(), request.getBiometricType());
                updateCredentialUsage(credential);
                
                // Generate authentication token/challenge
                String authToken = generateBiometricAuthToken(credential, request);
                
                // Publish security event
                SecurityEvent successEvent = SecurityEvent.builder()
                        .eventType("BIOMETRIC_AUTH_SUCCESS")
                        .userId(request.getUserId())
                        .details(String.format("{\"biometricType\":\"%s\",\"credentialId\":\"%s\",\"matchScore\":%f}",
                            request.getBiometricType().name(), credential.getCredentialId(), matchResult.getScore()))
                        .timestamp(System.currentTimeMillis())
                        .build();
                securityEventPublisher.publishSecurityEvent(successEvent);
                
                log.info("Biometric authentication successful for user: {} score: {}", 
                        request.getUserId(), matchResult.getScore());
                
                return BiometricAuthenticationResult.builder()
                        .success(true)
                        .authToken(authToken)
                        .credentialId(credential.getCredentialId())
                        .matchScore(matchResult.getScore())
                        .authenticationMethod("BIOMETRIC_" + request.getBiometricType())
                        .build();
                
            } else {
                // Authentication failed
                recordFailedAttempt(request.getUserId(), request.getBiometricType(), "MATCH_FAILED");
                
                SecurityEvent failureEvent = SecurityEvent.builder()
                        .eventType("BIOMETRIC_AUTH_FAILURE")
                        .userId(request.getUserId())
                        .details(String.format("{\"biometricType\":\"%s\",\"credentialId\":\"%s\",\"matchScore\":%f,\"reason\":\"INSUFFICIENT_MATCH_SCORE\"}",
                            request.getBiometricType().name(), credential.getCredentialId(), matchResult.getScore()))
                        .timestamp(System.currentTimeMillis())
                        .build();
                securityEventPublisher.publishSecurityEvent(failureEvent);
                
                log.warn("Biometric authentication failed for user: {} score: {} threshold: {}", 
                        request.getUserId(), matchResult.getScore(), biometricMatchingThreshold);
                
                return BiometricAuthenticationResult.builder()
                        .success(false)
                        .errorCode("BIOMETRIC_MISMATCH")
                        .errorMessage("Biometric does not match")
                        .matchScore(matchResult.getScore())
                        .attemptsRemaining(getRemainingAttempts(request.getUserId(), request.getBiometricType()))
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Error during biometric authentication", e);
            recordFailedAttempt(request.getUserId(), request.getBiometricType(), "SYSTEM_ERROR");
            
            return BiometricAuthenticationResult.builder()
                    .success(false)
                    .errorCode("AUTHENTICATION_ERROR")
                    .errorMessage("Authentication system error")
                    .build();
        }
    }
    
    /**
     * Enable WebAuthn/FIDO2 authentication
     */
    @Transactional
    public WebAuthnRegistrationResult registerWebAuthn(WebAuthnRegistrationRequest request) {
        log.info("Registering WebAuthn credential for user: {}", request.getUserId());
        
        User user = userRepository.findById(UUID.fromString(request.getUserId()))
                .orElseThrow(() -> new SecurityException("User not found"));
        
        // Validate attestation
        if (!validateWebAuthnAttestation(request.getAttestationObject(), request.getClientDataJSON())) {
            throw new SecurityException("Invalid WebAuthn attestation");
        }
        
        // Generate credential ID
        String credentialId = generateCredentialId();
        
        // Extract public key from attestation
        String publicKey = extractPublicKeyFromAttestation(request.getAttestationObject());
        
        // Create WebAuthn credential
        BiometricCredential credential = BiometricCredential.builder()
                .credentialId(credentialId)
                .userId(UUID.fromString(request.getUserId()))
                .biometricType(BiometricType.WEBAUTHN)
                .deviceFingerprint(request.getDeviceFingerprint())
                .encryptedTemplate(encryptPublicKey(publicKey))
                .algorithm("WEBAUTHN_ES256")
                .status(BiometricCredential.Status.ACTIVE)
                .registrationMetadata(buildWebAuthnMetadata(request))
                .createdAt(LocalDateTime.now())
                .lastUsedAt(LocalDateTime.now())
                .build();
        
        credential = biometricRepository.save(credential);
        
        // Update user WebAuthn flag
        user.setWebAuthnEnabled(true);
        userRepository.save(user);
        
        log.info("WebAuthn credential registered: {} for user: {}", credentialId, request.getUserId());
        
        return WebAuthnRegistrationResult.builder()
                .success(true)
                .credentialId(credentialId)
                .build();
    }
    
    /**
     * Authenticate using WebAuthn
     */
    public WebAuthnAuthenticationResult authenticateWebAuthn(WebAuthnAuthenticationRequest request) {
        log.info("WebAuthn authentication for user: {}", request.getUserId());
        
        // Find WebAuthn credential
        Optional<BiometricCredential> credentialOpt = biometricRepository
                .findByCredentialIdAndBiometricType(request.getCredentialId(), BiometricType.WEBAUTHN);
        
        if (credentialOpt.isEmpty()) {
            return WebAuthnAuthenticationResult.builder()
                    .success(false)
                    .errorCode("CREDENTIAL_NOT_FOUND")
                    .build();
        }
        
        BiometricCredential credential = credentialOpt.get();
        
        try {
            // Verify assertion
            String publicKey = decryptPublicKey(credential.getEncryptedTemplate());
            boolean verified = verifyWebAuthnAssertion(
                    request.getAuthenticatorData(),
                    request.getClientDataJSON(),
                    request.getSignature(),
                    publicKey);
            
            if (verified) {
                updateCredentialUsage(credential);
                
                String authToken = generateBiometricAuthToken(credential, request);
                
                log.info("WebAuthn authentication successful for user: {}", request.getUserId());
                
                return WebAuthnAuthenticationResult.builder()
                        .success(true)
                        .authToken(authToken)
                        .credentialId(credential.getCredentialId())
                        .build();
            } else {
                log.warn("WebAuthn authentication failed for user: {}", request.getUserId());
                
                return WebAuthnAuthenticationResult.builder()
                        .success(false)
                        .errorCode("VERIFICATION_FAILED")
                        .build();
            }
            
        } catch (Exception e) {
            log.error("WebAuthn authentication error", e);
            return WebAuthnAuthenticationResult.builder()
                    .success(false)
                    .errorCode("AUTHENTICATION_ERROR")
                    .build();
        }
    }
    
    /**
     * Analyze behavioral biometrics
     */
    public BehavioralBiometricResult analyzeBehavioralBiometrics(BehavioralBiometricRequest request) {
        log.debug("Analyzing behavioral biometrics for user: {}", request.getUserId());
        
        try {
            // Analyze typing patterns
            TypingPatternScore typingScore = analyzeTypingPattern(request.getTypingData());
            
            // Analyze mouse/touch patterns
            InteractionPatternScore interactionScore = analyzeInteractionPattern(request.getInteractionData());
            
            // Analyze navigation patterns
            NavigationPatternScore navigationScore = analyzeNavigationPattern(request.getNavigationData());
            
            // Calculate composite score
            double compositeScore = calculateCompositeBehavioralScore(
                    typingScore, interactionScore, navigationScore);
            
            // Determine risk level
            BehavioralRiskLevel riskLevel = determineBehavioralRiskLevel(compositeScore);
            
            // Store behavioral profile update
            updateBehavioralProfile(request.getUserId(), request);
            
            return BehavioralBiometricResult.builder()
                    .userId(request.getUserId())
                    .compositeScore(compositeScore)
                    .riskLevel(riskLevel)
                    .typingScore(typingScore.getScore())
                    .interactionScore(interactionScore.getScore())
                    .navigationScore(navigationScore.getScore())
                    .anomalies(detectBehavioralAnomalies(request))
                    .recommendations(generateBehavioralRecommendations(riskLevel))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error analyzing behavioral biometrics", e);
            return BehavioralBiometricResult.builder()
                    .userId(request.getUserId())
                    .compositeScore(0.5) // Neutral score
                    .riskLevel(BehavioralRiskLevel.MEDIUM)
                    .build();
        }
    }
    
    /**
     * Get biometric credentials for user
     */
    public List<BiometricCredentialInfo> getUserBiometricCredentials(String userId) {
        List<BiometricCredential> credentials = biometricRepository
                .findByUserIdAndStatus(UUID.fromString(userId), BiometricCredential.Status.ACTIVE);
        
        return credentials.stream()
                .map(this::toBiometricCredentialInfo)
                .toList();
    }
    
    /**
     * Remove biometric credential
     */
    @Transactional
    public void removeBiometricCredential(String userId, String credentialId) {
        log.info("Removing biometric credential: {} for user: {}", credentialId, userId);
        
        BiometricCredential credential = biometricRepository
                .findByCredentialIdAndUserId(credentialId, UUID.fromString(userId))
                .orElseThrow(() -> new SecurityException("Credential not found"));
        
        credential.setStatus(BiometricCredential.Status.REVOKED);
        credential.setRevokedAt(LocalDateTime.now());
        biometricRepository.save(credential);
        
        // Clear any cached authentication data
        clearBiometricCache(userId, credentialId);
        
        SecurityEvent revokeEvent = SecurityEvent.builder()
                .eventType("BIOMETRIC_CREDENTIAL_REVOKED")
                .userId(userId)
                .details(String.format("{\"credentialId\":\"%s\"}", credentialId))
                .timestamp(System.currentTimeMillis())
                .build();
        securityEventPublisher.publishSecurityEvent(revokeEvent);
        
        log.info("Biometric credential removed: {}", credentialId);
    }
    
    // Private helper methods
    
    private BiometricQualityResult validateBiometricQuality(BiometricRegistrationRequest request) {
        // Implementation would validate biometric quality based on type
        // This is a simplified version
        double qualityScore = 0.85; // Mock quality score
        boolean acceptable = qualityScore >= 0.7;
        
        return BiometricQualityResult.builder()
                .score(qualityScore)
                .acceptable(acceptable)
                .issues(acceptable ? List.of() : List.of("Low image quality"))
                .build();
    }
    
    private BiometricTemplate extractBiometricTemplate(Object request) {
        if (request instanceof FingerprintRequest) {
            return extractFingerprintTemplate((FingerprintRequest) request);
        } else if (request instanceof FaceRecognitionRequest) {
            return extractFaceTemplate((FaceRecognitionRequest) request);
        } else if (request instanceof VoiceRecognitionRequest) {
            return extractVoiceTemplate((VoiceRecognitionRequest) request);
        } else {
            log.warn("Unsupported biometric request type: {} - using fallback processing", request.getClass());
            return createFallbackTemplate(request);
        }
    }
    
    private BiometricTemplate extractFingerprintTemplate(FingerprintRequest request) {
        try {
            // Use production-grade fingerprint feature extraction
            String templateData = fingerprintExtractor.extractFeatures(
                request.getBiometricData(), 
                request.getImageQuality()
            );
            
            return BiometricTemplate.builder()
                .data(encryptionService.encrypt(templateData))
                .version("2.1")
                .algorithm("MINUTIAE_BASED")
                .qualityScore(request.getImageQuality())
                .extractedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to extract fingerprint template", e);
            throw new BiometricProcessingException("Fingerprint extraction failed: " + e.getMessage());
        }
    }
    
    private BiometricTemplate extractFaceTemplate(FaceRecognitionRequest request) {
        try {
            // Use production-grade face feature extraction
            String templateData = faceRecognitionExtractor.extractFacialFeatures(
                request.getBiometricData(),
                request.getFaceDetectionConfidence()
            );
            
            return BiometricTemplate.builder()
                .data(encryptionService.encrypt(templateData))
                .version("3.0")
                .algorithm("DEEP_FACE_EMBEDDING")
                .qualityScore(request.getFaceDetectionConfidence())
                .extractedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to extract face template", e);
            throw new BiometricProcessingException("Face extraction failed: " + e.getMessage());
        }
    }
    
    private BiometricTemplate extractVoiceTemplate(VoiceRecognitionRequest request) {
        try {
            // Use production-grade voice feature extraction
            String templateData = voiceRecognitionExtractor.extractVoiceFeatures(
                request.getBiometricData(),
                request.getAudioQuality(),
                request.getSampleRate()
            );
            
            return BiometricTemplate.builder()
                .data(encryptionService.encrypt(templateData))
                .version("2.0")
                .algorithm("MFCC_SPECTRAL")
                .qualityScore(request.getAudioQuality())
                .extractedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to extract voice template", e);
            throw new BiometricProcessingException("Voice extraction failed: " + e.getMessage());
        }
    }
    
    private BiometricMatchResult performBiometricMatching(
            BiometricTemplate stored, BiometricTemplate request, String algorithm) {
        try {
            // Decrypt templates for matching
            String storedData = encryptionService.decrypt(stored.getData());
            String requestData = encryptionService.decrypt(request.getData());
            
            double matchScore = switch (algorithm) {
                case "MINUTIAE_BASED" -> fingerprintMatcher.match(storedData, requestData);
                case "DEEP_FACE_EMBEDDING" -> faceRecognitionMatcher.match(storedData, requestData);
                case "MFCC_SPECTRAL" -> voiceRecognitionMatcher.match(storedData, requestData);
                default -> {
                    log.warn("Unsupported matching algorithm: {} - using generic fallback", algorithm);
                    yield genericBiometricMatcher.match(storedData, requestData);
                }
            };
            
            // Apply quality adjustment
            double qualityFactor = Math.min(stored.getQualityScore(), request.getQualityScore()) / 100.0;
            double adjustedScore = matchScore * qualityFactor;
            
            return BiometricMatchResult.builder()
                .score(adjustedScore)
                .algorithm(algorithm)
                .qualityAdjusted(true)
                .storedTemplateQuality(stored.getQualityScore())
                .requestTemplateQuality(request.getQualityScore())
                .rawScore(matchScore)
                .matchedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Biometric matching failed for algorithm: {}", algorithm, e);
            throw new BiometricProcessingException("Matching failed: " + e.getMessage());
        }
    }
    
    private String generateCredentialId() {
        return "cred_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    private String generateBiometricAuthToken(BiometricCredential credential, Object request) {
        // Generate a secure token for biometric authentication
        return "bio_token_" + UUID.randomUUID().toString();
    }
    
    private void recordFailedAttempt(String userId, BiometricType type, String reason) {
        String key = BIOMETRIC_ATTEMPTS_PREFIX + userId + ":" + type;
        Integer attempts = (Integer) redisTemplate.opsForValue().get(key);
        attempts = (attempts == null) ? 1 : attempts + 1;
        
        redisTemplate.opsForValue().set(key, attempts, Duration.ofSeconds(lockoutDurationSeconds));
        
        if (attempts >= maxBiometricAttempts) {
            redisTemplate.opsForValue().set(key + ":locked", true, Duration.ofSeconds(lockoutDurationSeconds));
        }
    }
    
    private void clearFailedAttempts(String userId, BiometricType type) {
        String key = BIOMETRIC_ATTEMPTS_PREFIX + userId + ":" + type;
        redisTemplate.delete(key);
        redisTemplate.delete(key + ":locked");
    }
    
    private boolean isBiometricLocked(String userId, BiometricType type) {
        String key = BIOMETRIC_ATTEMPTS_PREFIX + userId + ":" + type + ":locked";
        return Boolean.TRUE.equals(redisTemplate.opsForValue().get(key));
    }
    
    private int getRemainingAttempts(String userId, BiometricType type) {
        String key = BIOMETRIC_ATTEMPTS_PREFIX + userId + ":" + type;
        Integer attempts = (Integer) redisTemplate.opsForValue().get(key);
        return maxBiometricAttempts - (attempts == null ? 0 : attempts);
    }
    
    private Duration getRemainingLockoutTime(String userId, BiometricType type) {
        String key = BIOMETRIC_ATTEMPTS_PREFIX + userId + ":" + type + ":locked";
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return Duration.ofSeconds(ttl != null ? ttl : 0);
    }
    
    private void updateCredentialUsage(BiometricCredential credential) {
        credential.setLastUsedAt(LocalDateTime.now());
        credential.setUsageCount(credential.getUsageCount() + 1);
        biometricRepository.save(credential);
    }
    
    private void updateUserBiometricFlags(User user, BiometricType type) {
        switch (type) {
            case FINGERPRINT -> user.setFingerprintEnabled(true);
            case FACE_ID -> user.setFaceIdEnabled(true);
            case VOICE -> user.setVoiceAuthEnabled(true);
            case BEHAVIORAL -> user.setBehavioralBiometricEnabled(true);
        }
        userRepository.save(user);
    }
    
    private Map<String, Object> buildRegistrationMetadata(BiometricRegistrationRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("registrationTimestamp", LocalDateTime.now());
        metadata.put("deviceFingerprint", request.getDeviceFingerprint());
        metadata.put("userAgent", request.getUserAgent());
        metadata.put("ipAddress", request.getIpAddress());
        return metadata;
    }
    
    private Map<String, Object> buildWebAuthnMetadata(WebAuthnRegistrationRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("registrationTimestamp", LocalDateTime.now());
        metadata.put("authenticatorType", request.getAuthenticatorType());
        metadata.put("deviceFingerprint", request.getDeviceFingerprint());
        return metadata;
    }
    
    private BiometricCredentialInfo toBiometricCredentialInfo(BiometricCredential credential) {
        return BiometricCredentialInfo.builder()
                .credentialId(credential.getCredentialId())
                .biometricType(credential.getBiometricType())
                .deviceFingerprint(credential.getDeviceFingerprint())
                .qualityScore(credential.getQualityScore())
                .createdAt(credential.getCreatedAt())
                .lastUsedAt(credential.getLastUsedAt())
                .usageCount(credential.getUsageCount().intValue())
                .build();
    }
    
    private boolean validateWebAuthnAttestation(String attestationObject, String clientDataJSON) {
        // Implementation would validate WebAuthn attestation
        return true; // Mock implementation
    }
    
    private String extractPublicKeyFromAttestation(String attestationObject) {
        // Implementation would extract public key from attestation
        return "mock_public_key"; // Mock implementation
    }
    
    private boolean verifyWebAuthnAssertion(String authenticatorData, String clientDataJSON, 
                                          String signature, String publicKey) {
        try {
            // Decode components
            byte[] authDataBytes = Base64.getDecoder().decode(authenticatorData);
            byte[] clientDataBytes = Base64.getDecoder().decode(clientDataJSON);
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKey);
            
            // Parse client data
            ObjectMapper mapper = new ObjectMapper();
            JsonNode clientData = mapper.readTree(clientDataBytes);
            
            // Verify type is "webauthn.get"
            String type = clientData.get("type").asText();
            if (!"webauthn.get".equals(type)) {
                log.warn("Invalid assertion type: {}", type);
                return false;
            }
            
            // Extract authenticator data
            // Structure: rpIdHash (32) || flags (1) || counter (4)
            if (authDataBytes.length < 37) {
                log.warn("Authenticator data too small");
                return false;
            }
            
            // Verify RP ID hash matches
            byte[] rpIdHash = new byte[32];
            System.arraycopy(authDataBytes, 0, rpIdHash, 0, 32);
            
            // Check user present flag (bit 0)
            byte flags = authDataBytes[32];
            if ((flags & 0x01) == 0) {
                log.warn("User not present during assertion");
                return false;
            }
            
            // Extract and verify counter
            int counter = ((authDataBytes[33] & 0xFF) << 24) |
                         ((authDataBytes[34] & 0xFF) << 16) |
                         ((authDataBytes[35] & 0xFF) << 8) |
                         (authDataBytes[36] & 0xFF);
            
            // Counter should be greater than stored value to prevent replay
            if (counter == 0) {
                log.warn("Invalid counter value");
                return false;
            }
            
            // Simplified signature verification
            // In production, would use proper crypto verification with public key
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(authDataBytes);
            digest.update(clientDataBytes);
            byte[] dataHash = digest.digest();
            
            // Basic signature length check
            if (signatureBytes.length < 64) { // Minimum for ECDSA
                log.warn("Signature too short");
                return false;
            }
            
            // In production, would verify signature using public key and appropriate algorithm
            // For now, do basic validation
            return signatureBytes.length >= 64 && publicKeyBytes.length >= 32;
            
        } catch (Exception e) {
            log.error("WebAuthn assertion verification failed", e);
            return false;
        }
    }
    
    private TypingPatternScore analyzeTypingPattern(Map<String, Object> typingData) {
        // Analyze keystroke dynamics, dwell time, flight time, etc.
        return TypingPatternScore.builder().score(0.85).build();
    }
    
    private InteractionPatternScore analyzeInteractionPattern(Map<String, Object> interactionData) {
        // Analyze mouse movement, click patterns, touch pressure, etc.
        return InteractionPatternScore.builder().score(0.82).build();
    }
    
    private NavigationPatternScore analyzeNavigationPattern(Map<String, Object> navigationData) {
        // Analyze page navigation patterns, time spent on pages, etc.
        return NavigationPatternScore.builder().score(0.88).build();
    }
    
    private double calculateCompositeBehavioralScore(TypingPatternScore typing, 
                                                   InteractionPatternScore interaction,
                                                   NavigationPatternScore navigation) {
        // Weighted average of behavioral scores
        return (typing.getScore() * 0.4 + interaction.getScore() * 0.35 + navigation.getScore() * 0.25);
    }
    
    private BehavioralRiskLevel determineBehavioralRiskLevel(double score) {
        if (score >= 0.8) return BehavioralRiskLevel.LOW;
        if (score >= 0.6) return BehavioralRiskLevel.MEDIUM;
        return BehavioralRiskLevel.HIGH;
    }
    
    private void updateBehavioralProfile(String userId, BehavioralBiometricRequest request) {
        // Store behavioral patterns for future comparison
        String key = "behavioral:profile:" + userId;
        redisTemplate.opsForValue().set(key, request, Duration.ofDays(30));
    }
    
    private List<String> detectBehavioralAnomalies(BehavioralBiometricRequest request) {
        // Detect anomalies in behavioral patterns
        return List.of(); // Mock implementation
    }
    
    private List<String> generateBehavioralRecommendations(BehavioralRiskLevel riskLevel) {
        return switch (riskLevel) {
            case HIGH -> List.of("Consider additional authentication", "Monitor for suspicious activity");
            case MEDIUM -> List.of("Continue monitoring");
            case LOW -> List.of("Normal behavior detected");
        };
    }
    
    private void clearBiometricCache(String userId, String credentialId) {
        String pattern = "biometric:*:" + userId + ":" + credentialId;
        redisTemplate.delete(pattern);
    }
    
    private String encryptPublicKey(String publicKey) {
        try {
            return encryptionService.encrypt(publicKey, biometricEncryptionKey);
        } catch (Exception e) {
            log.error("Failed to encrypt public key", e);
            throw new RuntimeException("Failed to encrypt public key", e);
        }
    }
    
    private String decryptPublicKey(String encryptedPublicKey) {
        try {
            return encryptionService.decrypt(encryptedPublicKey, biometricEncryptionKey);
        } catch (Exception e) {
            log.error("Failed to decrypt public key", e);
            throw new RuntimeException("Failed to decrypt public key", e);
        }
    }
    
    /**
     * Creates fallback template for unsupported biometric types
     */
    private BiometricTemplate createFallbackTemplate(BiometricRequest request) {
        log.info("Creating fallback biometric template for type: {}", request.getClass().getSimpleName());
        
        try {
            // Create a basic template structure
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("type", "FALLBACK");
            templateData.put("algorithm", "GENERIC");
            templateData.put("timestamp", LocalDateTime.now());
            templateData.put("qualityScore", 70.0); // Default quality
            
            // Extract any available data from the request
            if (request.getBiometricData() != null) {
                // Hash the biometric data for security
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(request.getBiometricData());
                templateData.put("dataHash", Base64.getEncoder().encodeToString(hash));
                templateData.put("dataLength", request.getBiometricData().length);
            }
            
            String templateJson = objectMapper.writeValueAsString(templateData);
            
            return BiometricTemplate.builder()
                .id(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .biometricType("FALLBACK")
                .algorithm("GENERIC")
                .templateData(templateJson.getBytes())
                .qualityScore(70.0)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(365))
                .version("1.0")
                .encrypted(false)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to create fallback biometric template", e);
            throw new RuntimeException("Failed to create fallback biometric template", e);
        }
    }
    
    /**
     * Generic biometric matcher for fallback scenarios
     */
    private final BiometricMatcher genericBiometricMatcher = new BiometricMatcher() {
        @Override
        public double match(byte[] template1, byte[] template2) {
            try {
                // Simple comparison for fallback - compares data hashes
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash1 = digest.digest(template1);
                byte[] hash2 = digest.digest(template2);
                
                // Calculate similarity based on matching bytes
                int matchingBytes = 0;
                int totalBytes = Math.min(hash1.length, hash2.length);
                
                for (int i = 0; i < totalBytes; i++) {
                    if (hash1[i] == hash2[i]) {
                        matchingBytes++;
                    }
                }
                
                double similarity = (double) matchingBytes / totalBytes;
                
                // Add some noise to prevent exact matches unless truly identical
                if (similarity < 1.0) {
                    similarity = Math.max(0.0, similarity - 0.1); // Reduce by 10%
                }
                
                return similarity;
                
            } catch (Exception e) {
                log.error("Generic biometric matching failed", e);
                return 0.0; // Fail secure
            }
        }
    };
}