package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.TransactionReversal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transaction Reversal Repository
 */
@Repository
public interface TransactionReversalRepository extends JpaRepository<TransactionReversal, UUID> {
    
    Optional<TransactionReversal> findByOriginalTransactionId(String originalTransactionId);
    
    Optional<TransactionReversal> findByReversalTransactionId(String reversalTransactionId);
    
    List<TransactionReversal> findByReversalDateBetweenOrderByReversalDateDesc(
        LocalDateTime startDate, LocalDateTime endDate);
    
    List<TransactionReversal> findByStatus(TransactionReversal.ReversalStatus status);
    
    boolean existsByOriginalTransactionId(String originalTransactionId);
}