package com.waqiti.dispute.repository;

import com.waqiti.dispute.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Dispute entities
 * Provides database access for dispute management operations
 */
@Repository
public interface DisputeRepository extends JpaRepository<Dispute, String> {
    
    Optional<Dispute> findByDisputeId(String disputeId);
    
    List<Dispute> findByUserId(String userId);
    
    List<Dispute> findByStatus(String status);
    
    List<Dispute> findByUserIdAndStatus(String userId, String status);
    
    @Query("SELECT d FROM Dispute d WHERE d.createdAt BETWEEN :startDate AND :endDate")
    List<Dispute> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                   @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT d FROM Dispute d WHERE d.status = :status ORDER BY d.createdAt DESC")
    List<Dispute> findByStatusOrderByCreatedAtDesc(@Param("status") String status);
    
    long countByStatus(String status);
    
    long countByUserIdAndStatus(String userId, String status);
}
