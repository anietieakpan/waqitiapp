package com.waqiti.billpayment.repository;

import com.waqiti.billpayment.entity.BillShareParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BillShareParticipant entity operations
 * Manages participants in bill sharing/splitting requests
 */
@Repository
public interface BillShareParticipantRepository extends JpaRepository<BillShareParticipant, UUID> {

    /**
     * Find all participants for a specific share request
     */
    List<BillShareParticipant> findByShareRequestId(UUID shareRequestId);

    /**
     * Find participant by share request and user
     */
    Optional<BillShareParticipant> findByShareRequestIdAndParticipantUserId(
            UUID shareRequestId, String participantUserId);

    /**
     * Find all share requests a user is participating in
     */
    List<BillShareParticipant> findByParticipantUserId(String participantUserId);

    /**
     * Find participants by status for a share request
     */
    List<BillShareParticipant> findByShareRequestIdAndStatus(UUID shareRequestId, String status);

    /**
     * Find unpaid participants for a share request
     */
    @Query("SELECT p FROM BillShareParticipant p WHERE p.shareRequestId = :shareRequestId " +
           "AND p.status IN ('PENDING', 'ACCEPTED')")
    List<BillShareParticipant> findUnpaidParticipants(@Param("shareRequestId") UUID shareRequestId);

    /**
     * Count participants by status
     */
    long countByShareRequestIdAndStatus(UUID shareRequestId, String status);

    /**
     * Count total participants in a share request
     */
    long countByShareRequestId(UUID shareRequestId);

    /**
     * Find participants with pending payments
     */
    @Query("SELECT p FROM BillShareParticipant p WHERE p.status = 'ACCEPTED' " +
           "AND p.paidAmount < p.owedAmount " +
           "AND p.paymentDueDate <= CURRENT_DATE")
    List<BillShareParticipant> findOverdueParticipants();

    /**
     * Find participant by email for a specific share request
     */
    Optional<BillShareParticipant> findByShareRequestIdAndParticipantEmail(
            UUID shareRequestId, String email);

    /**
     * Check if user is already participant in share request
     */
    boolean existsByShareRequestIdAndParticipantUserId(UUID shareRequestId, String participantUserId);

    /**
     * Get total paid amount for a share request
     */
    @Query("SELECT COALESCE(SUM(p.paidAmount), 0) FROM BillShareParticipant p " +
           "WHERE p.shareRequestId = :shareRequestId")
    java.math.BigDecimal getTotalPaidAmount(@Param("shareRequestId") UUID shareRequestId);

    /**
     * Get total owed amount for a share request
     */
    @Query("SELECT COALESCE(SUM(p.owedAmount), 0) FROM BillShareParticipant p " +
           "WHERE p.shareRequestId = :shareRequestId")
    java.math.BigDecimal getTotalOwedAmount(@Param("shareRequestId") UUID shareRequestId);

    /**
     * Find participants needing reminders (no reminder sent recently)
     */
    @Query("SELECT p FROM BillShareParticipant p WHERE p.shareRequestId = :shareRequestId " +
           "AND p.status IN ('PENDING', 'ACCEPTED') " +
           "AND p.paidAmount < p.owedAmount " +
           "AND (p.lastReminderSentAt IS NULL OR p.lastReminderSentAt < :cutoffTime)")
    List<BillShareParticipant> findParticipantsNeedingReminder(
            @Param("shareRequestId") UUID shareRequestId,
            @Param("cutoffTime") java.time.LocalDateTime cutoffTime);
}
