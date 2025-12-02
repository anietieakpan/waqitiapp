package com.waqiti.gamification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "challenges", indexes = {
    @Index(name = "idx_challenge_type", columnList = "challenge_type"),
    @Index(name = "idx_challenge_status", columnList = "status"),
    @Index(name = "idx_challenge_start_date", columnList = "start_date"),
    @Index(name = "idx_challenge_end_date", columnList = "end_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Challenge {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "challenge_code", nullable = false, unique = true, length = 50)
    private String challengeCode;
    
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "challenge_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ChallengeType challengeType;
    
    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private ChallengeCategory category;
    
    @Column(name = "difficulty", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Difficulty difficulty = Difficulty.MEDIUM;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ChallengeStatus status = ChallengeStatus.DRAFT;
    
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;
    
    @Column(name = "points_reward", nullable = false)
    private Long pointsReward;
    
    @Column(name = "bonus_points")
    private Long bonusPoints;
    
    @Column(name = "cash_reward", precision = 19, scale = 2)
    private BigDecimal cashReward;
    
    @Column(name = "target_value", nullable = false)
    private Long targetValue;
    
    @Column(name = "target_unit", length = 50)
    private String targetUnit;
    
    @Column(name = "min_participants")
    private Integer minParticipants;
    
    @Column(name = "max_participants")
    private Integer maxParticipants;
    
    @Column(name = "current_participants", nullable = false)
    @Builder.Default
    private Integer currentParticipants = 0;
    
    @Column(name = "completion_percentage_required", nullable = false)
    @Builder.Default
    private Integer completionPercentageRequired = 100;
    
    @Column(name = "is_repeatable", nullable = false)
    @Builder.Default
    private Boolean isRepeatable = false;
    
    @Column(name = "repeat_interval_days")
    private Integer repeatIntervalDays;
    
    @Column(name = "is_team_challenge", nullable = false)
    @Builder.Default
    private Boolean isTeamChallenge = false;
    
    @Column(name = "team_size")
    private Integer teamSize;
    
    @Column(name = "icon_url", length = 500)
    private String iconUrl;
    
    @Column(name = "banner_url", length = 500)
    private String bannerUrl;
    
    @Column(name = "rules", columnDefinition = "TEXT")
    private String rules;
    
    @Column(name = "terms_conditions", columnDefinition = "TEXT")
    private String termsConditions;
    
    @OneToMany(mappedBy = "challenge", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserChallenge> userChallenges = new HashSet<>();
    
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    public enum ChallengeType {
        DAILY,
        WEEKLY,
        MONTHLY,
        SEASONAL,
        SPECIAL,
        PROMOTIONAL,
        COMMUNITY,
        MILESTONE
    }
    
    public enum ChallengeCategory {
        TRANSACTION,
        SAVINGS,
        INVESTMENT,
        SOCIAL,
        EDUCATION,
        SPENDING,
        WELLNESS,
        ENVIRONMENTAL,
        CHARITY
    }
    
    public enum Difficulty {
        EASY(1, 1.0),
        MEDIUM(2, 1.5),
        HARD(3, 2.0),
        EXTREME(4, 3.0);
        
        private final int level;
        private final double multiplier;
        
        Difficulty(int level, double multiplier) {
            this.level = level;
            this.multiplier = multiplier;
        }
        
        public int getLevel() { return level; }
        public double getMultiplier() { return multiplier; }
    }
    
    public enum ChallengeStatus {
        DRAFT,
        SCHEDULED,
        ACTIVE,
        COMPLETED,
        CANCELLED,
        EXPIRED
    }
}