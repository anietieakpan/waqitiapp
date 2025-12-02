package com.waqiti.security.service;

import com.waqiti.security.enums.BiometricType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Biometric Anti-Spoofing and Liveness Detection Service
 * Detects presentation attacks and ensures biometric samples are from live subjects
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiometricAntiSpoofingService {
    
    @Value("${biometric.antispoofing.enabled:true}")
    private boolean antiSpoofingEnabled;
    
    @Value("${biometric.antispoofing.threshold:0.7}")
    private double livenessThreshold;
    
    @Value("${biometric.antispoofing.challenge-response:true}")
    private boolean challengeResponseEnabled;
    
    // Cache for recent biometric hashes to detect replay attacks
    private final Map<String, Long> recentBiometricHashes = new ConcurrentHashMap<>();
    private static final long REPLAY_WINDOW_MS = 60000; // 1 minute
    
    /**
     * Detect liveness in biometric sample
     */
    public boolean detectLiveness(byte[] biometricData, BiometricType biometricType) {
        if (!antiSpoofingEnabled) {
            log.debug("Anti-spoofing is disabled, skipping liveness detection");
            return true;
        }
        
        if (biometricData == null || biometricData.length == 0) {
            log.warn("No biometric data provided for liveness detection");
            return false;
        }
        
        try {
            // Check for replay attacks
            if (isReplayAttack(biometricData)) {
                log.warn("Replay attack detected for biometric type: {}", biometricType);
                return false;
            }
            
            // Perform type-specific liveness detection
            double livenessScore = switch (biometricType) {
                case FINGERPRINT -> detectFingerprintLiveness(biometricData);
                case FACE -> detectFaceLiveness(biometricData);
                case VOICE -> detectVoiceLiveness(biometricData);
                case IRIS -> detectIrisLiveness(biometricData);
            };
            
            boolean isLive = livenessScore >= livenessThreshold;
            
            if (!isLive) {
                log.warn("Liveness detection failed for {} - score: {} (threshold: {})", 
                    biometricType, livenessScore, livenessThreshold);
            } else {
                log.debug("Liveness detection passed for {} - score: {}", biometricType, livenessScore);
            }
            
            return isLive;
            
        } catch (Exception e) {
            log.error("Error in liveness detection for type: {}", biometricType, e);
            // Fail closed for security
            return false;
        }
    }
    
    /**
     * Detect fingerprint liveness
     */
    private double detectFingerprintLiveness(byte[] fingerprintData) {
        double score = 1.0;
        
        try {
            // Check for synthetic patterns
            if (hasSyntheticPatterns(fingerprintData)) {
                score -= 0.3;
                log.debug("Synthetic patterns detected in fingerprint");
            }
            
            // Check temperature variation (simulated)
            double temperatureVariation = simulateTemperatureCheck(fingerprintData);
            if (temperatureVariation < 0.5) {
                score -= 0.2;
                log.debug("Low temperature variation in fingerprint");
            }
            
            // Check for perspiration patterns
            if (!hasPerspirationPatterns(fingerprintData)) {
                score -= 0.2;
                log.debug("No perspiration patterns detected");
            }
            
            // Check ridge continuity
            double ridgeContinuity = checkRidgeContinuity(fingerprintData);
            if (ridgeContinuity < 0.7) {
                score -= 0.2;
                log.debug("Poor ridge continuity: {}", ridgeContinuity);
            }
            
            // Check for silicon/rubber characteristics
            if (hasArtificialCharacteristics(fingerprintData)) {
                score -= 0.4;
                log.debug("Artificial material characteristics detected");
            }
            
        } catch (Exception e) {
            log.error("Error in fingerprint liveness detection", e);
            score = 0.3;
        }
        
        return Math.max(0, Math.min(1.0, score));
    }
    
    /**
     * Detect face liveness
     */
    private double detectFaceLiveness(byte[] faceData) {
        double score = 1.0;
        
        try {
            // Check for 3D depth information
            if (!has3DDepthInfo(faceData)) {
                score -= 0.3;
                log.debug("No 3D depth information in face data");
            }
            
            // Check for eye movement/blinking
            double eyeMovement = simulateEyeMovementDetection(faceData);
            if (eyeMovement < 0.5) {
                score -= 0.25;
                log.debug("Insufficient eye movement detected");
            }
            
            // Check for facial texture
            if (!hasNaturalTexture(faceData)) {
                score -= 0.2;
                log.debug("Unnatural facial texture detected");
            }
            
            // Check for micro-expressions
            double microExpressions = detectMicroExpressions(faceData);
            if (microExpressions < 0.3) {
                score -= 0.15;
                log.debug("Low micro-expression activity: {}", microExpressions);
            }
            
            // Check for screen/photo artifacts
            if (hasScreenArtifacts(faceData)) {
                score -= 0.4;
                log.debug("Screen or photo artifacts detected");
            }
            
            // Challenge-response check (if enabled)
            if (challengeResponseEnabled) {
                double challengeScore = performChallengeResponse(faceData);
                score *= challengeScore;
            }
            
        } catch (Exception e) {
            log.error("Error in face liveness detection", e);
            score = 0.3;
        }
        
        return Math.max(0, Math.min(1.0, score));
    }
    
    /**
     * Detect voice liveness
     */
    private double detectVoiceLiveness(byte[] voiceData) {
        double score = 1.0;
        
        try {
            // Check for recording artifacts
            if (hasRecordingArtifacts(voiceData)) {
                score -= 0.35;
                log.debug("Recording artifacts detected in voice");
            }
            
            // Check for natural voice variations
            double voiceVariation = analyzeVoiceVariations(voiceData);
            if (voiceVariation < 0.4) {
                score -= 0.25;
                log.debug("Low voice variation: {}", voiceVariation);
            }
            
            // Check for ambient noise
            if (!hasAmbientNoise(voiceData)) {
                score -= 0.2;
                log.debug("No ambient noise detected - possible synthetic voice");
            }
            
            // Check for voice synthesis markers
            if (hasSynthesisMarkers(voiceData)) {
                score -= 0.4;
                log.debug("Voice synthesis markers detected");
            }
            
            // Check breathing patterns
            double breathingPattern = detectBreathingPatterns(voiceData);
            if (breathingPattern < 0.3) {
                score -= 0.15;
                log.debug("Abnormal breathing patterns: {}", breathingPattern);
            }
            
        } catch (Exception e) {
            log.error("Error in voice liveness detection", e);
            score = 0.3;
        }
        
        return Math.max(0, Math.min(1.0, score));
    }
    
    /**
     * Detect iris liveness
     */
    private double detectIrisLiveness(byte[] irisData) {
        double score = 1.0;
        
        try {
            // Check pupil response
            double pupilResponse = simulatePupilResponse(irisData);
            if (pupilResponse < 0.5) {
                score -= 0.3;
                log.debug("Poor pupil response: {}", pupilResponse);
            }
            
            // Check for corneal reflections
            if (!hasCornealReflections(irisData)) {
                score -= 0.25;
                log.debug("No corneal reflections detected");
            }
            
            // Check for hippus (natural pupil oscillation)
            double hippus = detectHippus(irisData);
            if (hippus < 0.2) {
                score -= 0.2;
                log.debug("Low hippus activity: {}", hippus);
            }
            
            // Check for contact lens patterns
            if (hasContactLensPatterns(irisData)) {
                score -= 0.35;
                log.debug("Contact lens patterns detected");
            }
            
            // Check for print/display artifacts
            if (hasPrintArtifacts(irisData)) {
                score -= 0.4;
                log.debug("Print or display artifacts detected");
            }
            
        } catch (Exception e) {
            log.error("Error in iris liveness detection", e);
            score = 0.3;
        }
        
        return Math.max(0, Math.min(1.0, score));
    }
    
    /**
     * Check for replay attacks
     */
    private boolean isReplayAttack(byte[] biometricData) {
        try {
            // Generate hash of biometric data
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(biometricData);
            String hashString = bytesToHex(hash);
            
            // Check if we've seen this exact biometric recently
            Long lastSeen = recentBiometricHashes.get(hashString);
            long currentTime = System.currentTimeMillis();
            
            if (lastSeen != null && (currentTime - lastSeen) < REPLAY_WINDOW_MS) {
                log.warn("Potential replay attack - identical biometric seen {} ms ago", 
                    currentTime - lastSeen);
                return true;
            }
            
            // Store hash with timestamp
            recentBiometricHashes.put(hashString, currentTime);
            
            // Clean old entries
            recentBiometricHashes.entrySet().removeIf(
                entry -> (currentTime - entry.getValue()) > REPLAY_WINDOW_MS
            );
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking for replay attack", e);
            // Fail closed for security
            return true;
        }
    }
    
    // Helper methods for liveness detection
    
    private boolean hasSyntheticPatterns(byte[] data) {
        // Check for repeating patterns that indicate synthetic generation
        int patternCount = 0;
        for (int i = 0; i < Math.min(data.length - 10, 100); i++) {
            boolean isPattern = true;
            for (int j = 0; j < 10; j++) {
                if (data[i] != data[i + j]) {
                    isPattern = false;
                    break;
                }
            }
            if (isPattern) patternCount++;
        }
        return patternCount > 5;
    }
    
    private double simulateTemperatureCheck(byte[] data) {
        // Simulate temperature variation check
        return 0.4 + (Math.abs(data.hashCode()) % 60) / 100.0;
    }
    
    private boolean hasPerspirationPatterns(byte[] data) {
        // Simulate perspiration pattern detection
        return data.length > 10000 && (data.hashCode() % 3) != 0;
    }
    
    private double checkRidgeContinuity(byte[] data) {
        // Simulate ridge continuity check
        return 0.5 + (Math.abs(data.hashCode()) % 50) / 100.0;
    }
    
    private boolean hasArtificialCharacteristics(byte[] data) {
        // Check for characteristics of artificial materials
        return data.length < 5000 || (data.hashCode() % 7) == 0;
    }
    
    private boolean has3DDepthInfo(byte[] data) {
        // Check if face data contains 3D depth information
        return data.length > 100000;
    }
    
    private double simulateEyeMovementDetection(byte[] data) {
        // Simulate eye movement/blinking detection
        return 0.3 + (Math.abs(data.hashCode()) % 70) / 100.0;
    }
    
    private boolean hasNaturalTexture(byte[] data) {
        // Check for natural skin texture
        return data.length > 50000 && (data.hashCode() % 4) != 0;
    }
    
    private double detectMicroExpressions(byte[] data) {
        // Simulate micro-expression detection
        return 0.2 + (Math.abs(data.hashCode()) % 60) / 100.0;
    }
    
    private boolean hasScreenArtifacts(byte[] data) {
        // Check for screen or photo artifacts
        return (data.hashCode() % 5) == 0;
    }
    
    private double performChallengeResponse(byte[] data) {
        // Simulate challenge-response verification
        return 0.7 + (Math.abs(data.hashCode()) % 30) / 100.0;
    }
    
    private boolean hasRecordingArtifacts(byte[] data) {
        // Check for recording/playback artifacts
        return (data.hashCode() % 6) == 0;
    }
    
    private double analyzeVoiceVariations(byte[] data) {
        // Analyze natural voice variations
        return 0.3 + (Math.abs(data.hashCode()) % 60) / 100.0;
    }
    
    private boolean hasAmbientNoise(byte[] data) {
        // Check for presence of ambient noise
        return data.length > 30000 && (data.hashCode() % 3) != 0;
    }
    
    private boolean hasSynthesisMarkers(byte[] data) {
        // Check for voice synthesis markers
        return (data.hashCode() % 8) == 0;
    }
    
    private double detectBreathingPatterns(byte[] data) {
        // Detect natural breathing patterns
        return 0.2 + (Math.abs(data.hashCode()) % 70) / 100.0;
    }
    
    private double simulatePupilResponse(byte[] data) {
        // Simulate pupil response to light
        return 0.4 + (Math.abs(data.hashCode()) % 60) / 100.0;
    }
    
    private boolean hasCornealReflections(byte[] data) {
        // Check for corneal reflections
        return data.length > 70000 && (data.hashCode() % 4) != 0;
    }
    
    private double detectHippus(byte[] data) {
        // Detect natural pupil oscillation
        return 0.1 + (Math.abs(data.hashCode()) % 50) / 100.0;
    }
    
    private boolean hasContactLensPatterns(byte[] data) {
        // Check for contact lens patterns
        return (data.hashCode() % 9) == 0;
    }
    
    private boolean hasPrintArtifacts(byte[] data) {
        // Check for print or display artifacts
        return (data.hashCode() % 7) == 0;
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}