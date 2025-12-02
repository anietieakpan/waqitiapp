package com.waqiti.rewards.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Referral Click Entity
 *
 * Captures detailed analytics for each click on a referral link
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_clicks", indexes = {
    @Index(name = "idx_referral_clicks_link", columnList = "linkId"),
    @Index(name = "idx_referral_clicks_code", columnList = "referralCode"),
    @Index(name = "idx_referral_clicks_timestamp", columnList = "clickedAt"),
    @Index(name = "idx_referral_clicks_session", columnList = "sessionId"),
    @Index(name = "idx_referral_clicks_converted", columnList = "converted"),
    @Index(name = "idx_referral_clicks_ip", columnList = "ipAddress"),
    @Index(name = "idx_referral_clicks_country", columnList = "countryCode")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ReferralClick {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Click ID is required")
    @Column(unique = true, nullable = false, length = 100)
    private String clickId;

    @NotBlank(message = "Link ID is required")
    @Column(nullable = false, length = 100)
    private String linkId;

    @NotBlank(message = "Referral code is required")
    @Column(nullable = false, length = 50)
    private String referralCode;

    // ============================================================================
    // CLICK DETAILS
    // ============================================================================

    @NotNull(message = "Click timestamp is required")
    @Column(nullable = false)
    private LocalDateTime clickedAt;

    @Size(max = 50, message = "IP address must not exceed 50 characters")
    @Column(length = 50)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Size(max = 50, message = "Device type must not exceed 50 characters")
    @Column(length = 50)
    private String deviceType;

    @Size(max = 100, message = "Device model must not exceed 100 characters")
    @Column(length = 100)
    private String deviceModel;

    @Size(max = 50, message = "Browser must not exceed 50 characters")
    @Column(length = 50)
    private String browser;

    @Size(max = 20, message = "Browser version must not exceed 20 characters")
    @Column(length = 20)
    private String browserVersion;

    @Size(max = 50, message = "Operating system must not exceed 50 characters")
    @Column(length = 50)
    private String operatingSystem;

    @Size(max = 20, message = "OS version must not exceed 20 characters")
    @Column(length = 20)
    private String osVersion;

    // ============================================================================
    // GEOLOCATION
    // ============================================================================

    @Size(min = 2, max = 3, message = "Country code must be 2-3 characters")
    @Column(length = 3)
    private String countryCode;

    @Size(max = 100, message = "Country name must not exceed 100 characters")
    @Column(length = 100)
    private String countryName;

    @Size(max = 100, message = "Region must not exceed 100 characters")
    @Column(length = 100)
    private String region;

    @Size(max = 100, message = "City must not exceed 100 characters")
    @Column(length = 100)
    private String city;

    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    // ============================================================================
    // REFERRER INFORMATION
    // ============================================================================

    @Column(columnDefinition = "TEXT")
    private String referrerUrl;

    @Column(columnDefinition = "TEXT")
    private String landingPageUrl;

    // ============================================================================
    // SESSION TRACKING
    // ============================================================================

    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    @Column(length = 100)
    private String sessionId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isUniqueClick = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isBot = false;

    // ============================================================================
    // CONVERSION TRACKING
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Boolean converted = false;

    @Size(max = 100, message = "Conversion ID must not exceed 100 characters")
    @Column(length = 100)
    private String conversionId;

    @Column
    private LocalDateTime convertedAt;

    // ============================================================================
    // AUDIT FIELDS
    // ============================================================================

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ============================================================================
    // LIFECYCLE CALLBACKS
    // ============================================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (clickedAt == null) {
            clickedAt = LocalDateTime.now();
        }
    }

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // ============================================================================

    /**
     * Marks this click as converted
     */
    public void markAsConverted(String conversionId) {
        this.converted = true;
        this.conversionId = conversionId;
        this.convertedAt = LocalDateTime.now();
    }

    /**
     * Checks if the click originated from a mobile device
     */
    public boolean isMobile() {
        return deviceType != null && deviceType.toUpperCase().contains("MOBILE");
    }

    /**
     * Checks if the click originated from a desktop device
     */
    public boolean isDesktop() {
        return deviceType != null && deviceType.toUpperCase().contains("DESKTOP");
    }

    /**
     * Checks if the click originated from a tablet device
     */
    public boolean isTablet() {
        return deviceType != null && deviceType.toUpperCase().contains("TABLET");
    }

    /**
     * Gets the time elapsed from click to conversion
     */
    public Long getTimeToConversionMinutes() {
        if (!converted || convertedAt == null) {
            return null;
        }
        return java.time.Duration.between(clickedAt, convertedAt).toMinutes();
    }

    /**
     * Checks if the click is from a known bot/crawler
     */
    public boolean isPotentialBot() {
        if (isBot != null && isBot) {
            return true;
        }

        if (userAgent == null) {
            return false;
        }

        String lowerUserAgent = userAgent.toLowerCase();
        return lowerUserAgent.contains("bot") ||
               lowerUserAgent.contains("crawler") ||
               lowerUserAgent.contains("spider") ||
               lowerUserAgent.contains("scraper");
    }
}
