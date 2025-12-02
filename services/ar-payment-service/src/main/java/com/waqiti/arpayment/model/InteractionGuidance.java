package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents guidance for user interactions in AR
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionGuidance {
    private UUID id;
    private GuidanceType type;
    private String message;
    private List<GuidanceStep> steps;
    private String visualIndicatorType; // ARROW, HIGHLIGHT, PULSE, HAND_GESTURE
    private Map<String, Double> targetPosition;
    private long displayDuration;
    private boolean isSkippable;
    private String voiceInstruction;
    private List<String> supportedGestures;
    private Map<String, Object> visualProperties;
    
    public enum GuidanceType {
        TUTORIAL,
        HINT,
        ERROR_RECOVERY,
        FEATURE_DISCOVERY,
        GESTURE_TRAINING,
        VOICE_COMMAND,
        CONTEXTUAL_HELP,
        ONBOARDING
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuidanceStep {
        private int order;
        private String instruction;
        private String targetElementId;
        private String expectedAction;
        private String successMessage;
        private List<String> visualCues;
        private long timeout;
    }
}