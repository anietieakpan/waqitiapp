package com.waqiti.arpayment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ar_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARSession {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "session_token", unique = true, nullable = false, length = 100)
    private String sessionToken;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "session_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SessionType sessionType;
    
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.ACTIVE;
    
    @Column(name = "device_id", length = 100)
    private String deviceId;
    
    @Column(name = "device_type", length = 50)
    private String deviceType; // ARKit, ARCore, HoloLens, Magic Leap, etc.
    
    @Column(name = "ar_platform", length = 50)
    private String arPlatform;
    
    @Column(name = "ar_platform_version", length = 20)
    private String arPlatformVersion;
    
    @Type(type = "jsonb")
    @Column(name = "device_capabilities", columnDefinition = "jsonb")
    private Map<String, Object> deviceCapabilities;
    
    @Type(type = "jsonb")
    @Column(name = "spatial_mapping_data", columnDefinition = "jsonb")
    private Map<String, Object> spatialMappingData;
    
    @Type(type = "jsonb")
    @Column(name = "anchor_points", columnDefinition = "jsonb")
    private List<AnchorPoint> anchorPoints;
    
    @Column(name = "current_location_lat")
    private Double currentLocationLat;
    
    @Column(name = "current_location_lng")
    private Double currentLocationLng;
    
    @Column(name = "location_accuracy")
    private Double locationAccuracy;
    
    @Column(name = "indoor_location", columnDefinition = "TEXT")
    private String indoorLocation;
    
    @Type(type = "jsonb")
    @Column(name = "detected_surfaces", columnDefinition = "jsonb")
    private List<DetectedSurface> detectedSurfaces;
    
    @Type(type = "jsonb")
    @Column(name = "recognized_objects", columnDefinition = "jsonb")
    private List<RecognizedObject> recognizedObjects;
    
    @Type(type = "jsonb")
    @Column(name = "active_overlays", columnDefinition = "jsonb")
    private List<AROverlay> activeOverlays;
    
    @Column(name = "payment_amount", precision = 19, scale = 2)
    private BigDecimal paymentAmount;
    
    @Column(name = "currency", length = 3)
    private String currency = "USD";
    
    @Column(name = "recipient_id")
    private UUID recipientId;
    
    @Column(name = "recipient_name", length = 100)
    private String recipientName;
    
    @Column(name = "payment_id")
    private UUID paymentId;
    
    @Column(name = "interaction_count")
    private Integer interactionCount = 0;
    
    @Column(name = "gesture_count")
    private Integer gestureCount = 0;
    
    @Type(type = "jsonb")
    @Column(name = "interaction_history", columnDefinition = "jsonb")
    private List<InteractionEvent> interactionHistory;
    
    @Column(name = "ar_quality_score")
    private Double arQualityScore;
    
    @Column(name = "tracking_quality", length = 20)
    private String trackingQuality; // GOOD, REDUCED, LIMITED
    
    @Column(name = "lighting_intensity")
    private Double lightingIntensity;
    
    @Column(name = "frame_rate")
    private Integer frameRate;
    
    @Type(type = "jsonb")
    @Column(name = "performance_metrics", columnDefinition = "jsonb")
    private Map<String, Object> performanceMetrics;
    
    @Type(type = "jsonb")
    @Column(name = "session_metadata", columnDefinition = "jsonb")
    private Map<String, Object> sessionMetadata;
    
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;
    
    @Column(name = "ended_at")
    private LocalDateTime endedAt;
    
    @Column(name = "duration_seconds")
    private Long durationSeconds;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        startedAt = LocalDateTime.now();
        lastActiveAt = LocalDateTime.now();

        // Session token must be set by the service layer using SecureTokenGenerator
        // This ensures cryptographically secure tokens with HMAC signatures
        if (sessionToken == null) {
            throw new IllegalStateException(
                "Session token must be set before persisting. " +
                "Use SecureTokenGenerator.generateSessionToken() in the service layer."
            );
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastActiveAt = LocalDateTime.now();
        
        if (status == SessionStatus.ENDED && endedAt == null) {
            endedAt = LocalDateTime.now();
            calculateDuration();
        }
    }
    
    public enum SessionType {
        PAYMENT_SCAN,           // Scan QR codes or objects to pay
        SPATIAL_PAYMENT,        // Place payment in 3D space
        VIRTUAL_STOREFRONT,     // AR shopping experience
        BILL_SPLIT_AR,         // Visual bill splitting
        CRYPTO_WALLET_AR,      // AR crypto wallet visualization
        MERCHANT_DISCOVERY,    // Find nearby merchants in AR
        SOCIAL_PAYMENT,        // Social AR payments
        GAMIFIED_PAYMENT,      // AR payment games
        EDUCATIONAL,           // AR financial education
        DEMO                   // Demo/tutorial mode
    }
    
    public enum SessionStatus {
        INITIALIZING,
        ACTIVE,
        PAUSED,
        BACKGROUND,
        ENDED,
        ERROR,
        TIMEOUT
    }
    
    // Nested classes for JSON storage
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnchorPoint {
        private String id;
        private Double x;
        private Double y;
        private Double z;
        private Double rotationX;
        private Double rotationY;
        private Double rotationZ;
        private String type;
        private Map<String, Object> metadata;
        private LocalDateTime createdAt;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedSurface {
        private String id;
        private String type; // HORIZONTAL, VERTICAL, IRREGULAR
        private Double area;
        private List<Double> boundingBox;
        private Double confidence;
        private Map<String, Object> properties;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecognizedObject {
        private String id;
        private String type;
        private String label;
        private Double confidence;
        private List<Double> boundingBox;
        private Map<String, Object> attributes;
        private LocalDateTime detectedAt;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AROverlay {
        private String id;
        private String type;
        private String content;
        private Map<String, Object> position;
        private Map<String, Object> style;
        private Boolean isInteractive;
        private String action;
        private LocalDateTime createdAt;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionEvent {
        private String type; // TAP, SWIPE, PINCH, GAZE, VOICE
        private Map<String, Object> data;
        private String targetId;
        private LocalDateTime timestamp;
        private Double duration;
    }
    
    public void incrementInteraction() {
        this.interactionCount = (this.interactionCount == null ? 0 : this.interactionCount) + 1;
    }
    
    public void incrementGesture() {
        this.gestureCount = (this.gestureCount == null ? 0 : this.gestureCount) + 1;
    }
    
    public void calculateDuration() {
        if (startedAt != null && endedAt != null) {
            this.durationSeconds = java.time.Duration.between(startedAt, endedAt).getSeconds();
        }
    }
    
    public boolean isActive() {
        return status == SessionStatus.ACTIVE || status == SessionStatus.PAUSED;
    }
    
    public boolean canProcessPayment() {
        return isActive() && arQualityScore != null && arQualityScore > 0.7;
    }
    
    public void addAnchorPoint(AnchorPoint anchor) {
        if (anchorPoints == null) {
            anchorPoints = new java.util.ArrayList<>();
        }
        anchorPoints.add(anchor);
    }
    
    public void addOverlay(AROverlay overlay) {
        if (activeOverlays == null) {
            activeOverlays = new java.util.ArrayList<>();
        }
        activeOverlays.add(overlay);
    }
    
    public void recordInteraction(InteractionEvent event) {
        if (interactionHistory == null) {
            interactionHistory = new java.util.ArrayList<>();
        }
        interactionHistory.add(event);
        incrementInteraction();
        
        if (event.getType().equals("GESTURE")) {
            incrementGesture();
        }
    }
}