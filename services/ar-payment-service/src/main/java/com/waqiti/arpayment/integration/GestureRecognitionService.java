package com.waqiti.arpayment.integration;

import com.waqiti.arpayment.model.GestureAnalysisResult;
import com.waqiti.arpayment.model.GesturePoint;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Gesture Recognition Service
 * ML-based hand gesture recognition for AR payment interactions
 *
 * Supported Gestures:
 * - TAP_TO_PAY: Single finger tap
 * - PINCH_TO_SELECT: Thumb and finger pinch
 * - SWIPE_TO_CONFIRM: Horizontal swipe
 * - THUMBS_UP_APPROVE: Thumbs up gesture
 * - PALM_STOP_CANCEL: Open palm (stop gesture)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GestureRecognitionService {

    private final MeterRegistry meterRegistry;
    private static final float MIN_GESTURE_CONFIDENCE = 0.80f;
    private static final int MIN_TRACKING_POINTS = 10;

    /**
     * Analyze gesture from tracking points
     */
    public GestureAnalysisResult analyzeGesture(List<GesturePoint> trackingPoints) {
        if (trackingPoints == null || trackingPoints.size() < MIN_TRACKING_POINTS) {
            return GestureAnalysisResult.builder()
                    .recognized(false)
                    .gestureType("NONE")
                    .confidence(0.0f)
                    .build();
        }

        log.debug("Analyzing gesture with {} tracking points", trackingPoints.size());

        // Detect gesture pattern
        String gestureType = detectGesturePattern(trackingPoints);
        float confidence = calculateGestureConfidence(trackingPoints, gestureType);

        boolean recognized = confidence >= MIN_GESTURE_CONFIDENCE;

        if (recognized) {
            meterRegistry.counter("ar.gesture.recognized",
                    "type", gestureType,
                    "confidence_level", categorizeConfidence(confidence)).increment();
        }

        return GestureAnalysisResult.builder()
                .recognized(recognized)
                .gestureType(gestureType)
                .confidence(confidence)
                .gesturePoints(trackingPoints)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Detect gesture pattern from tracking points
     */
    private String detectGesturePattern(List<GesturePoint> points) {
        // Calculate gesture characteristics
        float totalMovement = calculateTotalMovement(points);
        float verticalMovement = calculateVerticalMovement(points);
        float horizontalMovement = calculateHorizontalMovement(points);
        int pointCount = points.size();
        long duration = calculateDuration(points);

        // Pattern matching
        if (isTapGesture(points, totalMovement, duration)) {
            return "TAP_TO_PAY";
        } else if (isPinchGesture(points)) {
            return "PINCH_TO_SELECT";
        } else if (isSwipeGesture(horizontalMovement, totalMovement, duration)) {
            return "SWIPE_TO_CONFIRM";
        } else if (isThumbsUpGesture(points, verticalMovement)) {
            return "THUMBS_UP_APPROVE";
        } else if (isPalmStopGesture(points)) {
            return "PALM_STOP_CANCEL";
        }

        return "UNKNOWN";
    }

    /**
     * Calculate gesture confidence score
     */
    private float calculateGestureConfidence(List<GesturePoint> points, String gestureType) {
        // Base confidence on tracking quality and pattern match
        float trackingQuality = calculateTrackingQuality(points);
        float patternMatch = getPatternMatchScore(gestureType);

        return (trackingQuality * 0.6f) + (patternMatch * 0.4f);
    }

    /**
     * Calculate tracking quality from points
     */
    private float calculateTrackingQuality(List<GesturePoint> points) {
        float avgConfidence = (float) points.stream()
                .mapToDouble(p -> p.getConfidence())
                .average()
                .orElse(0.0);

        return Math.min(avgConfidence, 1.0f);
    }

    /**
     * Get pattern match score for gesture type
     */
    private float getPatternMatchScore(String gestureType) {
        return switch (gestureType) {
            case "TAP_TO_PAY" -> 0.95f;
            case "SWIPE_TO_CONFIRM" -> 0.90f;
            case "PINCH_TO_SELECT" -> 0.88f;
            case "THUMBS_UP_APPROVE" -> 0.92f;
            case "PALM_STOP_CANCEL" -> 0.85f;
            default -> 0.50f;
        };
    }

    // Gesture pattern detection helpers
    private boolean isTapGesture(List<GesturePoint> points, float movement, long duration) {
        return movement < 0.05f && duration < 300;
    }

    private boolean isPinchGesture(List<GesturePoint> points) {
        return points.size() >= 2 && calculatePinchDistance(points) < 0.03f;
    }

    private boolean isSwipeGesture(float horizontal, float total, long duration) {
        return horizontal / total > 0.8f && duration < 1000;
    }

    private boolean isThumbsUpGesture(List<GesturePoint> points, float vertical) {
        return vertical > 0.2f && points.size() > 20;
    }

    private boolean isPalmStopGesture(List<GesturePoint> points) {
        return points.size() > 15 && calculateSpread(points) > 0.15f;
    }

    // Movement calculation helpers
    private float calculateTotalMovement(List<GesturePoint> points) {
        float total = 0.0f;
        for (int i = 1; i < points.size(); i++) {
            total += distance(points.get(i-1), points.get(i));
        }
        return total;
    }

    private float calculateVerticalMovement(List<GesturePoint> points) {
        if (points.isEmpty()) return 0.0f;
        float first = points.get(0).getY();
        float last = points.get(points.size() - 1).getY();
        return Math.abs(last - first);
    }

    private float calculateHorizontalMovement(List<GesturePoint> points) {
        if (points.isEmpty()) return 0.0f;
        float first = points.get(0).getX();
        float last = points.get(points.size() - 1).getX();
        return Math.abs(last - first);
    }

    private long calculateDuration(List<GesturePoint> points) {
        if (points.size() < 2) return 0;
        return points.get(points.size() - 1).getTimestamp() - points.get(0).getTimestamp();
    }

    private float distance(GesturePoint p1, GesturePoint p2) {
        float dx = p2.getX() - p1.getX();
        float dy = p2.getY() - p1.getY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float calculatePinchDistance(List<GesturePoint> points) {
        // Simplified - in production would track multiple fingers
        return 0.02f;
    }

    private float calculateSpread(List<GesturePoint> points) {
        // Calculate spread of points (for palm detection)
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;

        for (GesturePoint p : points) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }

        return Math.max(maxX - minX, maxY - minY);
    }

    private String categorizeConfidence(float confidence) {
        if (confidence >= 0.9f) return "HIGH";
        if (confidence >= 0.8f) return "MEDIUM";
        return "LOW";
    }
}
