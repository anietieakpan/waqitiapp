package com.waqiti.security.consumer.voice;

import com.waqiti.security.event.VoiceBiometricEvent;
import com.waqiti.security.service.VoiceProcessingService;
import com.waqiti.security.service.BiometricAuthService;
import com.waqiti.security.service.VoiceAnalysisService;
import com.waqiti.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-grade Kafka consumer for voice and biometric events
 * Handles: voice-session-events, voice-enrollment-events, voice-preferences-events,
 * biometric-authentication-events, voice-verification-events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceBiometricConsumer {

    private final VoiceProcessingService voiceProcessingService;
    private final BiometricAuthService biometricAuthService;
    private final VoiceAnalysisService voiceAnalysisService;
    private final SecurityService securityService;

    @KafkaListener(topics = {"voice-session-events", "voice-enrollment-events", "voice-preferences-events",
                             "biometric-authentication-events", "voice-verification-events"}, 
                   groupId = "voice-biometric-processor")
    @Transactional
    public void processVoiceBiometricEvent(@Payload VoiceBiometricEvent event,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment) {
        try {
            log.info("Processing voice/biometric event: {} - Type: {} - User: {} - Session: {}", 
                    event.getEventId(), event.getEventType(), event.getUserId(), event.getSessionId());
            
            // Process based on topic
            switch (topic) {
                case "voice-session-events" -> handleVoiceSession(event);
                case "voice-enrollment-events" -> handleVoiceEnrollment(event);
                case "voice-preferences-events" -> handleVoicePreferences(event);
                case "biometric-authentication-events" -> handleBiometricAuthentication(event);
                case "voice-verification-events" -> handleVoiceVerification(event);
            }
            
            // Update biometric metrics
            updateBiometricMetrics(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed voice/biometric event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to process voice/biometric event {}: {}", 
                    event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("Voice/biometric processing failed", e);
        }
    }

    private void handleVoiceSession(VoiceBiometricEvent event) {
        String sessionType = event.getSessionType();
        String sessionId = event.getSessionId();
        
        switch (sessionType) {
            case "SESSION_STARTED" -> {
                // Initialize voice session
                voiceProcessingService.initializeSession(
                    sessionId,
                    event.getUserId(),
                    event.getDeviceInfo(),
                    event.getAudioConfig()
                );
                
                // Setup audio processing pipeline
                voiceProcessingService.setupAudioPipeline(
                    sessionId,
                    event.getSampleRate(),
                    event.getChannels(),
                    event.getBitDepth()
                );
                
                // Enable real-time processing
                if (event.isRealtimeProcessing()) {
                    voiceProcessingService.enableRealtimeProcessing(
                        sessionId,
                        event.getProcessingConfig()
                    );
                }
            }
            case "AUDIO_RECEIVED" -> {
                // Process audio data
                Map<String, Object> audioAnalysis = voiceProcessingService.processAudioData(
                    sessionId,
                    event.getAudioData(),
                    event.getAudioMetadata()
                );
                
                // Extract voice features
                Map<String, Object> voiceFeatures = voiceAnalysisService.extractVoiceFeatures(
                    event.getAudioData(),
                    event.getFeatureExtractionConfig()
                );
                
                // Store voice features
                voiceProcessingService.storeVoiceFeatures(
                    sessionId,
                    event.getUserId(),
                    voiceFeatures
                );
                
                // Perform voice quality assessment
                Map<String, Object> qualityAssessment = voiceAnalysisService.assessVoiceQuality(
                    event.getAudioData(),
                    event.getQualityThresholds()
                );
                
                // Store quality metrics
                voiceProcessingService.storeQualityMetrics(
                    sessionId,
                    qualityAssessment
                );
            }
            case "SESSION_ENDED" -> {
                // Finalize session
                Map<String, Object> sessionSummary = voiceProcessingService.finalizeSession(
                    sessionId,
                    event.getSessionDuration(),
                    event.getEndReason()
                );
                
                // Generate session report
                voiceProcessingService.generateSessionReport(
                    sessionId,
                    sessionSummary,
                    event.getReportFormat()
                );
                
                // Cleanup session resources
                voiceProcessingService.cleanupSessionResources(sessionId);
            }
            case "SESSION_ERROR" -> {
                // Handle session error
                voiceProcessingService.handleSessionError(
                    sessionId,
                    event.getErrorType(),
                    event.getErrorMessage(),
                    event.getErrorContext()
                );
                
                // Log error for analysis
                voiceProcessingService.logSessionError(
                    sessionId,
                    event.getErrorDetails(),
                    LocalDateTime.now()
                );
            }
        }
    }

    private void handleVoiceEnrollment(VoiceBiometricEvent event) {
        String enrollmentType = event.getEnrollmentType();
        String userId = event.getUserId();
        
        switch (enrollmentType) {
            case "ENROLLMENT_STARTED" -> {
                // Initialize enrollment process
                String enrollmentId = biometricAuthService.initializeEnrollment(
                    userId,
                    event.getBiometricType(),
                    event.getEnrollmentConfig()
                );
                event.setEnrollmentId(enrollmentId);
                
                // Setup enrollment session
                biometricAuthService.setupEnrollmentSession(
                    enrollmentId,
                    event.getSessionConfig(),
                    event.getQualityRequirements()
                );
            }
            case "VOICE_SAMPLE_CAPTURED" -> {
                // Process voice sample
                Map<String, Object> sampleAnalysis = voiceAnalysisService.analyzeVoiceSample(
                    event.getVoiceSample(),
                    event.getAnalysisConfig()
                );
                
                // Validate sample quality
                boolean isQualityAcceptable = voiceAnalysisService.validateSampleQuality(
                    event.getVoiceSample(),
                    event.getQualityThresholds()
                );
                
                if (isQualityAcceptable) {
                    // Extract biometric template
                    Map<String, Object> biometricTemplate = voiceAnalysisService.extractBiometricTemplate(
                        event.getVoiceSample(),
                        event.getTemplateConfig()
                    );
                    
                    // Store enrollment sample
                    biometricAuthService.storeEnrollmentSample(
                        event.getEnrollmentId(),
                        biometricTemplate,
                        sampleAnalysis
                    );
                } else {
                    // Request sample re-capture
                    biometricAuthService.requestSampleRecapture(
                        event.getEnrollmentId(),
                        event.getQualityIssues(),
                        event.getRecaptureInstructions()
                    );
                }
            }
            case "ENROLLMENT_COMPLETED" -> {
                // Finalize enrollment
                Map<String, Object> enrollmentResult = biometricAuthService.finalizeEnrollment(
                    event.getEnrollmentId(),
                    event.getEnrollmentSamples(),
                    event.getConfidenceThreshold()
                );
                
                // Generate master template
                Map<String, Object> masterTemplate = voiceAnalysisService.generateMasterTemplate(
                    event.getEnrollmentSamples(),
                    event.getTemplateGenerationConfig()
                );
                
                // Store master template securely
                biometricAuthService.storeMasterTemplate(
                    userId,
                    masterTemplate,
                    event.getEncryptionKey()
                );
                
                // Update user biometric status
                biometricAuthService.updateBiometricStatus(
                    userId,
                    event.getBiometricType(),
                    "ENROLLED",
                    enrollmentResult
                );
                
                // Send enrollment confirmation
                biometricAuthService.sendEnrollmentConfirmation(
                    userId,
                    event.getEnrollmentId(),
                    enrollmentResult
                );
            }
            case "ENROLLMENT_FAILED" -> {
                // Handle enrollment failure
                biometricAuthService.handleEnrollmentFailure(
                    event.getEnrollmentId(),
                    event.getFailureReason(),
                    event.getRetryOptions()
                );
                
                // Log failure for analysis
                biometricAuthService.logEnrollmentFailure(
                    userId,
                    event.getFailureReason(),
                    event.getFailureContext()
                );
            }
        }
    }

    private void handleVoicePreferences(VoiceBiometricEvent event) {
        String userId = event.getUserId();
        String preferenceType = event.getPreferenceType();
        
        switch (preferenceType) {
            case "VOICE_SETTINGS_UPDATED" -> {
                // Update voice settings
                voiceProcessingService.updateVoiceSettings(
                    userId,
                    event.getVoiceSettings(),
                    event.getUpdateReason()
                );
                
                // Validate settings
                Map<String, Object> validationResult = voiceProcessingService.validateVoiceSettings(
                    event.getVoiceSettings(),
                    event.getValidationRules()
                );
                
                // Apply settings
                if ((Boolean) validationResult.get("isValid")) {
                    voiceProcessingService.applyVoiceSettings(
                        userId,
                        event.getVoiceSettings()
                    );
                } else {
                    voiceProcessingService.rejectSettingsUpdate(
                        userId,
                        validationResult,
                        event.getFallbackSettings()
                    );
                }
            }
            case "PRIVACY_SETTINGS_CHANGED" -> {
                // Update privacy settings
                biometricAuthService.updatePrivacySettings(
                    userId,
                    event.getPrivacySettings(),
                    event.getPrivacyLevel()
                );
                
                // Apply data handling changes
                biometricAuthService.applyDataHandlingChanges(
                    userId,
                    event.getDataRetentionPolicy(),
                    event.getDataSharingPreferences()
                );
            }
            case "SECURITY_PREFERENCES_UPDATED" -> {
                // Update security preferences
                securityService.updateSecurityPreferences(
                    userId,
                    event.getSecurityPreferences(),
                    event.getSecurityLevel()
                );
                
                // Apply security policies
                securityService.applySecurityPolicies(
                    userId,
                    event.getSecurityPolicies()
                );
            }
        }
        
        // Store preference history
        voiceProcessingService.storePreferenceHistory(
            userId,
            preferenceType,
            event.getOldPreferences(),
            event.getNewPreferences(),
            LocalDateTime.now()
        );
    }

    private void handleBiometricAuthentication(VoiceBiometricEvent event) {
        String authType = event.getAuthenticationType();
        String userId = event.getUserId();
        
        switch (authType) {
            case "AUTHENTICATION_REQUESTED" -> {
                // Initialize authentication
                String authSessionId = biometricAuthService.initializeAuthentication(
                    userId,
                    event.getBiometricType(),
                    event.getAuthConfig()
                );
                event.setAuthSessionId(authSessionId);
                
                // Setup authentication context
                biometricAuthService.setupAuthContext(
                    authSessionId,
                    event.getDeviceInfo(),
                    event.getLocationInfo(),
                    event.getRiskFactors()
                );
            }
            case "BIOMETRIC_CAPTURED" -> {
                // Process biometric sample
                Map<String, Object> capturedSample = event.getBiometricSample();
                
                // Validate sample quality
                Map<String, Object> qualityAssessment = voiceAnalysisService.assessSampleQuality(
                    capturedSample,
                    event.getQualityRequirements()
                );
                
                if ((Boolean) qualityAssessment.get("isAcceptable")) {
                    // Extract features for matching
                    Map<String, Object> features = voiceAnalysisService.extractAuthFeatures(
                        capturedSample,
                        event.getFeatureConfig()
                    );
                    
                    // Store for matching
                    biometricAuthService.storeCapturedSample(
                        event.getAuthSessionId(),
                        features,
                        qualityAssessment
                    );
                } else {
                    // Request sample recapture
                    biometricAuthService.requestAuthRecapture(
                        event.getAuthSessionId(),
                        qualityAssessment,
                        event.getRecaptureGuidance()
                    );
                }
            }
            case "AUTHENTICATION_COMPLETED" -> {
                // Perform biometric matching
                Map<String, Object> matchingResult = biometricAuthService.performBiometricMatching(
                    event.getAuthSessionId(),
                    userId,
                    event.getMatchingConfig()
                );
                
                // Calculate authentication score
                Double authScore = (Double) matchingResult.get("matchingScore");
                boolean isAuthenticated = authScore >= event.getAuthThreshold();
                
                // Store authentication result
                biometricAuthService.storeAuthResult(
                    event.getAuthSessionId(),
                    userId,
                    isAuthenticated,
                    authScore,
                    matchingResult
                );
                
                // Update authentication metrics
                biometricAuthService.updateAuthMetrics(
                    userId,
                    event.getBiometricType(),
                    isAuthenticated,
                    authScore
                );
                
                // Send authentication response
                biometricAuthService.sendAuthResponse(
                    event.getAuthSessionId(),
                    isAuthenticated,
                    event.getResponseConfig()
                );
                
                // Log authentication attempt
                securityService.logAuthenticationAttempt(
                    userId,
                    event.getBiometricType(),
                    isAuthenticated,
                    event.getAuthContext()
                );
            }
            case "AUTHENTICATION_FAILED" -> {
                // Handle authentication failure
                biometricAuthService.handleAuthFailure(
                    event.getAuthSessionId(),
                    event.getFailureReason(),
                    event.getFailureCode()
                );
                
                // Update failure metrics
                biometricAuthService.updateFailureMetrics(
                    userId,
                    event.getBiometricType(),
                    event.getFailureReason()
                );
                
                // Check for fraud patterns
                if (biometricAuthService.detectFraudPattern(userId, event.getFailureHistory())) {
                    securityService.flagFraudulentActivity(
                        userId,
                        event.getBiometricType(),
                        event.getFraudIndicators()
                    );
                }
            }
        }
    }

    private void handleVoiceVerification(VoiceBiometricEvent event) {
        String verificationType = event.getVerificationType();
        String userId = event.getUserId();
        
        switch (verificationType) {
            case "VOICE_VERIFICATION_REQUESTED" -> {
                // Initialize voice verification
                String verificationId = voiceAnalysisService.initializeVerification(
                    userId,
                    event.getVerificationConfig(),
                    event.getVerificationPhrase()
                );
                event.setVerificationId(verificationId);
                
                // Setup verification parameters
                voiceAnalysisService.setupVerificationParameters(
                    verificationId,
                    event.getVerificationThreshold(),
                    event.getVerificationMode()
                );
            }
            case "VOICE_PHRASE_CAPTURED" -> {
                // Process voice phrase
                Map<String, Object> phraseAnalysis = voiceAnalysisService.analyzeVoicePhrase(
                    event.getVoicePhrase(),
                    event.getExpectedPhrase(),
                    event.getAnalysisConfig()
                );
                
                // Verify phrase content
                boolean isPhraseCorrect = (Boolean) phraseAnalysis.get("isPhraseCorrect");
                
                // Extract speaker verification features
                Map<String, Object> speakerFeatures = voiceAnalysisService.extractSpeakerFeatures(
                    event.getVoicePhrase(),
                    event.getSpeakerConfig()
                );
                
                // Store verification data
                voiceAnalysisService.storeVerificationData(
                    event.getVerificationId(),
                    phraseAnalysis,
                    speakerFeatures,
                    isPhraseCorrect
                );
            }
            case "VERIFICATION_COMPLETED" -> {
                // Perform speaker verification
                Map<String, Object> verificationResult = voiceAnalysisService.performSpeakerVerification(
                    event.getVerificationId(),
                    userId,
                    event.getVerificationThreshold()
                );
                
                // Calculate verification confidence
                Double confidence = (Double) verificationResult.get("confidence");
                boolean isVerified = confidence >= event.getConfidenceThreshold();
                
                // Store verification result
                voiceAnalysisService.storeVerificationResult(
                    event.getVerificationId(),
                    userId,
                    isVerified,
                    confidence,
                    verificationResult
                );
                
                // Send verification response
                voiceAnalysisService.sendVerificationResponse(
                    event.getVerificationId(),
                    isVerified,
                    confidence,
                    event.getResponseFormat()
                );
                
                // Update verification metrics
                voiceAnalysisService.updateVerificationMetrics(
                    userId,
                    isVerified,
                    confidence,
                    event.getVerificationDuration()
                );
            }
            case "VERIFICATION_FAILED" -> {
                // Handle verification failure
                voiceAnalysisService.handleVerificationFailure(
                    event.getVerificationId(),
                    event.getFailureReason(),
                    event.getFailureDetails()
                );
                
                // Log failure for improvement
                voiceAnalysisService.logVerificationFailure(
                    userId,
                    event.getFailureReason(),
                    event.getFailureContext(),
                    LocalDateTime.now()
                );
            }
        }
    }

    private void updateBiometricMetrics(VoiceBiometricEvent event) {
        // Update processing metrics
        biometricAuthService.updateProcessingMetrics(
            event.getEventType(),
            event.getBiometricType(),
            event.getProcessingTime(),
            event.getSuccessRate()
        );
        
        // Update quality metrics
        if (event.getQualityMetrics() != null) {
            voiceAnalysisService.updateQualityMetrics(
                event.getBiometricType(),
                event.getQualityMetrics()
            );
        }
        
        // Update security metrics
        securityService.updateSecurityMetrics(
            event.getEventType(),
            event.getSecurityLevel(),
            event.getThreatIndicators()
        );
    }
}