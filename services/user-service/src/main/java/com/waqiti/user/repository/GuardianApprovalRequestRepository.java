package com.waqiti.user.repository;

import com.waqiti.user.domain.GuardianApprovalRequest;
import com.waqiti.user.security.FamilyGuardianMfaService.GuardianActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuardianApprovalRequestRepository extends JpaRepository<GuardianApprovalRequest, UUID> {

    /**
     * Find approval request by unique approval request ID
     */
    Optional<GuardianApprovalRequest> findByApprovalRequestId(String approvalRequestId);

    /**
     * Find all approval requests for a dependent user
     */
    List<GuardianApprovalRequest> findByDependentUserId(UUID dependentUserId);

    /**
     * Find approval requests by dependent user and status
     */
    List<GuardianApprovalRequest> findByDependentUserIdAndStatus(UUID dependentUserId, 
                                                                GuardianApprovalRequest.ApprovalStatus status);

    /**
     * Find pending approval requests for a dependent
     */
    List<GuardianApprovalRequest> findByDependentUserIdAndStatusOrderByCreatedAtDesc(
        UUID dependentUserId, GuardianApprovalRequest.ApprovalStatus status);

    /**
     * Find approval requests where guardian is required to approve
     */
    @Query("SELECT ar FROM GuardianApprovalRequest ar JOIN ar.requiredGuardianIds rg WHERE rg = ?1 AND ar.status = 'PENDING'")
    List<GuardianApprovalRequest> findPendingRequestsForGuardian(UUID guardianId);

    /**
     * Find approval requests by action ID
     */
    Optional<GuardianApprovalRequest> findByActionId(String actionId);

    /**
     * Find approval requests by action type
     */
    List<GuardianApprovalRequest> findByActionType(GuardianActionType actionType);

    /**
     * Find expired approval requests
     */
    @Query("SELECT ar FROM GuardianApprovalRequest ar WHERE ar.status = 'PENDING' AND ar.expiresAt < CURRENT_TIMESTAMP")
    List<GuardianApprovalRequest> findExpiredRequests();

    /**
     * Find approval requests expiring soon
     */
    @Query("SELECT ar FROM GuardianApprovalRequest ar WHERE ar.status = 'PENDING' AND " +
           "ar.expiresAt BETWEEN CURRENT_TIMESTAMP AND ?1")
    List<GuardianApprovalRequest> findRequestsExpiringSoon(LocalDateTime dateTime);

    /**
     * Find recent approval requests for a dependent
     */
    @Query("SELECT ar FROM GuardianApprovalRequest ar WHERE ar.dependentUserId = ?1 AND " +
           "ar.createdAt >= ?2 ORDER BY ar.createdAt DESC")
    List<GuardianApprovalRequest> findRecentRequestsForDependent(UUID dependentUserId, LocalDateTime since);

    /**
     * Count pending approval requests for a dependent
     */
    long countByDependentUserIdAndStatus(UUID dependentUserId, GuardianApprovalRequest.ApprovalStatus status);

    /**
     * Count pending requests requiring specific guardian
     */
    @Query("SELECT COUNT(ar) FROM GuardianApprovalRequest ar JOIN ar.requiredGuardianIds rg " +
           "WHERE rg = ?1 AND ar.status = 'PENDING'")
    long countPendingRequestsForGuardian(UUID guardianId);

    /**
     * Find approval requests with specific guardian that are pending
     */
    @Query("SELECT ar FROM GuardianApprovalRequest ar JOIN ar.requiredGuardianIds rg " +
           "WHERE rg = ?1 AND ar.status = 'PENDING' AND ar.expiresAt > CURRENT_TIMESTAMP " +
           "ORDER BY ar.createdAt ASC")
    List<GuardianApprovalRequest> findActiveRequestsForGuardian(UUID guardianId);

    /**
     * Find requests by dependent and action type with status
     */
    List<GuardianApprovalRequest> findByDependentUserIdAndActionTypeAndStatusOrderByCreatedAtDesc(
        UUID dependentUserId, GuardianActionType actionType, GuardianApprovalRequest.ApprovalStatus status);

    /**
     * Delete old completed/expired requests (for cleanup)
     */
    @Query("DELETE FROM GuardianApprovalRequest ar WHERE ar.status IN ('APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED') " +
           "AND ar.completedAt < ?1")
    void deleteOldCompletedRequests(LocalDateTime cutoffDate);

    /**
     * Find requests that need guardian notifications
     */
    @Query("SELECT ar FROM GuardianApprovalRequest ar WHERE ar.status = 'PENDING' AND " +
           "ar.createdAt BETWEEN ?1 AND ?2")
    List<GuardianApprovalRequest> findRequestsCreatedBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Check if there's an active request for the same action
     */
    @Query("SELECT COUNT(ar) > 0 FROM GuardianApprovalRequest ar WHERE ar.dependentUserId = ?1 AND " +
           "ar.actionType = ?2 AND ar.status = 'PENDING' AND ar.expiresAt > CURRENT_TIMESTAMP")
    boolean hasActivePendingRequest(UUID dependentUserId, GuardianActionType actionType);
}