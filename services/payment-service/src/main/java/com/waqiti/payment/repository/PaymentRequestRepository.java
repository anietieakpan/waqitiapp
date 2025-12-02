package com.waqiti.payment.repository;

import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.domain.PaymentRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {
    /**
     * Find payment requests by requestor ID
     */
    Page<PaymentRequest> findByRequestorId(UUID requestorId, Pageable pageable);
    
    /**
     * Find payment requests by requestor ID and status
     */
    Page<PaymentRequest> findByRequestorIdAndStatus(UUID requestorId, PaymentRequestStatus status, Pageable pageable);
    
    /**
     * Find payment requests by recipient ID
     */
    Page<PaymentRequest> findByRecipientId(UUID recipientId, Pageable pageable);
    
    /**
     * Find payment requests by recipient ID and status
     */
    Page<PaymentRequest> findByRecipientIdAndStatus(UUID recipientId, PaymentRequestStatus status, Pageable pageable);
    
    /**
     * Find payment requests by reference number
     */
    Optional<PaymentRequest> findByReferenceNumber(String referenceNumber);
    
    /**
     * Find expired payment requests
     */
    List<PaymentRequest> findByStatusAndExpiryDateBefore(PaymentRequestStatus status, LocalDateTime now);
    
    /**
     * Find recent payment requests between two users
     */
    @Query("SELECT pr FROM PaymentRequest pr WHERE " +
           "(pr.requestorId = :user1Id AND pr.recipientId = :user2Id) OR " +
           "(pr.requestorId = :user2Id AND pr.recipientId = :user1Id) " +
           "ORDER BY pr.createdAt DESC")
    Page<PaymentRequest> findRecentBetweenUsers(
            @Param("user1Id") UUID user1Id,
            @Param("user2Id") UUID user2Id,
            Pageable pageable);

    /**
     * Find payment request by external transaction ID (e.g., ACH-uuid, STRIPE-txn-id)
     */
    Optional<PaymentRequest> findByExternalTransactionId(String externalTransactionId);
}