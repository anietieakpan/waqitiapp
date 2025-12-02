package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARGestureRequest {
    
    @NotBlank(message = "Gesture type is required")
    private String gestureType; // SWIPE, PINCH, TAP, CIRCLE, CUSTOM
    
    @NotNull(message = "Gesture points are required")
    private List<Map<String, Double>> gesturePoints; // Hand tracking points
    
    private Double confidence;
    private Long duration; // Gesture duration in milliseconds
    private Map<String, Object> handTrackingData;
    private String intentType; // SEND_PAYMENT, CONFIRM, CANCEL, etc.
}