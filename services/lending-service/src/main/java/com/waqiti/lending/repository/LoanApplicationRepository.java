package com.waqiti.lending.repository;

import com.waqiti.lending.domain.LoanApplication;
import com.waqiti.lending.domain.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Loan Application entities
 */
@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    /**
     * Find application by application ID
     */
    Optional<LoanApplication> findByApplicationId(String applicationId);

    /**
     * Check if application ID exists
     */
    boolean existsByApplicationId(String applicationId);

    /**
     * Find all applications for a borrower
     */
    List<LoanApplication> findByBorrowerIdOrderBySubmittedAtDesc(UUID borrowerId);

    /**
     * Find applications by status
     */
    Page<LoanApplication> findByApplicationStatus(ApplicationStatus status, Pageable pageable);

    /**
     * Find applications by borrower and status
     */
    List<LoanApplication> findByBorrowerIdAndApplicationStatus(UUID borrowerId, ApplicationStatus status);

    /**
     * Find pending applications (submitted or pending)
     */
    @Query("SELECT la FROM LoanApplication la WHERE la.applicationStatus IN ('SUBMITTED', 'PENDING') ORDER BY la.submittedAt ASC")
    List<LoanApplication> findPendingApplications();

    /**
     * Find applications requiring manual review
     */
    List<LoanApplication> findByApplicationStatusOrderBySubmittedAtAsc(ApplicationStatus status);

    /**
     * Find expired approved applications
     */
    @Query("SELECT la FROM LoanApplication la WHERE la.applicationStatus = 'APPROVED' AND la.expiresAt < :now")
    List<LoanApplication> findExpiredApprovedApplications(@Param("now") Instant now);

    /**
     * Count applications by status
     */
    long countByApplicationStatus(ApplicationStatus status);

    /**
     * Count applications by borrower
     */
    long countByBorrowerId(UUID borrowerId);

    /**
     * Find applications submitted in date range
     */
    @Query("SELECT la FROM LoanApplication la WHERE la.submittedAt BETWEEN :startDate AND :endDate ORDER BY la.submittedAt DESC")
    List<LoanApplication> findApplicationsInDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    /**
     * Find applications awaiting decision
     */
    @Query("SELECT la FROM LoanApplication la WHERE la.applicationStatus IN ('UNDER_REVIEW', 'MANUAL_REVIEW') AND la.reviewedAt IS NULL")
    List<LoanApplication> findApplicationsAwaitingDecision();

    /**
     * Get application statistics
     */
    @Query("SELECT la.applicationStatus, COUNT(la) FROM LoanApplication la GROUP BY la.applicationStatus")
    List<Object[]> getApplicationStatistics();
}
