package com.waqiti.payment.repository;

import com.waqiti.payment.model.AtmTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ATM Transaction Repository
 */
@Repository
public interface AtmTransactionRepository extends JpaRepository<AtmTransaction, String> {

    Optional<AtmTransaction> findByTransactionId(String transactionId);

    List<AtmTransaction> findByCustomerId(String customerId);

    List<AtmTransaction> findByCustomerIdAndTransactionTimeBetween(
        String customerId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM AtmTransaction t WHERE t.customerId = :customerId " +
           "AND t.atmId = :atmId AND t.transactionTime >= :since")
    List<AtmTransaction> findRecentTransactionsByCustomerAndAtm(
        @Param("customerId") String customerId,
        @Param("atmId") String atmId,
        @Param("since") LocalDateTime since);

    void save(AtmTransaction transaction);
}
