package com.waqiti.payment.repository;

import com.waqiti.payment.domain.SplitPaymentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SplitPaymentParticipantRepository extends JpaRepository<SplitPaymentParticipant, UUID> {
    /**
     * Find participants for a split payment
     */
    List<SplitPaymentParticipant> findBySplitPaymentId(UUID splitPaymentId);
    
    /**
     * Find participants for a split payment by payment status
     */
    List<SplitPaymentParticipant> findBySplitPaymentIdAndPaid(UUID splitPaymentId, boolean paid);
    
    /**
     * Find a specific participant in a split payment
     */
    Optional<SplitPaymentParticipant> findBySplitPaymentIdAndUserId(UUID splitPaymentId, UUID userId);
    
    /**
     * Check if a user is a participant in a split payment
     */
    boolean existsBySplitPaymentIdAndUserId(UUID splitPaymentId, UUID userId);
}