package com.waqiti.payment.repository;

import com.waqiti.payment.entity.ACHTransaction;
import com.waqiti.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ACH Transaction Repository - Enhanced for ACHBatchProcessorService compatibility
 */
@Repository
public interface ACHTransactionRepository extends JpaRepository<ACHTransaction, Long> {

    Optional<ACHTransaction> findByTransactionId(String transactionId);

    List<ACHTransaction> findByBatchId(Long batchId);

    // String variant for ACHBatchProcessorService
    List<ACHTransaction> findByBatchId(String batchId);

    // Find by batch ID ordered by sequence number
    List<ACHTransaction> findByBatchIdOrderBySequenceNumber(String batchId);

    List<ACHTransaction> findByStatus(PaymentStatus status);

    // Check if transaction hash exists (duplicate detection)
    boolean existsByTransactionHashAndCreatedAtAfter(String transactionHash, LocalDateTime createdAfter);

    // Find recent transactions by hash
    Optional<ACHTransaction> findByTransactionHash(String transactionHash);

    // Count transactions by batch
    long countByBatchId(String batchId);
}
