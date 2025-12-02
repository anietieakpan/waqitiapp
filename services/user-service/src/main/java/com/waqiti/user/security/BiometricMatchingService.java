package com.waqiti.user.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Biometric Template Matching Service
 * 
 * Provides matching algorithms for different biometric modalities
 */
@Service
@Slf4j
public class BiometricMatchingService {
    
    /**
     * Match fingerprint templates using minutiae-based comparison
     */
    public double match(String storedTemplate, String candidateTemplate) throws Exception {
        log.debug("Performing biometric template matching");
        
        if (storedTemplate == null || candidateTemplate == null) {
            throw new BiometricProcessingException("Template data cannot be null");
        }
        
        if (storedTemplate.trim().isEmpty() || candidateTemplate.trim().isEmpty()) {
            throw new BiometricProcessingException("Template data cannot be empty");
        }
        
        // Determine matching algorithm based on template format
        if (isMinutiaeTemplate(storedTemplate)) {
            return matchMinutiae(storedTemplate, candidateTemplate);
        } else if (isFaceEmbedding(storedTemplate)) {
            return matchFaceEmbedding(storedTemplate, candidateTemplate);
        } else if (isVoiceMFCC(storedTemplate)) {
            return matchVoiceMFCC(storedTemplate, candidateTemplate);
        } else {
            // Fallback to simple similarity matching
            return calculateStringSimilarity(storedTemplate, candidateTemplate);
        }
    }
    
    /**
     * Match fingerprint minutiae templates
     */
    private double matchMinutiae(String stored, String candidate) {
        try {
            String[] storedMinutiae = stored.split(";");
            String[] candidateMinutiae = candidate.split(";");
            
            int matchingPoints = 0;
            int totalPoints = Math.min(storedMinutiae.length, candidateMinutiae.length);
            
            for (String storedPoint : storedMinutiae) {
                if (storedPoint.trim().isEmpty()) continue;
                
                String[] storedCoords = storedPoint.split(",");
                if (storedCoords.length < 4) continue;
                
                int storedX = Integer.parseInt(storedCoords[0]);
                int storedY = Integer.parseInt(storedCoords[1]);
                int storedAngle = Integer.parseInt(storedCoords[2]);
                
                // Find matching minutiae in candidate template
                for (String candidatePoint : candidateMinutiae) {
                    if (candidatePoint.trim().isEmpty()) continue;
                    
                    String[] candidateCoords = candidatePoint.split(",");
                    if (candidateCoords.length < 4) continue;
                    
                    int candidateX = Integer.parseInt(candidateCoords[0]);
                    int candidateY = Integer.parseInt(candidateCoords[1]);
                    int candidateAngle = Integer.parseInt(candidateCoords[2]);
                    
                    // Calculate Euclidean distance
                    double distance = Math.sqrt(Math.pow(storedX - candidateX, 2) + 
                                              Math.pow(storedY - candidateY, 2));
                    
                    // Calculate angle difference
                    int angleDiff = Math.abs(storedAngle - candidateAngle);
                    angleDiff = Math.min(angleDiff, 360 - angleDiff);
                    
                    // Consider it a match if within tolerance
                    if (distance <= 10 && angleDiff <= 15) {
                        matchingPoints++;
                        break;
                    }
                }
            }
            
            double score = totalPoints > 0 ? (double) matchingPoints / totalPoints : 0.0;
            log.debug("Minutiae matching: {}/{} points matched, score: {}", 
                matchingPoints, totalPoints, score);
            
            return score;
            
        } catch (Exception e) {
            log.error("Error in minutiae matching", e);
            return 0.0;
        }
    }
    
    /**
     * Match face embedding vectors using cosine similarity
     */
    private double matchFaceEmbedding(String stored, String candidate) {
        try {
            String[] storedFeatures = stored.split(",");
            String[] candidateFeatures = candidate.split(",");
            
            int dimensions = Math.min(storedFeatures.length, candidateFeatures.length);
            if (dimensions < 100) { // Minimum embedding size
                log.warn("Face embedding too small: {} dimensions", dimensions);
                return 0.0;
            }
            
            double dotProduct = 0.0;
            double storedNorm = 0.0;
            double candidateNorm = 0.0;
            
            for (int i = 0; i < dimensions; i++) {
                try {
                    double storedVal = Double.parseDouble(storedFeatures[i]);
                    double candidateVal = Double.parseDouble(candidateFeatures[i]);
                    
                    dotProduct += storedVal * candidateVal;
                    storedNorm += storedVal * storedVal;
                    candidateNorm += candidateVal * candidateVal;
                } catch (NumberFormatException e) {
                    // Skip invalid values
                    continue;
                }
            }
            
            if (storedNorm == 0.0 || candidateNorm == 0.0) {
                return 0.0;
            }
            
            // Cosine similarity
            double similarity = dotProduct / (Math.sqrt(storedNorm) * Math.sqrt(candidateNorm));
            
            // Convert to [0, 1] range
            double score = (similarity + 1.0) / 2.0;
            
            log.debug("Face embedding matching: cosine similarity = {}, score = {}", 
                similarity, score);
            
            return Math.max(0.0, Math.min(1.0, score));
            
        } catch (Exception e) {
            log.error("Error in face embedding matching", e);
            return 0.0;
        }
    }
    
    /**
     * Match voice MFCC templates using DTW (Dynamic Time Warping) approximation
     */
    private double matchVoiceMFCC(String stored, String candidate) {
        try {
            String[] storedFrames = stored.split(";");
            String[] candidateFrames = candidate.split(";");
            
            if (storedFrames.length < 10 || candidateFrames.length < 10) {
                log.warn("Voice templates too short for reliable matching");
                return 0.0;
            }
            
            // Simplified DTW - compare frame by frame with alignment tolerance
            double totalDistance = 0.0;
            int comparisons = 0;
            
            int maxFrames = Math.min(storedFrames.length, candidateFrames.length);
            
            for (int i = 0; i < maxFrames; i++) {
                if (storedFrames[i].trim().isEmpty() || candidateFrames[i].trim().isEmpty()) {
                    continue;
                }
                
                String[] storedCoeffs = storedFrames[i].split(",");
                String[] candidateCoeffs = candidateFrames[i].split(",");
                
                int coeffCount = Math.min(storedCoeffs.length, candidateCoeffs.length);
                if (coeffCount < 5) continue;
                
                double frameDistance = 0.0;
                for (int j = 0; j < coeffCount; j++) {
                    try {
                        double storedCoeff = Double.parseDouble(storedCoeffs[j]);
                        double candidateCoeff = Double.parseDouble(candidateCoeffs[j]);
                        frameDistance += Math.pow(storedCoeff - candidateCoeff, 2);
                    } catch (NumberFormatException e) {
                        // Skip invalid coefficients
                        continue;
                    }
                }
                
                totalDistance += Math.sqrt(frameDistance);
                comparisons++;
            }
            
            if (comparisons == 0) {
                return 0.0;
            }
            
            double averageDistance = totalDistance / comparisons;
            
            // Convert distance to similarity score (higher distance = lower similarity)
            double score = Math.max(0.0, 1.0 - (averageDistance / 10.0));
            
            log.debug("Voice MFCC matching: avg distance = {}, score = {}", 
                averageDistance, score);
            
            return score;
            
        } catch (Exception e) {
            log.error("Error in voice MFCC matching", e);
            return 0.0;
        }
    }
    
    /**
     * Fallback string similarity calculation
     */
    private double calculateStringSimilarity(String stored, String candidate) {
        if (stored.equals(candidate)) {
            return 1.0;
        }
        
        // Simple Jaccard similarity for fallback
        int maxLen = Math.max(stored.length(), candidate.length());
        if (maxLen == 0) return 1.0;
        
        int commonChars = 0;
        int minLen = Math.min(stored.length(), candidate.length());
        
        for (int i = 0; i < minLen; i++) {
            if (stored.charAt(i) == candidate.charAt(i)) {
                commonChars++;
            }
        }
        
        return (double) commonChars / maxLen;
    }
    
    // Template format detection methods
    
    private boolean isMinutiaeTemplate(String template) {
        return template.contains(";") && template.contains(",") && 
               template.split(";").length > 5;
    }
    
    private boolean isFaceEmbedding(String template) {
        String[] parts = template.split(",");
        return parts.length >= 128; // Typical face embedding size
    }
    
    private boolean isVoiceMFCC(String template) {
        return template.contains(";") && template.contains(",") && 
               template.split(";").length >= 10; // Multiple frames
    }
}