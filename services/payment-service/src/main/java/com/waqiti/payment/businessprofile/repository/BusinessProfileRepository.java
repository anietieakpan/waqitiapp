package com.waqiti.payment.businessprofile.repository;

import com.waqiti.payment.businessprofile.BusinessProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {

    Optional<BusinessProfile> findByUserId(UUID userId);

    List<BusinessProfile> findByUserIdIn(List<UUID> userIds);

    boolean existsByTaxId(String taxId);

    boolean existsByRegistrationNumber(String registrationNumber);

    @Query("SELECT bp FROM BusinessProfile bp WHERE " +
           "(:query IS NULL OR " +
           "LOWER(bp.businessName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(bp.legalName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(bp.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:industry IS NULL OR bp.industry = :industry) AND " +
           "(:businessType IS NULL OR bp.businessType = :businessType) AND " +
           "(:verifiedOnly = false OR bp.status = 'VERIFIED')")
    Page<BusinessProfile> searchProfiles(
            @Param("query") String query,
            @Param("industry") String industry,
            @Param("businessType") String businessType,
            @Param("verifiedOnly") Boolean verifiedOnly,
            Pageable pageable);

    @Query("SELECT bp FROM BusinessProfile bp WHERE bp.status = 'PENDING_VERIFICATION'")
    List<BusinessProfile> findPendingVerification();

    @Query("SELECT bp FROM BusinessProfile bp WHERE bp.status = 'SUSPENDED'")
    List<BusinessProfile> findSuspended();

    @Query("SELECT COUNT(bp) FROM BusinessProfile bp WHERE bp.createdAt >= :startDate")
    long countCreatedAfter(@Param("startDate") java.time.Instant startDate);

    @Query("SELECT bp FROM BusinessProfile bp WHERE bp.industry = :industry AND bp.status = 'VERIFIED'")
    List<BusinessProfile> findVerifiedByIndustry(@Param("industry") String industry);
}