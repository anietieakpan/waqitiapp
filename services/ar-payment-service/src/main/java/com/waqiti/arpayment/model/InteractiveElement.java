package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an interactive element in AR interface
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveElement {
    private UUID id;
    private String name;
    private ElementType type;
    private Map<String, Double> position;
    private Map<String, Double> dimensions;
    private List<InteractionType> supportedInteractions;
    private Map<String, String> eventHandlers;
    private ElementState state;
    private Map<String, Object> properties;
    private boolean isEnabled;
    private boolean isVisible;
    private AccessibilityInfo accessibility;
    
    public enum ElementType {
        BUTTON,
        SLIDER,
        TOGGLE,
        DIAL,
        MENU,
        CARD,
        LIST,
        GRID,
        CAROUSEL,
        INPUT_FIELD,
        GESTURE_AREA,
        VOICE_TRIGGER
    }
    
    public enum InteractionType {
        TAP,
        DOUBLE_TAP,
        LONG_PRESS,
        SWIPE,
        PINCH,
        ROTATE,
        DRAG,
        HOVER,
        VOICE,
        GAZE
    }
    
    public enum ElementState {
        IDLE,
        HOVER,
        ACTIVE,
        DISABLED,
        LOADING,
        SUCCESS,
        ERROR
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessibilityInfo {
        private String label;
        private String hint;
        private String role;
        private Map<String, String> announcements;
    }
}