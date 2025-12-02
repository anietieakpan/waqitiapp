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
@Table(name = "ar_payment_experiences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARPaymentExperience {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "experience_id", unique = true, nullable = false, length = 50)
    private String experienceId;
    
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "experience_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ExperienceType experienceType;
    
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ExperienceStatus status = ExperienceStatus.INITIATED;
    
    @Column(name = "payment_method", length = 30)
    @Enumerated(EnumType.STRING)
    private ARPaymentMethod paymentMethod;
    
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency = "USD";
    
    @Column(name = "recipient_id")
    private UUID recipientId;
    
    @Column(name = "recipient_identifier", length = 200)
    private String recipientIdentifier; // Could be username, QR data, or AR marker ID
    
    @Column(name = "merchant_id")
    private UUID merchantId;
    
    @Column(name = "product_ids", columnDefinition = "TEXT")
    private String productIds; // Comma-separated for AR shopping
    
    @Type(type = "jsonb")
    @Column(name = "ar_visualization_data", columnDefinition = "jsonb")
    private Map<String, Object> arVisualizationData;
    
    @Type(type = "jsonb")
    @Column(name = "interaction_points", columnDefinition = "jsonb")
    private List<InteractionPoint> interactionPoints;
    
    @Type(type = "jsonb")
    @Column(name = "gesture_sequence", columnDefinition = "jsonb")
    private List<GestureData> gestureSequence;
    
    @Column(name = "qr_code_data", columnDefinition = "TEXT")
    private String qrCodeData;
    
    @Column(name = "ar_marker_id", length = 100)
    private String arMarkerId;
    
    @Type(type = "jsonb")
    @Column(name = "spatial_payment_data", columnDefinition = "jsonb")
    private SpatialPaymentData spatialPaymentData;
    
    @Type(type = "jsonb")
    @Column(name = "visualization_effects", columnDefinition = "jsonb")
    private List<VisualizationEffect> visualizationEffects;
    
    @Column(name = "confirmation_method", length = 30)
    private String confirmationMethod; // GESTURE, VOICE, BUTTON, GAZE
    
    @Column(name = "confirmation_timestamp")
    private LocalDateTime confirmationTimestamp;
    
    @Column(name = "security_score")
    private Double securityScore;
    
    @Type(type = "jsonb")
    @Column(name = "biometric_data", columnDefinition = "jsonb")
    private Map<String, Object> biometricData;
    
    @Column(name = "face_id_verified")
    private Boolean faceIdVerified;
    
    @Column(name = "gesture_accuracy")
    private Double gestureAccuracy;
    
    @Column(name = "environment_scan_quality")
    private Double environmentScanQuality;
    
    @Type(type = "jsonb")
    @Column(name = "gamification_elements", columnDefinition = "jsonb")
    private List<GamificationElement> gamificationElements;
    
    @Column(name = "points_earned")
    private Integer pointsEarned = 0;
    
    @Column(name = "achievement_unlocked", length = 100)
    private String achievementUnlocked;
    
    @Type(type = "jsonb")
    @Column(name = "social_sharing_data", columnDefinition = "jsonb")
    private Map<String, Object> socialSharingData;
    
    @Column(name = "is_shared_to_feed")
    private Boolean isSharedToFeed = false;
    
    @Column(name = "ar_screenshot_url", length = 500)
    private String arScreenshotUrl;
    
    @Column(name = "ar_video_url", length = 500)
    private String arVideoUrl;
    
    @Type(type = "jsonb")
    @Column(name = "analytics_data", columnDefinition = "jsonb")
    private Map<String, Object> analyticsData;
    
    @Column(name = "interaction_duration_seconds")
    private Long interactionDurationSeconds;
    
    @Column(name = "gesture_count")
    private Integer gestureCount = 0;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "payment_id")
    private UUID paymentId;
    
    @Column(name = "transaction_id", length = 100)
    private String transactionId;
    
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
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

        // Experience ID must be set by the service layer using SecureTokenGenerator
        // This ensures cryptographically secure identifiers
        if (experienceId == null) {
            throw new IllegalStateException(
                "Experience ID must be set before persisting. " +
                "Use SecureTokenGenerator.generateExperienceId() in the service layer."
            );
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        if ((status == ExperienceStatus.COMPLETED || status == ExperienceStatus.FAILED) && 
            completedAt == null) {
            completedAt = LocalDateTime.now();
            calculateDuration();
        }
    }
    
    public enum ExperienceType {
        QR_SCAN_TO_PAY,        // Scan QR code in AR to pay
        AR_MARKER_PAYMENT,     // Pay by pointing at AR markers
        SPATIAL_DROP,          // Drop payment in 3D space
        GESTURE_PAYMENT,       // Pay with hand gestures
        OBJECT_RECOGNITION,    // Pay by pointing at real objects
        VIRTUAL_SHOPPING,      // AR shopping cart experience
        HOLOGRAPHIC_WALLET,    // 3D wallet visualization
        SOCIAL_AR_PAYMENT,     // Send money to friends in AR
        CRYPTO_AR_TRADING,     // AR crypto trading interface
        BILL_SPLIT_VISUAL,     // Visual bill splitting in AR
        AR_INVOICE_VIEW,       // View and pay invoices in AR
        LOCATION_BASED,        // Location-triggered AR payments
        GAME_REWARD,          // AR game-based rewards
        EDUCATIONAL_AR        // AR financial education
    }
    
    public enum ExperienceStatus {
        INITIATED,
        SCANNING,
        PROCESSING,
        CONFIRMING,
        AUTHORIZED,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }
    
    public enum ARPaymentMethod {
        QR_CODE,
        AR_MARKER,
        GESTURE,
        OBJECT_SCAN,
        SPATIAL_TAP,
        VOICE_COMMAND,
        GAZE_SELECTION,
        HAND_TRACKING,
        FACIAL_RECOGNITION
    }
    
    // Nested classes for JSON storage
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionPoint {
        private String id;
        private String type;
        private Map<String, Double> position; // x, y, z coordinates
        private LocalDateTime timestamp;
        private Double duration;
        private String action;
        private Map<String, Object> data;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GestureData {
        private String gestureType;
        private List<Map<String, Double>> points; // Hand tracking points
        private Double confidence;
        private LocalDateTime timestamp;
        private Boolean recognized;
        private String mappedAction;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpatialPaymentData {
        private Map<String, Double> dropLocation; // 3D coordinates
        private Double dropHeight;
        private String surfaceType;
        private Map<String, Double> recipientLocation;
        private Double distance;
        private String visualEffect;
        private Map<String, Object> animationData;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisualizationEffect {
        private String effectType;
        private String effectName;
        private Map<String, Object> parameters;
        private Integer duration;
        private String triggerEvent;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamificationElement {
        private String elementType;
        private String achievement;
        private Integer pointsAwarded;
        private String badge;
        private String message;
        private LocalDateTime awardedAt;
    }
    
    public void calculateDuration() {
        if (startedAt != null && completedAt != null) {
            this.interactionDurationSeconds = java.time.Duration.between(startedAt, completedAt).getSeconds();
        }
    }
    
    public void incrementGestureCount() {
        this.gestureCount = (this.gestureCount == null ? 0 : this.gestureCount) + 1;
    }
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
    
    public void addInteractionPoint(InteractionPoint point) {
        if (interactionPoints == null) {
            interactionPoints = new java.util.ArrayList<>();
        }
        interactionPoints.add(point);
    }
    
    public void addGestureData(GestureData gesture) {
        if (gestureSequence == null) {
            gestureSequence = new java.util.ArrayList<>();
        }
        gestureSequence.add(gesture);
        incrementGestureCount();
    }
    
    public void addVisualizationEffect(VisualizationEffect effect) {
        if (visualizationEffects == null) {
            visualizationEffects = new java.util.ArrayList<>();
        }
        visualizationEffects.add(effect);
    }
    
    public void addGamificationElement(GamificationElement element) {
        if (gamificationElements == null) {
            gamificationElements = new java.util.ArrayList<>();
        }
        gamificationElements.add(element);
        
        if (element.getPointsAwarded() != null) {
            this.pointsEarned = (this.pointsEarned == null ? 0 : this.pointsEarned) + element.getPointsAwarded();
        }
        
        if (element.getAchievement() != null) {
            this.achievementUnlocked = element.getAchievement();
        }
    }
    
    public boolean isSuccessful() {
        return status == ExperienceStatus.COMPLETED && paymentId != null;
    }
    
    public boolean requiresHighSecurity() {
        return amount != null && amount.compareTo(new BigDecimal("100")) > 0;
    }
}