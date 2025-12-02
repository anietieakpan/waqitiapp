package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.CurrencyTransactionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CtrRepository extends JpaRepository<CurrencyTransactionReport, String> {
    List<CurrencyTransactionReport> findByCustomerId(String customerId);
    List<CurrencyTransactionReport> findByTransactionId(String transactionId);
}
