package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a holographic element in AR space
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HolographicElement {
    private UUID id;
    private String type; // STATIC, ANIMATED, INTERACTIVE, PARTICLE_SYSTEM
    private String modelUrl;
    private Map<String, Double> position;
    private Map<String, Double> rotation;
    private Map<String, Double> scale;
    private HologramProperties properties;
    private List<Animation> animations;
    private boolean isInteractive;
    private Map<String, Object> interactionHandlers;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HologramProperties {
        private double opacity;
        private String primaryColor;
        private String secondaryColor;
        private double glowIntensity;
        private boolean castsShadow;
        private boolean receivesLight;
        private String shaderType;
        private Map<String, Object> shaderParameters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Animation {
        private String name;
        private AnimationType type;
        private long duration;
        private boolean loop;
        private String easing;
        private Map<String, Object> keyframes;
    }
    
    public enum AnimationType {
        ROTATION,
        FLOAT,
        PULSE,
        WAVE,
        PARTICLE_BURST,
        MORPH,
        DISSOLVE,
        MATERIALIZE
    }
}