package com.waqiti.support.repository;

import com.waqiti.support.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, String>, JpaSpecificationExecutor<Ticket> {
    
    Optional<Ticket> findByTicketNumber(String ticketNumber);
    
    Page<Ticket> findByUserId(String userId, Pageable pageable);
    
    Page<Ticket> findByAssignedToAgentId(String agentId, Pageable pageable);
    
    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);
    
    Page<Ticket> findByPriority(TicketPriority priority, Pageable pageable);
    
    Page<Ticket> findByCategory(TicketCategory category, Pageable pageable);
    
    @Query("SELECT t FROM Ticket t WHERE t.status NOT IN :closedStatuses")
    Page<Ticket> findOpenTickets(@Param("closedStatuses") List<TicketStatus> closedStatuses, Pageable pageable);
    
    @Query("SELECT t FROM Ticket t WHERE t.slaBreachAt < :now AND t.status NOT IN :closedStatuses")
    List<Ticket> findSlaBreachedTickets(@Param("now") LocalDateTime now, 
                                       @Param("closedStatuses") List<TicketStatus> closedStatuses);
    
    @Query("SELECT t FROM Ticket t WHERE t.isEscalated = true AND t.status NOT IN :closedStatuses")
    Page<Ticket> findEscalatedTickets(@Param("closedStatuses") List<TicketStatus> closedStatuses, Pageable pageable);
    
    @Query("SELECT t FROM Ticket t WHERE t.status = :status AND t.updatedAt < :date")
    List<Ticket> findStaleTickets(@Param("status") TicketStatus status, @Param("date") LocalDateTime date);
    
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.userId = :userId AND t.createdAt > :since")
    Long countUserTicketsSince(@Param("userId") String userId, @Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(t.resolutionTimeMinutes) FROM Ticket t WHERE t.resolvedAt IS NOT NULL AND t.resolvedAt > :since")
    Double getAverageResolutionTime(@Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(t.satisfactionScore) FROM Ticket t WHERE t.satisfactionScore IS NOT NULL AND t.closedAt > :since")
    Double getAverageSatisfactionScore(@Param("since") LocalDateTime since);
    
    @Query("SELECT t.category, COUNT(t) FROM Ticket t WHERE t.createdAt > :since GROUP BY t.category")
    List<Object[]> getTicketCountByCategory(@Param("since") LocalDateTime since);
    
    @Query("SELECT t.priority, COUNT(t) FROM Ticket t WHERE t.status NOT IN :closedStatuses GROUP BY t.priority")
    List<Object[]> getOpenTicketCountByPriority(@Param("closedStatuses") List<TicketStatus> closedStatuses);
    
    @Query("SELECT t FROM Ticket t WHERE t.relatedTransactionId = :transactionId")
    List<Ticket> findByRelatedTransactionId(@Param("transactionId") String transactionId);
    
    @Query("SELECT t FROM Ticket t WHERE t.relatedPaymentId = :paymentId")
    List<Ticket> findByRelatedPaymentId(@Param("paymentId") String paymentId);
    
    @Query("SELECT t FROM Ticket t WHERE LOWER(t.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Ticket> searchTickets(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    @Query("SELECT t FROM Ticket t WHERE t.assignedToAgentId IS NULL AND t.status = :status ORDER BY t.priority DESC, t.createdAt ASC")
    List<Ticket> findUnassignedTickets(@Param("status") TicketStatus status);
    
    @Query("SELECT t.assignedToAgentId, COUNT(t) FROM Ticket t WHERE t.status NOT IN :closedStatuses " +
           "AND t.assignedToAgentId IS NOT NULL GROUP BY t.assignedToAgentId")
    List<Object[]> getAgentWorkload(@Param("closedStatuses") List<TicketStatus> closedStatuses);
    
    @Query("SELECT DATE(t.createdAt), COUNT(t) FROM Ticket t WHERE t.createdAt > :since GROUP BY DATE(t.createdAt)")
    List<Object[]> getTicketTrendData(@Param("since") LocalDateTime since);
    
    @Modifying
    @Query("UPDATE Ticket t SET t.status = :newStatus WHERE t.status = :oldStatus AND t.updatedAt < :before")
    int bulkUpdateStatus(@Param("oldStatus") TicketStatus oldStatus, 
                        @Param("newStatus") TicketStatus newStatus, 
                        @Param("before") LocalDateTime before);
    
    // VIP customer queries
    @Query("SELECT t FROM Ticket t WHERE t.isVip = true AND t.status NOT IN :closedStatuses ORDER BY t.priority DESC, t.createdAt ASC")
    List<Ticket> findVipTickets(@Param("closedStatuses") List<TicketStatus> closedStatuses);
    
    // First response time metrics
    @Query("SELECT AVG(TIMESTAMPDIFF(MINUTE, t.createdAt, t.firstResponseAt)) FROM Ticket t " +
           "WHERE t.firstResponseAt IS NOT NULL AND t.createdAt > :since")
    Double getAverageFirstResponseTime(@Param("since") LocalDateTime since);
    
    // Reopen rate
    @Query("SELECT COUNT(t) * 100.0 / (SELECT COUNT(*) FROM Ticket WHERE closedAt > :since) " +
           "FROM Ticket t WHERE t.reopenedCount > 0 AND t.closedAt > :since")
    Double getReopenRate(@Param("since") LocalDateTime since);
    
    // Find resolved tickets with similarity (placeholder for vector similarity)
    @Query("SELECT t FROM Ticket t WHERE t.status = 'RESOLVED' AND t.resolution IS NOT NULL ORDER BY t.resolvedAt DESC")
    List<Ticket> findResolvedTicketsWithSimilarity(Pageable pageable);
    
    // Find tickets by category and priority for resolution time estimation
    @Query("SELECT t FROM Ticket t WHERE t.category = :category AND t.priority = :priority AND t.resolvedAt IS NOT NULL")
    List<Ticket> findResolvedTicketsByCategoryAndPriority(@Param("category") String category, @Param("priority") String priority);
    
    // Find similar tickets by subject and description text search
    @Query("SELECT t FROM Ticket t WHERE t.status = 'RESOLVED' AND " +
           "(LOWER(t.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY t.resolvedAt DESC")
    List<Ticket> findSimilarResolvedTicketsByText(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    // Find tickets with successful resolutions by pattern
    @Query("SELECT t FROM Ticket t WHERE t.status = 'RESOLVED' AND t.resolution IS NOT NULL AND " +
           "t.satisfactionScore IS NOT NULL AND t.satisfactionScore >= 4")
    List<Ticket> findSuccessfullyResolvedTickets(Pageable pageable);
    
    // Auto-categorization support methods
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.category = :category")
    Long countByCategory(@Param("category") TicketCategory category);
    
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.userId = :userId AND t.isEscalated = true")
    Long countEscalatedTicketsByUser(@Param("userId") String userId);
    
    // Find similar tickets using keyword matching (simplified similarity)
    @Query("SELECT DISTINCT t FROM Ticket t WHERE " +
           "EXISTS (SELECT 1 FROM :keywords k WHERE " +
           "LOWER(t.subject) LIKE LOWER(CONCAT('%', k, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', k, '%'))) " +
           "ORDER BY t.createdAt DESC")
    List<Ticket> findSimilarTickets(@Param("keywords") List<String> keywords, Pageable pageable);

    // ===========================================================================
    // SOFT DELETE SUPPORT FOR GDPR COMPLIANCE
    // ===========================================================================

    /**
     * Override findAll to exclude soft-deleted tickets by default.
     */
    @Query("SELECT t FROM Ticket t WHERE t.deletedAt IS NULL")
    List<Ticket> findAll();

    /**
     * Override findById to exclude soft-deleted tickets.
     */
    @Query("SELECT t FROM Ticket t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Ticket> findById(@Param("id") String id);

    /**
     * Find soft-deleted tickets (for admins/audit).
     */
    @Query("SELECT t FROM Ticket t WHERE t.deletedAt IS NOT NULL ORDER BY t.deletedAt DESC")
    Page<Ticket> findDeletedTickets(Pageable pageable);

    /**
     * Find soft-deleted tickets by user (for GDPR data export).
     */
    @Query("SELECT t FROM Ticket t WHERE t.userId = :userId AND t.deletedAt IS NOT NULL")
    List<Ticket> findDeletedTicketsByUser(@Param("userId") String userId);

    /**
     * Find tickets eligible for permanent deletion (retention period expired).
     */
    @Query("SELECT t FROM Ticket t WHERE t.deletedAt IS NOT NULL " +
           "AND t.retentionUntil IS NOT NULL " +
           "AND t.retentionUntil < :now")
    List<Ticket> findTicketsEligibleForPermanentDeletion(@Param("now") LocalDateTime now);

    /**
     * Count soft-deleted tickets.
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedAt IS NOT NULL")
    Long countDeletedTickets();

    /**
     * Find tickets by user excluding soft-deleted.
     */
    @Query("SELECT t FROM Ticket t WHERE t.userId = :userId AND t.deletedAt IS NULL ORDER BY t.createdAt DESC")
    Page<Ticket> findActiveTicketsByUser(@Param("userId") String userId, Pageable pageable);

    /**
     * Restore soft-deleted ticket.
     */
    @Modifying
    @Query("UPDATE Ticket t SET t.deletedAt = NULL, t.deletedBy = NULL, " +
           "t.deletionReason = NULL, t.retentionUntil = NULL WHERE t.id = :id")
    int restoreTicket(@Param("id") String id);

    /**
     * Bulk soft delete tickets by user (GDPR right to erasure).
     */
    @Modifying
    @Query("UPDATE Ticket t SET t.deletedAt = :deletedAt, t.deletedBy = :deletedBy, " +
           "t.deletionReason = :reason, t.retentionUntil = :retentionUntil " +
           "WHERE t.userId = :userId AND t.deletedAt IS NULL")
    int softDeleteTicketsByUser(@Param("userId") String userId,
                                @Param("deletedBy") String deletedBy,
                                @Param("reason") String reason,
                                @Param("deletedAt") LocalDateTime deletedAt,
                                @Param("retentionUntil") LocalDateTime retentionUntil);
}