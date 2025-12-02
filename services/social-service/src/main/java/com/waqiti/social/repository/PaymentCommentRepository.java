package com.waqiti.social.repository;

import com.waqiti.social.model.PaymentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentCommentRepository extends JpaRepository<PaymentComment, UUID> {
    
    List<PaymentComment> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
    
    int countByPaymentId(UUID paymentId);
}