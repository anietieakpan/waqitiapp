package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an object recognized in AR space
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognizedObject {
    private UUID id;
    private String objectType;
    private String label;
    private double confidence;
    private BoundingBox boundingBox;
    private Map<String, Double> position;
    private Map<String, Double> rotation;
    private double distance;
    private Instant detectedAt;
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundingBox {
        private double x;
        private double y;
        private double z;
        private double width;
        private double height;
        private double depth;
    }
}