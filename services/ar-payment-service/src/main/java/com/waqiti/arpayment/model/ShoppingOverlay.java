package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents shopping overlay UI elements in AR
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingOverlay {
    private UUID id;
    private OverlayType type;
    private List<OverlayElement> elements;
    private Map<String, Double> position;
    private String theme;
    private boolean isMinimized;
    private boolean isDraggable;
    private Map<String, Object> configuration;
    
    public enum OverlayType {
        PRODUCT_INFO,
        PRICE_COMPARISON,
        REVIEWS,
        CART_SUMMARY,
        CHECKOUT,
        RECOMMENDATIONS,
        DEALS_BANNER,
        SIZE_GUIDE,
        VIRTUAL_ASSISTANT
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverlayElement {
        private String elementId;
        private String type; // TEXT, IMAGE, BUTTON, RATING, PRICE, VIDEO
        private String content;
        private Map<String, Object> style;
        private Map<String, String> actions;
        private boolean isInteractive;
    }
}