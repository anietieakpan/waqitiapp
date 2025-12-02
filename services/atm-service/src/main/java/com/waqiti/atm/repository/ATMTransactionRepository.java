package com.waqiti.atm.repository;

import com.waqiti.atm.domain.ATMTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ATMTransactionRepository extends JpaRepository<ATMTransaction, UUID> {
    
    Page<ATMTransaction> findByAccountIdOrderByTransactionDateDesc(UUID accountId, Pageable pageable);
    
    List<ATMTransaction> findByAccountIdAndTransactionDateBetween(
            UUID accountId, LocalDateTime startDate, LocalDateTime endDate);
    
    List<ATMTransaction> findByAtmId(UUID atmId);
    
    List<ATMTransaction> findByCardId(UUID cardId);
    
    @Query("SELECT SUM(t.amount) FROM ATMTransaction t WHERE t.accountId = :accountId " +
           "AND t.transactionType = 'WITHDRAWAL' AND t.status = 'SUCCESS' " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumWithdrawalsByAccountIdAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(t) FROM ATMTransaction t WHERE t.accountId = :accountId " +
           "AND t.transactionDate >= :startDate")
    Long countTransactionsSince(@Param("accountId") UUID accountId, 
                               @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT t FROM ATMTransaction t WHERE t.status = 'PENDING' " +
           "AND t.transactionDate < :cutoffTime")
    List<ATMTransaction> findPendingTransactionsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    Optional<ATMTransaction> findByReferenceNumber(String referenceNumber);
}