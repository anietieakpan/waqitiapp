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
 * Represents a product with AR visualization capabilities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductARModel {
    private UUID id;
    private String productId;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private String modelUrl;
    private String thumbnailUrl;
    private ModelProperties properties;
    private List<ProductVariant> variants;
    private Map<String, String> textures;
    private List<InteractionPoint> interactionPoints;
    private boolean isAnimated;
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelProperties {
        private Map<String, Double> defaultScale;
        private Map<String, Double> minScale;
        private Map<String, Double> maxScale;
        private boolean allowRotation;
        private boolean allowTranslation;
        private String defaultMaterial;
        private List<String> availableMaterials;
        private double lodDistance; // Level of detail distance
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductVariant {
        private String variantId;
        private String name;
        private String color;
        private String size;
        private BigDecimal priceModifier;
        private String modelOverrideUrl;
        private Map<String, String> textureOverrides;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionPoint {
        private String name;
        private Map<String, Double> position;
        private String action;
        private String tooltip;
        private String iconUrl;
    }
}