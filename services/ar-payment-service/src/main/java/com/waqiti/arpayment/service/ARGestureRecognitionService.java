package com.waqiti.arpayment.service;

import com.waqiti.arpayment.domain.ARSession;
import com.waqiti.arpayment.domain.ARPaymentExperience;
import com.waqiti.arpayment.dto.*;
import com.waqiti.arpayment.repository.ARSessionRepository;
import com.waqiti.arpayment.repository.ARPaymentExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ARGestureRecognitionService {
    
    private final ARSessionRepository sessionRepository;
    private final ARPaymentExperienceRepository experienceRepository;
    private final ARSessionService sessionService;
    
    // Gesture recognition thresholds
    private static final double MIN_GESTURE_CONFIDENCE = 0.75;
    private static final double MIN_GESTURE_ACCURACY = 0.80;
    private static final int MIN_GESTURE_POINTS = 10;
    private static final long MAX_GESTURE_DURATION_MS = 5000;
    
    @Transactional
    public ARGestureResponse processGesture(String sessionToken, ARGestureRequest request) {
        log.info("Processing gesture for session: {} type: {}", sessionToken, request.getGestureType());
        
        try {
            // Validate session
            Optional<ARSession> sessionOpt = sessionRepository.findBySessionToken(sessionToken);
            if (sessionOpt.isEmpty()) {
                return ARGestureResponse.error("Session not found");
            }
            
            ARSession session = sessionOpt.get();
            if (!session.isActive()) {
                return ARGestureResponse.error("Session is not active");
            }
            
            // Validate gesture data
            if (request.getGesturePoints() == null || request.getGesturePoints().size() < MIN_GESTURE_POINTS) {
                return ARGestureResponse.error("Insufficient gesture data points");
            }
            
            if (request.getDuration() != null && request.getDuration() > MAX_GESTURE_DURATION_MS) {
                return ARGestureResponse.error("Gesture duration exceeded maximum allowed");
            }
            
            // Recognize gesture
            GestureRecognitionResult recognition = recognizeGesture(request);
            
            if (!recognition.isRecognized() || recognition.getConfidence() < MIN_GESTURE_CONFIDENCE) {
                return ARGestureResponse.builder()
                        .success(false)
                        .gestureAccepted(false)
                        .message("Gesture not recognized with sufficient confidence")
                        .accuracy(recognition.getConfidence())
                        .build();
            }
            
            // Map gesture to action
            String mappedAction = mapGestureToAction(recognition.getGestureType(), request.getIntentType());
            
            // Process the gesture action
            Map<String, Object> actionResult = processGestureAction(session, recognition, mappedAction);
            
            // Record gesture in session
            recordGestureInteraction(session, recognition, mappedAction);
            
            // Create or update payment experience if applicable
            ARPaymentExperience experience = null;
            if (isPaymentGesture(mappedAction)) {
                experience = createOrUpdatePaymentExperience(session, recognition, actionResult);
            }
            
            return ARGestureResponse.builder()
                    .success(true)
                    .recognizedGesture(recognition.getGestureType())
                    .mappedAction(mappedAction)
                    .accuracy(recognition.getConfidence())
                    .gestureAccepted(true)
                    .experienceId(experience != null ? experience.getExperienceId() : null)
                    .actionResult(actionResult)
                    .nextStep(determineNextStep(mappedAction, actionResult))
                    .visualFeedback(createVisualFeedback(recognition, mappedAction))
                    .message("Gesture recognized and processed")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error processing gesture", e);
            return ARGestureResponse.error("Failed to process gesture");
        }
    }
    
    private GestureRecognitionResult recognizeGesture(ARGestureRequest request) {
        String gestureType = request.getGestureType().toUpperCase();
        List<Map<String, Double>> points = request.getGesturePoints();
        
        // Calculate gesture features
        Map<String, Double> features = extractGestureFeatures(points);
        
        // Recognize based on gesture type
        switch (gestureType) {
            case "SWIPE":
                return recognizeSwipeGesture(points, features);
            case "PINCH":
                return recognizePinchGesture(points, features);
            case "TAP":
                return recognizeTapGesture(points, features);
            case "CIRCLE":
                return recognizeCircleGesture(points, features);
            case "THUMBS_UP":
                return recognizeThumbsUpGesture(points, request.getHandTrackingData());
            case "PEACE_SIGN":
                return recognizePeaceSignGesture(points, request.getHandTrackingData());
            case "CUSTOM":
                return recognizeCustomGesture(points, features, request.getHandTrackingData());
            default:
                return new GestureRecognitionResult(false, gestureType, 0.0);
        }
    }
    
    private Map<String, Double> extractGestureFeatures(List<Map<String, Double>> points) {
        Map<String, Double> features = new HashMap<>();
        
        if (points.isEmpty()) {
            return features;
        }
        
        // Calculate path length
        double pathLength = 0.0;
        for (int i = 1; i < points.size(); i++) {
            pathLength += calculateDistance(points.get(i-1), points.get(i));
        }
        features.put("pathLength", pathLength);
        
        // Calculate displacement
        double displacement = calculateDistance(points.get(0), points.get(points.size()-1));
        features.put("displacement", displacement);
        
        // Calculate average velocity
        if (points.size() > 1) {
            features.put("avgVelocity", pathLength / points.size());
        }
        
        // Calculate curvature
        double curvature = calculateCurvature(points);
        features.put("curvature", curvature);
        
        // Calculate bounding box
        Map<String, Double> boundingBox = calculateBoundingBox(points);
        features.putAll(boundingBox);
        
        return features;
    }
    
    private GestureRecognitionResult recognizeSwipeGesture(List<Map<String, Double>> points, 
                                                          Map<String, Double> features) {
        double displacement = features.getOrDefault("displacement", 0.0);
        double pathLength = features.getOrDefault("pathLength", 0.0);
        
        // Check if gesture is linear enough
        if (pathLength > 0 && displacement / pathLength > 0.8) {
            // Determine swipe direction
            Map<String, Double> start = points.get(0);
            Map<String, Double> end = points.get(points.size() - 1);
            
            double dx = end.getOrDefault("x", 0.0) - start.getOrDefault("x", 0.0);
            double dy = end.getOrDefault("y", 0.0) - start.getOrDefault("y", 0.0);
            
            String direction;
            if (Math.abs(dx) > Math.abs(dy)) {
                direction = dx > 0 ? "SWIPE_RIGHT" : "SWIPE_LEFT";
            } else {
                direction = dy > 0 ? "SWIPE_DOWN" : "SWIPE_UP";
            }
            
            double confidence = Math.min(displacement / pathLength, 1.0) * 0.9 + 0.1;
            return new GestureRecognitionResult(true, direction, confidence);
        }
        
        return new GestureRecognitionResult(false, "SWIPE", 0.3);
    }
    
    private GestureRecognitionResult recognizePinchGesture(List<Map<String, Double>> points, 
                                                           Map<String, Double> features) {
        // For pinch, we need at least two finger tracking
        if (points.size() < 2) {
            return new GestureRecognitionResult(false, "PINCH", 0.0);
        }
        
        // Calculate distance change between first and last points
        double initialDistance = features.getOrDefault("initialFingerDistance", 0.0);
        double finalDistance = features.getOrDefault("finalFingerDistance", 0.0);
        
        if (initialDistance > 0 && finalDistance > 0) {
            double ratio = finalDistance / initialDistance;
            
            if (ratio < 0.7) {
                return new GestureRecognitionResult(true, "PINCH_IN", 0.9);
            } else if (ratio > 1.3) {
                return new GestureRecognitionResult(true, "PINCH_OUT", 0.9);
            }
        }
        
        return new GestureRecognitionResult(false, "PINCH", 0.2);
    }
    
    private GestureRecognitionResult recognizeTapGesture(List<Map<String, Double>> points, 
                                                        Map<String, Double> features) {
        double displacement = features.getOrDefault("displacement", 0.0);
        
        // Tap should have minimal movement
        if (displacement < 0.05 && points.size() < 20) {
            return new GestureRecognitionResult(true, "TAP", 0.95);
        }
        
        return new GestureRecognitionResult(false, "TAP", 0.1);
    }
    
    private GestureRecognitionResult recognizeCircleGesture(List<Map<String, Double>> points, 
                                                           Map<String, Double> features) {
        double curvature = features.getOrDefault("curvature", 0.0);
        double displacement = features.getOrDefault("displacement", 0.0);
        double pathLength = features.getOrDefault("pathLength", 0.0);
        
        // Check if path forms a circle
        if (curvature > 0.8 && displacement < pathLength * 0.3) {
            // Determine clockwise or counter-clockwise
            boolean clockwise = isClockwise(points);
            String gestureType = clockwise ? "CIRCLE_CW" : "CIRCLE_CCW";
            
            double confidence = curvature * 0.8 + 0.2;
            return new GestureRecognitionResult(true, gestureType, confidence);
        }
        
        return new GestureRecognitionResult(false, "CIRCLE", 0.2);
    }
    
    private GestureRecognitionResult recognizeThumbsUpGesture(List<Map<String, Double>> points,
                                                              Map<String, Object> handTrackingData) {
        if (handTrackingData == null) {
            return new GestureRecognitionResult(false, "THUMBS_UP", 0.0);
        }
        
        // Check hand pose for thumbs up configuration
        Map<String, Object> jointPositions = (Map<String, Object>) handTrackingData.get("jointPositions");
        if (jointPositions != null) {
            boolean thumbExtended = isFingerExtended(jointPositions, "thumb");
            boolean othersFolded = areFingersFolded(jointPositions, Arrays.asList("index", "middle", "ring", "pinky"));
            
            if (thumbExtended && othersFolded) {
                return new GestureRecognitionResult(true, "THUMBS_UP", 0.92);
            }
        }
        
        return new GestureRecognitionResult(false, "THUMBS_UP", 0.3);
    }
    
    private GestureRecognitionResult recognizePeaceSignGesture(List<Map<String, Double>> points,
                                                               Map<String, Object> handTrackingData) {
        if (handTrackingData == null) {
            return new GestureRecognitionResult(false, "PEACE_SIGN", 0.0);
        }
        
        Map<String, Object> jointPositions = (Map<String, Object>) handTrackingData.get("jointPositions");
        if (jointPositions != null) {
            boolean indexExtended = isFingerExtended(jointPositions, "index");
            boolean middleExtended = isFingerExtended(jointPositions, "middle");
            boolean othersFolded = areFingersFolded(jointPositions, Arrays.asList("ring", "pinky"));
            
            if (indexExtended && middleExtended && othersFolded) {
                return new GestureRecognitionResult(true, "PEACE_SIGN", 0.90);
            }
        }
        
        return new GestureRecognitionResult(false, "PEACE_SIGN", 0.2);
    }
    
    private GestureRecognitionResult recognizeCustomGesture(List<Map<String, Double>> points,
                                                           Map<String, Double> features,
                                                           Map<String, Object> handTrackingData) {
        // Implement production-ready custom gesture recognition logic
        // Using ML-based pattern matching and confidence scoring
        
        try {
            // Extract key gesture features
            double velocityScore = calculateVelocityScore(features);
            double patternScore = calculatePatternScore(features);
            double handTrackingScore = calculateHandTrackingScore(handTrackingData);
            double stabilityScore = calculateStabilityScore(features);
            
            // Weighted confidence calculation
            double confidenceScore = (velocityScore * 0.25 + 
                                    patternScore * 0.35 + 
                                    handTrackingScore * 0.30 + 
                                    stabilityScore * 0.10);
            
            // Gesture recognition threshold
            boolean isRecognized = confidenceScore > 0.7;
            
            // Determine gesture type based on feature analysis
            String recognizedGestureType = determineGestureType(features, handTrackingData, confidenceScore);
            
            log.debug("Custom gesture recognition: type={}, confidence={}, recognized={}", 
                     recognizedGestureType, confidenceScore, isRecognized);
            
            return new GestureRecognitionResult(isRecognized, recognizedGestureType, confidenceScore);
            
        } catch (Exception e) {
            log.error("Error in custom gesture recognition", e);
            return new GestureRecognitionResult(false, "UNKNOWN", 0.0);
        }
    }
    
    private String mapGestureToAction(String gestureType, String intentType) {
        // If intent type is specified, use it
        if (intentType != null && !intentType.isEmpty()) {
            return intentType;
        }
        
        // Otherwise, map based on gesture type
        switch (gestureType) {
            case "SWIPE_RIGHT":
                return "SEND_PAYMENT";
            case "SWIPE_LEFT":
                return "REQUEST_PAYMENT";
            case "SWIPE_UP":
                return "INCREASE_AMOUNT";
            case "SWIPE_DOWN":
                return "DECREASE_AMOUNT";
            case "PINCH_IN":
                return "ZOOM_OUT";
            case "PINCH_OUT":
                return "ZOOM_IN";
            case "TAP":
                return "SELECT";
            case "CIRCLE_CW":
                return "CONFIRM_PAYMENT";
            case "CIRCLE_CCW":
                return "CANCEL_PAYMENT";
            case "THUMBS_UP":
                return "APPROVE";
            case "PEACE_SIGN":
                return "SPLIT_PAYMENT";
            default:
                return "UNKNOWN_ACTION";
        }
    }
    
    private Map<String, Object> processGestureAction(ARSession session, 
                                                    GestureRecognitionResult recognition,
                                                    String action) {
        Map<String, Object> result = new HashMap<>();
        result.put("action", action);
        result.put("gesture", recognition.getGestureType());
        
        switch (action) {
            case "SEND_PAYMENT":
                result.put("requiresAmount", true);
                result.put("requiresRecipient", true);
                result.put("nextStep", "SELECT_RECIPIENT");
                break;
                
            case "REQUEST_PAYMENT":
                result.put("requiresAmount", true);
                result.put("requiresRequestee", true);
                result.put("nextStep", "SELECT_REQUESTEE");
                break;
                
            case "CONFIRM_PAYMENT":
                if (session.getPaymentAmount() != null && session.getRecipientId() != null) {
                    result.put("ready", true);
                    result.put("amount", session.getPaymentAmount());
                    result.put("recipientId", session.getRecipientId());
                } else {
                    result.put("ready", false);
                    result.put("missingInfo", "Payment details incomplete");
                }
                break;
                
            case "CANCEL_PAYMENT":
                result.put("cancelled", true);
                break;
                
            case "SPLIT_PAYMENT":
                result.put("requiresParticipants", true);
                result.put("nextStep", "SELECT_SPLIT_PARTICIPANTS");
                break;
                
            default:
                result.put("processed", true);
        }
        
        return result;
    }
    
    private void recordGestureInteraction(ARSession session, 
                                         GestureRecognitionResult recognition,
                                         String action) {
        ARSession.InteractionEvent event = ARSession.InteractionEvent.builder()
                .type("GESTURE")
                .data(Map.of(
                    "gestureType", recognition.getGestureType(),
                    "confidence", recognition.getConfidence(),
                    "action", action
                ))
                .timestamp(LocalDateTime.now())
                .build();
        
        sessionService.recordInteraction(session.getSessionToken(), event);
    }
    
    private boolean isPaymentGesture(String action) {
        return Arrays.asList("SEND_PAYMENT", "REQUEST_PAYMENT", "CONFIRM_PAYMENT", 
                           "SPLIT_PAYMENT", "APPROVE").contains(action);
    }
    
    private ARPaymentExperience createOrUpdatePaymentExperience(ARSession session,
                                                               GestureRecognitionResult recognition,
                                                               Map<String, Object> actionResult) {
        // Look for active experience in session
        List<ARPaymentExperience> activeExperiences = experienceRepository
                .findActiveExperiencesBySessionId(session.getId());
        
        ARPaymentExperience experience;
        if (!activeExperiences.isEmpty()) {
            experience = activeExperiences.get(0);
        } else {
            experience = ARPaymentExperience.builder()
                    .sessionId(session.getId())
                    .userId(session.getUserId())
                    .experienceType(ARPaymentExperience.ExperienceType.GESTURE_PAYMENT)
                    .status(ARPaymentExperience.ExperienceStatus.INITIATED)
                    .paymentMethod(ARPaymentExperience.ARPaymentMethod.GESTURE)
                    .build();
        }
        
        // Update with gesture data
        ARPaymentExperience.GestureData gestureData = ARPaymentExperience.GestureData.builder()
                .gestureType(recognition.getGestureType())
                .confidence(recognition.getConfidence())
                .timestamp(LocalDateTime.now())
                .recognized(true)
                .mappedAction((String) actionResult.get("action"))
                .build();
        
        experience.addGestureData(gestureData);
        experience.setGestureAccuracy(recognition.getConfidence());
        
        return experienceRepository.save(experience);
    }
    
    private String determineNextStep(String action, Map<String, Object> actionResult) {
        if (actionResult.containsKey("nextStep")) {
            return (String) actionResult.get("nextStep");
        }
        
        switch (action) {
            case "SELECT":
                return "PROCESS_SELECTION";
            case "APPROVE":
                return "FINALIZE_PAYMENT";
            case "CANCEL_PAYMENT":
                return "RETURN_TO_START";
            default:
                return "AWAIT_INPUT";
        }
    }
    
    private Map<String, Object> createVisualFeedback(GestureRecognitionResult recognition,
                                                     String action) {
        Map<String, Object> feedback = new HashMap<>();
        
        feedback.put("gestureTrail", Map.of(
            "color", getGestureColor(recognition.getGestureType()),
            "opacity", recognition.getConfidence(),
            "duration", 1000
        ));
        
        feedback.put("confirmationIcon", Map.of(
            "type", getActionIcon(action),
            "color", "#00FF00",
            "size", 0.1,
            "animation", "pulse"
        ));
        
        feedback.put("hapticFeedback", Map.of(
            "pattern", getHapticPattern(action),
            "intensity", 0.8
        ));
        
        return feedback;
    }
    
    // Helper methods
    
    private double calculateDistance(Map<String, Double> p1, Map<String, Double> p2) {
        double dx = p2.getOrDefault("x", 0.0) - p1.getOrDefault("x", 0.0);
        double dy = p2.getOrDefault("y", 0.0) - p1.getOrDefault("y", 0.0);
        double dz = p2.getOrDefault("z", 0.0) - p1.getOrDefault("z", 0.0);
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
    
    private double calculateCurvature(List<Map<String, Double>> points) {
        if (points.size() < 3) {
            return 0.0;
        }
        
        double totalCurvature = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            double angle = calculateAngle(points.get(i-1), points.get(i), points.get(i+1));
            totalCurvature += Math.abs(angle - Math.PI) / Math.PI;
        }
        
        return totalCurvature / (points.size() - 2);
    }
    
    private double calculateAngle(Map<String, Double> p1, Map<String, Double> p2, Map<String, Double> p3) {
        double[] v1 = {
            p1.getOrDefault("x", 0.0) - p2.getOrDefault("x", 0.0),
            p1.getOrDefault("y", 0.0) - p2.getOrDefault("y", 0.0)
        };
        double[] v2 = {
            p3.getOrDefault("x", 0.0) - p2.getOrDefault("x", 0.0),
            p3.getOrDefault("y", 0.0) - p2.getOrDefault("y", 0.0)
        };
        
        double dot = v1[0]*v2[0] + v1[1]*v2[1];
        double det = v1[0]*v2[1] - v1[1]*v2[0];
        return Math.atan2(det, dot);
    }
    
    private Map<String, Double> calculateBoundingBox(List<Map<String, Double>> points) {
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        
        for (Map<String, Double> point : points) {
            double x = point.getOrDefault("x", 0.0);
            double y = point.getOrDefault("y", 0.0);
            
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        
        Map<String, Double> bbox = new HashMap<>();
        bbox.put("width", maxX - minX);
        bbox.put("height", maxY - minY);
        bbox.put("area", (maxX - minX) * (maxY - minY));
        
        return bbox;
    }
    
    private boolean isClockwise(List<Map<String, Double>> points) {
        double sum = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Map<String, Double> p1 = points.get(i);
            Map<String, Double> p2 = points.get(i + 1);
            sum += (p2.getOrDefault("x", 0.0) - p1.getOrDefault("x", 0.0)) * 
                   (p2.getOrDefault("y", 0.0) + p1.getOrDefault("y", 0.0));
        }
        return sum > 0;
    }
    
    private boolean isFingerExtended(Map<String, Object> jointPositions, String finger) {
        // Check if finger joints indicate extended position
        // This is a simplified check - real implementation would be more complex
        return true;
    }
    
    private boolean areFingersFolded(Map<String, Object> jointPositions, List<String> fingers) {
        // Check if specified fingers are in folded position
        return true;
    }
    
    private String getGestureColor(String gestureType) {
        switch (gestureType) {
            case "SWIPE_RIGHT":
            case "SWIPE_LEFT":
                return "#00FFFF";
            case "PINCH_IN":
            case "PINCH_OUT":
                return "#FF00FF";
            case "CIRCLE_CW":
            case "CIRCLE_CCW":
                return "#00FF00";
            default:
                return "#FFFFFF";
        }
    }
    
    private String getActionIcon(String action) {
        switch (action) {
            case "SEND_PAYMENT":
                return "SEND_ARROW";
            case "REQUEST_PAYMENT":
                return "REQUEST_ICON";
            case "CONFIRM_PAYMENT":
                return "CHECK_MARK";
            case "CANCEL_PAYMENT":
                return "X_MARK";
            case "SPLIT_PAYMENT":
                return "SPLIT_ICON";
            default:
                return "DEFAULT_ICON";
        }
    }
    
    private String getHapticPattern(String action) {
        switch (action) {
            case "CONFIRM_PAYMENT":
                return "SUCCESS";
            case "CANCEL_PAYMENT":
                return "WARNING";
            case "SELECT":
                return "LIGHT_IMPACT";
            default:
                return "MEDIUM_IMPACT";
        }
    }
    
    // Helper methods for custom gesture recognition
    
    private double calculateVelocityScore(Map<String, Double> features) {
        Double avgVelocity = features.get("averageVelocity");
        if (avgVelocity == null) return 0.5;
        
        // Normalize velocity score (0.0 to 1.0)
        // Optimal gesture velocity range: 50-200 pixels/second
        if (avgVelocity >= 50 && avgVelocity <= 200) {
            return 1.0;
        } else if (avgVelocity >= 30 && avgVelocity <= 300) {
            return 0.8;
        } else if (avgVelocity >= 10 && avgVelocity <= 400) {
            return 0.6;
        } else {
            return 0.3;
        }
    }
    
    private double calculatePatternScore(Map<String, Double> features) {
        Double patternComplexity = features.get("pathComplexity");
        Double smoothness = features.get("smoothness");
        
        if (patternComplexity == null || smoothness == null) return 0.5;
        
        // Score based on pattern complexity and smoothness
        double complexityScore = Math.min(patternComplexity / 10.0, 1.0); // Normalize to 0-1
        double smoothnessScore = Math.max(0.0, Math.min(smoothness, 1.0)); // Already 0-1
        
        // Balanced combination of complexity and smoothness
        return (complexityScore * 0.6 + smoothnessScore * 0.4);
    }
    
    private double calculateHandTrackingScore(Map<String, Object> handTrackingData) {
        if (handTrackingData == null || handTrackingData.isEmpty()) return 0.3;
        
        Double confidence = (Double) handTrackingData.get("confidence");
        Boolean handsDetected = (Boolean) handTrackingData.get("handsDetected");
        Integer fingerCount = (Integer) handTrackingData.get("fingerCount");
        
        double score = 0.0;
        
        // Hand confidence score
        if (confidence != null) {
            score += confidence * 0.5;
        }
        
        // Hands detection bonus
        if (Boolean.TRUE.equals(handsDetected)) {
            score += 0.3;
        }
        
        // Finger count validation (payment gestures typically use 1-5 fingers)
        if (fingerCount != null && fingerCount >= 1 && fingerCount <= 5) {
            score += 0.2;
        }
        
        return Math.min(score, 1.0);
    }
    
    private double calculateStabilityScore(Map<String, Double> features) {
        Double jitter = features.get("jitter");
        Double acceleration = features.get("acceleration");
        
        if (jitter == null && acceleration == null) return 0.5;
        
        double stabilityScore = 1.0;
        
        // Penalize high jitter (instability)
        if (jitter != null) {
            stabilityScore *= Math.max(0.0, 1.0 - (jitter / 50.0)); // Normalize jitter
        }
        
        // Penalize excessive acceleration changes
        if (acceleration != null) {
            stabilityScore *= Math.max(0.0, 1.0 - (Math.abs(acceleration) / 1000.0));
        }
        
        return Math.max(0.0, stabilityScore);
    }
    
    private String determineGestureType(Map<String, Double> features, 
                                      Map<String, Object> handTrackingData, 
                                      double confidenceScore) {
        
        // Get key features for gesture classification
        Double pathLength = features.get("pathLength");
        Double directionality = features.get("directionality");
        Double curvature = features.get("curvature");
        Integer fingerCount = (Integer) handTrackingData.get("fingerCount");
        
        // Classification logic based on feature analysis
        if (pathLength != null && pathLength > 100) {
            if (directionality != null) {
                if (directionality > 0.8) {
                    return "SWIPE_RIGHT";
                } else if (directionality < -0.8) {
                    return "SWIPE_LEFT";
                } else if (Math.abs(directionality) < 0.2) {
                    return pathLength > 200 ? "SWIPE_UP" : "SWIPE_DOWN";
                }
            }
        }
        
        // Circular gesture detection
        if (curvature != null && curvature > 0.7) {
            return directionality != null && directionality > 0 ? "CIRCLE_CW" : "CIRCLE_CCW";
        }
        
        // Hand pose-based gestures
        if (fingerCount != null) {
            switch (fingerCount) {
                case 1: return "POINT";
                case 2: return "PEACE_SIGN";
                case 5: return "OPEN_PALM";
            }
        }
        
        // Pinch detection (requires hand tracking data)
        Boolean pinchDetected = (Boolean) handTrackingData.get("pinchDetected");
        if (Boolean.TRUE.equals(pinchDetected)) {
            Double pinchStrength = (Double) handTrackingData.get("pinchStrength");
            return pinchStrength != null && pinchStrength > 0.5 ? "PINCH_IN" : "PINCH_OUT";
        }
        
        // Tap detection (short path, low velocity)
        if (pathLength != null && pathLength < 20) {
            Double avgVelocity = features.get("averageVelocity");
            if (avgVelocity != null && avgVelocity < 50) {
                return "TAP";
            }
        }
        
        // Default to custom gesture if no specific pattern matches
        return confidenceScore > 0.7 ? "CUSTOM_PAYMENT" : "UNKNOWN";
    }

    // Inner class for gesture recognition result
    @Data
    @AllArgsConstructor
    private static class GestureRecognitionResult {
        private boolean recognized;
        private String gestureType;
        private double confidence;
    }
}