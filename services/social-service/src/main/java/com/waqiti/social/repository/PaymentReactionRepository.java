package com.waqiti.social.repository;

import com.waqiti.social.model.PaymentReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentReactionRepository extends JpaRepository<PaymentReaction, UUID> {
    
    List<PaymentReaction> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
    
    int countByPaymentId(UUID paymentId);
    
    boolean existsByPaymentIdAndUserId(UUID paymentId, UUID userId);
}