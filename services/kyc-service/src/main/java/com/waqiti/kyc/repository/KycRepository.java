package com.waqiti.kyc.repository;

import com.waqiti.kyc.domain.KycProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * KYC Repository for database operations
 * 
 * @author Waqiti KYC Team
 * @version 3.0.0
 */
@Repository
public interface KycRepository extends JpaRepository<KycProfile, String> {

    /**
     * Find KYC profile by user ID
     */
    Optional<KycProfile> findByUserId(String userId);

    /**
     * Find KYC profiles by status
     */
    @Query("SELECT k FROM KycProfile k WHERE k.status = :status")
    List<KycProfile> findByStatus(@Param("status") String status);

    /**
     * Find KYC profiles requiring review
     */
    @Query("SELECT k FROM KycProfile k WHERE k.requiresReview = true")
    List<KycProfile> findProfilesRequiringReview();

    /**
     * Find KYC profiles by jurisdiction
     */
    @Query("SELECT k FROM KycProfile k WHERE k.jurisdiction = :jurisdiction")
    List<KycProfile> findByJurisdiction(@Param("jurisdiction") String jurisdiction);
}