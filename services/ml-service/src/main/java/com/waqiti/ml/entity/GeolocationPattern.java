package com.waqiti.ml.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Production-ready GeolocationPattern entity for storing location-based transaction patterns.
 * Supports advanced geolocation analytics, fraud detection, and behavioral analysis.
 */
@Entity
@Table(name = "geolocation_patterns", 
       indexes = {
           @Index(name = "idx_geolocation_user_timestamp", columnList = "user_id, timestamp"),
           @Index(name = "idx_geolocation_transaction", columnList = "transaction_id"),
           @Index(name = "idx_geolocation_coords", columnList = "latitude, longitude"),
           @Index(name = "idx_geolocation_country_city", columnList = "country, city"),
           @Index(name = "idx_geolocation_risk_score", columnList = "risk_score"),
           @Index(name = "idx_geolocation_velocity", columnList = "velocity_impossible"),
           @Index(name = "idx_geolocation_timestamp_desc", columnList = "timestamp DESC")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class GeolocationPattern {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "VARCHAR(36)")
    private String id;

    @NotNull
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @NotNull
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    @Column(name = "latitude", nullable = false, precision = 10, scale = 8)
    private Double latitude;

    @NotNull
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    @Column(name = "longitude", nullable = false, precision = 11, scale = 8)
    private Double longitude;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "country_name", length = 100)
    private String countryName;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "altitude_meters")
    private Double altitudeMeters;

    @Column(name = "is_mock_location")
    @Builder.Default
    private Boolean isMockLocation = false;

    @Column(name = "provider", length = 50)
    private String provider; // GPS, NETWORK, PASSIVE, etc.

    @DecimalMin(value = "0.0", message = "Risk score must be between 0 and 100")
    @DecimalMax(value = "100.0", message = "Risk score must be between 0 and 100")
    @Column(name = "risk_score", precision = 5, scale = 2)
    private Double riskScore;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "velocity_impossible")
    @Builder.Default
    private Boolean velocityImpossible = false;

    @Column(name = "distance_from_last_location", precision = 10, scale = 2)
    private Double distanceFromLastLocation;

    @Column(name = "estimated_travel_speed", precision = 10, scale = 2)
    private Double estimatedTravelSpeed;

    @Column(name = "location_risk_score", precision = 5, scale = 2)
    private Double locationRiskScore;

    @Column(name = "velocity_risk_score", precision = 5, scale = 2)
    private Double velocityRiskScore;

    @Column(name = "geographic_anomaly_score", precision = 5, scale = 2)
    private Double geographicAnomalyScore;

    @Column(name = "threat_intel_score", precision = 5, scale = 2)
    private Double threatIntelScore;

    @Column(name = "country_risk_level", length = 20)
    private String countryRiskLevel;

    @Column(name = "mock_location_detected")
    @Builder.Default
    private Boolean mockLocationDetected = false;

    @Column(name = "ip_geolocation_consistent")
    @Builder.Default
    private Boolean ipGeolocationConsistent = true;

    @Column(name = "time_zone_consistent")
    @Builder.Default
    private Boolean timeZoneConsistent = true;

    @Column(name = "fraud_hotspot_detected")
    @Builder.Default
    private Boolean fraudHotspotDetected = false;

    @Column(name = "cluster_id", length = 50)
    private String clusterId;

    @Column(name = "cluster_distance", precision = 10, scale = 2)
    private Double clusterDistance;

    @Column(name = "is_usual_location")
    @Builder.Default
    private Boolean isUsualLocation = false;

    @Column(name = "location_frequency")
    @Builder.Default
    private Integer locationFrequency = 1;

    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    @Builder.Default
    private Double confidenceScore = 1.0;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "ml_model_version", length = 20)
    private String mlModelVersion;

    @Column(name = "processed_by_ml")
    @Builder.Default
    private Boolean processedByMl = false;

    @Column(name = "flagged_for_review")
    @Builder.Default
    private Boolean flaggedForReview = false;

    @Column(name = "review_status", length = 20)
    private String reviewStatus; // PENDING, APPROVED, REJECTED, ESCALATED

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Calculate distance to another geolocation pattern
     */
    public double calculateDistanceTo(GeolocationPattern other) {
        if (other == null || other.getLatitude() == null || other.getLongitude() == null) {
            return Double.MAX_VALUE;
        }
        
        return calculateHaversineDistance(
            this.latitude, this.longitude,
            other.getLatitude(), other.getLongitude()
        );
    }

    /**
     * Check if this location is within a certain radius of another location
     */
    public boolean isWithinRadius(GeolocationPattern other, double radiusKm) {
        return calculateDistanceTo(other) <= radiusKm;
    }

    /**
     * Check if this is a high-risk location based on risk score
     */
    public boolean isHighRisk() {
        return riskScore != null && riskScore >= 60.0;
    }

    /**
     * Check if this location indicates potential fraud
     */
    public boolean isPotentialFraud() {
        return Boolean.TRUE.equals(velocityImpossible) ||
               Boolean.TRUE.equals(mockLocationDetected) ||
               Boolean.TRUE.equals(fraudHotspotDetected) ||
               (riskScore != null && riskScore >= 80.0);
    }

    /**
     * Get location description for logging and analysis
     */
    public String getLocationDescription() {
        StringBuilder desc = new StringBuilder();
        
        if (city != null && countryName != null) {
            desc.append(city).append(", ").append(countryName);
        } else if (countryName != null) {
            desc.append(countryName);
        } else if (country != null) {
            desc.append(country);
        } else {
            desc.append("Unknown Location");
        }
        
        if (region != null) {
            desc.append(" (").append(region).append(")");
        }
        
        return desc.toString();
    }

    /**
     * Check if location data is complete
     */
    public boolean hasCompleteLocationData() {
        return latitude != null && longitude != null && 
               country != null && city != null;
    }

    /**
     * Calculate accuracy quality score
     */
    public double getAccuracyQualityScore() {
        if (accuracyMeters == null) return 0.0;
        
        if (accuracyMeters <= 10) return 1.0;   // Excellent
        if (accuracyMeters <= 50) return 0.8;   // Good
        if (accuracyMeters <= 100) return 0.6;  // Fair
        if (accuracyMeters <= 500) return 0.4;  // Poor
        return 0.2; // Very poor
    }

    /**
     * Check if this is a recent location pattern
     */
    public boolean isRecent(int hoursThreshold) {
        return timestamp != null && 
               timestamp.isAfter(LocalDateTime.now().minusHours(hoursThreshold));
    }

    /**
     * Update risk assessment
     */
    public void updateRiskAssessment(double newRiskScore, String newRiskLevel) {
        this.riskScore = newRiskScore;
        this.riskLevel = newRiskLevel;
        this.flaggedForReview = newRiskScore >= 70.0;
    }

    /**
     * Mark as processed by ML
     */
    public void markAsProcessed(String modelVersion) {
        this.processedByMl = true;
        this.mlModelVersion = modelVersion;
    }

    /**
     * Soft delete this pattern
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * Check if this pattern is deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Private helper method for Haversine distance calculation
     */
    private static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_KM = 6371.0;
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLatRad = Math.toRadians(lat2 - lat1);
        double deltaLonRad = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                  Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                  Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Lifecycle callback - set defaults before persist
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (confidenceScore == null) {
            confidenceScore = calculateDefaultConfidenceScore();
        }
        if (firstSeenAt == null) {
            firstSeenAt = timestamp;
        }
        lastSeenAt = timestamp;
    }

    /**
     * Lifecycle callback - update last seen on update
     */
    @PreUpdate
    protected void onUpdate() {
        lastSeenAt = LocalDateTime.now();
    }

    /**
     * Calculate default confidence score based on data quality
     */
    private double calculateDefaultConfidenceScore() {
        double score = 1.0;
        
        // Reduce confidence for poor accuracy
        if (accuracyMeters != null && accuracyMeters > 100) {
            score -= 0.3;
        }
        
        // Reduce confidence for mock locations
        if (Boolean.TRUE.equals(isMockLocation)) {
            score -= 0.4;
        }
        
        // Reduce confidence for incomplete data
        if (!hasCompleteLocationData()) {
            score -= 0.2;
        }
        
        return Math.max(score, 0.1); // Minimum confidence of 10%
    }
}