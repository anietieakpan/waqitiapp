package com.waqiti.atm.repository;

import com.waqiti.atm.domain.ATMDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ATMDepositRepository extends JpaRepository<ATMDeposit, UUID> {

    @Query("SELECT SUM(d.cashAmount) FROM ATMDeposit d " +
           "WHERE d.accountId = :accountId " +
           "AND DATE(d.depositDate) = :date " +
           "AND d.status != 'REJECTED'")
    Optional<BigDecimal> sumCashDepositsByAccountIdAndDate(
            @Param("accountId") UUID accountId,
            @Param("date") LocalDate date);

    @Query("SELECT SUM(d.checkAmount) FROM ATMDeposit d " +
           "WHERE d.accountId = :accountId " +
           "AND DATE(d.depositDate) = :date " +
           "AND d.status != 'REJECTED'")
    Optional<BigDecimal> sumCheckDepositsByAccountIdAndDate(
            @Param("accountId") UUID accountId,
            @Param("date") LocalDate date);
}
