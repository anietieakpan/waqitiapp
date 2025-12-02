package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a virtual shopping cart in AR space
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualShoppingCart {
    private UUID cartId;
    private UUID userId;
    private List<CartItem> items;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal discount;
    private BigDecimal total;
    private String currency;
    private CartVisualization visualization;
    private Instant createdAt;
    private Instant updatedAt;
    private String status;
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItem {
        private UUID itemId;
        private String productId;
        private String productName;
        private String variantId;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private String thumbnailUrl;
        private String modelUrl;
        private Map<String, Object> customization;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartVisualization {
        private String visualizationType; // FLOATING_BASKET, MINI_CART, FULL_3D, LIST_VIEW
        private Map<String, Double> position;
        private Map<String, Double> anchorOffset;
        private boolean followsUser;
        private boolean showItemModels;
        private String theme;
        private Map<String, Object> customStyles;
    }
    
    public List<CartItem> getItems() {
        return items != null ? items : new ArrayList<>();
    }
    
    public BigDecimal getTotal() {
        return total != null ? total : BigDecimal.ZERO;
    }
}