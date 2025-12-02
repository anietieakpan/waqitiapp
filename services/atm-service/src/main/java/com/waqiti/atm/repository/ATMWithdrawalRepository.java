package com.waqiti.atm.repository;

import com.waqiti.atm.domain.ATMWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ATMWithdrawalRepository extends JpaRepository<ATMWithdrawal, UUID> {

    @Query("SELECT SUM(w.amount) FROM ATMWithdrawal w " +
           "JOIN ATMCard c ON w.cardId = c.id " +
           "WHERE c.cardNumber = :cardNumber " +
           "AND DATE(w.withdrawalDate) = :date " +
           "AND w.status = 'COMPLETED'")
    Optional<BigDecimal> sumWithdrawalAmountByCardNumberAndDate(
            @Param("cardNumber") String cardNumber,
            @Param("date") LocalDate date);

    @Query("SELECT COUNT(w) FROM ATMWithdrawal w " +
           "JOIN ATMCard c ON w.cardId = c.id " +
           "WHERE c.cardNumber = :cardNumber " +
           "AND DATE(w.withdrawalDate) = :date " +
           "AND w.status = 'COMPLETED'")
    long countWithdrawalsByCardNumberAndDate(
            @Param("cardNumber") String cardNumber,
            @Param("date") LocalDate date);
}
