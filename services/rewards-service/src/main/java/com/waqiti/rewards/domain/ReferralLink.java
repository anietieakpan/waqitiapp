package com.waqiti.rewards.domain;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Referral Link Entity
 *
 * Represents a personalized referral link with tracking capabilities
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_links", indexes = {
    @Index(name = "idx_referral_links_user", columnList = "userId"),
    @Index(name = "idx_referral_links_program", columnList = "program_id"),
    @Index(name = "idx_referral_links_code", columnList = "referralCode"),
    @Index(name = "idx_referral_links_active", columnList = "isActive"),
    @Index(name = "idx_referral_links_channel", columnList = "channel")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "program")
public class ReferralLink {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Link ID is required")
    @Column(unique = true, nullable = false, length = 100)
    private String linkId;

    @NotNull(message = "User ID is required")
    @Column(nullable = false)
    private UUID userId;

    @NotNull(message = "Program is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", referencedColumnName = "programId", nullable = false)
    private ReferralProgram program;

    @NotBlank(message = "Referral code is required")
    @Size(min = 4, max = 50, message = "Referral code must be between 4 and 50 characters")
    @Column(unique = true, nullable = false, length = 50)
    private String referralCode;

    // ============================================================================
    // LINK DETAILS
    // ============================================================================

    @NotBlank(message = "Short URL is required")
    @Column(unique = true, nullable = false)
    private String shortUrl;

    @NotBlank(message = "Full URL is required")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String fullUrl;

    @Column(columnDefinition = "TEXT")
    private String qrCodeUrl;

    // ============================================================================
    // LINK CONFIGURATION
    // ============================================================================

    @NotBlank(message = "Link type is required")
    @Builder.Default
    @Column(nullable = false, length = 50)
    private String linkType = "WEB";

    @NotBlank(message = "Channel is required")
    @Builder.Default
    @Column(nullable = false, length = 50)
    private String channel = "DIRECT";

    // ============================================================================
    // TRACKING METRICS
    // ============================================================================

    @Builder.Default
    @Min(value = 0, message = "Click count must be non-negative")
    @Column(nullable = false)
    private Integer clickCount = 0;

    @Builder.Default
    @Min(value = 0, message = "Unique click count must be non-negative")
    @Column(nullable = false)
    private Integer uniqueClickCount = 0;

    @Builder.Default
    @Min(value = 0, message = "Signup count must be non-negative")
    @Column(nullable = false)
    private Integer signupCount = 0;

    @Builder.Default
    @Min(value = 0, message = "Conversion count must be non-negative")
    @Column(nullable = false)
    private Integer conversionCount = 0;

    @Column
    private LocalDateTime lastClickedAt;

    // ============================================================================
    // UTM PARAMETERS
    // ============================================================================

    @Size(max = 100, message = "UTM source must not exceed 100 characters")
    @Column(length = 100)
    private String utmSource;

    @Size(max = 100, message = "UTM medium must not exceed 100 characters")
    @Column(length = 100)
    private String utmMedium;

    @Size(max = 100, message = "UTM campaign must not exceed 100 characters")
    @Column(length = 100)
    private String utmCampaign;

    @Size(max = 100, message = "UTM term must not exceed 100 characters")
    @Column(length = 100)
    private String utmTerm;

    @Size(max = 100, message = "UTM content must not exceed 100 characters")
    @Column(length = 100)
    private String utmContent;

    // ============================================================================
    // STATUS AND EXPIRY
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Column
    private LocalDateTime expiresAt;

    // ============================================================================
    // METADATA
    // ============================================================================

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> customMetadata = new HashMap<>();

    // ============================================================================
    // AUDIT FIELDS
    // ============================================================================

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ============================================================================
    // LIFECYCLE CALLBACKS
    // ============================================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // ============================================================================

    /**
     * Increments the total click count and updates last clicked timestamp
     */
    public void recordClick(boolean isUnique) {
        this.clickCount++;
        if (isUnique) {
            this.uniqueClickCount++;
        }
        this.lastClickedAt = LocalDateTime.now();
    }

    /**
     * Increments the signup count
     */
    public void recordSignup() {
        this.signupCount++;
    }

    /**
     * Increments the conversion count
     */
    public void recordConversion() {
        this.conversionCount++;
    }

    /**
     * Checks if the link is currently valid (active and not expired)
     */
    public boolean isValid() {
        if (!isActive) {
            return false;
        }

        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }

        return true;
    }

    /**
     * Calculates the click-to-signup conversion rate
     */
    public double getClickToSignupRate() {
        if (clickCount == 0) {
            return 0.0;
        }
        return (double) signupCount / clickCount;
    }

    /**
     * Calculates the signup-to-conversion rate
     */
    public double getSignupToConversionRate() {
        if (signupCount == 0) {
            return 0.0;
        }
        return (double) conversionCount / signupCount;
    }

    /**
     * Calculates the overall conversion rate (conversions / clicks)
     */
    public double getOverallConversionRate() {
        if (clickCount == 0) {
            return 0.0;
        }
        return (double) conversionCount / clickCount;
    }

    /**
     * Checks if the link has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Deactivates the link
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Activates the link
     */
    public void activate() {
        this.isActive = true;
    }
}
