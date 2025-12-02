package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an animation sequence for AR elements
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnimationSequence {
    private UUID id;
    private String name;
    private List<AnimationStep> steps;
    private long totalDuration;
    private boolean loop;
    private int loopCount; // -1 for infinite
    private String trigger; // START, ON_INTERACTION, ON_EVENT, MANUAL
    private Map<String, Object> triggerParameters;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnimationStep {
        private int order;
        private String targetElementId;
        private AnimationType animationType;
        private long duration;
        private long delay;
        private String easing;
        private Map<String, Object> fromValues;
        private Map<String, Object> toValues;
        private boolean parallel; // Run parallel with next step
    }
    
    public enum AnimationType {
        TRANSLATE,
        ROTATE,
        SCALE,
        FADE,
        COLOR_TRANSITION,
        MORPH,
        PATH_FOLLOW,
        SPRING,
        PARTICLE_EMIT,
        SHADER_TRANSITION
    }
}