package com.waqiti.rewards.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Referral Code entity
 * Stores unique referral codes for users to share
 */
@Entity
@Table(name = "referral_codes", indexes = {
    @Index(name = "idx_referral_code", columnList = "code", unique = true),
    @Index(name = "idx_user_id", columnList = "userId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralCode {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false, unique = true, length = 20)
    private String code;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    private Instant expiresAt;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @Column(nullable = false)
    @Builder.Default
    private int usageCount = 0;
    
    private Integer maxUsageLimit;
}
