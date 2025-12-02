package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a visual representation of a transaction in AR
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionVisualization {
    private UUID id;
    private UUID transactionId;
    private VisualizationType type;
    private BigDecimal amount;
    private String currency;
    private Map<String, Double> startPosition;
    private Map<String, Double> endPosition;
    private List<VisualizationElement> elements;
    private AnimationSequence animationSequence;
    private long duration;
    private String status;
    
    public enum VisualizationType {
        PARTICLE_STREAM,
        COIN_TRANSFER,
        ENERGY_BEAM,
        BUBBLE_FLOW,
        LIGHTNING_BOLT,
        WAVE_PATTERN,
        CRYSTALLIZATION,
        MONEY_RAIN
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisualizationElement {
        private String type;
        private String modelUrl;
        private String particleSystemUrl;
        private Map<String, Object> properties;
        private List<String> textures;
    }
}