package com.waqiti.user.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Biometric Template Data Structure
 * 
 * Represents encrypted biometric template data for secure storage and matching
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricTemplate {
    
    private String data; // Encrypted template data
    private String version; // Template version
    private String algorithm; // Extraction algorithm used
    private Double qualityScore; // Template quality score
    private LocalDateTime extractedAt; // When template was extracted
    
    /**
     * Create BiometricTemplate from string representation
     */
    public static BiometricTemplate fromString(String templateString) {
        // Parse template string format: "data|version|algorithm|quality|timestamp"
        String[] parts = templateString.split("\\|");
        
        if (parts.length < 1) {
            throw new IllegalArgumentException("Invalid template string format");
        }
        
        return BiometricTemplate.builder()
            .data(parts[0])
            .version(parts.length > 1 ? parts[1] : "1.0")
            .algorithm(parts.length > 2 ? parts[2] : "DEFAULT")
            .qualityScore(parts.length > 3 ? Double.parseDouble(parts[3]) : 0.85)
            .extractedAt(parts.length > 4 ? LocalDateTime.parse(parts[4]) : LocalDateTime.now())
            .build();
    }
    
    /**
     * Convert template to string representation
     */
    @Override
    public String toString() {
        return String.format("%s|%s|%s|%f|%s",
            data != null ? data : "",
            version != null ? version : "1.0",
            algorithm != null ? algorithm : "DEFAULT",
            qualityScore != null ? qualityScore : 0.85,
            extractedAt != null ? extractedAt : LocalDateTime.now());
    }
}