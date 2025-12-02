package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of gesture analysis in AR
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GestureAnalysisResult {
    private String gestureType; // SWIPE, TAP, PINCH, ROTATE, LONG_PRESS, etc.
    private double confidence;
    private boolean isRecognized;
    private List<GesturePoint> gesturePoints;
    private long duration; // in milliseconds
    private double velocity;
    private double distance;
    private Map<String, Double> direction; // x, y, z components
    private Instant startTime;
    private Instant endTime;
    private Map<String, Object> additionalData;
    
    public boolean isRecognized() {
        return isRecognized;
    }
    
    public String getGestureType() {
        return gestureType != null ? gestureType : "UNKNOWN";
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public List<GesturePoint> getGesturePoints() {
        return gesturePoints != null ? gesturePoints : new ArrayList<>();
    }
}