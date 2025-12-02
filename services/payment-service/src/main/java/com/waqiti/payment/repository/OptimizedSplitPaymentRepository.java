package com.waqiti.payment.repository;

import com.waqiti.payment.domain.SplitPayment;
import com.waqiti.payment.domain.SplitPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Optimized repository for SplitPayment with N+1 query prevention
 */
@Repository
public interface OptimizedSplitPaymentRepository extends JpaRepository<SplitPayment, UUID> {
    
    /**
     * Find split payment with participants eagerly loaded
     */
    @EntityGraph(attributePaths = {"participants"})
    Optional<SplitPayment> findWithParticipantsById(UUID id);
    
    /**
     * Find split payment by ID and organizer with participants
     */
    @EntityGraph(attributePaths = {"participants"})
    Optional<SplitPayment> findWithParticipantsByIdAndOrganizerId(UUID id, UUID organizerId);
    
    /**
     * Find all split payments for a user (as organizer) with participants
     */
    @Query("SELECT DISTINCT sp FROM SplitPayment sp " +
           "LEFT JOIN FETCH sp.participants " +
           "WHERE sp.organizerId = :userId " +
           "ORDER BY sp.createdAt DESC")
    Page<SplitPayment> findByOrganizerIdWithParticipants(@Param("userId") UUID userId, Pageable pageable);
    
    /**
     * Find all split payments where user is a participant with all related data
     */
    @Query("SELECT DISTINCT sp FROM SplitPayment sp " +
           "LEFT JOIN FETCH sp.participants p " +
           "WHERE p.userId = :userId " +
           "ORDER BY sp.createdAt DESC")
    Page<SplitPayment> findByParticipantUserIdWithParticipants(@Param("userId") UUID userId, Pageable pageable);
    
    /**
     * Find pending split payments for a user with participants
     */
    @Query("SELECT DISTINCT sp FROM SplitPayment sp " +
           "LEFT JOIN FETCH sp.participants p " +
           "WHERE p.userId = :userId " +
           "AND p.status = 'PENDING' " +
           "AND sp.status IN ('PENDING', 'PARTIALLY_PAID') " +
           "ORDER BY sp.createdAt DESC")
    List<SplitPayment> findPendingForUserWithParticipants(@Param("userId") UUID userId);
    
    /**
     * Find expired split payments with participants for cleanup
     */
    @Query("SELECT sp FROM SplitPayment sp " +
           "LEFT JOIN FETCH sp.participants " +
           "WHERE sp.status = 'PENDING' " +
           "AND sp.expiresAt < :now")
    List<SplitPayment> findExpiredWithParticipants(@Param("now") LocalDateTime now);
    
    /**
     * Count split payments by status for a user (optimized)
     */
    @Query("SELECT sp.status, COUNT(sp) FROM SplitPayment sp " +
           "WHERE sp.organizerId = :userId " +
           "GROUP BY sp.status")
    List<Object[]> countByStatusForUser(@Param("userId") UUID userId);
    
    /**
     * Find split payments with specific status and participants
     */
    @EntityGraph(attributePaths = {"participants"})
    Page<SplitPayment> findByStatusOrderByCreatedAtDesc(SplitPaymentStatus status, Pageable pageable);
    
    /**
     * Batch load split payments with participants
     */
    @Query("SELECT DISTINCT sp FROM SplitPayment sp " +
           "LEFT JOIN FETCH sp.participants " +
           "WHERE sp.id IN :ids")
    List<SplitPayment> findAllByIdInWithParticipants(@Param("ids") List<UUID> ids);
    
    /**
     * Find recent split payments for dashboard with limited data
     */
    @Query("SELECT new com.waqiti.payment.dto.SplitPaymentSummary(" +
           "sp.id, sp.title, sp.totalAmount, sp.status, sp.createdAt, " +
           "COUNT(p), SUM(CASE WHEN p.status = 'PAID' THEN p.amount ELSE 0 END)) " +
           "FROM SplitPayment sp " +
           "LEFT JOIN sp.participants p " +
           "WHERE sp.organizerId = :userId " +
           "GROUP BY sp.id, sp.title, sp.totalAmount, sp.status, sp.createdAt " +
           "ORDER BY sp.createdAt DESC")
    Page<Object> findRecentSummariesForUser(@Param("userId") UUID userId, Pageable pageable);
}