package com.waqiti.gdpr.repository;

import com.waqiti.gdpr.domain.DataSubjectRequest;
import com.waqiti.gdpr.domain.RequestStatus;
import com.waqiti.gdpr.domain.RequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Data Subject Requests (GDPR Article 15-22)
 * Production-ready Spring Data JPA repository with GDPR-specific queries
 */
@Repository
public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, String> {

    /**
     * Find all requests for a specific user
     */
    List<DataSubjectRequest> findByUserId(String userId);

    /**
     * Find requests by user ID and request type with specific statuses
     */
    List<DataSubjectRequest> findByUserIdAndRequestTypeAndStatusIn(
            String userId,
            RequestType requestType,
            List<RequestStatus> statuses
    );

    /**
     * Find requests by status
     */
    List<DataSubjectRequest> findByStatus(RequestStatus status);

    /**
     * Find requests by multiple statuses
     */
    List<DataSubjectRequest> findByStatusIn(List<RequestStatus> statuses);

    /**
     * Find overdue requests that are still pending
     */
    @Query("SELECT r FROM DataSubjectRequest r " +
           "WHERE r.status IN :statuses " +
           "AND r.deadline < :now")
    List<DataSubjectRequest> findOverdueRequests(
            @Param("statuses") List<RequestStatus> statuses,
            @Param("now") LocalDateTime now
    );

    /**
     * Find requests approaching deadline (for alerts)
     */
    @Query("SELECT r FROM DataSubjectRequest r " +
           "WHERE r.status IN :statuses " +
           "AND r.deadline BETWEEN :now AND :alertThreshold")
    List<DataSubjectRequest> findRequestsApproachingDeadline(
            @Param("statuses") List<RequestStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("alertThreshold") LocalDateTime alertThreshold
    );

    /**
     * Find requests by request type
     */
    List<DataSubjectRequest> findByRequestType(RequestType requestType);

    /**
     * Find requests submitted within a date range
     */
    @Query("SELECT r FROM DataSubjectRequest r " +
           "WHERE r.submittedAt BETWEEN :startDate AND :endDate")
    List<DataSubjectRequest> findBySubmittedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find completed requests within a date range (for reporting)
     */
    @Query("SELECT r FROM DataSubjectRequest r " +
           "WHERE r.status = :status " +
           "AND r.completedAt BETWEEN :startDate AND :endDate")
    List<DataSubjectRequest> findCompletedRequests(
            @Param("status") RequestStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count requests by status
     */
    long countByStatus(RequestStatus status);

    /**
     * Count requests by user and request type
     */
    long countByUserIdAndRequestType(String userId, RequestType requestType);

    /**
     * Find requests with expired export URLs
     */
    @Query("SELECT r FROM DataSubjectRequest r " +
           "WHERE r.exportExpiresAt IS NOT NULL " +
           "AND r.exportExpiresAt < :now")
    List<DataSubjectRequest> findRequestsWithExpiredExports(
            @Param("now") LocalDateTime now
    );

    /**
     * Find requests by verification token (for verification)
     */
    Optional<DataSubjectRequest> findByVerificationToken(String verificationToken);

    /**
     * Check if user has pending requests of a specific type
     */
    @Query("SELECT COUNT(r) > 0 FROM DataSubjectRequest r " +
           "WHERE r.userId = :userId " +
           "AND r.requestType = :requestType " +
           "AND r.status IN :statuses")
    boolean hasPendingRequest(
            @Param("userId") String userId,
            @Param("requestType") RequestType requestType,
            @Param("statuses") List<RequestStatus> statuses
    );
}
