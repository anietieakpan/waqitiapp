package com.waqiti.security.repository;

import com.waqiti.security.entity.FraudAction;
import com.waqiti.security.entity.FraudCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for fraud action persistence and retrieval.
 * Tracks all actions taken in response to fraud detection.
 */
@Repository
public interface FraudActionRepository extends JpaRepository<FraudAction, String> {
    
    List<FraudAction> findByFraudCase(FraudCase fraudCase);
    
    List<FraudAction> findByStatus(FraudAction.Status status);
    
    List<FraudAction> findByActionType(FraudAction.ActionType actionType);
    
    @Query("SELECT fa FROM FraudAction fa WHERE fa.fraudCase.id = :caseId")
    List<FraudAction> findByCaseId(@Param("caseId") String caseId);
    
    @Query("SELECT fa FROM FraudAction fa WHERE fa.status = 'PENDING' AND fa.requiresApproval = true")
    List<FraudAction> findPendingApprovals();
    
    @Query("SELECT fa FROM FraudAction fa WHERE fa.status = 'FAILED' AND fa.attemptCount < 3")
    List<FraudAction> findRetryableActions();
    
    @Query("SELECT fa FROM FraudAction fa WHERE fa.status = 'RETRY_SCHEDULED' AND fa.nextRetryAt <= :now")
    List<FraudAction> findActionsReadyForRetry(@Param("now") LocalDateTime now);
    
    @Query("SELECT fa FROM FraudAction fa WHERE fa.reversible = true AND fa.reversed = false")
    List<FraudAction> findReversibleActions();
    
    @Query("SELECT COUNT(fa) FROM FraudAction fa WHERE fa.actionType = :type AND fa.status = :status")
    Long countByTypeAndStatus(@Param("type") FraudAction.ActionType type, 
                             @Param("status") FraudAction.Status status);
    
    @Query("SELECT fa FROM FraudAction fa WHERE fa.executedBy = :executor AND fa.timestamp >= :since")
    List<FraudAction> findByExecutorSince(@Param("executor") String executor, 
                                          @Param("since") LocalDateTime since);
    
    @Query("SELECT fa FROM FraudAction fa WHERE fa.targetEntityId = :entityId AND fa.targetEntityType = :entityType")
    List<FraudAction> findByTargetEntity(@Param("entityId") String entityId, 
                                         @Param("entityType") String entityType);
}