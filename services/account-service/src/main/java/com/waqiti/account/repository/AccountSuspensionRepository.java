package com.waqiti.account.repository;

import com.waqiti.account.domain.AccountSuspension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account Suspension Repository
 */
@Repository
public interface AccountSuspensionRepository extends JpaRepository<AccountSuspension, UUID> {
    
    Optional<AccountSuspension> findByAccountIdAndStatus(UUID accountId, AccountSuspension.SuspensionStatus status);
    
    List<AccountSuspension> findByAccountIdOrderBySuspendedAtDesc(UUID accountId);
    
    List<AccountSuspension> findByStatusAndReviewDateBefore(
        AccountSuspension.SuspensionStatus status, LocalDateTime reviewDate);
    
    @Query("SELECT a FROM AccountSuspension a WHERE a.status = :status AND a.automaticUnsuspension = true " +
           "AND a.suspendedAt < :cutoffTime")
    List<AccountSuspension> findSuspensionsForAutomaticReview(
        @Param("status") AccountSuspension.SuspensionStatus status,
        @Param("cutoffTime") LocalDateTime cutoffTime);
    
    List<AccountSuspension> findBySeverityAndStatus(String severity, AccountSuspension.SuspensionStatus status);
    
    long countByStatusAndSeverity(AccountSuspension.SuspensionStatus status, String severity);
}