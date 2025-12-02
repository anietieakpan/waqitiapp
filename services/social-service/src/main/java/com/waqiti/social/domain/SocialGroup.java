package com.waqiti.social.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "social_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialGroup {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    
    @Column(name = "group_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private GroupType groupType;
    
    @Column(name = "privacy_level", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PrivacyLevel privacyLevel;
    
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    
    @Column(name = "banner_url", length = 500)
    private String bannerUrl;
    
    @Type(type = "jsonb")
    @Column(name = "member_ids", columnDefinition = "jsonb")
    private List<UUID> memberIds;
    
    @Type(type = "jsonb")
    @Column(name = "admin_ids", columnDefinition = "jsonb")
    private List<UUID> adminIds;
    
    @Column(name = "member_count")
    private Integer memberCount = 0;
    
    @Column(name = "max_members")
    private Integer maxMembers = 50;
    
    @Column(name = "invite_code", length = 20)
    private String inviteCode;
    
    @Column(name = "invite_expires_at")
    private LocalDateTime inviteExpiresAt;
    
    @Column(name = "total_payments", precision = 19, scale = 2)
    private BigDecimal totalPayments = BigDecimal.ZERO;
    
    @Column(name = "total_splits", precision = 19, scale = 2)
    private BigDecimal totalSplits = BigDecimal.ZERO;
    
    @Column(name = "payment_count")
    private Integer paymentCount = 0;
    
    @Type(type = "jsonb")
    @Column(name = "group_rules", columnDefinition = "jsonb")
    private List<String> groupRules;
    
    @Type(type = "jsonb")
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;
    
    @Column(name = "location", length = 200)
    private String location;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "is_archived")
    private Boolean isArchived = false;
    
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
    
    @Type(type = "jsonb")
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;
    
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (inviteCode == null) {
            inviteCode = generateInviteCode();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum GroupType {
        FRIENDS,
        FAMILY,
        ROOMMATES,
        COWORKERS,
        TRAVEL,
        DINING,
        EVENTS,
        SPORTS,
        HOBBY,
        STUDY,
        CHARITY,
        CUSTOM
    }
    
    public enum PrivacyLevel {
        PUBLIC,     // Anyone can join
        PRIVATE,    // Invite only
        SECRET      // Hidden from search
    }
    
    private String generateInviteCode() {
        return "GRP" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
    
    public boolean isUserMember(UUID userId) {
        return memberIds != null && memberIds.contains(userId);
    }
    
    public boolean isUserAdmin(UUID userId) {
        return adminIds != null && adminIds.contains(userId);
    }
    
    public boolean canAddMoreMembers() {
        return memberCount < maxMembers;
    }
    
    public boolean isInviteValid() {
        return inviteExpiresAt == null || inviteExpiresAt.isAfter(LocalDateTime.now());
    }
    
    public void addPayment(BigDecimal amount) {
        this.totalPayments = this.totalPayments.add(amount);
        this.paymentCount = (this.paymentCount == null ? 0 : this.paymentCount) + 1;
    }
    
    public void addSplit(BigDecimal amount) {
        this.totalSplits = this.totalSplits.add(amount);
    }
    
    public void incrementMemberCount() {
        this.memberCount = (this.memberCount == null ? 0 : this.memberCount) + 1;
    }
    
    public void decrementMemberCount() {
        this.memberCount = Math.max(0, (this.memberCount == null ? 0 : this.memberCount) - 1);
    }
}