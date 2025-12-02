package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARSpatialPaymentRequest {
    
    @NotNull(message = "Drop location is required")
    private Map<String, Double> dropLocation; // x, y, z coordinates
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    private String currency = "USD";
    
    private String message;
    private String surfaceType; // FLOOR, TABLE, WALL
    private Double dropHeight;
    private String visualEffect; // COIN_DROP, GIFT_BOX, FLOATING_MONEY
    private Map<String, Object> animationSettings;
    private Long expirationMinutes;
    private Double pickupRadius; // Meters
    private boolean requiresProximity;
    private String recipientIdentifier; // Optional specific recipient
}