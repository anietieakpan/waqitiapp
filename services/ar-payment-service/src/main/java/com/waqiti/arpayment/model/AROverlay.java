package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an AR overlay element
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AROverlay {
    private UUID id;
    private String type; // TEXT, IMAGE, 3D_MODEL, ANIMATION, HOLOGRAM
    private String content;
    private Map<String, Double> position;
    private Map<String, Double> rotation;
    private Map<String, Double> scale;
    private String anchor; // WORLD, OBJECT, SURFACE, FACE
    private UUID anchorId;
    private OverlayStyle style;
    private List<Animation> animations;
    private boolean isInteractive;
    private Map<String, Object> interactionHandlers;
    private int zIndex;
    private double opacity;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverlayStyle {
        private String backgroundColor;
        private String textColor;
        private String fontSize;
        private String fontFamily;
        private String borderColor;
        private double borderWidth;
        private double borderRadius;
        private Map<String, Object> customStyles;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Animation {
        private String type; // FADE, SCALE, ROTATE, TRANSLATE, CUSTOM
        private long duration;
        private String easing;
        private Map<String, Object> properties;
        private boolean loop;
        private long delay;
    }
}