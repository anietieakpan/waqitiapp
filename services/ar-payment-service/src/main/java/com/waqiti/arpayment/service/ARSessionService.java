package com.waqiti.arpayment.service;

import com.waqiti.arpayment.domain.ARSession;
import com.waqiti.arpayment.dto.*;
import com.waqiti.arpayment.repository.ARSessionRepository;
import com.waqiti.arpayment.util.SecureTokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ARSessionService {

    private final ARSessionRepository arSessionRepository;
    private final SecureTokenGenerator secureTokenGenerator;

    private static final int MAX_ACTIVE_SESSIONS_PER_USER = 3;
    private static final int SESSION_TIMEOUT_MINUTES = 30;
    
    @Transactional
    public ARSessionResponse initializeSession(ARSessionRequest request) {
        log.info("Initializing AR session for user: {} with type: {}", 
                request.getUserId(), request.getSessionType());
        
        try {
            // Check active sessions count
            long activeCount = arSessionRepository.countActiveSessionsByUserId(request.getUserId());
            if (activeCount >= MAX_ACTIVE_SESSIONS_PER_USER) {
                // End oldest active session
                List<ARSession> activeSessions = arSessionRepository.findActiveSessionsByUserId(request.getUserId());
                if (!activeSessions.isEmpty()) {
                    ARSession oldestSession = activeSessions.get(activeSessions.size() - 1);
                    endSession(oldestSession.getSessionToken());
                }
            }
            
            // Create new session
            ARSession.SessionType sessionType = ARSession.SessionType.valueOf(request.getSessionType());

            // Generate cryptographically secure session token with HMAC signature
            String sessionToken = secureTokenGenerator.generateSessionToken();
            log.debug("Generated secure session token for user: {}", request.getUserId());

            ARSession session = ARSession.builder()
                    .sessionToken(sessionToken)  // Set secure token BEFORE persistence
                    .userId(request.getUserId())
                    .sessionType(sessionType)
                    .deviceId(request.getDeviceId())
                    .deviceType(request.getDeviceType())
                    .arPlatform(request.getArPlatform())
                    .arPlatformVersion(request.getArPlatformVersion())
                    .deviceCapabilities(request.getDeviceCapabilities())
                    .currentLocationLat(request.getCurrentLocationLat())
                    .currentLocationLng(request.getCurrentLocationLng())
                    .locationAccuracy(request.getLocationAccuracy())
                    .indoorLocation(request.getIndoorLocation())
                    .sessionMetadata(request.getSessionMetadata())
                    .status(ARSession.SessionStatus.INITIALIZING)
                    .arQualityScore(0.9) // Initial quality score
                    .trackingQuality("GOOD")
                    .frameRate(60)
                    .build();
            
            session = arSessionRepository.save(session);
            
            // Initialize session features based on device capabilities
            Map<String, Object> supportedFeatures = determineSupportedFeatures(
                    request.getDeviceCapabilities(), sessionType);
            
            // Update session status to active
            session.setStatus(ARSession.SessionStatus.ACTIVE);
            session = arSessionRepository.save(session);
            
            return ARSessionResponse.builder()
                    .sessionId(session.getId())
                    .sessionToken(session.getSessionToken())
                    .status(session.getStatus().name())
                    .sessionType(session.getSessionType().name())
                    .startedAt(session.getStartedAt())
                    .initialConfiguration(createInitialConfiguration(session))
                    .supportedFeatures(supportedFeatures)
                    .success(true)
                    .message("AR session initialized successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error initializing AR session", e);
            throw new RuntimeException("Failed to initialize AR session", e);
        }
    }
    
    @Transactional(readOnly = true)
    public ARSessionStatusResponse getSessionStatus(String sessionToken) {
        log.debug("Getting status for AR session: {}", sessionToken);
        
        Optional<ARSession> sessionOpt = arSessionRepository.findBySessionToken(sessionToken);
        if (sessionOpt.isEmpty()) {
            log.warn("AR session not found for token: {}", sessionToken);
            
            // Return proper response instead of null
            return ARSessionStatusResponse.builder()
                    .sessionToken(sessionToken)
                    .status(ARSession.SessionStatus.NOT_FOUND)
                    .valid(false)
                    .errorCode("SESSION_NOT_FOUND")
                    .errorMessage("AR session not found or has expired")
                    .timestamp(LocalDateTime.now())
                    .build();
        }
        
        ARSession session = sessionOpt.get();
        
        // Check for timeout
        if (session.getStatus() == ARSession.SessionStatus.ACTIVE) {
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
            if (session.getLastActiveAt().isBefore(timeoutThreshold)) {
                arSessionRepository.timeoutSession(sessionToken, LocalDateTime.now());
                session.setStatus(ARSession.SessionStatus.TIMEOUT);
            }
        }
        
        return ARSessionStatusResponse.builder()
                .sessionId(session.getId())
                .sessionToken(session.getSessionToken())
                .status(session.getStatus().name())
                .sessionType(session.getSessionType().name())
                .isActive(session.isActive())
                .canProcessPayment(session.canProcessPayment())
                .arQualityScore(session.getArQualityScore())
                .trackingQuality(session.getTrackingQuality())
                .frameRate(session.getFrameRate())
                .interactionCount(session.getInteractionCount())
                .gestureCount(session.getGestureCount())
                .paymentAmount(session.getPaymentAmount())
                .currency(session.getCurrency())
                .recipientId(session.getRecipientId())
                .recipientName(session.getRecipientName())
                .startedAt(session.getStartedAt())
                .lastActiveAt(session.getLastActiveAt())
                .durationSeconds(calculateDuration(session))
                .activeOverlays(convertOverlays(session.getActiveOverlays()))
                .detectedSurfaces(session.getDetectedSurfaces() != null ? 
                        session.getDetectedSurfaces().size() : 0)
                .recognizedObjects(session.getRecognizedObjects() != null ? 
                        session.getRecognizedObjects().size() : 0)
                .build();
    }
    
    @Transactional
    public ARSessionEndResponse endSession(String sessionToken) {
        log.info("Ending AR session: {}", sessionToken);
        
        try {
            Optional<ARSession> sessionOpt = arSessionRepository.findBySessionToken(sessionToken);
            if (sessionOpt.isEmpty()) {
                return ARSessionEndResponse.error("Session not found");
            }
            
            ARSession session = sessionOpt.get();
            
            if (session.getStatus() == ARSession.SessionStatus.ENDED) {
                return ARSessionEndResponse.builder()
                        .sessionId(session.getId())
                        .success(true)
                        .message("Session already ended")
                        .endedAt(session.getEndedAt())
                        .totalDuration(session.getDurationSeconds())
                        .build();
            }
            
            session.setStatus(ARSession.SessionStatus.ENDED);
            session.setEndedAt(LocalDateTime.now());
            session.calculateDuration();
            
            session = arSessionRepository.save(session);
            
            // Calculate session statistics
            Map<String, Object> sessionStats = calculateSessionStatistics(session);
            
            return ARSessionEndResponse.builder()
                    .sessionId(session.getId())
                    .success(true)
                    .message("Session ended successfully")
                    .endedAt(session.getEndedAt())
                    .totalDuration(session.getDurationSeconds())
                    .totalInteractions(session.getInteractionCount())
                    .totalGestures(session.getGestureCount())
                    .sessionStatistics(sessionStats)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error ending AR session", e);
            throw new RuntimeException("Failed to end AR session", e);
        }
    }
    
    @Transactional
    public void updateSessionTracking(String sessionToken, Map<String, Object> trackingData) {
        log.debug("Updating tracking data for session: {}", sessionToken);
        
        Optional<ARSession> sessionOpt = arSessionRepository.findBySessionToken(sessionToken);
        if (sessionOpt.isEmpty()) {
            log.warn("Session not found for tracking update: {}", sessionToken);
            return;
        }
        
        ARSession session = sessionOpt.get();
        
        // Update spatial mapping data
        if (trackingData.containsKey("spatialMapping")) {
            session.setSpatialMappingData((Map<String, Object>) trackingData.get("spatialMapping"));
        }
        
        // Update detected surfaces
        if (trackingData.containsKey("surfaces")) {
            List<Map<String, Object>> surfaceData = (List<Map<String, Object>>) trackingData.get("surfaces");
            List<ARSession.DetectedSurface> surfaces = surfaceData.stream()
                    .map(this::convertToDetectedSurface)
                    .collect(Collectors.toList());
            session.setDetectedSurfaces(surfaces);
        }
        
        // Update tracking quality
        if (trackingData.containsKey("trackingQuality")) {
            session.setTrackingQuality((String) trackingData.get("trackingQuality"));
        }
        
        // Update AR quality score
        if (trackingData.containsKey("qualityScore")) {
            session.setArQualityScore(((Number) trackingData.get("qualityScore")).doubleValue());
        }
        
        // Update performance metrics
        if (trackingData.containsKey("frameRate")) {
            session.setFrameRate(((Number) trackingData.get("frameRate")).intValue());
        }
        
        if (trackingData.containsKey("lightingIntensity")) {
            session.setLightingIntensity(((Number) trackingData.get("lightingIntensity")).doubleValue());
        }
        
        session.setLastActiveAt(LocalDateTime.now());
        arSessionRepository.save(session);
    }
    
    @Transactional
    public void addAnchorPoint(String sessionToken, ARSession.AnchorPoint anchorPoint) {
        Optional<ARSession> sessionOpt = arSessionRepository.findBySessionToken(sessionToken);
        if (sessionOpt.isPresent()) {
            ARSession session = sessionOpt.get();
            session.addAnchorPoint(anchorPoint);
            arSessionRepository.save(session);
        }
    }
    
    @Transactional
    public void addOverlay(String sessionToken, ARSession.AROverlay overlay) {
        Optional<ARSession> sessionOpt = arSessionRepository.findBySessionToken(sessionToken);
        if (sessionOpt.isPresent()) {
            ARSession session = sessionOpt.get();
            session.addOverlay(overlay);
            arSessionRepository.save(session);
        }
    }
    
    @Transactional
    public void recordInteraction(String sessionToken, ARSession.InteractionEvent event) {
        Optional<ARSession> sessionOpt = arSessionRepository.findBySessionToken(sessionToken);
        if (sessionOpt.isPresent()) {
            ARSession session = sessionOpt.get();
            session.recordInteraction(event);
            arSessionRepository.save(session);
        }
    }
    
    @Transactional(readOnly = true)
    public List<ARSession> findActiveSessionsInArea(double latitude, double longitude, double radiusKm) {
        // Calculate bounding box
        double latDelta = radiusKm / 111.0; // Rough conversion
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(latitude)));
        
        return arSessionRepository.findActiveSessionsInArea(
                latitude - latDelta,
                latitude + latDelta,
                longitude - lngDelta,
                longitude + lngDelta
        );
    }
    
    @Transactional
    public void cleanupInactiveSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
        List<ARSession> inactiveSessions = arSessionRepository.findInactiveSessions(threshold);
        
        for (ARSession session : inactiveSessions) {
            log.info("Timing out inactive session: {}", session.getSessionToken());
            session.setStatus(ARSession.SessionStatus.TIMEOUT);
            session.setEndedAt(LocalDateTime.now());
            session.calculateDuration();
            arSessionRepository.save(session);
        }
    }
    
    // Helper methods
    
    private Map<String, Object> determineSupportedFeatures(Map<String, Object> capabilities, 
                                                          ARSession.SessionType sessionType) {
        Map<String, Object> features = new HashMap<>();
        
        // Check device capabilities
        boolean hasARKit = capabilities.getOrDefault("arkit", false).equals(true);
        boolean hasARCore = capabilities.getOrDefault("arcore", false).equals(true);
        boolean hasHandTracking = capabilities.getOrDefault("handTracking", false).equals(true);
        boolean hasFaceTracking = capabilities.getOrDefault("faceTracking", false).equals(true);
        boolean hasObjectDetection = capabilities.getOrDefault("objectDetection", false).equals(true);
        boolean hasDepthAPI = capabilities.getOrDefault("depthAPI", false).equals(true);
        
        // Base features
        features.put("qrScanning", true);
        features.put("markerTracking", true);
        features.put("surfaceDetection", hasARKit || hasARCore);
        
        // Advanced features
        features.put("gesturePayments", hasHandTracking);
        features.put("facialRecognition", hasFaceTracking);
        features.put("objectRecognition", hasObjectDetection);
        features.put("spatialPayments", hasDepthAPI);
        
        // Session type specific features
        switch (sessionType) {
            case PAYMENT_SCAN:
                features.put("instantPayments", true);
                features.put("multipleScans", true);
                break;
            case SPATIAL_PAYMENT:
                features.put("3dDrops", hasDepthAPI);
                features.put("proximityDetection", true);
                break;
            case VIRTUAL_STOREFRONT:
                features.put("productVisualization", true);
                features.put("virtualTryOn", hasObjectDetection);
                break;
            case GAMIFIED_PAYMENT:
                features.put("achievements", true);
                features.put("leaderboards", true);
                features.put("rewards", true);
                break;
        }
        
        return features;
    }
    
    private Map<String, Object> createInitialConfiguration(ARSession session) {
        Map<String, Object> config = new HashMap<>();
        
        config.put("sessionToken", session.getSessionToken());
        config.put("arPlatform", session.getArPlatform());
        config.put("deviceType", session.getDeviceType());
        
        // AR configuration
        Map<String, Object> arConfig = new HashMap<>();
        arConfig.put("targetFrameRate", 60);
        arConfig.put("lightEstimation", true);
        arConfig.put("planeDetection", "horizontal_and_vertical");
        arConfig.put("imageTracking", true);
        arConfig.put("objectTracking", true);
        
        config.put("arConfiguration", arConfig);
        
        // Payment configuration
        Map<String, Object> paymentConfig = new HashMap<>();
        paymentConfig.put("maxAmount", 1000);
        paymentConfig.put("defaultCurrency", "USD");
        paymentConfig.put("requiresConfirmation", true);
        paymentConfig.put("biometricRequired", session.getSessionType() != ARSession.SessionType.DEMO);
        
        config.put("paymentConfiguration", paymentConfig);
        
        return config;
    }
    
    private Map<String, Object> calculateSessionStatistics(ARSession session) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalDuration", session.getDurationSeconds());
        stats.put("interactionCount", session.getInteractionCount());
        stats.put("gestureCount", session.getGestureCount());
        stats.put("averageQualityScore", session.getArQualityScore());
        
        if (session.getDetectedSurfaces() != null) {
            stats.put("surfacesDetected", session.getDetectedSurfaces().size());
        }
        
        if (session.getRecognizedObjects() != null) {
            stats.put("objectsRecognized", session.getRecognizedObjects().size());
        }
        
        if (session.getAnchorPoints() != null) {
            stats.put("anchorsPlaced", session.getAnchorPoints().size());
        }
        
        return stats;
    }
    
    private Long calculateDuration(ARSession session) {
        if (session.getDurationSeconds() != null) {
            return session.getDurationSeconds();
        }
        
        if (session.getStartedAt() != null) {
            LocalDateTime endTime = session.getEndedAt() != null ? 
                    session.getEndedAt() : LocalDateTime.now();
            return java.time.Duration.between(session.getStartedAt(), endTime).getSeconds();
        }
        
        return 0L;
    }
    
    private List<Map<String, Object>> convertOverlays(List<ARSession.AROverlay> overlays) {
        if (overlays == null) {
            return new ArrayList<>();
        }
        
        return overlays.stream()
                .map(overlay -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", overlay.getId());
                    map.put("type", overlay.getType());
                    map.put("content", overlay.getContent());
                    map.put("isInteractive", overlay.getIsInteractive());
                    return map;
                })
                .collect(Collectors.toList());
    }
    
    private ARSession.DetectedSurface convertToDetectedSurface(Map<String, Object> data) {
        return ARSession.DetectedSurface.builder()
                .id((String) data.get("id"))
                .type((String) data.get("type"))
                .area(((Number) data.getOrDefault("area", 0)).doubleValue())
                .confidence(((Number) data.getOrDefault("confidence", 0)).doubleValue())
                .boundingBox((List<Double>) data.get("boundingBox"))
                .properties((Map<String, Object>) data.get("properties"))
                .build();
    }
}