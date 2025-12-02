package com.waqiti.arpayment.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * AR Payment Domain Models
 */
public class ARPaymentModels {

    // Spatial Session Models
    
    @Data
    @Builder
    public static class SpatialSession {
        private String sessionId;
        private String userId;
        private DeviceInfo deviceInfo;
        private SpatialConfiguration spatialConfiguration;
        private Vector3 worldOrigin;
        private LocalDateTime createdAt;
        private SessionStatus status;
        private List<SpatialAnchor> spatialAnchors;
        private List<PaymentZone> paymentZones;
        private SLAMState slamState;
        private Matrix4x4 worldToCamera;
        private Matrix4x4 cameraToWorld;
        private PointCloud pointCloud;
        private ScheduledFuture<?> trackingFuture;
        private LocalDateTime lastUpdate;
        private float trackingQuality;
    }

    @Data
    @Builder
    public static class ARSessionRequest {
        private String userId;
        private DeviceInfo deviceInfo;
        private Vector3 worldOrigin;
        private TrackingMode trackingMode;
        private boolean depthEnabled;
        private boolean occlusionEnabled;
        private boolean planeDetectionEnabled;
        private boolean lightEstimationEnabled;
    }

    @Data
    @Builder
    public static class SpatialSessionResult {
        private boolean success;
        private String sessionId;
        private List<SpatialAnchor> spatialAnchors;
        private List<PaymentZone> paymentZones;
        private TrackingState trackingState;
        private String errorMessage;
    }

    // Object Detection Models
    
    @Data
    @Builder
    public static class ARObject {
        private String objectId;
        private ObjectType type;
        private Vector3 position;
        private Quaternion rotation;
        private Vector3 scale;
        private BoundingBox3D boundingBox;
        private float confidence;
        private Map<String, Object> metadata;
        private LocalDateTime detectedAt;
        private LocalDateTime lastSeen;
        private boolean inPaymentZone;
        private String paymentZoneId;
        private boolean occluded;
    }

    @Data
    @Builder
    public static class ObjectDetectionRequest {
        private String sessionId;
        private Vector3 position;
        private PointCloud pointCloud;
        private CameraFrame cameraFrame;
        private DepthMap depthMap;
        private boolean hapticEnabled;
    }

    @Data
    @Builder
    public static class ObjectDetectionResult {
        private boolean success;
        private ARObject object;
        private boolean inPaymentZone;
        private float confidence;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class DetectedObject {
        private ObjectType type;
        private Vector3 position;
        private Quaternion rotation;
        private Vector3 scale;
        private BoundingBox3D boundingBox;
        private float confidence;
        private Map<String, Object> metadata;
    }

    // Gesture Recognition Models
    
    @Data
    @Builder
    public static class GestureRequest {
        private String sessionId;
        private List<Keypoint3D> handKeypoints;
        private DepthData depthData;
        private long timestamp;
        private boolean hapticEnabled;
    }

    @Data
    @Builder
    public static class GestureResult {
        private boolean success;
        private GestureType gestureType;
        private float confidence;
        private boolean authorized;
        private LocalDateTime timestamp;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class RecognizedGesture {
        private GestureType type;
        private float confidence;
        private long timestamp;
        private List<Keypoint3D> keypoints;
    }

    @Data
    @Builder
    public static class HandTracking {
        private List<Keypoint3D> keypoints;
        private float confidence;
        private Handedness handedness;
    }

    // Virtual Terminal Models
    
    @Data
    @Builder
    public static class VirtualTerminal {
        private String terminalId;
        private String anchorId;
        private Vector3 position;
        private Quaternion rotation;
        private TerminalType terminalType;
        private MerchantInfo merchantInfo;
        private List<String> paymentMethods;
        private VisualStyle visualStyle;
        private float interactionRadius;
        private LocalDateTime createdAt;
        private TerminalStatus status;
    }

    @Data
    @Builder
    public static class VirtualTerminalRequest {
        private String sessionId;
        private String anchorId;
        private Vector3 position;
        private Quaternion rotation;
        private TerminalType terminalType;
        private MerchantInfo merchantInfo;
        private List<String> paymentMethods;
        private VisualStyle visualStyle;
    }

    @Data
    @Builder
    public static class VirtualTerminalResult {
        private boolean success;
        private VirtualTerminal terminal;
        private SpatialAnchor anchor;
        private PaymentZone paymentZone;
        private String errorMessage;
    }

    // Payment Processing Models
    
    @Data
    @Builder
    public static class ARPaymentRequest {
        private String paymentId;
        private String sessionId;
        private String terminalId;
        private Vector3 userPosition;
        private String gestureToken;
        private java.math.BigDecimal amount;  // FIXED: Changed from double to BigDecimal for financial precision
        private String currency;
        private String paymentMethod;
        private String merchantId;
        private String merchantName;
    }

    @Data
    @Builder
    public static class ARPaymentResult {
        private boolean success;
        private String paymentId;
        private ARReceipt receipt;
        private LocalDateTime timestamp;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class PaymentResult {
        private boolean success;
        private String transactionId;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class ARReceipt {
        private String receiptId;
        private String transactionId;
        private java.math.BigDecimal amount;  // FIXED: Changed from double to BigDecimal for financial precision
        private String currency;
        private String merchantName;
        private LocalDateTime timestamp;
        private ARVisualization arVisualization;
    }

    // Marker Tracking Models
    
    @Data
    @Builder
    public static class MarkerTrackingRequest {
        private String sessionId;
        private CameraFrame cameraFrame;
        private CameraIntrinsics cameraIntrinsics;
    }

    @Data
    @Builder
    public static class MarkerTrackingResult {
        private boolean success;
        private List<PaymentMarker> markers;
        private float trackingQuality;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class DetectedMarker {
        private String markerId;
        private MarkerType type;
        private Pose pose;
        private byte[] data;
    }

    @Data
    @Builder
    public static class PaymentMarker {
        private String markerId;
        private MarkerType type;
        private Pose pose;
        private byte[] data;
        private String sessionId;
        private LocalDateTime lastSeen;
        private float trackingQuality;
    }

    // Spatial Components
    
    @Data
    @Builder
    public static class PaymentZone {
        private String zoneId;
        private String terminalId;
        private Vector3 center;
        private float radius;
        private boolean active;
        
        public boolean contains(Vector3 position) {
            float distance = Vector3.distance(center, position);
            return distance <= radius;
        }
    }

    @Data
    @Builder
    public static class SpatialAnchor {
        private String anchorId;
        private Vector3 position;
        private Quaternion rotation;
        private float confidence;
        private boolean persistent;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class SpatialConfiguration {
        private CoordinateSystem coordinateSystem;
        private TrackingMode trackingMode;
        private boolean depthEnabled;
        private boolean occlusionEnabled;
        private boolean planeDetection;
        private boolean lightEstimation;
    }

    @Data
    @Builder
    public static class SpatialValidation {
        private boolean valid;
        private float confidence;
        private String reason;
    }

    // Supporting Classes
    
    @Data
    @Builder
    public static class Vector3 {
        private float x;
        private float y;
        private float z;
        
        public static float distance(Vector3 a, Vector3 b) {
            float dx = a.x - b.x;
            float dy = a.y - b.y;
            float dz = a.z - b.z;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    @Data
    @Builder
    public static class Quaternion {
        private float x;
        private float y;
        private float z;
        private float w;
    }

    @Data
    @Builder
    public static class Matrix4x4 {
        private float[][] values = new float[4][4];
        
        public static Matrix4x4 identity() {
            Matrix4x4 matrix = new Matrix4x4();
            for (int i = 0; i < 4; i++) {
                matrix.values[i][i] = 1.0f;
            }
            return matrix;
        }
    }

    @Data
    @Builder
    public static class Pose {
        private Vector3 position;
        private Quaternion rotation;
    }

    @Data
    @Builder
    public static class BoundingBox3D {
        private Vector3 min;
        private Vector3 max;
        private Vector3 center;
        private Vector3 size;
    }

    @Data
    @Builder
    public static class Keypoint3D {
        private Vector3 position;
        private float confidence;
        private KeypointType type;
    }

    @Data
    @Builder
    public static class ARVisualization {
        private String modelUrl;
        private AnimationType animationType;
        private int duration;
    }

    @Data
    @Builder
    public static class DeviceInfo {
        private String deviceId;
        private String deviceModel;
        private String osVersion;
        private boolean arCapable;
    }

    @Data
    @Builder
    public static class MerchantInfo {
        private String merchantId;
        private String merchantName;
        private String category;
    }

    @Data
    @Builder
    public static class VisualStyle {
        private String theme;
        private String colorScheme;
        private float opacity;
    }

    @Data
    public static class GestureSequence {
        private List<RecognizedGesture> gestures;
        
        public void addGesture(RecognizedGesture gesture) {
            // Add gesture to sequence
        }
        
        public boolean isValid() {
            // Validate gesture sequence
            return true;
        }
    }

    // Data structures
    
    public static class PointCloud {
        private List<Vector3> points;
        private List<Float> confidences;
    }

    public static class CameraFrame {
        private byte[] imageData;
        private int width;
        private int height;
        private long timestamp;
    }

    public static class DepthMap {
        private float[][] depths;
        private int width;
        private int height;
    }

    public static class DepthData {
        private DepthMap depthMap;
        private float minDepth;
        private float maxDepth;
    }

    public static class CameraIntrinsics {
        private float focalLengthX;
        private float focalLengthY;
        private float principalPointX;
        private float principalPointY;
    }

    // Enums
    
    public enum SessionStatus {
        INITIALIZING, ACTIVE, PAUSED, TERMINATED
    }

    public enum TrackingState {
        NOT_TRACKING, LIMITED, TRACKING
    }

    public enum TrackingMode {
        ORIENTATION_ONLY, POSITION_AND_ORIENTATION, BEST_AVAILABLE
    }

    public enum SLAMState {
        INITIALIZING, TRACKING, LOST, RECOVERING
    }

    public enum ObjectType {
        PAYMENT_TERMINAL, PRODUCT, QR_CODE, MERCHANT_SIGN, UNKNOWN
    }

    public enum GestureType {
        PAYMENT_AUTHORIZE, CANCEL, SELECT, SWIPE, PINCH, TAP
    }

    public enum Handedness {
        LEFT, RIGHT, UNKNOWN
    }

    public enum TerminalType {
        STANDARD, EXPRESS, CONTACTLESS, QR_SCANNER
    }

    public enum TerminalStatus {
        ACTIVE, INACTIVE, MAINTENANCE
    }

    public enum MarkerType {
        PAYMENT_QR, MERCHANT_MARKER, PRODUCT_TAG, ANCHOR_MARKER
    }

    public enum HapticType {
        OBJECT_DETECTED, GESTURE_CONFIRMED, PAYMENT_SUCCESS, ERROR
    }

    public enum VisualFeedbackType {
        SUCCESS, ERROR, WARNING, INFO
    }

    public enum AnimationType {
        FADE_IN, SLIDE_UP, BOUNCE, ROTATE
    }

    public enum KeypointType {
        THUMB_TIP, INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP, PALM_CENTER
    }

    public enum CoordinateSystem {
        RIGHT_HANDED_Y_UP, LEFT_HANDED_Y_UP, RIGHT_HANDED_Z_UP
    }
}