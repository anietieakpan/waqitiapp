package com.waqiti.kyc.repository;

import com.waqiti.kyc.model.AMLResult;
import com.waqiti.kyc.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AMLResultRepository extends JpaRepository<AMLResult, String> {

    Optional<AMLResult> findByKycApplicationId(String kycApplicationId);
    
    List<AMLResult> findByUserId(String userId);
    
    List<AMLResult> findByUserIdAndStatus(String userId, VerificationStatus status);
    
    List<AMLResult> findByProvider(String provider);
    
    List<AMLResult> findByStatus(VerificationStatus status);
    
    List<AMLResult> findBySanctionsMatchTrue();
    
    List<AMLResult> findByPepMatchTrue();
    
    List<AMLResult> findByAdverseMediaMatchTrue();
    
    @Query("SELECT a FROM AMLResult a WHERE a.riskScore >= :minScore")
    List<AMLResult> findByRiskScoreGreaterThanEqual(@Param("minScore") Integer minScore);
    
    @Query("SELECT a FROM AMLResult a WHERE a.screenedAt BETWEEN :startDate AND :endDate")
    List<AMLResult> findByScreenedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(a) FROM AMLResult a WHERE a.status = :status AND a.screenedAt >= :date")
    Long countByStatusAndScreenedAtAfter(@Param("status") VerificationStatus status, 
                                        @Param("date") LocalDateTime date);
}