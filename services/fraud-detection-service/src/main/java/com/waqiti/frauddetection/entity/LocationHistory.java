package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for tracking user location history
 * Supports impossible travel detection and geolocation-based fraud detection
 */
@Entity
@Table(name = "location_history", indexes = {
        @Index(name = "idx_location_history_user_timestamp", columnList = "user_id, timestamp DESC"),
        @Index(name = "idx_location_history_country", columnList = "country"),
        @Index(name = "idx_location_history_timestamp", columnList = "timestamp DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "country", length = 2)
    private String country; // ISO 2-letter country code

    @Column(name = "country_name", length = 100)
    private String countryName;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "latitude", precision = 10, scale = 7)
    private Double latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private Double longitude;

    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(name = "isp", length = 200)
    private String isp;

    @Column(name = "organization", length = 200)
    private String organization;

    @Column(name = "is_vpn")
    @Builder.Default
    private Boolean isVpn = false;

    @Column(name = "is_proxy")
    @Builder.Default
    private Boolean isProxy = false;

    @Column(name = "is_tor")
    @Builder.Default
    private Boolean isTor = false;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private Double riskScore;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Version
    private Long version;
}
