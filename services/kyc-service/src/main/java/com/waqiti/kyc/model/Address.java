package com.waqiti.kyc.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "addresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "json", typeClass = JsonType.class)
public class Address {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "address_type", nullable = false)
    private String addressType; // PRIMARY, SECONDARY, BILLING, SHIPPING

    @Column(name = "address_line_1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line_2")
    private String addressLine2;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(name = "country", nullable = false)
    private String country;

    // Standardized/corrected address fields
    @Column(name = "standardized_line_1")
    private String standardizedLine1;

    @Column(name = "standardized_line_2")
    private String standardizedLine2;

    @Column(name = "standardized_city")
    private String standardizedCity;

    @Column(name = "standardized_state")
    private String standardizedState;

    @Column(name = "standardized_postal_code")
    private String standardizedPostalCode;

    // Verification details
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    private VerificationStatus verificationStatus;

    @Column(name = "verification_score")
    private Integer verificationScore;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verification_provider")
    private String verificationProvider;

    @Type(type = "json")
    @Column(name = "verification_details", columnDefinition = "jsonb")
    private Map<String, Object> verificationDetails;

    // Geolocation data
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "timezone")
    private String timezone;

    // Risk assessment
    @Column(name = "risk_level")
    private String riskLevel; // LOW, MEDIUM, HIGH

    @Column(name = "is_deliverable")
    private Boolean isDeliverable;

    @Column(name = "is_residential")
    private Boolean isResidential;

    @Column(name = "is_po_box")
    private Boolean isPoBox;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        if (verificationStatus == null) {
            verificationStatus = VerificationStatus.PENDING;
        }
        
        if (riskLevel == null) {
            riskLevel = "UNKNOWN";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }

    public boolean needsVerification() {
        return verificationStatus == VerificationStatus.PENDING;
    }

    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(addressLine1);
        if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
            sb.append(", ").append(addressLine2);
        }
        sb.append(", ").append(city);
        if (state != null && !state.trim().isEmpty()) {
            sb.append(", ").append(state);
        }
        sb.append(" ").append(postalCode);
        sb.append(", ").append(country);
        return sb.toString();
    }

    public String getStandardizedFullAddress() {
        if (standardizedLine1 == null) {
            return getFullAddress();
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(standardizedLine1);
        if (standardizedLine2 != null && !standardizedLine2.trim().isEmpty()) {
            sb.append(", ").append(standardizedLine2);
        }
        sb.append(", ").append(standardizedCity != null ? standardizedCity : city);
        if (standardizedState != null && !standardizedState.trim().isEmpty()) {
            sb.append(", ").append(standardizedState);
        }
        sb.append(" ").append(standardizedPostalCode != null ? standardizedPostalCode : postalCode);
        sb.append(", ").append(country);
        return sb.toString();
    }
}