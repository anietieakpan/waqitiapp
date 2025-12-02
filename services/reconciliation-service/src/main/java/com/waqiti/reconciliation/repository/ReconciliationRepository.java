package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.domain.ReconciliationRecord;
import com.waqiti.reconciliation.domain.ReconciliationStatus;
import com.waqiti.reconciliation.domain.ReconciliationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReconciliationRepository extends JpaRepository<ReconciliationRecord, UUID> {
    
    Page<ReconciliationRecord> findByStatus(ReconciliationStatus status, Pageable pageable);
    
    Page<ReconciliationRecord> findByReconciliationType(ReconciliationType type, Pageable pageable);
    
    Page<ReconciliationRecord> findByReconciliationDate(LocalDate reconciliationDate, Pageable pageable);
    
    @Query("SELECT r FROM ReconciliationRecord r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:type IS NULL OR r.reconciliationType = :type) AND " +
           "(:startDate IS NULL OR r.reconciliationDate >= :startDate) AND " +
           "(:endDate IS NULL OR r.reconciliationDate <= :endDate)")
    Page<ReconciliationRecord> findByFilters(@Param("status") ReconciliationStatus status,
                                            @Param("type") ReconciliationType type,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate,
                                            Pageable pageable);
    
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.reconciliationDate = :date " +
           "AND r.reconciliationType = :type")
    Optional<ReconciliationRecord> findByDateAndType(@Param("date") LocalDate date,
                                                     @Param("type") ReconciliationType type);
    
    @Query("SELECT COUNT(r) FROM ReconciliationRecord r WHERE r.status = :status")
    Long countByStatus(@Param("status") ReconciliationStatus status);
    
    @Query("SELECT r.reconciliationType, COUNT(r) FROM ReconciliationRecord r " +
           "WHERE r.reconciliationDate >= :startDate GROUP BY r.reconciliationType")
    List<Object[]> countByTypeSince(@Param("startDate") LocalDate startDate);
    
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.status = 'DISCREPANCY' " +
           "ORDER BY r.discrepancyAmount DESC")
    List<ReconciliationRecord> findDiscrepanciesOrderByAmount();
    
    @Query("SELECT SUM(ABS(r.discrepancyAmount)) FROM ReconciliationRecord r " +
           "WHERE r.status = 'DISCREPANCY' " +
           "AND r.reconciliationDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalDiscrepancyAmount(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);
    
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.status = 'PENDING' " +
           "AND r.reconciliationDate < :cutoffDate")
    List<ReconciliationRecord> findOverdueReconciliations(@Param("cutoffDate") LocalDate cutoffDate);
    
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.status IN ('RUNNING', 'PENDING') " +
           "AND r.startTime < :timeout")
    List<ReconciliationRecord> findTimedOutReconciliations(@Param("timeout") LocalDateTime timeout);
    
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (r.completedTime - r.startTime))/60) " +
           "FROM ReconciliationRecord r WHERE r.status = 'COMPLETED' " +
           "AND r.reconciliationType = :type")
    Double getAverageProcessingTimeMinutes(@Param("type") ReconciliationType type);
    
    @Query("SELECT COUNT(r) * 100.0 / (SELECT COUNT(rs) FROM ReconciliationRecord rs " +
           "WHERE rs.reconciliationType = :type) FROM ReconciliationRecord r " +
           "WHERE r.reconciliationType = :type AND r.status = 'COMPLETED'")
    Double getSuccessRateByType(@Param("type") ReconciliationType type);
    
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.externalReference = :reference")
    Optional<ReconciliationRecord> findByExternalReference(@Param("reference") String reference);
    
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.batchId = :batchId " +
           "ORDER BY r.startTime ASC")
    List<ReconciliationRecord> findByBatchId(@Param("batchId") String batchId);
    
    @Modifying
    @Query("UPDATE ReconciliationRecord r SET r.status = :status, " +
           "r.completedTime = :completedTime WHERE r.id = :recordId")
    void updateStatus(@Param("recordId") UUID recordId,
                     @Param("status") ReconciliationStatus status,
                     @Param("completedTime") LocalDateTime completedTime);
    
    @Modifying
    @Query("UPDATE ReconciliationRecord r SET r.discrepancyAmount = :amount, " +
           "r.discrepancyReason = :reason, r.status = 'DISCREPANCY' " +
           "WHERE r.id = :recordId")
    void updateDiscrepancy(@Param("recordId") UUID recordId,
                          @Param("amount") BigDecimal amount,
                          @Param("reason") String reason);
    
    @Modifying
    @Query("UPDATE ReconciliationRecord r SET r.reconciledBy = :reconciledBy, " +
           "r.reconciliationNotes = :notes WHERE r.id = :recordId")
    void updateReconciliationDetails(@Param("recordId") UUID recordId,
                                    @Param("reconciledBy") String reconciledBy,
                                    @Param("notes") String notes);
    
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.reconciliationDate " +
           "BETWEEN :startDate AND :endDate ORDER BY r.reconciliationDate DESC")
    List<ReconciliationRecord> findReconciliationsInRange(@Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);
    
    @Query("SELECT DISTINCT r.reconciliationDate FROM ReconciliationRecord r " +
           "WHERE r.reconciliationType = :type ORDER BY r.reconciliationDate DESC")
    List<LocalDate> findAvailableReconciliationDates(@Param("type") ReconciliationType type);
    
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.status = 'DISCREPANCY' " +
           "AND r.autoResolved = false ORDER BY r.reconciliationDate DESC")
    List<ReconciliationRecord> findUnresolvedDiscrepancies();
    
    @Query("SELECT COUNT(r) FROM ReconciliationRecord r WHERE r.reconciliationDate = :date " +
           "AND r.status = 'COMPLETED'")
    Long countCompletedForDate(@Param("date") LocalDate date);
    
    boolean existsByReconciliationDateAndReconciliationType(LocalDate date, ReconciliationType type);
    
    void deleteByReconciliationDateBefore(LocalDate date);
}