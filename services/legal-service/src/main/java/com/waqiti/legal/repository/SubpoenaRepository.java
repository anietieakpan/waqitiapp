package com.waqiti.legal.repository;

import com.waqiti.legal.domain.Subpoena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Subpoena Repository
 *
 * Complete data access layer for Subpoena entities with custom query methods
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-17
 */
@Repository
public interface SubpoenaRepository extends JpaRepository<Subpoena, UUID> {

    /**
     * Find subpoena by subpoena ID
     */
    Optional<Subpoena> findBySubpoenaId(String subpoenaId);

    /**
     * Find all subpoenas for a customer
     */
    List<Subpoena> findByCustomerId(String customerId);

    /**
     * Find all subpoenas by case number
     */
    List<Subpoena> findByCaseNumber(String caseNumber);

    /**
     * Find all subpoenas by status
     */
    List<Subpoena> findByStatus(Subpoena.SubpoenaStatus status);

    /**
     * Find all incomplete subpoenas
     */
    List<Subpoena> findByCompletedFalse();

    /**
     * Find overdue subpoenas (past deadline and not completed)
     */
    @Query("SELECT s FROM Subpoena s WHERE s.completed = false AND s.responseDeadline < :currentDate")
    List<Subpoena> findOverdueSubpoenas(@Param("currentDate") LocalDate currentDate);

    /**
     * Find subpoenas approaching deadline (within specified days)
     */
    @Query("SELECT s FROM Subpoena s WHERE s.completed = false AND s.responseDeadline BETWEEN :startDate AND :endDate")
    List<Subpoena> findApproachingDeadline(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find subpoenas assigned to user
     */
    List<Subpoena> findByAssignedTo(String userId);

    /**
     * Find subpoenas by priority level
     */
    List<Subpoena> findByPriorityLevel(Subpoena.PriorityLevel priorityLevel);

    /**
     * Find escalated subpoenas
     */
    List<Subpoena> findByEscalatedToLegalCounselTrue();

    /**
     * Find subpoenas pending customer notification
     */
    @Query("SELECT s FROM Subpoena s WHERE s.customerNotificationRequired = true AND s.customerNotified = false")
    List<Subpoena> findPendingCustomerNotification();

    /**
     * Find subpoenas by issuing court
     */
    List<Subpoena> findByIssuingCourt(String issuingCourt);

    /**
     * Find subpoenas by type
     */
    List<Subpoena> findBySubpoenaType(Subpoena.SubpoenaType subpoenaType);

    /**
     * Count active subpoenas (not completed)
     */
    long countByCompletedFalse();

    /**
     * Count overdue subpoenas
     */
    @Query("SELECT COUNT(s) FROM Subpoena s WHERE s.completed = false AND s.responseDeadline < :currentDate")
    long countOverdueSubpoenas(@Param("currentDate") LocalDate currentDate);

    /**
     * Find subpoenas requiring action (validated but not completed)
     */
    @Query("SELECT s FROM Subpoena s WHERE s.validated = true AND s.completed = false ORDER BY s.responseDeadline ASC")
    List<Subpoena> findRequiringAction();

    /**
     * Check if subpoena exists for case number
     */
    boolean existsByCaseNumber(String caseNumber);
}
