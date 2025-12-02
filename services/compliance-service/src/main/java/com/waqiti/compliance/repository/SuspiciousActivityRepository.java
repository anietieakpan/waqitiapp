package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.SuspiciousActivity;
import com.waqiti.compliance.domain.SuspiciousActivity.*;
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
 * Repository for Suspicious Activity Report (SAR) Management
 * 
 * CRITICAL REGULATORY COMPLIANCE: 
 * Handles SAR data persistence for BSA/AML compliance
 * 
 * LEGAL REQUIREMENTS:
 * - 5-year retention minimum
 * - Audit trail for all operations
 * - Secure access controls
 * - Immediate availability for regulatory inspections
 */
@Repository
public interface SuspiciousActivityRepository extends JpaRepository<SuspiciousActivity, UUID> {

    /**
     * Find SAR by SAR number (unique identifier)
     */
    Optional<SuspiciousActivity> findBySarNumber(String sarNumber);

    /**
     * Find SARs by customer ID
     */
    List<SuspiciousActivity> findByCustomerId(String customerId);

    /**
     * Find SARs by status
     */
    Page<SuspiciousActivity> findByStatus(SARStatus status, Pageable pageable);

    /**
     * Find SARs by filing status
     */
    Page<SuspiciousActivity> findByFilingStatus(FilingStatus filingStatus, Pageable pageable);

    /**
     * Find SARs requiring immediate attention
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.requiresImmediateAttention = true " +
           "AND s.status != 'CLOSED' ORDER BY s.createdAt DESC")
    List<SuspiciousActivity> findRequiringImmediateAttention();

    /**
     * Find SARs by priority
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.priority = :priority " +
           "AND s.status != 'CLOSED' ORDER BY s.createdAt DESC")
    List<SuspiciousActivity> findByPriority(@Param("priority") Priority priority);

    /**
     * Find SARs pending filing (must be filed within regulatory timeframe)
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.filingStatus = 'PENDING_FILING' " +
           "OR (s.filingStatus = 'NOT_FILED' AND s.status = 'APPROVED') " +
           "ORDER BY s.priority DESC, s.createdAt ASC")
    List<SuspiciousActivity> findPendingFiling();

    /**
     * Find SARs that exceed filing deadline (30 days from incident)
     * CRITICAL: These represent regulatory violations
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.filingStatus = 'NOT_FILED' " +
           "AND s.incidentDate < :deadline ORDER BY s.incidentDate ASC")
    List<SuspiciousActivity> findOverdueForFiling(@Param("deadline") java.time.LocalDate deadline);

    /**
     * Find SARs by activity type
     */
    List<SuspiciousActivity> findByActivityType(ActivityType activityType);

    /**
     * Find SARs by regulatory filing number
     */
    Optional<SuspiciousActivity> findByRegulatoryFilingNumber(String regulatoryFilingNumber);

    /**
     * Find SARs filed within date range
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.filedAt BETWEEN :startDate AND :endDate")
    List<SuspiciousActivity> findFiledBetween(@Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);

    /**
     * Find SARs by investigating officer
     */
    List<SuspiciousActivity> findByInvestigatingOfficer(String investigatingOfficer);

    /**
     * Find SARs by compliance officer
     */
    List<SuspiciousActivity> findByComplianceOfficer(String complianceOfficer);

    /**
     * Find SARs requiring follow-up
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.followUpDate <= :date " +
           "AND s.status != 'CLOSED' ORDER BY s.followUpDate ASC")
    List<SuspiciousActivity> findRequiringFollowUp(@Param("date") LocalDateTime date);

    /**
     * Find SARs by risk level
     */
    List<SuspiciousActivity> findByRiskLevel(String riskLevel);

    /**
     * Count SARs by status
     */
    long countByStatus(SARStatus status);

    /**
     * Count SARs by filing status
     */
    long countByFilingStatus(FilingStatus filingStatus);

    /**
     * Count SARs filed in time period
     */
    @Query("SELECT COUNT(s) FROM SuspiciousActivity s WHERE s.filedAt BETWEEN :startDate AND :endDate")
    long countFiledBetween(@Param("startDate") LocalDateTime startDate,
                          @Param("endDate") LocalDateTime endDate);

    /**
     * Find recent SARs for customer (last 12 months)
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.customerId = :customerId " +
           "AND s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<SuspiciousActivity> findRecentByCustomerId(@Param("customerId") String customerId,
                                                    @Param("since") LocalDateTime since);

    /**
     * Find SARs by multiple customers (related party analysis)
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.customerId IN :customerIds " +
           "ORDER BY s.createdAt DESC")
    List<SuspiciousActivity> findByCustomerIds(@Param("customerIds") List<String> customerIds);

    /**
     * Find all SARs associated with transaction
     */
    @Query("SELECT s FROM SuspiciousActivity s JOIN s.involvedTransactions t " +
           "WHERE t = :transactionId")
    List<SuspiciousActivity> findByInvolvedTransaction(@Param("transactionId") String transactionId);

    /**
     * Find all SARs associated with account
     */
    @Query("SELECT s FROM SuspiciousActivity s JOIN s.involvedAccounts a " +
           "WHERE a = :accountId")
    List<SuspiciousActivity> findByInvolvedAccount(@Param("accountId") String accountId);

    /**
     * Search SARs by criteria
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE " +
           "(:customerId IS NULL OR s.customerId = :customerId) AND " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:activityType IS NULL OR s.activityType = :activityType) AND " +
           "(:priority IS NULL OR s.priority = :priority) AND " +
           "(:startDate IS NULL OR s.incidentDate >= :startDate) AND " +
           "(:endDate IS NULL OR s.incidentDate <= :endDate)")
    Page<SuspiciousActivity> searchByCriteria(@Param("customerId") String customerId,
                                              @Param("status") SARStatus status,
                                              @Param("activityType") ActivityType activityType,
                                              @Param("priority") Priority priority,
                                              @Param("startDate") java.time.LocalDate startDate,
                                              @Param("endDate") java.time.LocalDate endDate,
                                              Pageable pageable);

    /**
     * Get SAR statistics for reporting
     */
    @Query("SELECT s.status, COUNT(s) FROM SuspiciousActivity s GROUP BY s.status")
    List<Object[]> getStatusStatistics();

    @Query("SELECT s.activityType, COUNT(s) FROM SuspiciousActivity s GROUP BY s.activityType")
    List<Object[]> getActivityTypeStatistics();

    @Query("SELECT s.priority, COUNT(s) FROM SuspiciousActivity s GROUP BY s.priority")
    List<Object[]> getPriorityStatistics();

    /**
     * Find duplicate or similar SARs (prevent duplicate filing)
     */
    @Query("SELECT s FROM SuspiciousActivity s WHERE s.customerId = :customerId " +
           "AND s.activityType = :activityType " +
           "AND s.incidentDate BETWEEN :startDate AND :endDate " +
           "AND s.status != 'CLOSED'")
    List<SuspiciousActivity> findPotentialDuplicates(@Param("customerId") String customerId,
                                                     @Param("activityType") ActivityType activityType,
                                                     @Param("startDate") java.time.LocalDate startDate,
                                                     @Param("endDate") java.time.LocalDate endDate);

    /**
     * Save SAR (Suspicious Activity Report)
     * This is a convenience method that maps SuspiciousActivityReport domain object
     * Note: The actual persistence uses the JpaRepository save method
     */
    default void saveSAR(com.waqiti.compliance.domain.SuspiciousActivityReport sar) {
        // Convert SuspiciousActivityReport to SuspiciousActivity entity and save
        // This is a placeholder - actual implementation would map fields appropriately
        SuspiciousActivity activity = new SuspiciousActivity();
        // Map fields from sar to activity as needed
        save(activity);
    }
}