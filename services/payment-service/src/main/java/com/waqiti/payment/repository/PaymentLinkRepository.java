package com.waqiti.payment.repository;

import com.waqiti.payment.domain.PaymentLink;
import com.waqiti.payment.domain.PaymentLink.PaymentLinkStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PaymentLink entities
 */
@Repository
public interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {
    
    /**
     * Find payment link by its shareable link ID
     */
    Optional<PaymentLink> findByLinkId(String linkId);
    
    /**
     * Find payment links created by a specific user
     */
    Page<PaymentLink> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);
    
    /**
     * Find active payment links by creator
     */
    Page<PaymentLink> findByCreatorIdAndStatusOrderByCreatedAtDesc(
            UUID creatorId, PaymentLinkStatus status, Pageable pageable);
    
    /**
     * Find payment links by status
     */
    List<PaymentLink> findByStatus(PaymentLinkStatus status);
    
    /**
     * Find expired payment links that are still active
     */
    @Query("SELECT pl FROM PaymentLink pl WHERE pl.status = 'ACTIVE' AND pl.expiresAt < :now")
    List<PaymentLink> findExpiredActiveLinks(@Param("now") LocalDateTime now);
    
    /**
     * Find payment links that have reached max uses but are still active
     */
    @Query("SELECT pl FROM PaymentLink pl WHERE pl.status = 'ACTIVE' " +
           "AND pl.maxUses IS NOT NULL AND pl.currentUses >= pl.maxUses")
    List<PaymentLink> findLinksReachedMaxUses();
    
    /**
     * Count active payment links by creator
     */
    long countByCreatorIdAndStatus(UUID creatorId, PaymentLinkStatus status);
    
    /**
     * Check if link ID already exists
     */
    boolean existsByLinkId(String linkId);
    
    /**
     * Find popular payment links (by usage)
     */
    @Query("SELECT pl FROM PaymentLink pl WHERE pl.currentUses > 0 " +
           "ORDER BY pl.currentUses DESC, pl.totalCollected DESC")
    Page<PaymentLink> findMostUsedLinks(Pageable pageable);
    
    /**
     * Find payment links expiring soon
     */
    @Query("SELECT pl FROM PaymentLink pl WHERE pl.status = 'ACTIVE' " +
           "AND pl.expiresAt BETWEEN :now AND :expiryThreshold")
    List<PaymentLink> findLinksExpiringSoon(
            @Param("now") LocalDateTime now, 
            @Param("expiryThreshold") LocalDateTime expiryThreshold);
    
    /**
     * Bulk update expired links to EXPIRED status
     */
    @Modifying
    @Query("UPDATE PaymentLink pl SET pl.status = 'EXPIRED' " +
           "WHERE pl.status = 'ACTIVE' AND pl.expiresAt < :now")
    int markExpiredLinksAsExpired(@Param("now") LocalDateTime now);
    
    /**
     * Bulk update links that reached max uses to COMPLETED status
     */
    @Modifying
    @Query("UPDATE PaymentLink pl SET pl.status = 'COMPLETED' " +
           "WHERE pl.status = 'ACTIVE' AND pl.maxUses IS NOT NULL AND pl.currentUses >= pl.maxUses")
    int markCompletedLinks();
    
    /**
     * Find payment links by creator and date range
     */
    @Query("SELECT pl FROM PaymentLink pl WHERE pl.creatorId = :creatorId " +
           "AND pl.createdAt BETWEEN :startDate AND :endDate ORDER BY pl.createdAt DESC")
    Page<PaymentLink> findByCreatorIdAndDateRange(
            @Param("creatorId") UUID creatorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    
    /**
     * Get payment link statistics for a creator
     */
    @Query("SELECT COUNT(pl), SUM(pl.totalCollected), SUM(pl.currentUses) " +
           "FROM PaymentLink pl WHERE pl.creatorId = :creatorId")
    Object[] getCreatorStatistics(@Param("creatorId") UUID creatorId);
    
    /**
     * Find payment links created in the last N days
     */
    @Query("SELECT pl FROM PaymentLink pl WHERE pl.createdAt >= :sinceDate ORDER BY pl.createdAt DESC")
    List<PaymentLink> findRecentLinks(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Search payment links by title or description
     */
    @Query("SELECT pl FROM PaymentLink pl WHERE pl.creatorId = :creatorId " +
           "AND (LOWER(pl.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(pl.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<PaymentLink> searchByTitleOrDescription(
            @Param("creatorId") UUID creatorId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);
}