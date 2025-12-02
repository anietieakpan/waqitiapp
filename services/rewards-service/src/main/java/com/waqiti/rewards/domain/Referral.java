package com.waqiti.rewards.domain;

import com.waqiti.rewards.enums.ReferralStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Referral relationship entity
 * Tracks referrals between users
 */
@Entity
@Table(name = "referrals", indexes = {
    @Index(name = "idx_referrer_id", columnList = "referrerId"),
    @Index(name = "idx_referee_id", columnList = "refereeId"),
    @Index(name = "idx_referral_code", columnList = "referralCode")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Referral {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String referrerId;
    
    @Column(nullable = false)
    private String refereeId;
    
    @Column(nullable = false, length = 20)
    private String referralCode;
    
    @Column(nullable = false)
    private String refereeAccountType;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal referrerReward;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal refereeReward;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReferralStatus status = ReferralStatus.PENDING;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    private Instant completedAt;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
}
