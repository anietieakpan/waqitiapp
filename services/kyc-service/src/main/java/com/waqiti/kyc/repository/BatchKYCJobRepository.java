package com.waqiti.kyc.repository;

import com.waqiti.kyc.domain.BatchKYCJob;
import com.waqiti.kyc.domain.BatchKYCJob.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for BatchKYCJob entities
 */
@Repository
public interface BatchKYCJobRepository extends JpaRepository<BatchKYCJob, String> {
    
    List<BatchKYCJob> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
    
    List<BatchKYCJob> findByOrganizationId(String organizationId);
    
    List<BatchKYCJob> findByStatus(JobStatus status);
    
    List<BatchKYCJob> findByStatusAndCreatedAtBefore(JobStatus status, LocalDateTime before);
    
    @Query("SELECT b FROM BatchKYCJob b WHERE b.organizationId = :orgId AND b.createdAt BETWEEN :start AND :end")
    List<BatchKYCJob> findByOrganizationIdAndDateRange(
            @Param("orgId") String organizationId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
    
    @Query("SELECT COUNT(b) FROM BatchKYCJob b WHERE b.organizationId = :orgId AND b.status = :status")
    long countByOrganizationIdAndStatus(
            @Param("orgId") String organizationId,
            @Param("status") JobStatus status
    );
    
    List<BatchKYCJob> findByParentJobId(String parentJobId);
    
    @Query("SELECT b FROM BatchKYCJob b WHERE b.status = 'IN_PROGRESS' AND b.startedAt < :timeout")
    List<BatchKYCJob> findStuckJobs(@Param("timeout") LocalDateTime timeout);
}