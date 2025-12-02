package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.ManualComplianceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ManualComplianceReviewRepository extends JpaRepository<ManualComplianceReview, UUID> {
    List<ManualComplianceReview> findByTransactionId(String transactionId);
    List<ManualComplianceReview> findByCustomerId(String customerId);
    List<ManualComplianceReview> findByStatus(String status);
    List<ManualComplianceReview> findByPriorityAndStatus(String priority, String status);
    List<ManualComplianceReview> findByTransactionIdAndStatus(String transactionId, String status);

    @Query("SELECT r FROM ManualComplianceReview r WHERE r.status = 'PENDING' AND r.slaDeadline < :currentTime")
    List<ManualComplianceReview> findOverdueReviews(@Param("currentTime") Instant currentTime);
}
