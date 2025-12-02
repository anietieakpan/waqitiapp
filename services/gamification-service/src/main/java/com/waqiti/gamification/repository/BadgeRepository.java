package com.waqiti.gamification.repository;

import com.waqiti.gamification.domain.Badge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {
    
    Optional<Badge> findByBadgeCode(String badgeCode);
    
    List<Badge> findByIsActiveTrue();
    
    List<Badge> findByCategoryAndIsActiveTrue(Badge.BadgeCategory category);
    
    List<Badge> findByTierAndIsActiveTrue(Badge.BadgeTier tier);
    
    @Query("SELECT b FROM Badge b WHERE b.isActive = true AND b.isSecret = false ORDER BY b.displayOrder ASC, b.name ASC")
    List<Badge> findPublicBadgesOrderByDisplayOrder();
    
    @Query("SELECT b FROM Badge b WHERE b.isActive = true AND (b.validUntil IS NULL OR b.validUntil > :now) ORDER BY b.displayOrder ASC")
    List<Badge> findActiveBadges(@Param("now") LocalDateTime now);
    
    @Query("SELECT b FROM Badge b WHERE b.category = :category AND b.isActive = true AND b.isSecret = false ORDER BY b.tier ASC, b.pointsReward ASC")
    List<Badge> findByCategoryAndPublic(@Param("category") Badge.BadgeCategory category);
    
    @Query("SELECT b FROM Badge b WHERE b.requirementType = :requirementType AND b.isActive = true")
    List<Badge> findByRequirementType(@Param("requirementType") Badge.RequirementType requirementType);
    
    @Query("SELECT b FROM Badge b WHERE b.requirementType = :requirementType AND b.requirementValue <= :value AND b.isActive = true ORDER BY b.requirementValue DESC")
    List<Badge> findEligibleBadges(@Param("requirementType") Badge.RequirementType requirementType, @Param("value") Long value);
    
    @Query("SELECT b FROM Badge b WHERE b.pointsReward >= :minReward AND b.isActive = true ORDER BY b.pointsReward DESC")
    List<Badge> findHighValueBadges(@Param("minReward") Long minReward);
    
    @Query("SELECT b FROM Badge b WHERE b.validUntil IS NOT NULL AND b.validUntil BETWEEN :startDate AND :endDate AND b.isActive = true")
    List<Badge> findExpiringBadges(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT b.category, COUNT(b) FROM Badge b WHERE b.isActive = true GROUP BY b.category")
    List<Object[]> findBadgeCountByCategory();
    
    @Query("SELECT b.tier, COUNT(b) FROM Badge b WHERE b.isActive = true GROUP BY b.tier")
    List<Object[]> findBadgeCountByTier();
    
    @Query("SELECT b FROM Badge b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(b.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND b.isActive = true")
    Page<Badge> findBySearchTerm(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    @Query("SELECT b FROM Badge b WHERE b.isSecret = true AND b.isActive = true")
    List<Badge> findSecretBadges();
    
    @Query("SELECT COUNT(ub) FROM UserBadge ub WHERE ub.badge.id = :badgeId")
    Long countUsersBadgeEarned(@Param("badgeId") Long badgeId);
}