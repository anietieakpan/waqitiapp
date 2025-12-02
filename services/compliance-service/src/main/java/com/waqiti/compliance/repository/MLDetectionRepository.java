package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.MLDetectionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MLDetectionRepository extends JpaRepository<MLDetectionResult, UUID> {

    @Query("SELECT m FROM MLDetectionResult m WHERE m.accountId = :accountId " +
           "AND m.detectedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY m.confidenceScore DESC")
    List<MLDetectionResult> findByAccountIdAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<MLDetectionResult> findByAccountId(UUID accountId);

    @Query("SELECT m FROM MLDetectionResult m WHERE m.confidenceScore >= :minScore " +
           "ORDER BY m.detectedAt DESC")
    List<MLDetectionResult> findByMinConfidenceScore(@Param("minScore") Double minScore);
}
