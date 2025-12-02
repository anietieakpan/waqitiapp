package com.waqiti.rewards.domain;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Referral Campaign Entity
 *
 * Represents time-bound marketing campaigns for referral programs
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_campaigns", indexes = {
    @Index(name = "idx_referral_campaigns_program", columnList = "program_id"),
    @Index(name = "idx_referral_campaigns_status", columnList = "status"),
    @Index(name = "idx_referral_campaigns_dates", columnList = "startDate,endDate"),
    @Index(name = "idx_referral_campaigns_type", columnList = "campaignType")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "program")
public class ReferralCampaign {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Campaign ID is required")
    @Column(unique = true, nullable = false, length = 100)
    private String campaignId;

    @NotNull(message = "Program is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", referencedColumnName = "programId", nullable = false)
    private ReferralProgram program;

    @NotBlank(message = "Campaign name is required")
    @Column(nullable = false)
    private String campaignName;

    @NotBlank(message = "Campaign type is required")
    @Column(nullable = false, length = 50)
    private String campaignType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Start date is required")
    @Column(nullable = false)
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @NotBlank(message = "Status is required")
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @ElementCollection
    @CollectionTable(name = "campaign_target_audience", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "audience")
    @Builder.Default
    private Set<String> targetAudience = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "campaign_promotional_channels", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "channel")
    @Builder.Default
    private Set<String> promotionalChannels = new HashSet<>();

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> promotionalMaterials = new HashMap<>();

    @Column(columnDefinition = "TEXT")
    private String landingPageUrl;

    @DecimalMin(value = "0.00", message = "Budget must be non-negative")
    @Column(precision = 15, scale = 2)
    private BigDecimal budgetAmount;

    @Builder.Default
    @Column(length = 3)
    private String budgetCurrency = "USD";

    @Min(value = 0, message = "Target must be non-negative")
    @Column
    private Integer targetReferrals;

    @Builder.Default
    @Column(nullable = false)
    private Integer totalReferrals = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer successfulReferrals = 0;

    @Builder.Default
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal totalRewardsIssued = BigDecimal.ZERO;

    @Column(precision = 10, scale = 4)
    private BigDecimal roi;

    @NotBlank(message = "Creator is required")
    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
