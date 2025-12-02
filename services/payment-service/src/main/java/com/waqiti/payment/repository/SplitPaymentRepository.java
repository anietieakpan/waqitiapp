package com.waqiti.payment.repository;

import com.waqiti.payment.domain.SplitPayment;
import com.waqiti.payment.domain.SplitPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SplitPaymentRepository extends JpaRepository<SplitPayment, UUID> {
    /**
     * Find split payments by organizer ID
     */
    Page<SplitPayment> findByOrganizerId(UUID organizerId, Pageable pageable);
    
    /**
     * Find split payments by organizer ID and status
     */
    Page<SplitPayment> findByOrganizerIdAndStatus(UUID organizerId, SplitPaymentStatus status, Pageable pageable);
    
    /**
     * Find split payments that a user is participating in
     */
    @Query("SELECT DISTINCT sp FROM SplitPayment sp " +
           "JOIN sp.participants p " +
           "WHERE p.userId = :userId")
    Page<SplitPayment> findByParticipantId(@Param("userId") UUID userId, Pageable pageable);
    
    /**
     * Find split payments that a user is participating in with a specific status
     */
    @Query("SELECT DISTINCT sp FROM SplitPayment sp " +
           "JOIN sp.participants p " +
           "WHERE p.userId = :userId AND sp.status = :status")
    Page<SplitPayment> findByParticipantIdAndStatus(
            @Param("userId") UUID userId, 
            @Param("status") SplitPaymentStatus status, 
            Pageable pageable);
    
    /**
     * Find expired active split payments
     */
    List<SplitPayment> findByStatusAndExpiryDateBefore(SplitPaymentStatus status, LocalDateTime now);
}