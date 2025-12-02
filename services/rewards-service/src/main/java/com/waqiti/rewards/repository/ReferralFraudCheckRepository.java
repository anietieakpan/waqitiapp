package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralFraudCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralFraudCheckRepository extends JpaRepository<ReferralFraudCheck, UUID> {

    Optional<ReferralFraudCheck> findByCheckId(String checkId);

    List<ReferralFraudCheck> findByReferralId(String referralId);

    List<ReferralFraudCheck> findByCheckStatus(String checkStatus);

    List<ReferralFraudCheck> findByCheckType(String checkType);

    List<ReferralFraudCheck> findByReviewDecision(String reviewDecision);

    @Query("SELECT f FROM ReferralFraudCheck f WHERE f.reviewDecision = 'PENDING' " +
           "OR (f.reviewDecision IS NULL AND f.checkStatus = 'REVIEW_REQUIRED') " +
           "ORDER BY f.createdAt ASC")
    List<ReferralFraudCheck> findPendingReviews();

    @Query("SELECT f FROM ReferralFraudCheck f WHERE f.riskScore >= :minScore " +
           "ORDER BY f.riskScore DESC")
    List<ReferralFraudCheck> findHighRiskChecks(@Param("minScore") BigDecimal minScore);

    @Query("SELECT f FROM ReferralFraudCheck f WHERE f.checkStatus IN ('FAILED', 'SUSPICIOUS') " +
           "AND f.reviewedBy IS NULL")
    List<ReferralFraudCheck> findUnreviewedSuspiciousChecks();

    @Query("SELECT COUNT(f) FROM ReferralFraudCheck f WHERE f.referralId = :referralId " +
           "AND f.checkStatus = 'FAILED'")
    Long countFailedChecksByReferral(@Param("referralId") String referralId);

    boolean existsByReferralIdAndCheckType(String referralId, String checkType);
}
