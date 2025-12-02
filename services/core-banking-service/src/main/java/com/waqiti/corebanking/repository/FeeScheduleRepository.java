package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.FeeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fee Schedule Repository
 * 
 * Repository interface for FeeSchedule entity operations
 */
@Repository
public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, UUID> {

    /**
     * Find active fee schedules
     */
    List<FeeSchedule> findByStatusAndEffectiveDateBeforeAndExpiryDateAfter(
        FeeSchedule.FeeScheduleStatus status, LocalDateTime effectiveDate, LocalDateTime expiryDate);

    /**
     * Find fee schedules by type and status
     */
    List<FeeSchedule> findByFeeTypeAndStatus(FeeSchedule.FeeType feeType, FeeSchedule.FeeScheduleStatus status);

    /**
     * Find fee schedules by type, status and period type
     */
    List<FeeSchedule> findByFeeTypeAndStatusAndPeriodType(
        FeeSchedule.FeeType feeType, 
        FeeSchedule.FeeScheduleStatus status,
        FeeSchedule.PeriodType periodType);

    /**
     * Find fee schedules by name
     */
    List<FeeSchedule> findByNameContainingIgnoreCase(String name);

    /**
     * Find active fee schedules by type
     */
    @Query("SELECT fs FROM FeeSchedule fs WHERE fs.feeType = :feeType " +
           "AND fs.status = 'ACTIVE' " +
           "AND fs.effectiveDate <= :now " +
           "AND (fs.expiryDate IS NULL OR fs.expiryDate > :now)")
    List<FeeSchedule> findActiveFeeSchedulesByType(@Param("feeType") FeeSchedule.FeeType feeType, 
                                                   @Param("now") LocalDateTime now);

    /**
     * Find default fee schedule for account type
     */
    @Query("SELECT fs FROM FeeSchedule fs WHERE fs.status = 'ACTIVE' " +
           "AND fs.effectiveDate <= :now " +
           "AND (fs.expiryDate IS NULL OR fs.expiryDate > :now) " +
           "AND (fs.appliesToAccountTypes IS NULL OR fs.appliesToAccountTypes LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:accountType, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\_'), '%')) " +
           "ORDER BY fs.effectiveDate DESC")
    Optional<FeeSchedule> findDefaultForAccountType(@Param("accountType") Account.AccountType accountType,
                                                   @Param("now") LocalDateTime now);

    /**
     * Overloaded method with current time
     */
    default Optional<FeeSchedule> findDefaultForAccountType(Account.AccountType accountType) {
        return findDefaultForAccountType(accountType, LocalDateTime.now());
    }

    /**
     * Find fee schedules expiring soon
     */
    @Query("SELECT fs FROM FeeSchedule fs WHERE fs.status = 'ACTIVE' " +
           "AND fs.expiryDate IS NOT NULL " +
           "AND fs.expiryDate BETWEEN :now AND :futureDate")
    List<FeeSchedule> findExpiringSoon(@Param("now") LocalDateTime now, 
                                      @Param("futureDate") LocalDateTime futureDate);

    /**
     * Count fee schedules by status
     */
    long countByStatus(FeeSchedule.FeeScheduleStatus status);

    /**
     * Find fee schedules by currency
     */
    List<FeeSchedule> findByCurrency(String currency);

    /**
     * Find fee schedules created by user
     */
    List<FeeSchedule> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    /**
     * Find fee schedules by calculation method
     */
    List<FeeSchedule> findByCalculationMethod(FeeSchedule.CalculationMethod calculationMethod);

    /**
     * Check if fee schedule name exists
     */
    boolean existsByName(String name);

    /**
     * Find superseded fee schedules
     */
    @Query("SELECT fs FROM FeeSchedule fs WHERE fs.status = 'SUPERSEDED' " +
           "AND fs.name = :name ORDER BY fs.createdAt DESC")
    List<FeeSchedule> findSupersededVersions(@Param("name") String name);

    /**
     * Find fee schedules with free transactions
     */
    @Query("SELECT fs FROM FeeSchedule fs WHERE fs.freeTransactionsPerPeriod IS NOT NULL " +
           "AND fs.freeTransactionsPerPeriod > 0 AND fs.status = 'ACTIVE'")
    List<FeeSchedule> findWithFreeTransactions();
}