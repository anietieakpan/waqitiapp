package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 3D Model representation for AR visualization
 * Supports GLTF 2.0 format with PBR materials
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AR3DModel {

    private String modelId;
    private String modelPath; // Path to GLTF/GLB file
    private float[] position; // [x, y, z] in world coordinates
    private float[] rotation; // [x, y, z] Euler angles in degrees
    private float[] scale; // [x, y, z] scale factors
    private String renderQuality; // ULTRA, HIGH, MEDIUM, LOW
    private String lod; // LOD_0 (highest) to LOD_3 (lowest)
    private boolean castsShadows;
    private boolean receivesShadows;
    private String materialOverride; // Optional material path
    private AnimationState animationState;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnimationState {
        private String currentAnimation;
        private float playbackSpeed;
        private boolean looping;
        private float timeOffset;
    }
}
