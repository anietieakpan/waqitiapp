package com.waqiti.compliance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SARRepository extends JpaRepository<com.waqiti.compliance.domain.SuspiciousActivity, java.util.UUID> {
    
    Optional<com.waqiti.compliance.domain.SuspiciousActivity> findBySarId(String sarId);
    
    List<com.waqiti.compliance.domain.SuspiciousActivity> findByUserId(String userId);
    
    List<com.waqiti.compliance.domain.SuspiciousActivity> findByStatus(String status);
    
    List<com.waqiti.compliance.domain.SuspiciousActivity> findByFilingStatus(String filingStatus);
    
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.filingStatus = 'PENDING' ORDER BY s.createdAt ASC")
    List<com.waqiti.compliance.domain.SuspiciousActivity> findPendingFilings();
    
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.userId = :userId AND s.createdAt BETWEEN :start AND :end")
    List<com.waqiti.compliance.domain.SuspiciousActivity> findByUserIdAndDateRange(
            @Param("userId") String userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
    
    long countByStatus(String status);
    
    boolean existsBySarId(String sarId);
}