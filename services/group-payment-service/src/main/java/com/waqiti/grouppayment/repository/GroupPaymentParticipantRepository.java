package com.waqiti.grouppayment.repository;

import com.waqiti.grouppayment.entity.GroupPayment;
import com.waqiti.grouppayment.entity.GroupPaymentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupPaymentParticipantRepository extends JpaRepository<GroupPaymentParticipant, Long> {

    List<GroupPaymentParticipant> findByGroupPaymentId(Long groupPaymentId);

    Optional<GroupPaymentParticipant> findByGroupPaymentIdAndUserId(Long groupPaymentId, String userId);

    List<GroupPaymentParticipant> findByUserIdAndStatus(String userId, GroupPaymentParticipant.ParticipantStatus status);

    @Query("SELECT p FROM GroupPaymentParticipant p WHERE p.userId = :userId AND p.owedAmount > p.paidAmount")
    List<GroupPaymentParticipant> findByUserIdWithOutstandingBalance(@Param("userId") String userId);

    @Query("SELECT p FROM GroupPaymentParticipant p WHERE p.status = :status AND p.lastReminderSent < :reminderThreshold")
    List<GroupPaymentParticipant> findParticipantsNeedingReminder(
        @Param("status") GroupPaymentParticipant.ParticipantStatus status,
        @Param("reminderThreshold") Instant reminderThreshold
    );

    @Query("SELECT SUM(p.owedAmount - p.paidAmount) FROM GroupPaymentParticipant p WHERE p.userId = :userId AND p.owedAmount > p.paidAmount")
    BigDecimal getTotalOutstandingByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(p) FROM GroupPaymentParticipant p WHERE p.groupPayment.id = :groupPaymentId AND p.status = 'PAID'")
    long countPaidParticipantsByGroupPaymentId(@Param("groupPaymentId") Long groupPaymentId);

    @Query("SELECT COUNT(p) FROM GroupPaymentParticipant p WHERE p.groupPayment.id = :groupPaymentId")
    long countTotalParticipantsByGroupPaymentId(@Param("groupPaymentId") Long groupPaymentId);

    /**
     * Find participants by group payment and status
     */
    List<GroupPaymentParticipant> findByGroupPaymentAndStatus(
        GroupPayment groupPayment,
        GroupPaymentParticipant.ParticipantStatus status
    );

    /**
     * Find all participants for a group payment
     */
    List<GroupPaymentParticipant> findByGroupPayment(GroupPayment groupPayment);

    /**
     * Check if participant exists in group payment
     */
    boolean existsByGroupPaymentAndUserId(GroupPayment groupPayment, String userId);
}