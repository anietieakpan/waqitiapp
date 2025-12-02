package com.waqiti.user.repository;

import com.waqiti.user.domain.KycVerification;
import com.waqiti.user.domain.KycStatus;
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

/**
 * Repository for KYC Verification entities
 */
@Repository
public interface KycVerificationRepository extends JpaRepository<KycVerification, Long> {
    
    /**
     * Find KYC verification by verification ID
     */
    Optional<KycVerification> findByVerificationId(String verificationId);
    
    /**
     * Find KYC verification by user ID
     */
    Optional<KycVerification> findByUserId(UUID userId);
    
    /**
     * Find all KYC verifications by status
     */
    List<KycVerification> findByStatus(KycStatus status);
    
    /**
     * Find KYC verifications by user ID and status
     */
    List<KycVerification> findByUserIdAndStatus(UUID userId, KycStatus status);
    
    /**
     * Find KYC verifications that need retry (ERROR status with retry count < max)
     */
    @Query("SELECT kv FROM KycVerification kv WHERE kv.status = 'ERROR' AND kv.retryCount < :maxRetries")
    List<KycVerification> findEligibleForRetry(@Param("maxRetries") int maxRetries);
    
    /**
     * Find KYC verifications created within date range
     */
    @Query("SELECT kv FROM KycVerification kv WHERE kv.createdAt BETWEEN :startDate AND :endDate")
    List<KycVerification> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find paginated KYC verifications by status
     */
    Page<KycVerification> findByStatus(KycStatus status, Pageable pageable);
    
    /**
     * Count KYC verifications by status
     */
    long countByStatus(KycStatus status);
    
    /**
     * Count KYC verifications by user ID
     */
    long countByUserId(UUID userId);
    
    /**
     * Find expired verifications (created more than specified hours ago with ERROR status)
     */
    @Query("SELECT kv FROM KycVerification kv WHERE kv.status = 'ERROR' AND kv.createdAt < :cutoffTime")
    List<KycVerification> findExpiredErrorVerifications(@Param("cutoffTime") LocalDateTime cutoffTime);
}