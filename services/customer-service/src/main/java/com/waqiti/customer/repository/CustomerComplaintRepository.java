package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerComplaint;
import com.waqiti.customer.entity.CustomerComplaint.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerComplaint entity
 *
 * Provides data access methods for customer complaint management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerComplaintRepository extends JpaRepository<CustomerComplaint, UUID> {

    /**
     * Find complaint by complaint ID
     *
     * @param complaintId the unique complaint identifier
     * @return Optional containing the complaint if found
     */
    Optional<CustomerComplaint> findByComplaintId(String complaintId);

    /**
     * Find all complaints for a customer
     *
     * @param customerId the customer ID
     * @return list of complaints
     */
    List<CustomerComplaint> findByCustomerId(String customerId);

    /**
     * Find complaints by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of complaints
     */
    Page<CustomerComplaint> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find complaints by status
     *
     * @param status the complaint status
     * @param pageable pagination information
     * @return page of complaints
     */
    Page<CustomerComplaint> findByStatus(Status status, Pageable pageable);

    /**
     * Find complaints by status for a customer
     *
     * @param customerId the customer ID
     * @param status the complaint status
     * @param pageable pagination information
     * @return page of complaints
     */
    Page<CustomerComplaint> findByCustomerIdAndStatus(String customerId, Status status, Pageable pageable);

    /**
     * Find open complaints
     *
     * @param pageable pagination information
     * @return page of open complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.status = 'OPEN' ORDER BY c.severity DESC, c.createdAt ASC")
    Page<CustomerComplaint> findOpenComplaints(Pageable pageable);

    /**
     * Find in-progress complaints
     *
     * @param pageable pagination information
     * @return page of in-progress complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.status = 'IN_PROGRESS' ORDER BY c.slaDueDate ASC")
    Page<CustomerComplaint> findInProgressComplaints(Pageable pageable);

    /**
     * Find escalated complaints
     *
     * @param pageable pagination information
     * @return page of escalated complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.status = 'ESCALATED' ORDER BY c.severity DESC, c.createdAt ASC")
    Page<CustomerComplaint> findEscalatedComplaints(Pageable pageable);

    /**
     * Find complaints by severity
     *
     * @param severity the severity level
     * @param pageable pagination information
     * @return page of complaints
     */
    Page<CustomerComplaint> findBySeverity(Severity severity, Pageable pageable);

    /**
     * Find critical complaints
     *
     * @param pageable pagination information
     * @return page of critical complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.severity = 'CRITICAL' AND c.status NOT IN ('RESOLVED', 'CLOSED') " +
           "ORDER BY c.slaDueDate ASC")
    Page<CustomerComplaint> findCriticalComplaints(Pageable pageable);

    /**
     * Find high severity complaints (HIGH and CRITICAL)
     *
     * @param pageable pagination information
     * @return page of high severity complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.severity IN ('HIGH', 'CRITICAL') " +
           "AND c.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY c.severity DESC, c.slaDueDate ASC")
    Page<CustomerComplaint> findHighSeverityComplaints(Pageable pageable);

    /**
     * Find overdue complaints by SLA
     *
     * @param currentDate the current date/time
     * @param pageable pagination information
     * @return page of overdue complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.slaDueDate IS NOT NULL " +
           "AND c.slaDueDate < :currentDate AND c.status NOT IN ('RESOLVED', 'CLOSED') " +
           "ORDER BY c.slaDueDate ASC")
    Page<CustomerComplaint> findOverdueBySLA(@Param("currentDate") LocalDateTime currentDate, Pageable pageable);

    /**
     * Find complaints approaching SLA deadline
     *
     * @param currentDate the current date/time
     * @param thresholdDate the threshold date/time
     * @param pageable pagination information
     * @return page of complaints approaching SLA
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.slaDueDate IS NOT NULL " +
           "AND c.slaDueDate > :currentDate AND c.slaDueDate <= :thresholdDate " +
           "AND c.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY c.slaDueDate ASC")
    Page<CustomerComplaint> findApproachingSLA(
        @Param("currentDate") LocalDateTime currentDate,
        @Param("thresholdDate") LocalDateTime thresholdDate,
        Pageable pageable
    );

    /**
     * Find complaints by type
     *
     * @param complaintType the complaint type
     * @param pageable pagination information
     * @return page of complaints
     */
    Page<CustomerComplaint> findByComplaintType(ComplaintType complaintType, Pageable pageable);

    /**
     * Find complaints by category
     *
     * @param complaintCategory the complaint category
     * @param pageable pagination information
     * @return page of complaints
     */
    Page<CustomerComplaint> findByComplaintCategory(ComplaintCategory complaintCategory, Pageable pageable);

    /**
     * Find CFPB submitted complaints
     *
     * @param pageable pagination information
     * @return page of CFPB complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.cfpbSubmitted = true ORDER BY c.cfpbSubmissionDate DESC")
    Page<CustomerComplaint> findCFPBComplaints(Pageable pageable);

    /**
     * Find complaints assigned to a specific user
     *
     * @param assignedTo the user assigned to
     * @param pageable pagination information
     * @return page of complaints
     */
    Page<CustomerComplaint> findByAssignedTo(String assignedTo, Pageable pageable);

    /**
     * Find unassigned complaints
     *
     * @param pageable pagination information
     * @return page of unassigned complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.assignedTo IS NULL " +
           "AND c.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY c.severity DESC, c.createdAt ASC")
    Page<CustomerComplaint> findUnassignedComplaints(Pageable pageable);

    /**
     * Find complaints created within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY c.createdAt DESC")
    Page<CustomerComplaint> findByCreatedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find resolved complaints within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of resolved complaints
     */
    @Query("SELECT c FROM CustomerComplaint c WHERE c.resolvedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY c.resolvedAt DESC")
    Page<CustomerComplaint> findResolvedBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Count complaints by customer ID
     *
     * @param customerId the customer ID
     * @return count of complaints
     */
    long countByCustomerId(String customerId);

    /**
     * Count complaints by status
     *
     * @param status the complaint status
     * @return count of complaints
     */
    long countByStatus(Status status);

    /**
     * Count complaints by severity
     *
     * @param severity the severity level
     * @return count of complaints
     */
    long countBySeverity(Severity severity);

    /**
     * Count open complaints for a customer
     *
     * @param customerId the customer ID
     * @return count of open complaints
     */
    @Query("SELECT COUNT(c) FROM CustomerComplaint c WHERE c.customerId = :customerId " +
           "AND c.status NOT IN ('RESOLVED', 'CLOSED')")
    long countOpenByCustomerId(@Param("customerId") String customerId);

    /**
     * Count overdue complaints
     *
     * @param currentDate the current date/time
     * @return count of overdue complaints
     */
    @Query("SELECT COUNT(c) FROM CustomerComplaint c WHERE c.slaDueDate IS NOT NULL " +
           "AND c.slaDueDate < :currentDate AND c.status NOT IN ('RESOLVED', 'CLOSED')")
    long countOverdue(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Count CFPB submitted complaints
     *
     * @return count of CFPB complaints
     */
    @Query("SELECT COUNT(c) FROM CustomerComplaint c WHERE c.cfpbSubmitted = true")
    long countCFPBSubmitted();

    /**
     * Get average resolution time in hours
     *
     * @return average resolution time
     */
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (c.resolvedAt - c.createdAt))/3600.0) FROM CustomerComplaint c " +
           "WHERE c.resolvedAt IS NOT NULL")
    Double getAverageResolutionTimeHours();
}
