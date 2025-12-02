package com.waqiti.atm.repository;

import com.waqiti.atm.domain.WithdrawalLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WithdrawalLimitRepository extends JpaRepository<WithdrawalLimit, UUID> {

    @Query("SELECT w.dailyLimit FROM WithdrawalLimit w " +
           "JOIN ATMCard c ON w.cardId = c.id " +
           "WHERE c.cardNumber = :cardNumber")
    Optional<BigDecimal> findDailyLimitByCardNumber(@Param("cardNumber") String cardNumber);

    @Query("SELECT w.perTransactionLimit FROM WithdrawalLimit w " +
           "JOIN ATMCard c ON w.cardId = c.id " +
           "WHERE c.cardNumber = :cardNumber")
    Optional<BigDecimal> findPerTransactionLimitByCardNumber(@Param("cardNumber") String cardNumber);
}
