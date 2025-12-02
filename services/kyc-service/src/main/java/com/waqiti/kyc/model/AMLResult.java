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
@Table(name = "aml_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "json", typeClass = JsonType.class)
public class AMLResult {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String id;

    @Column(name = "kyc_application_id", nullable = false)
    private String kycApplicationId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "screening_id")
    private String screeningId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VerificationStatus status;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "sanctions_match")
    private boolean sanctionsMatch;

    @Column(name = "pep_match")
    private boolean pepMatch;

    @Column(name = "adverse_media_match")
    private boolean adverseMediaMatch;

    @Column(name = "financial_regulator_match")
    private boolean financialRegulatorMatch;

    @Column(name = "law_enforcement_match")
    private boolean lawEnforcementMatch;

    @Type(type = "json")
    @Column(name = "match_details", columnDefinition = "jsonb")
    private Map<String, Object> matchDetails;

    @Column(name = "screened_at", nullable = false)
    private LocalDateTime screenedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Set expiry to 12 months from screening date
        if (screenedAt != null) {
            expiresAt = screenedAt.plusMonths(12);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean hasHighRisk() {
        return riskScore != null && riskScore >= 70;
    }

    public boolean requiresManualReview() {
        return sanctionsMatch || pepMatch || hasHighRisk();
    }
}