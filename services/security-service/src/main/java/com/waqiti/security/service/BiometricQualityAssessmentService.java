package com.waqiti.security.service;

import com.waqiti.security.enums.BiometricQuality;
import com.waqiti.security.enums.BiometricType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Biometric Quality Assessment Service
 * Evaluates the quality of biometric samples to ensure accurate matching
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiometricQualityAssessmentService {
    
    @Value("${biometric.quality.fingerprint.min-ridge-count:12}")
    private int minFingerprintRidgeCount;
    
    @Value("${biometric.quality.face.min-resolution:640}")
    private int minFaceResolution;
    
    @Value("${biometric.quality.voice.min-duration-ms:3000}")
    private int minVoiceDurationMs;
    
    @Value("${biometric.quality.iris.min-pupil-dilation:0.3}")
    private double minPupilDilation;
    
    /**
     * Assess the quality of biometric data
     */
    public BiometricQuality assessQuality(byte[] biometricData, BiometricType biometricType) {
        if (biometricData == null || biometricData.length == 0) {
            return BiometricQuality.builder()
                    .score(0)
                    .level(BiometricQuality.QualityLevel.POOR)
                    .issues(Map.of("error", "No biometric data provided"))
                    .build();
        }
        
        try {
            switch (biometricType) {
                case FINGERPRINT:
                    return assessFingerprintQuality(biometricData);
                case FACE:
                    return assessFaceQuality(biometricData);
                case VOICE:
                    return assessVoiceQuality(biometricData);
                case IRIS:
                    return assessIrisQuality(biometricData);
                default:
                    return BiometricQuality.builder()
                            .score(50)
                            .level(BiometricQuality.QualityLevel.FAIR)
                            .issues(Map.of("warning", "Unknown biometric type"))
                            .build();
            }
        } catch (Exception e) {
            log.error("Error assessing biometric quality for type: {}", biometricType, e);
            return BiometricQuality.builder()
                    .score(0)
                    .level(BiometricQuality.QualityLevel.POOR)
                    .issues(Map.of("error", e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Assess fingerprint quality
     */
    private BiometricQuality assessFingerprintQuality(byte[] fingerprintData) {
        Map<String, String> issues = new HashMap<>();
        int score = 100;
        
        try {
            // Check data size
            if (fingerprintData.length < 10000) {
                score -= 30;
                issues.put("size", "Fingerprint data too small");
            }
            
            // Simulate ridge count analysis
            int ridgeCount = simulateRidgeCount(fingerprintData);
            if (ridgeCount < minFingerprintRidgeCount) {
                score -= 20;
                issues.put("ridges", "Insufficient ridge details");
            }
            
            // Check for noise and clarity
            double noiseLevel = calculateNoiseLevel(fingerprintData);
            if (noiseLevel > 0.3) {
                score -= 15;
                issues.put("noise", "High noise level detected");
            }
            
            // Check contrast
            double contrast = calculateContrast(fingerprintData);
            if (contrast < 0.5) {
                score -= 10;
                issues.put("contrast", "Low contrast");
            }
            
        } catch (Exception e) {
            log.error("Error in fingerprint quality assessment", e);
            score = 30;
            issues.put("error", "Assessment error: " + e.getMessage());
        }
        
        return BiometricQuality.builder()
                .score(Math.max(0, Math.min(100, score)))
                .level(getQualityLevel(score))
                .issues(issues)
                .biometricType(BiometricType.FINGERPRINT)
                .build();
    }
    
    /**
     * Assess face quality
     */
    private BiometricQuality assessFaceQuality(byte[] faceData) {
        Map<String, String> issues = new HashMap<>();
        int score = 100;
        
        try {
            // Try to load as image
            ByteArrayInputStream bis = new ByteArrayInputStream(faceData);
            BufferedImage image = ImageIO.read(bis);
            
            if (image != null) {
                // Check resolution
                if (image.getWidth() < minFaceResolution || image.getHeight() < minFaceResolution) {
                    score -= 25;
                    issues.put("resolution", "Image resolution too low");
                }
                
                // Check aspect ratio
                double aspectRatio = (double) image.getWidth() / image.getHeight();
                if (aspectRatio < 0.7 || aspectRatio > 1.3) {
                    score -= 10;
                    issues.put("aspect", "Unusual aspect ratio");
                }
                
                // Simulate face detection confidence
                double faceConfidence = simulateFaceDetection(faceData);
                if (faceConfidence < 0.8) {
                    score -= 20;
                    issues.put("detection", "Low face detection confidence");
                }
                
                // Check lighting conditions
                double brightness = calculateAverageBrightness(image);
                if (brightness < 0.3 || brightness > 0.8) {
                    score -= 15;
                    issues.put("lighting", "Poor lighting conditions");
                }
                
            } else {
                score = 20;
                issues.put("format", "Invalid image format");
            }
            
        } catch (Exception e) {
            log.error("Error in face quality assessment", e);
            score = 30;
            issues.put("error", "Assessment error: " + e.getMessage());
        }
        
        return BiometricQuality.builder()
                .score(Math.max(0, Math.min(100, score)))
                .level(getQualityLevel(score))
                .issues(issues)
                .biometricType(BiometricType.FACE)
                .build();
    }
    
    /**
     * Assess voice quality
     */
    private BiometricQuality assessVoiceQuality(byte[] voiceData) {
        Map<String, String> issues = new HashMap<>();
        int score = 100;
        
        try {
            // Check data size (approximate duration)
            int estimatedDurationMs = (voiceData.length / 16); // Rough estimate
            if (estimatedDurationMs < minVoiceDurationMs) {
                score -= 30;
                issues.put("duration", "Voice sample too short");
            }
            
            // Simulate signal-to-noise ratio
            double snr = calculateSignalToNoiseRatio(voiceData);
            if (snr < 15) {
                score -= 25;
                issues.put("snr", "Low signal-to-noise ratio");
            }
            
            // Check for clipping
            if (hasAudioClipping(voiceData)) {
                score -= 15;
                issues.put("clipping", "Audio clipping detected");
            }
            
            // Check frequency range
            double frequencyRange = calculateFrequencyRange(voiceData);
            if (frequencyRange < 0.4) {
                score -= 10;
                issues.put("frequency", "Limited frequency range");
            }
            
        } catch (Exception e) {
            log.error("Error in voice quality assessment", e);
            score = 30;
            issues.put("error", "Assessment error: " + e.getMessage());
        }
        
        return BiometricQuality.builder()
                .score(Math.max(0, Math.min(100, score)))
                .level(getQualityLevel(score))
                .issues(issues)
                .biometricType(BiometricType.VOICE)
                .build();
    }
    
    /**
     * Assess iris quality
     */
    private BiometricQuality assessIrisQuality(byte[] irisData) {
        Map<String, String> issues = new HashMap<>();
        int score = 100;
        
        try {
            // Check data size
            if (irisData.length < 50000) {
                score -= 25;
                issues.put("size", "Iris data insufficient");
            }
            
            // Simulate iris visibility check
            double irisVisibility = simulateIrisVisibility(irisData);
            if (irisVisibility < 0.7) {
                score -= 30;
                issues.put("visibility", "Poor iris visibility");
            }
            
            // Check for motion blur
            double blurLevel = calculateBlurLevel(irisData);
            if (blurLevel > 0.2) {
                score -= 20;
                issues.put("blur", "Motion blur detected");
            }
            
            // Check pupil dilation
            double pupilDilation = simulatePupilDilation(irisData);
            if (pupilDilation < minPupilDilation) {
                score -= 15;
                issues.put("pupil", "Insufficient pupil dilation");
            }
            
        } catch (Exception e) {
            log.error("Error in iris quality assessment", e);
            score = 30;
            issues.put("error", "Assessment error: " + e.getMessage());
        }
        
        return BiometricQuality.builder()
                .score(Math.max(0, Math.min(100, score)))
                .level(getQualityLevel(score))
                .issues(issues)
                .biometricType(BiometricType.IRIS)
                .build();
    }
    
    // Simulation methods for quality metrics
    
    private int simulateRidgeCount(byte[] data) {
        // Simulate ridge count analysis
        return 8 + (Math.abs(data.hashCode()) % 10);
    }
    
    private double calculateNoiseLevel(byte[] data) {
        // Simulate noise level calculation
        double sum = 0;
        for (int i = 1; i < Math.min(data.length, 1000); i++) {
            sum += Math.abs(data[i] - data[i-1]);
        }
        return Math.min(1.0, sum / (1000 * 255.0));
    }
    
    private double calculateContrast(byte[] data) {
        // Simulate contrast calculation
        int min = 255, max = 0;
        for (int i = 0; i < Math.min(data.length, 1000); i++) {
            int value = data[i] & 0xFF;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return (max - min) / 255.0;
    }
    
    private double simulateFaceDetection(byte[] data) {
        // Simulate face detection confidence
        return 0.7 + (Math.abs(data.hashCode()) % 30) / 100.0;
    }
    
    private double calculateAverageBrightness(BufferedImage image) {
        // Calculate average brightness of image
        long sum = 0;
        int pixels = 0;
        
        for (int x = 0; x < Math.min(image.getWidth(), 100); x += 10) {
            for (int y = 0; y < Math.min(image.getHeight(), 100); y += 10) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                sum += (r + g + b) / 3;
                pixels++;
            }
        }
        
        return (pixels > 0) ? (sum / (pixels * 255.0)) : 0.5;
    }
    
    private double calculateSignalToNoiseRatio(byte[] data) {
        // Simulate SNR calculation
        double signal = 0, noise = 0;
        for (int i = 0; i < Math.min(data.length, 1000); i++) {
            signal += Math.abs(data[i]);
            if (i > 0) {
                noise += Math.abs(data[i] - data[i-1]);
            }
        }
        return (noise > 0) ? 20 * Math.log10(signal / noise) : 20;
    }
    
    private boolean hasAudioClipping(byte[] data) {
        // Check for audio clipping
        int clippedSamples = 0;
        for (int i = 0; i < Math.min(data.length, 1000); i++) {
            int value = data[i] & 0xFF;
            if (value == 0 || value == 255) {
                clippedSamples++;
            }
        }
        return clippedSamples > 50;
    }
    
    private double calculateFrequencyRange(byte[] data) {
        // Simulate frequency range analysis
        return 0.3 + (Math.abs(data.hashCode()) % 70) / 100.0;
    }
    
    private double simulateIrisVisibility(byte[] data) {
        // Simulate iris visibility check
        return 0.6 + (Math.abs(data.hashCode()) % 40) / 100.0;
    }
    
    private double calculateBlurLevel(byte[] data) {
        // Simulate blur detection
        double edgeStrength = 0;
        for (int i = 1; i < Math.min(data.length, 1000); i++) {
            edgeStrength += Math.abs(data[i] - data[i-1]);
        }
        return 1.0 - Math.min(1.0, edgeStrength / (500 * 128));
    }
    
    private double simulatePupilDilation(byte[] data) {
        // Simulate pupil dilation measurement
        return 0.2 + (Math.abs(data.hashCode()) % 60) / 100.0;
    }
    
    private BiometricQuality.QualityLevel getQualityLevel(int score) {
        if (score >= 80) return BiometricQuality.QualityLevel.EXCELLENT;
        if (score >= 60) return BiometricQuality.QualityLevel.GOOD;
        if (score >= 40) return BiometricQuality.QualityLevel.FAIR;
        return BiometricQuality.QualityLevel.POOR;
    }
}