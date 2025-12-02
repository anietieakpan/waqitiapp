package com.waqiti.payment.repository;

import com.waqiti.payment.entity.ACHBatch;
import com.waqiti.payment.entity.ACHBatchStatus;
import com.waqiti.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ACH Batch Repository - Enhanced for ACHBatchProcessorService compatibility
 */
@Repository
public interface ACHBatchRepository extends JpaRepository<ACHBatch, String> {

    // findById is inherited from JpaRepository<ACHBatch, String>

    // Alias method for backward compatibility
    default Optional<ACHBatch> findByBatchId(String batchId) {
        return findById(batchId);
    }

    // Count methods for ACHBatchStatus
    long countByStatus(ACHBatchStatus status);

    // Find by status methods
    List<ACHBatch> findByStatus(PaymentStatus status);

    List<ACHBatch> findByStatus(ACHBatchStatus status);

    // Find by effective date
    List<ACHBatch> findByEffectiveDate(LocalDate effectiveDate);

    List<ACHBatch> findByStatusAndEffectiveDate(PaymentStatus status, LocalDate effectiveDate);

    // Find by company with pagination
    Page<ACHBatch> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable pageable);

    // Find by status and scheduled date (for scheduled processing job)
    List<ACHBatch> findByStatusAndScheduledProcessingDateBefore(
        ACHBatchStatus status, LocalDateTime dateTime);

    // Find by status and created date (for cleanup job)
    List<ACHBatch> findByStatusAndCreatedAtBefore(
        ACHBatchStatus status, LocalDateTime dateTime);

    @Query("SELECT b FROM ACHBatch b WHERE b.serviceClassCode = :secCode AND b.effectiveDate = :effectiveDate")
    List<ACHBatch> findBySECCodeAndEffectiveDate(@Param("secCode") String secCode, @Param("effectiveDate") LocalDate effectiveDate);

    // Additional query methods for enhanced functionality
    @Query("SELECT COUNT(b) FROM ACHBatch b WHERE b.status = :status")
    long countBatchesByStatus(@Param("status") ACHBatchStatus status);

    @Query("SELECT b FROM ACHBatch b WHERE b.companyId = :companyId AND b.status = :status")
    List<ACHBatch> findByCompanyIdAndStatus(@Param("companyId") String companyId, @Param("status") ACHBatchStatus status);
}
